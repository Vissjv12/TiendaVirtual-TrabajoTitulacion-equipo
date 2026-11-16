package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.service.InventarioExistenteException;
import com.example.msvc_inventario.application.service.InventarioServiceImpl;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A-01: el inventario inicial no sobrescribe stock; cada cambio de cantidad registra el DELTA y
 * el usuario real del JWT; los rechazos no dejan cambios ni movimientos.
 */
class InventarioServiceMovimientosTest {

    private InventarioRepository inventarioRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private ProductoClient productoClient;
    private InventarioServiceImpl service;

    @BeforeEach
    void setUp() {
        inventarioRepository = mock(InventarioRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        productoClient = mock(ProductoClient.class);
        service = new InventarioServiceImpl(inventarioRepository, movimientoRepository, productoClient,
                new com.example.msvc_inventario.application.service.InventarioAuthorizationService(productoClient, inventarioRepository, movimientoRepository));

        ProductoDto producto = new ProductoDto();
        producto.setId(1L);
        producto.setEmpresa(new ProductoDto.EmpresaDto(1L));
        when(productoClient.obtenerProducto(1L)).thenReturn(producto);
        ProductoDto ajeno = new ProductoDto();
        ajeno.setId(2L);
        ajeno.setEmpresa(new ProductoDto.EmpresaDto(99L));
        when(productoClient.obtenerProducto(2L)).thenReturn(ajeno);
        when(inventarioRepository.save(any())).thenAnswer(inv -> {
            Inventario i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(10L);
            }
            return i;
        });

        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("emp")
                .claim("userId", 5L).claim("empresaId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_EMP"))));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registroInicialNoSobrescribeStockExistente() {
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 40)));

        assertThrows(InventarioExistenteException.class, () -> service.registrarInventario(nuevo(1L, 999)));
        verify(inventarioRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void registroInicialNoSobrescribeSiHayHistorialAunqueElStockSeaCero() {
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 0)));
        when(movimientoRepository.findByInventarioId(10L)).thenReturn(List.of(new MovimientoInventario()));

        assertThrows(InventarioExistenteException.class, () -> service.registrarInventario(nuevo(1L, 30)));
        verify(inventarioRepository, never()).save(any());
    }

    @Test
    void registroInicialSobreElMarcadorAutomaticoFijaLaCantidadYRegistraLaEntrada() {
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 0)));
        when(movimientoRepository.findByInventarioId(10L)).thenReturn(List.of());

        Inventario r = service.registrarInventario(nuevo(1L, 30));

        assertEquals(30, r.getCantidad());
        MovimientoInventario m = movimientoGuardado();
        assertEquals(MovimientoInventario.TipoMovimiento.ENTRADA, m.getTipoMovimiento());
        assertEquals(30, m.getCantidad());
        assertEquals(5L, m.getUsuarioId());
    }

    @Test
    void registroDeProductoInexistenteNoGuardaNada() {
        when(productoClient.obtenerProducto(9L)).thenReturn(null);

        // EMP: sin producto no hay propiedad que demostrar -> 403
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.registrarInventario(nuevo(9L, 5)));
        // ADMIN: pasa la propiedad y falla la existencia -> 400
        Jwt admin = Jwt.withTokenValue("t").header("alg", "HS256").subject("admin").claim("userId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(admin, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThrows(IllegalArgumentException.class, () -> service.registrarInventario(nuevo(9L, 5)));
        verify(inventarioRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void ajusteRegistraElDeltaConSignoYElUsuarioReal() {
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 10)));

        Inventario r = service.actualizarStock(1L, 4, "AJUSTE", "Conteo");

        assertEquals(4, r.getCantidad());
        MovimientoInventario m = movimientoGuardado();
        assertEquals(MovimientoInventario.TipoMovimiento.AJUSTE, m.getTipoMovimiento());
        assertEquals(-6, m.getCantidad());
        assertEquals(5L, m.getUsuarioId());
        assertEquals("Conteo (10 -> 4)", m.getMotivo());
    }

    @Test
    void salidaRegistraLaCantidadMovidaYSinStockNoCambiaNada() {
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 3)));

        assertThrows(IllegalArgumentException.class, () -> service.actualizarStock(1L, 5, "SALIDA", "Venta"));
        verify(inventarioRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());

        service.actualizarStock(1L, 2, "SALIDA", "Venta");
        assertEquals(2, movimientoGuardado().getCantidad());
    }

    @Test
    void credencialDeServicioRegistraUsuarioNulo() {
        Jwt servicio = Jwt.withTokenValue("t").header("alg", "HS256").subject("servicio:pagos")
                .claim("scope", "ROLE_SERVICIO_PAGOS").claim("tipo", "servicio").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(servicio, List.of(new SimpleGrantedAuthority("ROLE_SERVICIO_PAGOS"))));
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 3)));

        service.actualizarStock(1L, 1, "SALIDA", "Venta #F-1");
        assertNull(movimientoGuardado().getUsuarioId());
    }

    @Test
    void edicionNoPuedeReasignarElInventarioAOtroProducto() {
        when(inventarioRepository.findById(10L)).thenReturn(Optional.of(inventario(10L, 1L, 10)));

        assertThrows(IllegalArgumentException.class, () -> service.actualizarInventario(10L, nuevo(2L, 10)));
        verify(inventarioRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void edicionDeCantidadRegistraAjusteConDelta() {
        when(inventarioRepository.findById(10L)).thenReturn(Optional.of(inventario(10L, 1L, 10)));

        service.actualizarInventario(10L, nuevo(1L, 25));

        MovimientoInventario m = movimientoGuardado();
        assertEquals(MovimientoInventario.TipoMovimiento.AJUSTE, m.getTipoMovimiento());
        assertEquals(15, m.getCantidad());
        assertEquals(5L, m.getUsuarioId());
    }

    @Test
    void edicionSinCambioDeCantidadNoRegistraMovimiento() {
        when(inventarioRepository.findById(10L)).thenReturn(Optional.of(inventario(10L, 1L, 10)));

        Inventario cambio = nuevo(1L, 10);
        cambio.setUbicacion("Bodega 2");
        service.actualizarInventario(10L, cambio);

        verify(movimientoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- defensa en profundidad (dominio)

    @Test
    void elServicioRechazaEscriturasSobreProductosAjenosAunqueSeInvoqueDirectamente() {
        when(inventarioRepository.findById(20L)).thenReturn(Optional.of(inventario(20L, 2L, 10)));
        when(inventarioRepository.findByProductoId(2L)).thenReturn(Optional.of(inventario(20L, 2L, 10)));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.registrarInventario(nuevo(2L, 5)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.actualizarInventario(20L, nuevo(2L, 0)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.eliminar(20L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.actualizarStock(2L, 0, "AJUSTE", "x"));
        verify(inventarioRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void salidaEnLoteConUnProductoAjenoResponde403YNoDescuentaNada() {
        when(inventarioRepository.findByProductoId(2L)).thenReturn(Optional.of(inventario(20L, 2L, 10)));
        var item = new com.example.msvc_inventario.application.dto.SalidaInventarioItemDto();
        item.setProductoId(2L);
        item.setCantidad(1);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.procesarSalidaLote(List.of(item), "Venta"));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void sinAutenticacionElServicioNoEscribe() {
        SecurityContextHolder.clearContext();
        when(inventarioRepository.findByProductoId(1L)).thenReturn(Optional.of(inventario(10L, 1L, 10)));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.actualizarStock(1L, 1, "SALIDA", "x"));
        verify(inventarioRepository, never()).save(any());
    }

    private MovimientoInventario movimientoGuardado() {
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static Inventario nuevo(Long productoId, int cantidad) {
        Inventario i = new Inventario();
        i.setProductoId(productoId);
        i.setCantidad(cantidad);
        return i;
    }

    private static Inventario inventario(Long id, Long productoId, int cantidad) {
        Inventario i = nuevo(productoId, cantidad);
        i.setId(id);
        i.setActivo(true);
        return i;
    }
}
