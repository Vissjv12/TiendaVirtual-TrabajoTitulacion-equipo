package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.MovimientoInventarioResponseDto;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.mapper.MovimientoInventarioMapper;
import com.example.msvc_inventario.application.service.InventarioAuthorizationService;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import com.example.msvc_inventario.domain.service.MovimientoInventarioService;
import com.example.msvc_inventario.infrastructure.config.SecurityConfig;
import com.example.msvc_inventario.presentation.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A-01: registrar un movimiento exige ser dueño del inventario, y el autor se toma del JWT
 * (un usuarioId enviado en el cuerpo se ignora).
 */
@WebMvcTest(controllers = com.example.msvc_inventario.presentation.controller.MovimientoInventarioController.class,
        properties = "jwt.secret=local-test-secret-for-inventory")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, InventarioAuthorizationService.class})
class MovimientoPropiedadTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MovimientoInventarioService movimientoService;
    @MockitoBean private MovimientoInventarioMapper movimientoMapper;
    @MockitoBean private ProductoClient productoClient;
    @MockitoBean private InventarioRepository inventarioRepository;
    @MockitoBean private MovimientoInventarioRepository movimientoRepository;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        ProductoDto ajeno = new ProductoDto();
        ajeno.setId(2L);
        ajeno.setEmpresa(new ProductoDto.EmpresaDto(99L));
        ProductoDto propio = new ProductoDto();
        propio.setId(1L);
        propio.setEmpresa(new ProductoDto.EmpresaDto(1L));
        when(productoClient.obtenerProducto(1L)).thenReturn(propio);
        when(productoClient.obtenerProducto(2L)).thenReturn(ajeno);

        Inventario inv10 = new Inventario();
        inv10.setId(10L);
        inv10.setProductoId(1L);
        Inventario inv20 = new Inventario();
        inv20.setId(20L);
        inv20.setProductoId(2L);
        when(inventarioRepository.findById(10L)).thenReturn(Optional.of(inv10));
        when(inventarioRepository.findById(20L)).thenReturn(Optional.of(inv20));
    }

    @Test
    void movimientoSobreInventarioAjenoEsRechazado() throws Exception {
        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L).claim("userId", 5L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventarioId\":20,\"tipoMovimiento\":\"ENTRADA\",\"cantidad\":3}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(movimientoService);
    }

    @Test
    void movimientoPropioRegistraAlUsuarioDelTokenYNoElDelCuerpo() throws Exception {
        when(movimientoMapper.toEntity(any())).thenReturn(new MovimientoInventario());
        when(movimientoService.registrarMovimiento(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoMapper.toDto(any())).thenReturn(new MovimientoInventarioResponseDto());

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L).claim("userId", 5L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventarioId\":10,\"tipoMovimiento\":\"ENTRADA\",\"cantidad\":3,\"usuarioId\":1}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoService).registrarMovimiento(captor.capture());
        assertEquals(5L, captor.getValue().getUsuarioId());
    }

    @Test
    void consultasMovimientosExigenAutenticacionYPropiedad() throws Exception {
        MovimientoInventario movPropio = new MovimientoInventario();
        movPropio.setId(88L);
        movPropio.setInventarioId(10L);
        MovimientoInventario movAjeno = new MovimientoInventario();
        movAjeno.setId(99L);
        movAjeno.setInventarioId(20L);
        when(movimientoRepository.findById(88L)).thenReturn(Optional.of(movPropio));
        when(movimientoRepository.findById(99L)).thenReturn(Optional.of(movAjeno));
        when(movimientoService.obtenerPorId(88L)).thenReturn(movPropio);
        when(movimientoService.listarPorInventarioId(10L)).thenReturn(List.of(movPropio));
        when(movimientoService.listarTodos()).thenReturn(List.of());
        when(movimientoService.listarPaginado(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(movPropio)));
        when(movimientoMapper.toDto(any())).thenReturn(new MovimientoInventarioResponseDto());

        // Anónimo y ROLE_USER rechazados
        mockMvc.perform(get("/api/movimientos/paginado")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/movimientos/inventario/10")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/movimientos/88")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/movimientos/paginado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        // EMP no puede consultar movimientos sin filtro ni de inventarios/movimientos ajenos
        mockMvc.perform(get("/api/movimientos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movimientos/paginado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movimientos/paginado").param("inventarioIds", "10,20")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movimientos/inventario/20")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/movimientos/99")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isForbidden());

        // EMP sí puede consultar los de su inventario
        mockMvc.perform(get("/api/movimientos/paginado").param("inventarioIds", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/movimientos/inventario/10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/movimientos/88")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMP"))
                                .jwt(t -> t.claim("empresaId", 1L))))
                .andExpect(status().isOk());

        // ADMIN puede consultar listado general
        mockMvc.perform(get("/api/movimientos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
