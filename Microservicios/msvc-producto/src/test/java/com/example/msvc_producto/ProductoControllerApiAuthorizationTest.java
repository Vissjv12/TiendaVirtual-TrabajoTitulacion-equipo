package com.example.msvc_producto;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.dto.CategoriaResponseDto;
import com.example.msvc_producto.application.dto.EmpresaResponseDto;
import com.example.msvc_producto.application.mapper.CategoriaMapper;
import com.example.msvc_producto.application.mapper.EmpresaMapper;
import com.example.msvc_producto.application.mapper.ProductoMapper;
import com.example.msvc_producto.application.service.CategoriaCatalogoQueryService;
import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.service.ProductoService;
import com.example.msvc_producto.infrastructure.config.SecurityConfig;
import com.example.msvc_producto.presentation.controller.CategoriaController;
import com.example.msvc_producto.presentation.controller.EmpresaController;
import com.example.msvc_producto.presentation.controller.GlobalExceptionHandler;
import com.example.msvc_producto.presentation.controller.ProductoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ProductoController.class,
        CategoriaController.class,
        EmpresaController.class
})
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

    private static final String CATEGORIA_VALIDA = """
            {
              "nombre": "Categoria emprendedor",
              "descripcion": "Categoria creada en prueba de autorizacion",
              "activo": true
            }
            """;

    private static final String EMPRESA_VALIDA = """
            {
              "nombre": "Sigcholac",
              "ruc": "1212154849868",
              "direccion": "Produccion de lacteos artesanales",
              "telefono": "0992000198",
              "email": "sigcholac@gmail.com",
              "activo": true
            }
            """;

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
    void emprendedorNoPuedeModificarNiDesactivarProductoDeOtraEmpresa() throws Exception {
        when(productoAuthorizationService.canManageProduct(any(), eq(1L)))
                .thenReturn(false);

        mockMvc.perform(put("/api/productos/1")
                        .with(jwt().jwt(token -> token.claim("empresaId", 20L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCTO_VALIDO))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/productos/1")
                        .with(jwt().jwt(token -> token.claim("empresaId", 20L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void listarProductosPermanecePublico() throws Exception {
        when(productoService.listarProductos()).thenReturn(List.of());

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk());
    }

    @Test
    void emprendedorPuedeCrearCategoria() throws Exception {
        Categoria categoria = new Categoria();
        categoria.setId(15L);
        when(categoriaMapper.toEntity(any())).thenReturn(categoria);
        when(categoriaService.crearCategoria(any())).thenReturn(categoria);
        when(categoriaMapper.toDto(any())).thenReturn(new CategoriaResponseDto());

        mockMvc.perform(post("/api/categorias")
                        .with(jwt().jwt(token -> token.claim("empresaId", 2L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CATEGORIA_VALIDA))
                .andExpect(status().isCreated());
    }

    private static final String EMPRESA_CONTACTO = """
            {
              "nombre": "Sigcholac",
              "direccion": "Produccion de lacteos artesanales",
              "telefono": "0992000198",
              "email": "sigcholac@gmail.com"
            }
            """;

    // ---------------------------------------------------------------- A-02: datos de contacto (EMP)

    @Test
    void emprendedorPuedeActualizarLosDatosDeContactoDeSuEmpresa() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setId(2L);
        when(productoAuthorizationService.canManageCompany(any(), eq(2L))).thenReturn(true);
        when(empresaService.actualizarDatosContacto(eq(2L), any(), any(), any(), any())).thenReturn(empresa);
        when(empresaMapper.toDto(any())).thenReturn(new EmpresaResponseDto());

        mockMvc.perform(put("/api/empresas/2/contacto")
                        .with(jwt().jwt(token -> token.claim("empresaId", 2L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPRESA_CONTACTO))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(empresaService).actualizarDatosContacto(2L, "Sigcholac",
                "Produccion de lacteos artesanales", "0992000198", "sigcholac@gmail.com");
    }

    @Test
    void emprendedorNoPuedeActualizarContactoDeOtraEmpresa() throws Exception {
        when(productoAuthorizationService.canManageCompany(any(), eq(1L))).thenReturn(false);

        mockMvc.perform(put("/api/empresas/1/contacto")
                        .with(jwt().jwt(token -> token.claim("empresaId", 2L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPRESA_CONTACTO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(empresaService);
    }

    @Test
    void emprendedorQueEnviaRucOActivoEsRechazadoSinCambios() throws Exception {
        when(productoAuthorizationService.canManageCompany(any(), eq(2L))).thenReturn(true);

        for (String extra : new String[]{"\"ruc\": \"1799999999001\"", "\"activo\": true", "\"id\": 7"}) {
            String cuerpo = EMPRESA_CONTACTO.replace("\"nombre\"", extra + ",\n  \"nombre\"");
            mockMvc.perform(put("/api/empresas/2/contacto")
                            .with(jwt().jwt(token -> token.claim("empresaId", 2L))
                                    .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(empresaService);
    }

    // ---------------------------------------------------------------- A-02: actualización administrativa

    @Test
    void emprendedorNoPuedeUsarLaActualizacionAdministrativaNiSobreSuEmpresa() throws Exception {
        when(productoAuthorizationService.canManageCompany(any(), eq(2L))).thenReturn(true);

        mockMvc.perform(put("/api/empresas/2")
                        .with(jwt().jwt(token -> token.claim("empresaId", 2L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPRESA_VALIDA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(empresaService);
    }

    @Test
    void administradorPuedeActualizarRucYEstado() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setId(2L);
        when(empresaMapper.toEntity(any(), eq(2L))).thenReturn(empresa);
        when(empresaService.actualizarEmpresa(eq(2L), any())).thenReturn(empresa);
        when(empresaMapper.toDto(any())).thenReturn(new EmpresaResponseDto());

        mockMvc.perform(put("/api/empresas/2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPRESA_VALIDA))
                .andExpect(status().isOk());
    }

    @Test
    void sinTokenNoSeActualizaNinguna() throws Exception {
        mockMvc.perform(put("/api/empresas/2/contacto")
                        .contentType(MediaType.APPLICATION_JSON).content(EMPRESA_CONTACTO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/empresas/2")
                        .contentType(MediaType.APPLICATION_JSON).content(EMPRESA_VALIDA))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(empresaService);
    }
}
