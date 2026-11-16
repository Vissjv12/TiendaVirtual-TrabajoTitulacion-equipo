package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.InventarioResponseDto;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.mapper.InventarioMapper;
import com.example.msvc_inventario.application.service.InventarioAuthorizationService;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.service.InventarioService;
import com.example.msvc_inventario.infrastructure.config.SecurityConfig;
import com.example.msvc_inventario.presentation.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A-01: TODAS las escrituras de inventario validan que el producto pertenezca a la empresa del JWT.
 * Producto 1 -> empresa 1 (propia del EMP de prueba). Producto 2 -> empresa 99 (ajena).
 * Inventario 10 -> producto 1. Inventario 20 -> producto 2.
 * En los rechazos se verifica que el servicio no se invoca: no hay cambios ni movimientos.
 */
@WebMvcTest(controllers = com.example.msvc_inventario.presentation.controller.InventarioController.class,
        properties = "jwt.secret=local-test-secret-for-inventory")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, InventarioAuthorizationService.class})
class InventarioPropiedadEscriturasTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private InventarioService inventarioService;
    @MockitoBean private InventarioMapper inventarioMapper;
    @MockitoBean private ProductoClient productoClient;
    @MockitoBean private InventarioRepository inventarioRepository;
    @MockitoBean private com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository movimientoRepository;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(productoClient.obtenerProducto(1L)).thenReturn(producto(1L, 1L));
        when(productoClient.obtenerProducto(2L)).thenReturn(producto(2L, 99L));
        when(inventarioRepository.findById(10L)).thenReturn(Optional.of(inventario(10L, 1L)));
        when(inventarioRepository.findById(20L)).thenReturn(Optional.of(inventario(20L, 2L)));
        when(inventarioRepository.findById(30L)).thenReturn(Optional.empty());
        when(inventarioMapper.toEntity(any())).thenReturn(new Inventario());
        when(inventarioMapper.toDto(any(Inventario.class), any(ProductoDto.class))).thenReturn(new InventarioResponseDto());
        when(inventarioMapper.toDto(any(Inventario.class))).thenReturn(new InventarioResponseDto());
    }

    // ---------------------------------------------------------------- POST /api/inventarios

    @Test
    void crearInventarioDeProductoAjenoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/inventarios").with(emp()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(2L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    @Test
    void crearInventarioDeProductoPropioEsPermitido() throws Exception {
        when(inventarioService.registrarInventario(any())).thenReturn(inventario(10L, 1L));
        mockMvc.perform(post("/api/inventarios").with(emp()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(1L)))
                .andExpect(status().isCreated());
    }

    @Test
    void crearInventarioComoAdminEsPermitidoParaCualquierEmpresa() throws Exception {
        when(inventarioService.registrarInventario(any())).thenReturn(inventario(20L, 2L));
        mockMvc.perform(post("/api/inventarios").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(2L)))
                .andExpect(status().isCreated());
    }

    @Test
    void crearInventarioParaProductoAjenoPorRutaEsRechazado() throws Exception {
        mockMvc.perform(post("/api/inventarios/producto/2").param("stock", "50").with(emp()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    @Test
    void empSinEmpresaEnElTokenEsRechazado() throws Exception {
        mockMvc.perform(post("/api/inventarios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(1L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    // ---------------------------------------------------------------- PUT /api/inventarios/{id}

    @Test
    void actualizarInventarioAjenoEsRechazadoAunqueElCuerpoNombreUnProductoPropio() throws Exception {
        // El inventario 20 es del producto 2 (empresa 99); el cuerpo intenta disfrazarlo como producto 1
        mockMvc.perform(put("/api/inventarios/20").with(emp()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(1L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    @Test
    void actualizarInventarioPropioEsPermitido() throws Exception {
        when(inventarioService.actualizarInventario(anyLong(), any())).thenReturn(inventario(10L, 1L));
        mockMvc.perform(put("/api/inventarios/10").with(emp()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarInventarioInexistenteComoEmpEsRechazado() throws Exception {
        mockMvc.perform(put("/api/inventarios/30").with(emp()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(1L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    // ---------------------------------------------------------------- DELETE /api/inventarios/{id}

    @Test
    void eliminarInventarioAjenoEsRechazado() throws Exception {
        mockMvc.perform(delete("/api/inventarios/20").with(emp()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    @Test
    void eliminarInventarioPropioEsPermitido() throws Exception {
        mockMvc.perform(delete("/api/inventarios/10").with(emp()))
                .andExpect(status().isNoContent());
        verify(inventarioService).eliminar(10L);
    }

    @Test
    void eliminarInventarioComoAdminEsPermitido() throws Exception {
        mockMvc.perform(delete("/api/inventarios/20").with(admin()))
                .andExpect(status().isNoContent());
        verify(inventarioService).eliminar(20L);
    }

    @Test
    void eliminarSinTokenOComoClienteEsRechazado() throws Exception {
        mockMvc.perform(delete("/api/inventarios/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/inventarios/10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    // ---------------------------------------------------------------- helpers

    private static RequestPostProcessor emp() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                .jwt(token -> token.claim("empresaId", 1L).claim("userId", 5L));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String cuerpo(Long productoId) {
        return "{\"productoId\":" + productoId + ",\"cantidad\":10,\"ubicacion\":\"Bodega\",\"activo\":true}";
    }

    private static ProductoDto producto(Long id, Long empresaId) {
        ProductoDto producto = new ProductoDto();
        producto.setId(id);
        producto.setEmpresa(new ProductoDto.EmpresaDto(empresaId));
        return producto;
    }

    private static Inventario inventario(Long id, Long productoId) {
        Inventario inventario = new Inventario();
        inventario.setId(id);
        inventario.setProductoId(productoId);
        inventario.setCantidad(5);
        return inventario;
    }
}
