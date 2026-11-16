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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
@Import({SecurityConfig.class, GlobalExceptionHandler.class, InventarioAuthorizationService.class})
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository movimientoRepository;

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
    void preflightCorsDirectoNoEmiteCabeceraCorsDesdeElMicroservicio() throws Exception {
        mockMvc.perform(options("/api/inventarios/cantidad/batch")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightCorsDeEscrituraNoAbreLaOperacionReal() throws Exception {
        mockMvc.perform(options("/api/inventarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

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

    @Test
    void salidaDeInventarioConRolClienteEsRechazada() throws Exception {
        mockMvc.perform(post("/api/inventarios/salida-lote")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALIDA_VALIDA))
                .andExpect(status().isForbidden());
    }

    @Test
    void salidaDeInventarioDeEmpresaAjenaEsRechazada() throws Exception {
        ProductoDto producto = new ProductoDto();
        producto.setId(1L);
        producto.setEmpresa(new ProductoDto.EmpresaDto(99L));
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);

        mockMvc.perform(post("/api/inventarios/salida-lote")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(token -> token.claim("empresaId", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALIDA_VALIDA))
                .andExpect(status().isForbidden());
    }

    @Test
    void salidaDeInventarioDeEmpresaPropiaEsPermitida() throws Exception {
        ProductoDto producto = new ProductoDto();
        producto.setId(1L);
        producto.setEmpresa(new ProductoDto.EmpresaDto(1L));
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);
        // T07: la salida en lote se autoriza con una sola consulta de los productos de la empresa del JWT
        when(productoClient.obtenerIdsProductosPorEmpresa(1L)).thenReturn(List.of(1L));
        when(inventarioService.procesarSalidaLote(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/inventarios/salida-lote")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(token -> token.claim("empresaId", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALIDA_VALIDA))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarStockConRolClienteEsRechazado() throws Exception {
        mockMvc.perform(put("/api/inventarios/producto/1/stock")
                        .param("cantidad", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarStockDeEmpresaAjenaEsRechazado() throws Exception {
        ProductoDto producto = new ProductoDto();
        producto.setId(1L);
        producto.setEmpresa(new ProductoDto.EmpresaDto(99L));
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);

        mockMvc.perform(put("/api/inventarios/producto/1/stock")
                        .param("cantidad", "5")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(token -> token.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarStockDeEmpresaPropiaEsPermitido() throws Exception {
        ProductoDto producto = new ProductoDto();
        producto.setId(1L);
        producto.setEmpresa(new ProductoDto.EmpresaDto(1L));
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);

        Inventario inventario = new Inventario();
        inventario.setProductoId(1L);
        when(inventarioService.actualizarStock(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(inventario);
        when(inventarioMapper.toDto(any(Inventario.class), any(ProductoDto.class)))
                .thenReturn(new InventarioResponseDto());

        mockMvc.perform(put("/api/inventarios/producto/1/stock")
                        .param("cantidad", "5")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(token -> token.claim("empresaId", 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarStockConAdminEsPermitido() throws Exception {
        Inventario inventario = new Inventario();
        inventario.setProductoId(1L);
        when(inventarioService.actualizarStock(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(inventario);
        when(inventarioMapper.toDto(any(Inventario.class))).thenReturn(new InventarioResponseDto());

        mockMvc.perform(put("/api/inventarios/producto/1/stock")
                        .param("cantidad", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
