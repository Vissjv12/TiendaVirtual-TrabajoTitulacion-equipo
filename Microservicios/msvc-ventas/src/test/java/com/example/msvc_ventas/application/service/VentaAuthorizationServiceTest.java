package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaAuthorizationServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PagoRepository pagoRepository;

    private VentaAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new VentaAuthorizationService(ventaRepository, clienteRepository, pagoRepository);
    }

    private JwtAuthenticationToken jwtAuth(String role, String email, Long empresaId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(email != null ? email : "anon")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("scope", role);
        if (email != null) {
            builder.claim("email", email);
        }
        if (empresaId != null) {
            builder.claim("empresaId", empresaId);
        }
        return new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void canManageCompanySoloPermiteAdminOEmprendedorPropio() {
        var admin = jwtAuth("ROLE_ADMIN", "admin@test.com", null);
        var empPropio = jwtAuth("ROLE_EMP", "emp@test.com", 5L);
        var empAjeno = jwtAuth("ROLE_EMP", "emp2@test.com", 9L);
        var empSinClaim = jwtAuth("ROLE_EMP", "emp3@test.com", null);
        var user = jwtAuth("ROLE_USER", "cliente@test.com", 5L);

        assertTrue(authorizationService.canManageCompany(admin, 5L));
        assertTrue(authorizationService.canManageCompany(empPropio, 5L));
        assertFalse(authorizationService.canManageCompany(empAjeno, 5L));
        assertFalse(authorizationService.canManageCompany(empSinClaim, 5L));
        assertFalse(authorizationService.canManageCompany(user, 5L));
        assertFalse(authorizationService.canManageCompany(null, 5L));
    }

    @Test
    void canAccessClienteValidaEmailCaseInsensitive() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmail("Cliente@Sigchos.ec");
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        var admin = jwtAuth("ROLE_ADMIN", "admin@test.com", null);
        var propietario = jwtAuth("ROLE_USER", "cliente@sigchos.ec", null);
        var otro = jwtAuth("ROLE_USER", "otro@sigchos.ec", null);

        assertTrue(authorizationService.canAccessCliente(admin, 10L));
        assertTrue(authorizationService.canAccessCliente(propietario, 10L));
        assertFalse(authorizationService.canAccessCliente(otro, 10L));
        assertFalse(authorizationService.canAccessCliente(propietario, 99L));
        assertTrue(authorizationService.canAccessClienteByEmail(propietario, "Cliente@Sigchos.ec"));
        assertFalse(authorizationService.canAccessClienteByEmail(otro, "Cliente@Sigchos.ec"));
    }

    @Test
    void canViewYManageVentaDistingueCompradorDeEmprendedor() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmail("comprador@test.com");

        Venta venta = new Venta();
        venta.setId(50L);
        venta.setNumeroFactura("FAC-50");
        venta.setEmprendedorId(7L);
        venta.setCliente(cliente);

        when(ventaRepository.findById(50L)).thenReturn(Optional.of(venta));
        when(ventaRepository.findByNumeroFactura("FAC-50")).thenReturn(Optional.of(venta));

        var admin = jwtAuth("ROLE_ADMIN", "admin@test.com", null);
        var comprador = jwtAuth("ROLE_USER", "comprador@test.com", null);
        var empDueno = jwtAuth("ROLE_EMP", "emp@test.com", 7L);
        var empAjeno = jwtAuth("ROLE_EMP", "otro-emp@test.com", 8L);
        var intruso = jwtAuth("ROLE_USER", "intruso@test.com", null);

        assertTrue(authorizationService.canViewVenta(admin, 50L));
        assertTrue(authorizationService.canViewVenta(comprador, 50L));
        assertTrue(authorizationService.canViewVenta(empDueno, 50L));
        assertTrue(authorizationService.canViewVentaByFactura(comprador, "FAC-50"));
        assertFalse(authorizationService.canViewVenta(empAjeno, 50L));
        assertFalse(authorizationService.canViewVenta(intruso, 50L));

        assertTrue(authorizationService.canManageVenta(admin, 50L));
        assertTrue(authorizationService.canManageVenta(empDueno, 50L));
        assertFalse(authorizationService.canManageVenta(comprador, 50L));
        assertFalse(authorizationService.canManageVenta(empAjeno, 50L));
        assertFalse(authorizationService.canManageVenta(intruso, 50L));
    }

    @Test
    void canViewManageYUploadPagoAplicaReglasPorRol() {
        ClienteEntity cliente = new ClienteEntity();
        cliente.setId(10L);
        cliente.setEmail("comprador@test.com");

        VentaEntity venta = new VentaEntity();
        venta.setId(50L);
        venta.setEmprendedorId(7L);
        venta.setCliente(cliente);

        PagoEntity pago = new PagoEntity();
        pago.setId(300L);
        pago.setVenta(venta);

        when(pagoRepository.findById(300L)).thenReturn(Optional.of(pago));

        var admin = jwtAuth("ROLE_ADMIN", "admin@test.com", null);
        var comprador = jwtAuth("ROLE_USER", "comprador@test.com", null);
        var empDueno = jwtAuth("ROLE_EMP", "emp@test.com", 7L);
        var empAjeno = jwtAuth("ROLE_EMP", "emp2@test.com", 8L);
        var intruso = jwtAuth("ROLE_USER", "intruso@test.com", null);

        assertTrue(authorizationService.canViewPago(comprador, 300L));
        assertTrue(authorizationService.canViewPago(empDueno, 300L));
        assertFalse(authorizationService.canViewPago(empAjeno, 300L));
        assertFalse(authorizationService.canViewPago(intruso, 300L));

        assertTrue(authorizationService.canManagePago(admin, 300L));
        assertTrue(authorizationService.canManagePago(empDueno, 300L));
        assertFalse(authorizationService.canManagePago(comprador, 300L));
        assertFalse(authorizationService.canManagePago(empAjeno, 300L));

        assertTrue(authorizationService.canUploadComprobantePago(comprador, 300L));
        assertTrue(authorizationService.canUploadComprobantePago(admin, 300L));
        assertFalse(authorizationService.canUploadComprobantePago(intruso, 300L));
    }
}
