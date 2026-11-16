package com.example.demo.presentation.controller;

import com.example.demo.application.dto.PayphoneConfirmacionDto;
import com.example.demo.application.dto.PayphoneConfirmarRequestDto;
import com.example.demo.application.dto.PayphonePrepararResponseDto;
import com.example.demo.domain.service.ConfiguracionPagosService;
import com.example.demo.domain.service.PayphonePagoService;
import com.example.demo.infrastructure.payphone.PayphoneIntegracionException;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.example.demo.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hallazgo C-02: ninguna respuesta HTTP de Payphone contiene el token (ni el App ID en la pública).
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionPagosControllerPayphoneTest {

    private static final String BASE = "/api/emprendedor/configuracion-pagos";

    @Mock
    private ConfiguracionPagosService configuracionPagosService;

    @Mock
    private PayphonePagoService payphonePagoService;

    @Mock
    private UsuarioJpaRepository usuarioRepository;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @InjectMocks
    private ConfiguracionPagosController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void endpointPublicoSoloIndicaDisponibilidad() throws Exception {
        when(configuracionPagosService.payphoneDisponiblePorEmpresa(10L)).thenReturn(true);

        MvcResult r = mockMvc.perform(get(BASE + "/payphone/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaId").value(10))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.payphoneToken").doesNotExist())
                .andExpect(jsonPath("$.payphoneAppId").doesNotExist())
                .andReturn();

        assertSinCredenciales(r);
    }

    @Test
    void endpointPublicoDeEmpresaSinPayphoneRespondeNoDisponible() throws Exception {
        when(configuracionPagosService.payphoneDisponiblePorEmpresa(30L)).thenReturn(false);

        mockMvc.perform(get(BASE + "/payphone/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void prepararUsaLaVentaYElEmailDelTokenSinRecibirMontosDelNavegador() throws Exception {
        when(payphonePagoService.preparar(77L, "comprador@correo.test"))
                .thenReturn(new PayphonePrepararResponseDto("FD-77-abcdef", "pay-1",
                        "https://pay.payphonetodoesposible.com/p", "https://pay.payphonetodoesposible.com/c"));

        MvcResult r = mockMvc.perform(post(BASE + "/payphone/ventas/77/preparar")
                        .principal(usuario("comprador@correo.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientTransactionId").value("FD-77-abcdef"))
                .andExpect(jsonPath("$.payWithCard").value("https://pay.payphonetodoesposible.com/c"))
                .andReturn();

        assertSinCredenciales(r);
    }

    @Test
    void prepararDeVentaAjenaResponde403() throws Exception {
        when(payphonePagoService.preparar(eq(77L), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("no"));

        mockMvc.perform(post(BASE + "/payphone/ventas/77/preparar").principal(usuario("otro@correo.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmarConCuerpoInvalidoResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post(BASE + "/payphone/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":-5}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payphonePagoService);
    }

    @Test
    void notificacionRespondeRecibidoSinDatosSensibles() throws Exception {
        when(payphonePagoService.procesarNotificacion(any()))
                .thenReturn(java.util.Optional.of(PayphoneConfirmacionDto.builder()
                        .statusCode(3).resultadoVenta("COMPLETADA").build()));

        MvcResult r = mockMvc.perform(post(BASE + "/payphone/notificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1,\"clientTransactionId\":\"FD-77-abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recibido").value(true))
                .andExpect(jsonPath("$.resultadoVenta").value("COMPLETADA"))
                .andReturn();

        assertSinCredenciales(r);
    }

    @Test
    void notificacionConVentasCaidoResponde503ParaQuePayphoneReintente() throws Exception {
        when(payphonePagoService.procesarNotificacion(any()))
                .thenThrow(new com.example.demo.infrastructure.ventas.VentasNoDisponibleException("caído"));

        mockMvc.perform(post(BASE + "/payphone/notificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":1,\"clientTransactionId\":\"FD-77-abcdef\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void confirmarDevuelveResultadoSaneado() throws Exception {
        when(payphonePagoService.confirmar(any(PayphoneConfirmarRequestDto.class)))
                .thenReturn(PayphoneConfirmacionDto.builder()
                        .statusCode(3).transactionStatus("Approved").transactionId(555L).amount(1230L)
                        .currency("USD").build());

        MvcResult r = mockMvc.perform(post(BASE + "/payphone/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":555,\"clientTxId\":\"FD-77-abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(3))
                .andExpect(jsonPath("$.transactionId").value(555))
                .andReturn();

        assertSinCredenciales(r);
    }

    @Test
    void empresaSinPayphoneResponde404() throws Exception {
        when(payphonePagoService.confirmar(any(PayphoneConfirmarRequestDto.class)))
                .thenThrow(new NoSuchElementException("La empresa no tiene Payphone configurado"));

        mockMvc.perform(post(BASE + "/payphone/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"clientTxId\":\"FD-30-abcdef\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void falloDePayphoneResponde502ConMensajeSaneado() throws Exception {
        when(payphonePagoService.confirmar(any(PayphoneConfirmarRequestDto.class)))
                .thenThrow(new PayphoneIntegracionException("Payphone rechazó la solicitud al confirmar el pago"));

        MvcResult r = mockMvc.perform(post(BASE + "/payphone/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"clientTxId\":\"FD-10-abcdef\"}"))
                .andExpect(status().isBadGateway())
                .andReturn();

        assertSinCredenciales(r);
    }

    private static org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken usuario(String email) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                .header("alg", "HS256").subject(email).claim("email", email).build();
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
    }

    private void assertSinCredenciales(MvcResult r) throws Exception {
        String cuerpo = r.getResponse().getContentAsString();
        assertFalse(cuerpo.contains("payphoneToken"), cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("token"), cuerpo);
        assertFalse(cuerpo.contains("payphoneAppId"), cuerpo);
    }
}
