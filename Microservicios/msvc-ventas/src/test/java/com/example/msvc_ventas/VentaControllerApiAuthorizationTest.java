package com.example.msvc_ventas;

import com.example.msvc_ventas.application.dto.ClienteResponseDto;
import com.example.msvc_ventas.application.dto.PagoResponseDto;
import com.example.msvc_ventas.application.dto.VentaResponseDto;
import com.example.msvc_ventas.application.mapper.ClienteMapper;
import com.example.msvc_ventas.application.mapper.PagoMapper;
import com.example.msvc_ventas.application.mapper.VentaMapper;
import com.example.msvc_ventas.application.service.VentaApplicationService;
import com.example.msvc_ventas.application.service.VentaAuthorizationService;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.domain.service.ClienteService;
import com.example.msvc_ventas.domain.service.FacturaService;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
import com.example.msvc_ventas.domain.service.StripeService;
import com.example.msvc_ventas.domain.service.VentaService;
import com.example.msvc_ventas.infrastructure.config.SecurityConfig;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import com.example.msvc_ventas.presentation.controller.ClienteController;
import com.example.msvc_ventas.presentation.controller.GlobalExceptionHandler;
import com.example.msvc_ventas.presentation.controller.PagoController;
import com.example.msvc_ventas.presentation.controller.SeguimientoLogisticaController;
import com.example.msvc_ventas.presentation.controller.StripeController;
import com.example.msvc_ventas.presentation.controller.VentaController;
import com.example.msvcventas.infrastructure.controllers.ProcesoVentaBpmController;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                VentaController.class,
                ClienteController.class,
                PagoController.class,
                SeguimientoLogisticaController.class,
                StripeController.class,
                ProcesoVentaBpmController.class
        },
        properties = "jwt.secret=local-test-secret-for-ventas-a24-32bytes"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, VentaAuthorizationService.class, ProcesoVentaBpmController.class})
class VentaControllerApiAuthorizationTest {

    private static final String VENTA_REQUEST = """
            {
              "clienteId": 10,
              "items": [{"productoId": 1, "cantidad": 1}],
              "metodoPago": "TARJETA"
            }
            """;

