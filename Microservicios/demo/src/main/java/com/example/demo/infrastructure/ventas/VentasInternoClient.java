package com.example.demo.infrastructure.ventas;

import com.example.demo.infrastructure.security.ServicioTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Canal interno auth-service -> msvc-ventas para pagos de pasarela (A-03 / A-04).
 * Llama directamente al servicio (no por el Gateway, que bloquea /api/ventas/internal/**)
 * con la credencial de servicio de corta duración.
 */
@Component
public class VentasInternoClient {

    private static final Logger log = LoggerFactory.getLogger(VentasInternoClient.class);
    private static final String BASE_PATH = "/api/ventas/internal/pagos-pasarela/";

    private final RestClient restClient;
    private final ServicioTokenProvider servicioTokenProvider;

    public VentasInternoClient(RestClient.Builder builder,
                               ServicioTokenProvider servicioTokenProvider,
                               @Value("${app.msvc-ventas.url:http://localhost:8083}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.servicioTokenProvider = servicioTokenProvider;
    }

    public VentaResumenPago obtenerResumen(long ventaId) {
        try {
            VentaResumenPago resumen = restClient.get()
                    .uri(BASE_PATH + ventaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + servicioTokenProvider.emitirTokenPagos())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(VentaResumenPago.class);
            if (resumen == null) {
                throw new VentasNoDisponibleException("msvc-ventas no devolvió la venta");
            }
            return resumen;
        } catch (RestClientResponseException ex) {
            throw traducir(ex, "consultar la venta");
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a msvc-ventas al consultar la venta {}", ventaId);
            throw new VentasNoDisponibleException("El servicio de ventas no está disponible");
        }
    }

    public RegistroPagoVenta registrarPagoAprobado(long ventaId, String referenciaTransaccion,
                                                   long montoCentavos, String pasarela, String clientTransactionId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("referenciaTransaccion", referenciaTransaccion);
        cuerpo.put("montoCentavos", montoCentavos);
        cuerpo.put("pasarela", pasarela);
        cuerpo.put("clientTransactionId", clientTransactionId);
        try {
            RegistroPagoVenta registro = restClient.post()
                    .uri(BASE_PATH + ventaId + "/aprobar")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + servicioTokenProvider.emitirTokenPagos())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(RegistroPagoVenta.class);
            if (registro == null) {
                throw new VentasNoDisponibleException("msvc-ventas no confirmó el registro del pago");
            }
            return registro;
        } catch (RestClientResponseException ex) {
            throw traducir(ex, "registrar el pago");
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a msvc-ventas al registrar el pago de la venta {}", ventaId);
            throw new VentasNoDisponibleException("El servicio de ventas no está disponible");
        }
    }

    private RuntimeException traducir(RestClientResponseException ex, String operacion) {
        int estado = ex.getStatusCode().value();
        log.warn("msvc-ventas respondió HTTP {} al {}", estado, operacion);
        if (estado == HttpStatus.NOT_FOUND.value()) {
            return new NoSuchElementException("Venta no encontrada");
        }
        if (ex.getStatusCode().is4xxClientError()) {
            return new VentaPagoRechazadoException("La venta no admite registrar este pago");
        }
        return new VentasNoDisponibleException("El servicio de ventas no está disponible");
    }
}
