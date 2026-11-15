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
import com.example.demo.infrastructure.ventas.VentasNoDisponibleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Payphone en el servidor:
 *  C-02 token solo hacia Payphone · A-03 monto desde la venta · A-04 finalización interna
 *  idempotente · M-06 la notificación solo dispara la verificación con Payphone.
 */
class PayphonePagoServiceTest {

    private static final String BASE = "https://payphone.test";
    private static final String TOKEN_A = "tok-empresa-A-secreto";
    private static final String TOKEN_B = "tok-empresa-B-secreto";
    private static final String RESPONSE_URL = "https://tienda.test/checkout/payphone-confirmacion";
    private static final String COMPRADOR = "comprador@correo.test";
    private static final String TX = "FD-77-abcdef1234567890abcd";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer payphone;
    private VentasInternoClient ventas;
    private PayphonePagoService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        payphone = MockRestServiceServer.bindTo(builder).build();
        PayphoneApiClient cliente = new PayphoneApiClient(builder, BASE);

        ConfiguracionPagosService configuracion = mock(ConfiguracionPagosService.class);
        when(configuracion.obtenerTokenPayphonePorEmpresa(10L)).thenReturn(Optional.of(TOKEN_A));
        when(configuracion.obtenerTokenPayphonePorEmpresa(20L)).thenReturn(Optional.of(TOKEN_B));
        when(configuracion.obtenerTokenPayphonePorEmpresa(30L)).thenReturn(Optional.empty());

        ventas = mock(VentasInternoClient.class);
        // Venta 77 de la empresa 10: subtotal 10,00 + IVA 1,50 + envío 0,80 = 12,30
        when(ventas.obtenerResumen(77L)).thenReturn(venta(77L, 10L, "PENDIENTE", "PENDIENTE", "TARJETA"));

