package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Reglas de autorización de negocio sobre clientes, ventas, pagos y logística (A-24).
 *
 * Extrae identidad, rol y empresa asociada directamente desde el JWT validado por
 * Spring Security sin confiar en parámetros modificables enviados por el cliente.
 */
@Service("ventaAuthorizationService")
@RequiredArgsConstructor
public class VentaAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_EMP = "ROLE_EMP";
    /** Rol exclusivo de la credencial de servicio emitida por auth-service para pagos (A-04). */
    public static final String ROLE_SERVICIO_PAGOS = "ROLE_SERVICIO_PAGOS";

    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final PagoRepository pagoRepository;

    public boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, ROLE_ADMIN);
    }

    public boolean isEmprendedor(Authentication authentication) {
        return hasAuthority(authentication, ROLE_EMP);
    }

    /**
     * Credencial interna de servicio (auth-service -> msvc-ventas) para registrar pagos de pasarela.
     * Exige el rol de servicio Y el claim tipo=servicio, que los tokens de usuario nunca contienen.
     */
    public boolean isServicioPagos(Authentication authentication) {
        return hasAuthority(authentication, ROLE_SERVICIO_PAGOS)
                && "servicio".equals(getStringClaim(authentication, "tipo"));
    }

    /**
     * Comprueba si el usuario puede operar sobre la empresa/emprendedor indicado.
     * Los administradores tienen alcance global; los emprendedores solo sobre su empresaId del JWT.
     */
    public boolean canManageCompany(Authentication authentication, Long empresaId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || empresaId == null) {
            return false;
        }
        return Objects.equals(getLongClaim(authentication, "empresaId"), empresaId);
    }

    /** Comprueba si el usuario autenticado puede consultar/modificar al cliente indicado por ID. */
    public boolean canAccessCliente(Authentication authentication, Long clienteId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || clienteId == null) {
            return false;
        }
        Optional<Cliente> cliente = clienteRepository.findById(clienteId);
        return cliente.map(c -> emailMatchesAuth(authentication, c.getEmail())).orElse(false);
    }

    /** Comprueba si el usuario autenticado puede consultar un cliente por su email. */
    public boolean canAccessClienteByEmail(Authentication authentication, String email) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || email == null || email.isBlank()) {
            return false;
        }
        return emailMatchesAuth(authentication, email);
    }

    /** Comprueba si el usuario autenticado puede crear un registro de cliente para ese email. */
    public boolean canCreateClienteWithEmail(Authentication authentication, String email) {
        return canAccessClienteByEmail(authentication, email);
    }

    /**
     * Comprueba si el usuario puede consultar una venta (por factura, detalle, seguimiento o pagos):
     * administrador, emprendedor dueño o cliente comprador dueño.
     */
    public boolean canViewVenta(Authentication authentication, Long ventaId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || ventaId == null) {
            return false;
        }
        return ventaRepository.findById(ventaId)
                .map(venta -> ventaIsOwnedByAuth(authentication, venta))
                .orElse(false);
    }

    /** Variante de {@link #canViewVenta(Authentication, Long)} por número de factura. */
    public boolean canViewVentaByFactura(Authentication authentication, String numeroFactura) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || numeroFactura == null || numeroFactura.isBlank()) {
            return false;
        }
        return ventaRepository.findByNumeroFactura(numeroFactura)
                .map(venta -> ventaIsOwnedByAuth(authentication, venta))
                .orElse(false);
    }

    /**
     * Comprueba si el usuario puede gestionar operativamente una venta (completar, cancelar,
     * actualizar seguimiento logístico o generar factura): solo administrador o emprendedor dueño.
     */
    public boolean canManageVenta(Authentication authentication, Long ventaId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || ventaId == null) {
            return false;
        }
        Long empresaDelToken = getLongClaim(authentication, "empresaId");
        if (empresaDelToken == null) {
            return false;
        }
        return ventaRepository.findById(ventaId)
                .map(venta -> Objects.equals(venta.getEmprendedorId(), empresaDelToken))
                .orElse(false);
    }

    /** Comprueba si el usuario puede consultar un pago por su ID. */
    public boolean canViewPago(Authentication authentication, Long pagoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || pagoId == null) {
            return false;
        }
        Optional<PagoEntity> pago = pagoRepository.findById(pagoId);
        return pago.map(p -> ventaEntityIsViewableByAuth(authentication, p.getVenta())).orElse(false);
    }

    /**
     * Comprueba si el usuario puede aprobar o rechazar un pago: solo administrador
     * o emprendedor dueño de la venta asociada al pago.
     */
    public boolean canManagePago(Authentication authentication, Long pagoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || pagoId == null) {
            return false;
        }
        Long empresaDelToken = getLongClaim(authentication, "empresaId");
        if (empresaDelToken == null) {
            return false;
        }
        return pagoRepository.findById(pagoId)
                .map(PagoEntity::getVenta)
                .map(venta -> Objects.equals(venta.getEmprendedorId(), empresaDelToken))
                .orElse(false);
    }

    /**
     * Comprueba si el usuario puede subir comprobante para un pago:
     * administrador o cliente dueño de la venta asociada.
     */
    public boolean canUploadComprobantePago(Authentication authentication, Long pagoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (authentication == null || pagoId == null) {
            return false;
        }
        return pagoRepository.findById(pagoId)
                .map(PagoEntity::getVenta)
                .map(venta -> clienteEntityMatchesAuth(authentication, venta.getCliente()))
                .orElse(false);
    }

    public Optional<Long> getAuthenticatedCompanyId(Authentication authentication) {
        return Optional.ofNullable(getLongClaim(authentication, "empresaId"));
    }

    private boolean ventaIsOwnedByAuth(Authentication authentication, Venta venta) {
        if (isEmprendedor(authentication)) {
            Long empresaDelToken = getLongClaim(authentication, "empresaId");
            if (empresaDelToken != null && Objects.equals(empresaDelToken, venta.getEmprendedorId())) {
                return true;
            }
        }
        return venta.getCliente() != null && emailMatchesAuth(authentication, venta.getCliente().getEmail());
    }

    private boolean ventaEntityIsViewableByAuth(Authentication authentication, VentaEntity venta) {
        if (venta == null) {
            return false;
        }
        if (isEmprendedor(authentication)) {
            Long empresaDelToken = getLongClaim(authentication, "empresaId");
            if (empresaDelToken != null && Objects.equals(empresaDelToken, venta.getEmprendedorId())) {
                return true;
            }
        }
        return clienteEntityMatchesAuth(authentication, venta.getCliente());
    }

    private boolean clienteEntityMatchesAuth(Authentication authentication, ClienteEntity cliente) {
        return cliente != null && emailMatchesAuth(authentication, cliente.getEmail());
    }

    private boolean emailMatchesAuth(Authentication authentication, String targetEmail) {
        if (targetEmail == null || targetEmail.isBlank()) {
            return false;
        }
        String normalizedTarget = targetEmail.trim();
        String tokenEmail = getStringClaim(authentication, "email");
        if (tokenEmail != null && !tokenEmail.isBlank()) {
            return tokenEmail.trim().equalsIgnoreCase(normalizedTarget);
        }
        String principalName = authentication.getName();
        return principalName != null && !principalName.isBlank()
                && principalName.trim().equalsIgnoreCase(normalizedTarget);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private Long getLongClaim(Authentication authentication, String claimName) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return null;
        }
        Object claim = jwtToken.getToken().getClaims().get(claimName);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String value) {
            try {
                return Long.valueOf(value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String getStringClaim(Authentication authentication, String claimName) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return null;
        }
        Object claim = jwtToken.getToken().getClaims().get(claimName);
        return claim != null ? claim.toString() : null;
    }
}
