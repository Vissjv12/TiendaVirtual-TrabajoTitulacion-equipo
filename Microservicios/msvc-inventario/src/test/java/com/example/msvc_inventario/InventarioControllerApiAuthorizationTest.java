package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.mapper.InventarioMapper;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.service.InventarioService;
import com.example.msvc_inventario.infrastructure.config.SecurityConfig;
import com.example.msvc_inventario.presentation.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.msvc_inventario.presentation.controller.InventarioController.class,
        properties = "jwt.secret=local-test-secret-for-inventory")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InventarioControllerApiAuthorizationTest {

    private static final String INVENTARIO_VALIDO = """
            {
              "productoId": 1,
              "cantidad": 10,
              "ubicacion": "Almacen principal",
              "activo": true
            }
            """;

    private static final String SALIDA_VALIDA = """
            {
              "items": [
                {"productoId": 1, "cantidad": 1}
              ],
              "motivo": "Prueba de autorizacion"
            }
            """;

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
    private JwtDecoder jwtDecoder;

    @Test
    void crearInventarioSinTokenEsRechazado() throws Exception {
        mockMvc.perform(post("/api/inventarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVENTARIO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearInventarioConRolDeClienteEsRechazado() throws Exception {
        mockMvc.perform(post("/api/inventarios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVENTARIO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void salidaDeInventarioSinTokenEsRechazada() throws Exception {
        mockMvc.perform(post("/api/inventarios/salida-lote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALIDA_VALIDA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void modificarInventarioSinTokenEsRechazado() throws Exception {
        mockMvc.perform(put("/api/inventarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVENTARIO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preflightCorsDelBatchDeCantidadesEsPermitido() throws Exception {
        mockMvc.perform(options("/api/inventarios/cantidad/batch")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightCorsDeEscrituraNoAbreLaOperacionReal() throws Exception {
        mockMvc.perform(options("/api/inventarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVENTARIO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchDeCantidadesPermanecePublico() throws Exception {
        mockMvc.perform(post("/api/inventarios/cantidad/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2]"))
                .andExpect(status().isOk());
    }

    @Test
    void consultaDeInventariosPermanecePublica() throws Exception {
        when(inventarioService.listarTodos()).thenReturn(List.of());

        mockMvc.perform(get("/api/inventarios"))
                .andExpect(status().isOk());
    }
}
