package com.example.msvc_producto;

import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoAuthorizationServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    private ProductoAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new ProductoAuthorizationService(productoRepository);
    }

    @Test
    void administradorPuedeGestionarCualquierEmpresa() {
        JwtAuthenticationToken authentication = authentication("ROLE_ADMIN", null);

        assertTrue(authorizationService.canManageCompany(authentication, 999L));
    }

    @Test
    void emprendedorSoloPuedeGestionarSuEmpresa() {
        JwtAuthenticationToken authentication = authentication("ROLE_EMP", 10L);

        assertTrue(authorizationService.canManageCompany(authentication, 10L));
        assertFalse(authorizationService.canManageCompany(authentication, 20L));
    }

    @Test
    void emprendedoresDeEmpresasDiferentesNoCompartenAlcance() {
        JwtAuthenticationToken emprendedorEmpresaA = authentication("ROLE_EMP", 10L);
        JwtAuthenticationToken emprendedorEmpresaB = authentication("ROLE_EMP", 20L);

        assertTrue(authorizationService.canManageCompany(emprendedorEmpresaA, 10L));
        assertFalse(authorizationService.canManageCompany(emprendedorEmpresaA, 20L));
        assertTrue(authorizationService.canManageCompany(emprendedorEmpresaB, 20L));
        assertFalse(authorizationService.canManageCompany(emprendedorEmpresaB, 10L));
    }

    @Test
    void emprendedorSinEmpresaNoPuedeGestionarRecursos() {
        JwtAuthenticationToken authentication = authentication("ROLE_EMP", null);

        assertFalse(authorizationService.canManageCompany(authentication, 10L));
    }

    @Test
    void emprendedorSoloPuedeGestionarProductosDeSuEmpresa() {
        Producto producto = new Producto();
        Empresa empresa = new Empresa();
        empresa.setId(10L);
        producto.setEmpresa(empresa);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        assertTrue(authorizationService.canManageProduct(authentication("ROLE_EMP", 10L), 1L));
        assertFalse(authorizationService.canManageProduct(authentication("ROLE_EMP", 20L), 1L));
    }

    @Test
    void autenticacionSinRolDeGestionEsRechazada() {
        JwtAuthenticationToken authentication = authentication("ROLE_USER", 10L);

        assertFalse(authorizationService.canManageCompany(authentication, 10L));
    }

    @Test
    void autenticacionNoJwtEsRechazada() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_EMP")));

        assertFalse(authorizationService.canManageCompany(authentication, 10L));
    }

    private JwtAuthenticationToken authentication(String role, Long empresaId) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("userId", 100L)
                .claim("scope", role);
        if (empresaId != null) {
            jwtBuilder.claim("empresaId", empresaId);
        }

        Jwt jwt = jwtBuilder.build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }
}
