package com.example.demo.domain.service;

import com.example.demo.application.dto.PayphoneConfirmacionDto;
import com.example.demo.application.dto.PayphoneConfirmarRequestDto;
import com.example.demo.application.dto.PayphonePrepararResponseDto;
import com.example.demo.infrastructure.payphone.PayphoneApiClient;
import com.example.demo.infrastructure.payphone.PayphoneIntegracionException;
import com.example.demo.infrastructure.ventas.RegistroPagoVenta;
import com.example.demo.infrastructure.ventas.VentaPagoRechazadoException;
import com.example.demo.infrastructure.ventas.VentaResumenPago;
import com.example.demo.infrastructure.ventas.VentasInternoClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cobros con Payphone resueltos íntegramente en el backend.
 *
 *  - C-02: el token de la empresa solo se usa aquí; el navegador recibe enlaces y resultados saneados.
 *  - A-03: el monto se toma de la venta creada ANTES del cobro (msvc-ventas), nunca del navegador.
 *  - A-04: tras verificar el cobro con Payphone, la venta se completa por un canal interno
 *          autorizado e idempotente que descuenta stock una sola vez.
 *  - M-06: la notificación (webhook) solo dispara la misma verificación servidor-a-servidor;
 *          su contenido nunca se da por cierto.
 */
@Service
public class PayphonePagoService {

    private static final Logger log = LoggerFactory.getLogger(PayphonePagoService.class);

    static final int MAX_CLIENT_TX_ID = 50;
    static final String PASARELA = "PAYPHONE";
    private static final Pattern CLIENT_TX_ID = Pattern.compile("^FD-(\\d{1,18})-[A-Za-z0-9]{6,40}$");
    private static final Set<String> ESTADOS_PAGO_COBRABLES = Set.of("PENDIENTE", "PROCESANDO");

    /** Resultados de la venta tras confirmar, además de los de msvc-ventas (COMPLETADA, YA_PROCESADA, STOCK_PENDIENTE). */
    public static final String MONTO_NO_COINCIDE = "MONTO_NO_COINCIDE";
    public static final String REQUIERE_REVISION = "REQUIERE_REVISION";

    private final ConfiguracionPagosService configuracionPagosService;
    private final PayphoneApiClient payphoneApiClient;
    private final VentasInternoClient ventasInternoClient;
    private final String responseUrl;
    private final String cancellationUrl;

    public PayphonePagoService(
            ConfiguracionPagosService configuracionPagosService,
            PayphoneApiClient payphoneApiClient,
            VentasInternoClient ventasInternoClient,
            @Value("${payphone.response-url:http://localhost:3000/checkout/payphone-confirmacion}") String responseUrl,
            @Value("${payphone.cancellation-url:http://localhost:3000/checkout}") String cancellationUrl) {
        this.configuracionPagosService = configuracionPagosService;
        this.payphoneApiClient = payphoneApiClient;
        this.ventasInternoClient = ventasInternoClient;
        this.responseUrl = responseUrl;
        this.cancellationUrl = cancellationUrl;
    }

    // ------------------------------------------------------------------ preparar (A-03)

