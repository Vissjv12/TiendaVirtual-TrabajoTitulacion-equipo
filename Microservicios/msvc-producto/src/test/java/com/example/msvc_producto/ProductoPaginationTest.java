package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.dto.CategoriaResponseDto;
import com.example.msvc_producto.application.dto.EmpresaResponseDto;
import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.application.dto.ProductoResponseDto;
import com.example.msvc_producto.application.mapper.CategoriaMapper;
import com.example.msvc_producto.application.mapper.EmpresaMapper;
import com.example.msvc_producto.application.mapper.ProductoMapper;
import com.example.msvc_producto.application.service.CategoriaCatalogoQueryService;
import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import com.example.msvc_producto.application.util.PaginationUtils;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.service.ProductoService;
import com.example.msvc_producto.infrastructure.config.SecurityConfig;
import com.example.msvc_producto.presentation.controller.CategoriaController;
import com.example.msvc_producto.presentation.controller.EmpresaController;
import com.example.msvc_producto.presentation.controller.GlobalExceptionHandler;
import com.example.msvc_producto.presentation.controller.ProductoController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ProductoController.class,
        CategoriaController.class,
        EmpresaController.class
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductoPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private CategoriaService categoriaService;

    @MockitoBean
    private ProductoMapper productoMapper;

    @MockitoBean
    private CategoriaMapper categoriaMapper;

    @MockitoBean
    private CategoriaCatalogoQueryService categoriaCatalogoQueryService;

    @MockitoBean
    private EmpresaMapper empresaMapper;

    @MockitoBean
    private InventarioClient inventarioClient;

    @MockitoBean
    private ProductoAuthorizationService productoAuthorizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listarProductosPaginadoUsaContratoA19YLimitaSizeMaximo() throws Exception {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setNombre("Queso Fresco");

        ProductoResponseDto dto = new ProductoResponseDto();
        dto.setId(1L);
        dto.setNombre("Queso Fresco");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productoService.listarProductosPaginado(any(), isNull(), isNull(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable p = invocation.getArgument(4);
                    return new PageImpl<>(List.of(producto), p, 1);
                });
        when(productoMapper.toDto(producto)).thenReturn(dto);

        mockMvc.perform(get("/api/productos/paginado")
                        .param("page", "-3")
                        .param("size", "999")
                        .param("sort", "campoInexistente,asc")
                        .param("q", "queso")
                        .param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Queso Fresco"))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.empty").value(false));

        verify(productoService).listarProductosPaginado(any(), isNull(), isNull(), any(), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber());
        assertEquals(PaginationUtils.MAX_PAGE_SIZE, captured.getPageSize());
        assertEquals(Sort.Direction.DESC, captured.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void listarProductosVitrinaPaginadoUsaDefaultDoceYMetadatosCompletos() throws Exception {
        ProductoListadoDto item = new ProductoListadoDto(
                5L, "Miel de Abeja", "Natural", 8.50, null, "Alimentos", "Sigcholac", 2L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productoService.obtenerProductosOptimizadoPaginado(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable p = invocation.getArgument(3);
                    return new PageImpl<>(List.of(item), p, 25);
                });

        mockMvc.perform(get("/api/productos/listado/paginado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(12))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false));

        verify(productoService).obtenerProductosOptimizadoPaginado(
                isNull(), isNull(), isNull(), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertEquals(PaginationUtils.DEFAULT_STOREFRONT_SIZE, captured.getPageSize());
        assertEquals(Sort.Direction.ASC, captured.getSort().getOrderFor("nombre").getDirection());
    }

    @Test
    void listarCategoriasYEmpresasPaginadoRetornanContratoA19() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setId(2L);
        CategoriaResponseDto catDto = new CategoriaResponseDto();
        catDto.setId(2L);
        catDto.setNombre("Lacteos");

        when(categoriaService.listarCategoriasPaginado(isNull(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(categoria), inv.getArgument(1), 1));
        when(categoriaMapper.toDto(categoria)).thenReturn(catDto);

        mockMvc.perform(get("/api/categorias/paginado").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.empty").value(false));

        Empresa empresa = new Empresa();
        empresa.setId(3L);
        EmpresaResponseDto empDto = new EmpresaResponseDto();
        empDto.setId(3L);
        empDto.setNombre("Sigcholac");

        when(empresaService.listarEmpresasPaginado(isNull(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));
        when(empresaMapper.toDto(empresa)).thenReturn(empDto);

        mockMvc.perform(get("/api/empresas/paginado").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }

    @Test
    void paginationUtilsNormalizaSortPermitido() {
        Pageable p = PaginationUtils.createPageable(
                1, 15, "precio,asc", 10, "id", Sort.Direction.DESC, Map.of("precio", "precio"));
        assertEquals(1, p.getPageNumber());
        assertEquals(15, p.getPageSize());
        assertEquals(Sort.Direction.ASC, p.getSort().getOrderFor("precio").getDirection());
    }
}
