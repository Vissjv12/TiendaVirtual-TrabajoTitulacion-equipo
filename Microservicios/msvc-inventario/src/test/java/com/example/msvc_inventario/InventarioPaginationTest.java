package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.InventarioResponseDto;
import com.example.msvc_inventario.application.dto.MovimientoInventarioResponseDto;
import com.example.msvc_inventario.application.mapper.InventarioMapper;
import com.example.msvc_inventario.application.mapper.MovimientoInventarioMapper;
import com.example.msvc_inventario.application.util.PaginationUtils;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.service.InventarioService;
import com.example.msvc_inventario.domain.service.MovimientoInventarioService;
import com.example.msvc_inventario.infrastructure.config.SecurityConfig;
import com.example.msvc_inventario.presentation.controller.GlobalExceptionHandler;
import com.example.msvc_inventario.presentation.controller.InventarioController;
import com.example.msvc_inventario.presentation.controller.MovimientoInventarioController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {InventarioController.class, MovimientoInventarioController.class},
        properties = "jwt.secret=local-test-secret-for-inventory"
)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InventarioPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventarioService inventarioService;

    @MockitoBean
    private InventarioMapper inventarioMapper;

    @MockitoBean
    private ProductoClient productoClient;

    @MockitoBean
    private InventarioRepository inventarioRepository;

    @MockitoBean
    private MovimientoInventarioService movimientoService;

    @MockitoBean
    private MovimientoInventarioMapper movimientoMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listarInventariosPaginadoAplicaLimitesYMetadatos() throws Exception {
        Inventario inv = new Inventario();
        inv.setId(7L);
        inv.setProductoId(15L);
        inv.setCantidad(12);

        InventarioResponseDto dto = new InventarioResponseDto();
        dto.setId(7L);
        dto.setCantidad(12);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(inventarioService.listarPaginado(eq(true), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(inv), invocation.getArgument(1), 1));
        when(inventarioMapper.toDto(inv)).thenReturn(dto);

        mockMvc.perform(get("/api/inventarios/paginado")
                        .param("page", "-2")
                        .param("size", "200")
                        .param("sort", "propiedadNoPermitida,asc")
                        .param("soloActivos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.empty").value(false));

        verify(inventarioService).listarPaginado(eq(true), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber());
        assertEquals(PaginationUtils.MAX_PAGE_SIZE, captured.getPageSize());
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void listarMovimientosPaginadoValidaTipoYUsaDefaultFechaDesc() throws Exception {
        MovimientoInventario mov = new MovimientoInventario();
        mov.setId(11L);
        mov.setInventarioId(3L);

        MovimientoInventarioResponseDto dto = new MovimientoInventarioResponseDto();
        dto.setId(11L);
        dto.setInventarioId(3L);

        when(movimientoService.listarPaginado(
                isNull(), isNull(), eq(MovimientoInventario.TipoMovimiento.ENTRADA), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(mov), invocation.getArgument(6), 1));
        when(movimientoMapper.toDto(mov)).thenReturn(dto);

        mockMvc.perform(get("/api/movimientos/paginado")
                        .param("tipo", "ENTRADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.empty").value(false));

        mockMvc.perform(get("/api/movimientos/paginado")
                        .param("tipo", "TIPO_INEXISTENTE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoClient);
    }
}