    /**
     * Prepara el cobro de una venta existente. Montos tomados de la venta en el servidor.
     * Solo el cliente comprador de la venta puede iniciar su pago.
     */
    public PayphonePrepararResponseDto preparar(Long ventaId, String emailUsuario) {
        if (ventaId == null || ventaId <= 0) {
            throw new IllegalArgumentException("Venta inválida");
        }
        VentaResumenPago venta = ventasInternoClient.obtenerResumen(ventaId);

        if (emailUsuario == null || venta.clienteEmail() == null
                || !venta.clienteEmail().trim().equalsIgnoreCase(emailUsuario.trim())) {
            throw new AccessDeniedException("Solo el comprador puede pagar esta venta");
        }
        if (!"PENDIENTE".equals(venta.estado()) || !ESTADOS_PAGO_COBRABLES.contains(venta.estadoPago())) {
            throw new IllegalStateException("La venta no está pendiente de pago");
        }
        if (!"TARJETA".equals(venta.metodoPago())) {
            throw new IllegalArgumentException("La venta no fue creada para pago con Payphone");
        }
        String token = tokenDeEmpresa(venta.empresaId());

        Map<String, Object> cuerpo = new LinkedHashMap<>(desgloseDesdeVenta(venta));
        String clientTransactionId = generarClientTransactionId(ventaId);
        cuerpo.put("currency", "USD");
        cuerpo.put("clientTransactionId", clientTransactionId);
        cuerpo.put("reference", "Venta " + (venta.numeroFactura() != null ? venta.numeroFactura() : ventaId));
        // URLs de retorno definidas por el servidor, no por el navegador
        cuerpo.put("responseUrl", responseUrl);
        cuerpo.put("cancellationUrl", cancellationUrl);

        JsonNode respuesta = payphoneApiClient.preparar(token, cuerpo);

        String payWithPayPhone = urlSegura(texto(respuesta, "payWithPayPhone"));
        String payWithCard = urlSegura(texto(respuesta, "payWithCard"));
        if (payWithPayPhone == null && payWithCard == null) {
            throw new PayphoneIntegracionException("Payphone no devolvió un enlace de pago válido");
        }
        return new PayphonePrepararResponseDto(
                clientTransactionId, texto(respuesta, "paymentId"), payWithPayPhone, payWithCard);
    }

