package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.mapper.ProductoMapper;
import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.service.ProductoService;
import com.example.msvc_producto.infrastructure.config.SecurityConfig;
import com.example.msvc_producto.presentation.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.msvc_producto.presentation.controller.ProductoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductoControllerApiAuthorizationTest {

    private static final String PRODUCTO_VALIDO = """
            {
              "nombre": "Producto de prueba",
              "descripcion": "Producto utilizado en pruebas de autorizacion",
              "precio": 10.50,
              "activo": true,
              "empresaId": 10,
              "categoriaId": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    @MockBean
    private EmpresaService empresaService;

    @MockBean
    private CategoriaService categoriaService;

    @MockBean
    private ProductoMapper productoMapper;

    @MockBean
    private InventarioClient inventarioClient;

    @MockBean
    private ProductoAuthorizationService productoAuthorizationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void crearProductoSinTokenEsRechazado() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearProductoConRolSinPermisoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void emprendedorNoPuedeCrearProductoEnEmpresaAjena() throws Exception {
        when(productoAuthorizationService.canManageCompany(any(), eq(10L)))
                .thenReturn(false);

        mockMvc.perform(post("/api/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarProductosPermanecePublico() throws Exception {
        when(productoService.listarProductos()).thenReturn(List.of());

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk());
    }
}
