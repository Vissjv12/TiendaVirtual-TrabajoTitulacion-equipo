package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.SalidaInventarioItemDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_inventario.application.service.InventarioAuthorizationService;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T07: autorización de varios inventarios con la empresa del JWT, UNA llamada a msvc-producto y
 * UNA consulta agrupada (antes: una llamada HTTP y una consulta por inventario).
 *
 * Datos: empresa 1 -> productos 1, 2, 3 (inventarios 10, 20, 30); empresa 2 -> producto 4 (inventario 40).
 */
class InventarioAutorizacionLoteTest {

    private ProductoClient productoClient;
    private InventarioRepository inventarioRepository;
    private InventarioAuthorizationService service;

    private static final Map<Long, Long> PRODUCTO_POR_INVENTARIO = Map.of(10L, 1L, 20L, 2L, 30L, 3L, 40L, 4L);

    @BeforeEach
    void setUp() {
        productoClient = mock(ProductoClient.class);
        inventarioRepository = mock(InventarioRepository.class);
        service = new InventarioAuthorizationService(productoClient, inventarioRepository,
                mock(MovimientoInventarioRepository.class));

        when(productoClient.obtenerIdsProductosPorEmpresa(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(productoClient.obtenerIdsProductosPorEmpresa(2L)).thenReturn(List.of(4L));
        when(inventarioRepository.findProductoIdPorInventarioId(any())).thenAnswer(inv -> {
            Map<Long, Long> r = new HashMap<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                Long producto = PRODUCTO_POR_INVENTARIO.get((Long) id);
                if (producto != null) {
                    r.put((Long) id, producto);
                }
            }
            return r;
        });
    }

    @Test
    void variosInventariosPropiosSeAutorizanConUnaSolaLlamadaYUnaConsulta() {
        assertTrue(service.canManageAllInventarios(emp(1L), List.of(10L, 20L, 30L)));

        verify(productoClient, times(1)).obtenerIdsProductosPorEmpresa(1L);
        verify(productoClient, never()).obtenerProducto(anyLong());
        verify(inventarioRepository, times(1)).findProductoIdPorInventarioId(any());
        verify(inventarioRepository, never()).findById(anyLong());
    }

    @Test
    void unSoloInventarioDeOtraEmpresaDeniegaTodoElLote() {
        assertFalse(service.canManageAllInventarios(emp(1L), List.of(10L, 40L)));
        assertFalse(service.canManageAllInventarios(emp(2L), List.of(40L, 10L)));
        assertTrue(service.canManageAllInventarios(emp(2L), List.of(40L)));
    }

    @Test
    void inventarioInexistenteONuloSeDeniega() {
        assertFalse(service.canManageAllInventarios(emp(1L), List.of(10L, 999L)));
        assertFalse(service.canManageAllInventarios(emp(1L), java.util.Arrays.asList(10L, null)));
        assertFalse(service.canManageAllInventarios(emp(1L), List.of()));
    }

    @Test
    void laEmpresaSaleDelJwtNoDeLaPeticion() {
        // Sin claim empresaId no hay empresa que comprobar
        assertFalse(service.canManageAllInventarios(emp(null), List.of(10L)));
        verify(productoClient, never()).obtenerIdsProductosPorEmpresa(anyLong());
    }

    @Test
    void servicioDeProductosCaidoOFallbackDenieganElAcceso() {
        when(productoClient.obtenerIdsProductosPorEmpresa(1L)).thenReturn(null);
        assertFalse(service.canManageAllInventarios(emp(1L), List.of(10L)));

        when(productoClient.obtenerIdsProductosPorEmpresa(1L)).thenThrow(new RuntimeException("timeout"));
        assertFalse(service.canManageAllInventarios(emp(1L), List.of(10L)));
        assertTrue(service.inventariosDeEmpresaDelToken(emp(1L)).isEmpty());
    }

    @Test
    void adminNoNecesitaConsultarProductos() {
        assertTrue(service.canManageAllInventarios(admin(), List.of(10L, 40L)));
        verifyNoInteractions(productoClient);
    }

    @Test
    void inventariosDeLaEmpresaSeCalculanEnElServidor() {
        when(inventarioRepository.findIdsByProductoIdIn(Set.of(1L, 2L, 3L))).thenReturn(List.of(10L, 20L, 30L));
        when(inventarioRepository.findIdsByProductoIdIn(Set.of(4L))).thenReturn(List.of(40L));

        assertEquals(Set.of(10L, 20L, 30L), service.inventariosDeEmpresaDelToken(emp(1L)).orElseThrow());
        assertEquals(Set.of(40L), service.inventariosDeEmpresaDelToken(emp(2L)).orElseThrow());
        assertTrue(service.inventariosDeEmpresaDelToken(admin()).isEmpty(), "solo aplica a ROLE_EMP");
    }

    @Test
    void salidaEnLoteUsaUnaSolaLlamadaYRechazaProductosAjenos() {
        assertTrue(service.canManageSalidaLote(emp(1L), lote(1L, 2L, 3L)));
        verify(productoClient, times(1)).obtenerIdsProductosPorEmpresa(1L);
        verify(productoClient, never()).obtenerProducto(anyLong());

        assertFalse(service.canManageSalidaLote(emp(1L), lote(1L, 4L)));
        assertFalse(service.canManageSalidaLote(emp(1L), lote(1L, null)));
    }

    private static SalidaInventarioLoteRequestDto lote(Long... productos) {
        SalidaInventarioLoteRequestDto dto = new SalidaInventarioLoteRequestDto();
        dto.setItems(java.util.Arrays.stream(productos).map(p -> {
            SalidaInventarioItemDto item = new SalidaInventarioItemDto();
            item.setProductoId(p);
            item.setCantidad(1);
            return item;
        }).toList());
        return dto;
    }

    private static JwtAuthenticationToken emp(Long empresaId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("emp");
        if (empresaId != null) {
            b.claim("empresaId", empresaId);
        }
        return new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority("ROLE_EMP")));
    }

    private static JwtAuthenticationToken admin() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("admin").claim("userId", 1L).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