    private static final String CLIENTE_REQUEST = """
            {
              "nombre": "Ana",
              "apellido": "Lopez",
              "email": "cliente@test.com",
              "documento": "1712345678",
              "telefono": "0991234567",
              "direccion": "Sigchos"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VentaService ventaService;
    @MockBean
    private ClienteService clienteService;
    @MockBean
    private VentaApplicationService ventaApplicationService;
    @MockBean
    private VentaMapper ventaMapper;
    @MockBean
    private FacturaService facturaService;
    @MockBean
    private ClienteMapper clienteMapper;
    @MockBean
    private PagoService pagoService;
    @MockBean
    private PagoMapper pagoMapper;
    @MockBean
    private SeguimientoLogisticaService seguimientoService;
    @MockBean
    private StripeService stripeService;
    @MockBean
    private RuntimeService runtimeService;
    @MockBean
    private VentaRepository ventaRepository;
    @MockBean
    private ClienteRepository clienteRepository;
    @MockBean
    private PagoRepository pagoRepository;
    @MockBean
    private JwtDecoder jwtDecoder;

    private Cliente clienteDueno;
    private Venta ventaEmpresaUno;
    private PagoEntity pagoEmpresaUno;

    @BeforeEach
    void setUp() {
        clienteDueno = new Cliente();
        clienteDueno.setId(10L);
        clienteDueno.setEmail("cliente@test.com");

        Cliente otraPersona = new Cliente();
        otraPersona.setId(20L);
        otraPersona.setEmail("otro@test.com");

        ventaEmpresaUno = new Venta();
        ventaEmpresaUno.setId(50L);
        ventaEmpresaUno.setNumeroFactura("FAC-0050");
        ventaEmpresaUno.setEmprendedorId(1L);
        ventaEmpresaUno.setCliente(clienteDueno);

        ClienteEntity clienteEntity = new ClienteEntity();
        clienteEntity.setId(10L);
        clienteEntity.setEmail("cliente@test.com");

        VentaEntity ventaEntity = new VentaEntity();
        ventaEntity.setId(50L);
        ventaEntity.setEmprendedorId(1L);
        ventaEntity.setCliente(clienteEntity);

        pagoEmpresaUno = new PagoEntity();
        pagoEmpresaUno.setId(300L);
        pagoEmpresaUno.setVenta(ventaEntity);

        when(clienteRepository.findById(10L)).thenReturn(Optional.of(clienteDueno));
        when(clienteRepository.findById(20L)).thenReturn(Optional.of(otraPersona));
        when(ventaRepository.findById(50L)).thenReturn(Optional.of(ventaEmpresaUno));
        when(ventaRepository.findByNumeroFactura("FAC-0050")).thenReturn(Optional.of(ventaEmpresaUno));
        when(pagoRepository.findById(300L)).thenReturn(Optional.of(pagoEmpresaUno));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtUser(String email) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(token -> token.claim("email", email).claim("userId", 10L));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtEmp(Long empresaId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                .jwt(token -> token.claim("email", "emp" + empresaId + "@test.com").claim("empresaId", empresaId));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtAdmin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(token -> token.claim("email", "admin@test.com").claim("userId", 1L));
    }

    @Test
    void endpointsPrivadosSinTokenRetornanUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ventas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ventas/50")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pagos/300")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seguimiento-logistica/venta/50")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/stripe/create-payment-intent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ventaId\":50,\"clienteId\":10,\"monto\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bpm/ventas/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/camunda/app/welcome/default/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/engine-rest/engine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void preflightCorsEsPermitidoSinAbrirEscrituraReal() throws Exception {
        mockMvc.perform(options("/api/ventas")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ventas")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENTA_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearYConsultarVentasRespetaPropiedadPorClienteEmprendedorYAdmin() throws Exception {
        when(ventaApplicationService.crearVenta(any())).thenReturn(new VentaResponseDto());
        when(ventaService.obtenerVentaPorId(50L)).thenReturn(ventaEmpresaUno);
        when(ventaMapper.toDto(any(Venta.class))).thenReturn(new VentaResponseDto());
        when(ventaService.listarVentas()).thenReturn(List.of());
        when(ventaService.listarVentasPaginado(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(ventaService.listarVentasPorEmprendedorPaginado(anyLong(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(ventaService.completarVenta(50L)).thenReturn(ventaEmpresaUno);

        mockMvc.perform(post("/api/ventas")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENTA_REQUEST))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ventas")
                        .with(jwtUser("otro@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENTA_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ventas/50").with(jwtUser("cliente@test.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/50").with(jwtEmp(1L)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/50").with(jwtEmp(2L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/50").with(jwtUser("otro@test.com")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ventas").with(jwtUser("cliente@test.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/paginado").with(jwtEmp(1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas/paginado").with(jwtAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ventas/emprendedor/1/paginado").with(jwtEmp(1L)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/emprendedor/1/paginado").with(jwtEmp(2L)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/ventas/50/completar").with(jwtUser("cliente@test.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ventas/50/completar").with(jwtEmp(2L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/ventas/50/completar").with(jwtEmp(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void clientesProtegidosPorEmailPropioOAdmin() throws Exception {
        when(clienteMapper.toEntity(any())).thenReturn(clienteDueno);
        when(clienteService.crearCliente(any())).thenReturn(clienteDueno);
        when(clienteService.obtenerClientePorEmail("cliente@test.com")).thenReturn(clienteDueno);
        when(clienteMapper.toDto(any(Cliente.class))).thenReturn(new ClienteResponseDto());
        when(clienteService.listarClientesPaginado(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(post("/api/clientes")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENTE_REQUEST))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clientes")
                        .with(jwtUser("otro@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLIENTE_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/clientes/email/cliente@test.com").with(jwtUser("cliente@test.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes/email/cliente@test.com").with(jwtUser("otro@test.com")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/clientes/paginado").with(jwtUser("cliente@test.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/paginado").with(jwtAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void pagosYLogisticaRespetanRolesYPropiedad() throws Exception {
        when(pagoService.aprobarPago(300L, 1L)).thenReturn(pagoEmpresaUno);
        when(pagoService.subirComprobante(anyLong(), anyString())).thenReturn(pagoEmpresaUno);
        when(pagoMapper.toDto(any())).thenReturn(new PagoResponseDto());
        when(pagoService.listarPagosPendientesPorEmprendedorPaginado(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String aprobarBody = "{\"emprendedorId\": 1, \"observaciones\": \"ok\"}";
        mockMvc.perform(post("/api/pagos/300/aprobar")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aprobarBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pagos/300/aprobar")
                        .with(jwtEmp(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aprobarBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pagos/300/aprobar")
                        .with(jwtEmp(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aprobarBody))
                .andExpect(status().isOk());

        String comprobanteBody = "{\"comprobanteUrl\": \"https://cdn.example/comp.jpg\"}";
        mockMvc.perform(post("/api/pagos/300/comprobante")
                        .with(jwtUser("otro@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comprobanteBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pagos/300/comprobante")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comprobanteBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pagos/emprendedor/1/pendientes/paginado").with(jwtEmp(2L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pagos/emprendedor/1/pendientes/paginado").with(jwtEmp(1L)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pagos/estado/PENDIENTE").with(jwtEmp(1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pagos/estado/PENDIENTE").with(jwtAdmin()))
                .andExpect(status().isOk());

        SeguimientoLogistica seguimiento = SeguimientoLogistica.builder()
                .id(1L)
                .ventaId(50L)
                .estadoLogistica(SeguimientoLogistica.EstadoLogistica.EN_PREPARACION)
                .build();
        when(seguimientoService.crearSeguimiento(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(seguimiento);
        when(seguimientoService.obtenerHistorialPorVenta(50L)).thenReturn(List.of());

        String seguimientoBody = "{\"ventaId\": 50, \"estadoLogistica\": \"EN_PREPARACION\"}";
        mockMvc.perform(post("/api/seguimiento-logistica")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seguimientoBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/seguimiento-logistica")
                        .with(jwtEmp(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seguimientoBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/seguimiento-logistica")
                        .with(jwtEmp(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seguimientoBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/seguimiento-logistica/venta/50").with(jwtUser("otro@test.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seguimiento-logistica/venta/50").with(jwtUser("cliente@test.com")))
                .andExpect(status().isOk());
    }

    @Test
    void stripeWebhookEsPublicoPeroIntentsYCamundaEstanRestringidos() throws Exception {
        when(stripeService.crearPaymentIntent(any(BigDecimal.class), anyLong(), anyLong()))
                .thenReturn(Map.of("clientSecret", "sec", "paymentIntentId", "pi_123"));

        mockMvc.perform(post("/api/stripe/webhook")
                        .header("Stripe-Signature", "sig_test")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stripe/create-payment-intent")
                        .with(jwtUser("otro@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ventaId\":50,\"clienteId\":10,\"monto\":20}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/stripe/create-payment-intent")
                        .with(jwtUser("cliente@test.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ventaId\":50,\"clienteId\":10,\"monto\":20}"))
                .andExpect(status().isOk());

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("proc-1");
        when(instance.getProcessDefinitionId()).thenReturn("def-1");
        when(runtimeService.startProcessInstanceByKey(anyString(), anyMap())).thenReturn(instance);

        mockMvc.perform(post("/api/bpm/ventas/iniciar")
                        .with(jwtEmp(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bpm/ventas/iniciar")
                        .with(jwtAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics").with(jwtUser("cliente@test.com")))
                .andExpect(status().isForbidden());
    }
}
