package com.example.msvc_ventas;

import com.example.msvc_ventas.application.dto.ClienteResponseDto;
import com.example.msvc_ventas.application.dto.PagoResponseDto;
import com.example.msvc_ventas.application.dto.VentaResponseDto;
import com.example.msvc_ventas.application.mapper.ClienteMapper;
import com.example.msvc_ventas.application.mapper.PagoMapper;
import com.example.msvc_ventas.application.mapper.VentaMapper;
import com.example.msvc_ventas.application.service.VentaApplicationService;
import com.example.msvc_ventas.application.util.PaginationUtils;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.domain.service.ClienteService;
import com.example.msvc_ventas.domain.service.FacturaService;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
import com.example.msvc_ventas.domain.service.VentaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.presentation.controller.ClienteController;
import com.example.msvc_ventas.presentation.controller.PagoController;
import com.example.msvc_ventas.presentation.controller.SeguimientoLogisticaController;
import com.example.msvc_ventas.presentation.controller.VentaController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VentaPaginationTest {

    @Mock
    private VentaService ventaService;
    @Mock
    private ClienteService clienteService;
    @Mock
    private VentaApplicationService ventaApplicationService;
    @Mock
    private VentaMapper ventaMapper;
    @Mock
    private FacturaService facturaService;
    @Mock
    private PagoService pagoService;
    @Mock
    private PagoMapper pagoMapper;
    @Mock
    private SeguimientoLogisticaService seguimientoService;
    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private ClienteMapper clienteMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        VentaController ventaController = new VentaController(
                ventaService, clienteService, ventaApplicationService, ventaMapper, facturaService);
        PagoController pagoController = new PagoController(pagoService, pagoMapper);
        SeguimientoLogisticaController seguimientoController =
                new SeguimientoLogisticaController(seguimientoService, ventaRepository);
        ClienteController clienteController = new ClienteController(clienteService, clienteMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        ventaController, pagoController, seguimientoController, clienteController)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void ventasPaginadasAplicanLimitesYContratoA19() throws Exception {
        Venta venta = new Venta();
        venta.setId(42L);
        venta.setNumeroFactura("FAC-0042");

        VentaResponseDto dto = new VentaResponseDto();
        dto.setId(42L);
        dto.setNumeroFactura("FAC-0042");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(ventaService.listarVentasPaginado(eq("FAC"), eq(Venta.EstadoVenta.PENDIENTE), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(venta), inv.getArgument(2), 1));
        when(ventaMapper.toDto(venta)).thenReturn(dto);

        mockMvc.perform(get("/api/ventas/paginado")
                        .param("page", "-5")
                        .param("size", "999")
                        .param("sort", "invalido,asc")
                        .param("q", "FAC")
                        .param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.empty").value(false));

        verify(ventaService).listarVentasPaginado(eq("FAC"), eq(Venta.EstadoVenta.PENDIENTE), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(PaginationUtils.MAX_PAGE_SIZE, captured.getPageSize());
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("fechaVenta").getDirection());

        mockMvc.perform(get("/api/ventas/paginado").param("estado", "NO_EXISTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagosSeguimientoYClientesPaginadosRetornanContratoA19() throws Exception {
        PagoEntity pago = new PagoEntity();
        pago.setId(9L);
        PagoResponseDto pagoDto = new PagoResponseDto();
        pagoDto.setId(9L);

        when(pagoService.listarPagosPendientesPorEmprendedorPaginado(eq(4L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(pago), inv.getArgument(1), 1));
        when(pagoMapper.toDto(pago)).thenReturn(pagoDto);

        mockMvc.perform(get("/api/pagos/emprendedor/4/pendientes/paginado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));

        SeguimientoLogistica seg = new SeguimientoLogistica();
        seg.setId(21L);
        seg.setVentaId(42L);
        seg.setEstadoLogistica(SeguimientoLogistica.EstadoLogistica.EN_PREPARACION);

        Venta ventaRef = new Venta();
        ventaRef.setId(42L);
        ventaRef.setNumeroFactura("FAC-0042");

        when(seguimientoService.obtenerSeguimientosPorEmprendedorPaginado(
                eq(4L), eq(SeguimientoLogistica.EstadoLogistica.EN_PREPARACION), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(seg), inv.getArgument(2), 1));
        when(ventaRepository.findById(42L)).thenReturn(Optional.of(ventaRef));

        mockMvc.perform(get("/api/seguimiento-logistica/emprendedor/4/paginado")
                        .param("estado", "EN_PREPARACION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroFactura").value("FAC-0042"))
                .andExpect(jsonPath("$.totalElements").value(1));

        Cliente cliente = new Cliente();
        cliente.setId(8L);
        ClienteResponseDto cliDto = new ClienteResponseDto();
        cliDto.setId(8L);

        when(clienteService.listarClientesPaginado(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(cliente), inv.getArgument(0), 1));
        when(clienteMapper.toDto(cliente)).thenReturn(cliDto);

        mockMvc.perform(get("/api/clientes/paginado").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(8))
                .andExpect(jsonPath("$.empty").value(false));
    }
}