        service = new PayphonePagoService(configuracion, cliente, ventas, RESPONSE_URL, "https://tienda.test/checkout");
    }

    // ------------------------------------------------------------------ preparar (A-03)

    @Test
    void prepararCalculaElMontoDesdeLaVentaYUsaElTokenDeSuEmpresa() throws Exception {
        payphone.expect(requestTo(BASE + "/api/button/Prepare"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(jsonPath("$.amount").value(1230))
                .andExpect(jsonPath("$.amountWithTax").value(1000))
                .andExpect(jsonPath("$.amountWithoutTax").value(0))
                .andExpect(jsonPath("$.tax").value(150))
                .andExpect(jsonPath("$.service").value(80))
                .andExpect(jsonPath("$.tip").value(0))
                .andExpect(jsonPath("$.responseUrl").value(RESPONSE_URL))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andRespond(withSuccess("""
                        {"paymentId":"pay-1",
                         "payWithPayPhone":"https://pay.payphonetodoesposible.com/PayPhone/Index?paymentId=pay-1",
                         "payWithCard":"https://pay.payphonetodoesposible.com/Anonymous/Index?paymentId=pay-1",
                         "token":"eco-que-no-debe-salir"}
                        """, MediaType.APPLICATION_JSON));

        PayphonePrepararResponseDto r = service.preparar(77L, "Comprador@Correo.test");
        String json = objectMapper.writeValueAsString(r);

        payphone.verify();
        assertTrue(r.getClientTransactionId().startsWith("FD-77-"));
        assertTrue(r.getClientTransactionId().length() <= 50);
        assertFalse(json.toLowerCase().contains("token"), json);
    }

    @Test
    void ventaSinIvaUsaAmountWithoutTax() {
        Map<String, Object> d = PayphonePagoService.desgloseDesdeVenta(new VentaResumenPago(
                1L, "F", 10L, COMPRADOR, new BigDecimal("8.00"), BigDecimal.ZERO, new BigDecimal("13.00"),
                "PENDIENTE", "PENDIENTE", "TARJETA", null));
        assertEquals(1300L, d.get("amount"));
        assertEquals(800L, d.get("amountWithoutTax"));
        assertEquals(0L, d.get("amountWithTax"));
        assertEquals(500L, d.get("service"));
    }

    @Test
    void importesInconsistentesNoSeCobran() {
        assertThrows(IllegalStateException.class, () -> PayphonePagoService.desgloseDesdeVenta(new VentaResumenPago(
                1L, "F", 10L, COMPRADOR, new BigDecimal("10.00"), new BigDecimal("1.50"), new BigDecimal("5.00"),
                "PENDIENTE", "PENDIENTE", "TARJETA", null)));
    }

    @Test
    void soloElCompradorPuedePrepararElPago() {
        assertThrows(AccessDeniedException.class, () -> service.preparar(77L, "otro@correo.test"));
        assertThrows(AccessDeniedException.class, () -> service.preparar(77L, null));
        payphone.verify();
    }

    @Test
    void ventaNoPendienteOConOtroMetodoNoSePrepara() {
        when(ventas.obtenerResumen(78L)).thenReturn(venta(78L, 10L, "COMPLETADA", "APROBADO", "TARJETA"));
        when(ventas.obtenerResumen(79L)).thenReturn(venta(79L, 10L, "PENDIENTE", "PENDIENTE", "TRANSFERENCIA"));
        assertThrows(IllegalStateException.class, () -> service.preparar(78L, COMPRADOR));
        assertThrows(IllegalArgumentException.class, () -> service.preparar(79L, COMPRADOR));
        payphone.verify();
    }

    @Test
    void empresaSinPayphoneNoContactaAPayphone() {
        when(ventas.obtenerResumen(80L)).thenReturn(venta(80L, 30L, "PENDIENTE", "PENDIENTE", "TARJETA"));
        assertThrows(NoSuchElementException.class, () -> service.preparar(80L, COMPRADOR));
        payphone.verify();
    }

    @Test
    void enlaceDePagoFueraDePayphoneSeDescarta() {
        payphone.expect(requestTo(BASE + "/api/button/Prepare"))
                .andRespond(withSuccess(
                        "{\"payWithPayPhone\":\"https://evil.example/phish\",\"payWithCard\":\"http://pay.payphonetodoesposible.com/x\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(PayphoneIntegracionException.class, () -> service.preparar(77L, COMPRADOR));
    }

    // ------------------------------------------------------------------ confirmar (A-04)

    @Test
    void pagoAprobadoConMontoCorrectoCompletaLaVentaYDevuelveSoloListaBlanca() throws Exception {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionStatus\":\"Approved\",\"transactionId\":555,"
                + "\"clientTransactionId\":\"" + TX + "\",\"amount\":1230,\"currency\":\"USD\","
                + "\"email\":\"x@y.z\",\"phoneNumber\":\"0999\",\"document\":\"1700\",\"storeId\":\"s\",\"token\":\"eco\"}");
        when(ventas.registrarPagoAprobado(77L, "555", 1230L, "PAYPHONE", TX))
                .thenReturn(new RegistroPagoVenta(77L, "F-77", "COMPLETADA", "APROBADO", "COMPLETADA"));

        PayphoneConfirmacionDto r = service.confirmar(new PayphoneConfirmarRequestDto(555L, TX));
        String json = objectMapper.writeValueAsString(r);

        payphone.verify();
        assertEquals("COMPLETADA", r.getResultadoVenta());
        assertEquals(77L, r.getVentaId());
        for (String prohibido : new String[]{TOKEN_A, "eco", "x@y.z", "0999", "storeId", "phoneNumber", "token"}) {
            assertFalse(json.contains(prohibido), "No debe contener " + prohibido + ": " + json);
        }
    }

    @Test
    void montoDistintoAlDeLaVentaNoSeRegistra() {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionId\":555,\"clientTransactionId\":\""
                + TX + "\",\"amount\":100}");

        PayphoneConfirmacionDto r = service.confirmar(new PayphoneConfirmarRequestDto(555L, TX));

        assertEquals(PayphonePagoService.MONTO_NO_COINCIDE, r.getResultadoVenta());
        verify(ventas, never()).registrarPagoAprobado(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void clientTransactionIdDevueltoDistintoNoSeRegistra() {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionId\":555,"
                + "\"clientTransactionId\":\"FD-1-otroOtroOtro\",\"amount\":1230}");

        PayphoneConfirmacionDto r = service.confirmar(new PayphoneConfirmarRequestDto(555L, TX));

        assertEquals(PayphonePagoService.REQUIERE_REVISION, r.getResultadoVenta());
        verify(ventas, never()).registrarPagoAprobado(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void pagoCanceladoNoTocaLaVenta() {
        esperarConfirmacion(TOKEN_A, 556, "{\"statusCode\":2,\"transactionStatus\":\"Canceled\"}");

        PayphoneConfirmacionDto r = service.confirmar(new PayphoneConfirmarRequestDto(556L, TX));

        assertEquals(2, r.getStatusCode());
        assertNull(r.getResultadoVenta());
        verify(ventas, never()).registrarPagoAprobado(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void ventaQueRechazaElPagoQuedaParaRevision() {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionId\":555,\"amount\":1230}");
        when(ventas.registrarPagoAprobado(eq(77L), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new VentaPagoRechazadoException("cancelada"));

        assertEquals(PayphonePagoService.REQUIERE_REVISION,
                service.confirmar(new PayphoneConfirmarRequestDto(555L, TX)).getResultadoVenta());
    }

    @Test
    void ventasNoDisponibleSePropagaParaReintentar() {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionId\":555,\"amount\":1230}");
        when(ventas.registrarPagoAprobado(eq(77L), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new VentasNoDisponibleException("caído"));

        assertThrows(VentasNoDisponibleException.class,
                () -> service.confirmar(new PayphoneConfirmarRequestDto(555L, TX)));
    }

    @Test
    void confirmarConClientTxIdAjenoOInvalidoSeRechazaSinLlamadas() {
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(new PayphoneConfirmarRequestDto(1L, "ORD-123")));
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(new PayphoneConfirmarRequestDto(null, TX)));
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(new PayphoneConfirmarRequestDto(1L, " ")));
        payphone.verify();
        verify(ventas, never()).obtenerResumen(anyLong());
    }

    @Test
    void errorDePayphoneNoFiltraTokenNiCuerpo() {
        payphone.expect(requestTo(BASE + "/api/button/V2/Confirm"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Token invalido " + TOKEN_A + "\"}"));

        PayphoneIntegracionException ex = assertThrows(PayphoneIntegracionException.class,
                () -> service.confirmar(new PayphoneConfirmarRequestDto(1L, TX)));
        assertFalse(ex.getMessage().contains(TOKEN_A));
    }

    // ------------------------------------------------------------------ webhook (M-06)

    @Test
    void notificacionVerificaConPayphoneYReconcilia() throws Exception {
        esperarConfirmacion(TOKEN_A, 555, "{\"statusCode\":3,\"transactionId\":555,\"clientTransactionId\":\""
                + TX + "\",\"amount\":1230}");
        when(ventas.registrarPagoAprobado(77L, "555", 1230L, "PAYPHONE", TX))
                .thenReturn(new RegistroPagoVenta(77L, "F-77", "COMPLETADA", "APROBADO", "YA_PROCESADA"));

        // Cuerpo con mayúsculas y datos falsos: solo se usan TransactionId y ClientTransactionId
        var cuerpo = objectMapper.readTree("{\"TransactionId\":555,\"ClientTransactionId\":\"" + TX + "\","
                + "\"StatusCode\":3,\"Amount\":999999}");
        Optional<PayphoneConfirmacionDto> r = service.procesarNotificacion(cuerpo);

        payphone.verify();
        assertEquals("YA_PROCESADA", r.orElseThrow().getResultadoVenta());
    }

    @Test
    void notificacionFalsaNoAprobadaPorPayphoneNoCompletaNada() throws Exception {
        esperarConfirmacion(TOKEN_A, 999, "{\"statusCode\":2,\"transactionStatus\":\"Canceled\"}");

        var cuerpo = objectMapper.readTree("{\"transactionId\":999,\"clientTransactionId\":\"" + TX
                + "\",\"statusCode\":3,\"transactionStatus\":\"Approved\"}");
        service.procesarNotificacion(cuerpo);

        verify(ventas, never()).registrarPagoAprobado(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void notificacionAjenaSeIgnoraYMalformadaSeRechaza() throws Exception {
        assertTrue(service.procesarNotificacion(
                objectMapper.readTree("{\"transactionId\":1,\"clientTransactionId\":\"OTRA-TIENDA-1\"}")).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.procesarNotificacion(objectMapper.readTree("{\"foo\":1}")));
        assertThrows(IllegalArgumentException.class, () -> service.procesarNotificacion(null));
        payphone.verify();
        verify(ventas, never()).obtenerResumen(anyLong());
    }

    @Test
    void ventaIdSeExtraeSoloDeClientTxIdPropio() {
        assertEquals(77L, PayphonePagoService.ventaIdDesdeClientTxId(TX));
        assertNull(PayphonePagoService.ventaIdDesdeClientTxId("FD-0-abcdef"));
        assertNull(PayphonePagoService.ventaIdDesdeClientTxId("FD-77-abc"));
        assertNull(PayphonePagoService.ventaIdDesdeClientTxId("FD-77-abc def"));
        assertNull(PayphonePagoService.ventaIdDesdeClientTxId("x".repeat(60)));
    }

    // ------------------------------------------------------------------ helpers

    private void esperarConfirmacion(String token, long id, String respuesta) {
        payphone.expect(requestTo(BASE + "/api/button/V2/Confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.clientTxId").value(TX))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));
    }

    private static VentaResumenPago venta(Long id, Long empresaId, String estado, String estadoPago, String metodo) {
        return new VentaResumenPago(id, "F-" + id, empresaId, COMPRADOR,
                new BigDecimal("10.00"), new BigDecimal("1.50"), new BigDecimal("12.30"),
                estado, estadoPago, metodo, null);
    }
}