    /**
     * Desglose en centavos a partir de la venta. Payphone exige
     * amount = amountWithoutTax + amountWithTax + tax + service + tip.
     */
    static Map<String, Object> desgloseDesdeVenta(VentaResumenPago venta) {
        long total = centavos(venta.total());
        long subtotal = centavos(venta.subtotal());
        long impuesto = centavos(venta.impuesto());
        long envio = total - subtotal - impuesto;
        if (total <= 0 || subtotal < 0 || impuesto < 0 || envio < 0) {
            throw new IllegalStateException("Los importes de la venta son inconsistentes");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("amount", total);
        d.put("amountWithoutTax", impuesto > 0 ? 0L : subtotal);
        d.put("amountWithTax", impuesto > 0 ? subtotal : 0L);
        d.put("tax", impuesto);
        d.put("service", envio);
        d.put("tip", 0L);
        return d;
    }

    // ------------------------------------------------------------------ confirmar (A-04)

    /** Confirmación tras el retorno del navegador desde Payphone. */
    public PayphoneConfirmacionDto confirmar(PayphoneConfirmarRequestDto solicitud) {
        if (solicitud == null || solicitud.getId() == null || solicitud.getId() <= 0
                || solicitud.getClientTxId() == null || solicitud.getClientTxId().isBlank()) {
            throw new IllegalArgumentException("Faltan parámetros de la transacción (id o clientTxId)");
        }
        String clientTxId = solicitud.getClientTxId().trim();
        Long ventaId = ventaIdDesdeClientTxId(clientTxId);
        if (ventaId == null) {
            throw new IllegalArgumentException("clientTxId inválido");
        }
        return reconciliar(ventaId, solicitud.getId(), clientTxId);
    }

    // ------------------------------------------------------------------ webhook (M-06)

    /**
     * Notificación de Payphone. Solo se usan id y clientTransactionId para volver a consultar
     * a Payphone con el token del comercio; el resto del cuerpo se ignora (no es confiable).
     * Devuelve vacío si la transacción no pertenece a esta tienda.
     */
    public Optional<PayphoneConfirmacionDto> procesarNotificacion(JsonNode cuerpo) {
        if (cuerpo == null || !cuerpo.isObject()) {
            throw new IllegalArgumentException("Notificación vacía");
        }
        String clientTxId = primerTexto(cuerpo, "clientTransactionId", "ClientTransactionId", "clientTxId");
        Long id = primerLargo(cuerpo, "transactionId", "TransactionId", "id", "Id");
        if (clientTxId == null || id == null || id <= 0) {
            throw new IllegalArgumentException("Notificación sin transactionId o clientTransactionId");
        }
        Long ventaId = ventaIdDesdeClientTxId(clientTxId.trim());
        if (ventaId == null) {
            log.info("Notificación de Payphone ignorada: clientTransactionId ajeno a la tienda");
            return Optional.empty();
        }
        return Optional.of(reconciliar(ventaId, id, clientTxId.trim()));
    }

    // ------------------------------------------------------------------ núcleo común

    private PayphoneConfirmacionDto reconciliar(long ventaId, long id, String clientTxId) {
        VentaResumenPago venta = ventasInternoClient.obtenerResumen(ventaId);
        String token = tokenDeEmpresa(venta.empresaId());

        JsonNode r = payphoneApiClient.confirmar(token, id, clientTxId);

        Integer statusCode = entero(r, "statusCode");
        Long transactionId = largo(r, "transactionId");
        Long amount = largo(r, "amount");
        String txDevuelto = texto(r, "clientTransactionId");

        PayphoneConfirmacionDto.PayphoneConfirmacionDtoBuilder dto = PayphoneConfirmacionDto.builder()
                .statusCode(statusCode)
                .transactionStatus(texto(r, "transactionStatus"))
                .transactionId(transactionId)
                .clientTransactionId(txDevuelto)
                .amount(amount)
                .currency(texto(r, "currency"))
                .cardType(texto(r, "cardType"))
                .authorizationCode(texto(r, "authorizationCode"))
                .ventaId(ventaId)
                .numeroFactura(venta.numeroFactura());

        if (txDevuelto != null && !txDevuelto.equals(clientTxId)) {
            log.error("Payphone devolvió otro clientTransactionId para la venta {}", ventaId);
            return dto.resultadoVenta(REQUIERE_REVISION).build();
        }
        if (statusCode == null || statusCode != 3) {
            // Cancelado (2) u otro estado: la venta queda PENDIENTE y no se descuenta stock
            return dto.build();
        }

        long esperado = centavos(venta.total());
        if (amount == null || amount != esperado) {
            log.error("Monto cobrado por Payphone no coincide con la venta {} (esperado {} centavos)", ventaId, esperado);
            return dto.resultadoVenta(MONTO_NO_COINCIDE).build();
        }

        String referencia = String.valueOf(transactionId != null ? transactionId : id);
        try {
            RegistroPagoVenta registro = ventasInternoClient.registrarPagoAprobado(
                    ventaId, referencia, amount, PASARELA, clientTxId);
            return dto.resultadoVenta(registro.resultado()).build();
        } catch (VentaPagoRechazadoException ex) {
            log.error("Pago aprobado de la venta {} no pudo registrarse; requiere revisión", ventaId);
            return dto.resultadoVenta(REQUIERE_REVISION).build();
        }
    }

    private String tokenDeEmpresa(Long empresaId) {
        return configuracionPagosService.obtenerTokenPayphonePorEmpresa(empresaId)
                .orElseThrow(() -> new NoSuchElementException("La empresa no tiene Payphone configurado"));
    }

    static Long ventaIdDesdeClientTxId(String clientTxId) {
        if (clientTxId == null || clientTxId.length() > MAX_CLIENT_TX_ID) {
            return null;
        }
        Matcher m = CLIENT_TX_ID.matcher(clientTxId);
        if (!m.matches()) {
            return null;
        }
        long ventaId = Long.parseLong(m.group(1));
        return ventaId > 0 ? ventaId : null;
    }

    private String generarClientTransactionId(Long ventaId) {
        String id = "FD-" + ventaId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return id.length() > MAX_CLIENT_TX_ID ? id.substring(0, MAX_CLIENT_TX_ID) : id;
    }

    static long centavos(BigDecimal valor) {
        return valor == null ? 0L : valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** Solo se aceptan enlaces https de dominios de Payphone, para no redirigir a terceros. */
    private String urlSegura(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean dominioPayphone = host.equals("payphonetodoesposible.com")
                    || host.endsWith(".payphonetodoesposible.com");
            return "https".equalsIgnoreCase(uri.getScheme()) && dominioPayphone ? uri.toString() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String primerTexto(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            String valor = texto(nodo, campo);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    private static Long primerLargo(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            Long valor = largo(nodo, campo);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static Integer entero(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() || !valor.canConvertToInt() ? null : valor.asInt();
    }

    private static Long largo(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (valor.isTextual()) {
            try {
                return Long.parseLong(valor.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return valor.canConvertToLong() ? valor.asLong() : null;
    }
}
