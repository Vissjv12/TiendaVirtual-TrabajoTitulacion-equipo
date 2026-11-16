package com.example.msvc_inventario;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.service.InventarioAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A-04: la salida de stock de una venta pagada por pasarela usa la credencial interna de
 * servicio (rol ROLE_SERVICIO_PAGOS + claim tipo=servicio), nunca un token de comprador.
 */
class InventarioServicioPagosAuthorizationTest {

    private final InventarioAuthorizationService service =
            new InventarioAuthorizationService(mock(ProductoClient.class),
                    mock(com.example.msvc_inventario.domain.repository.InventarioRepository.class),
                    mock(com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository.class));

    private static JwtAuthenticationToken token(String rol, String tipo) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("s").claim("scope", rol);
        if (tipo != null) {
            b.claim("tipo", tipo);
        }
        return new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority(rol)));
    }

    @Test
    void credencialDeServicioEsAceptada() {
        assertTrue(service.isServicioPagos(token("ROLE_SERVICIO_PAGOS", "servicio")));
    }

    @Test
    void tokensDeUsuarioOSinClaimTipoSonRechazados() {
        assertFalse(service.isServicioPagos(token("ROLE_USER", null)));
        assertFalse(service.isServicioPagos(token("ROLE_CLIENTE", "servicio")));
        assertFalse(service.isServicioPagos(token("ROLE_SERVICIO_PAGOS", null)));
        assertFalse(service.isServicioPagos(token("ROLE_SERVICIO_PAGOS", "usuario")));
        assertFalse(service.isServicioPagos(null));
    }
}
