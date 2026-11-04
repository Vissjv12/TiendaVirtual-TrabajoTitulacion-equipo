package com.example.msvc_producto.application.service;

import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Reglas de autorización de recursos de producto para A-03.
 *
 * Este componente no decide por datos enviados desde el frontend: obtiene la
 * empresa y el usuario desde los claims del JWT validado por Spring Security.
 */
@Service("productoAuthorizationService")
public class ProductoAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_EMP = "ROLE_EMP";

    private final ProductoRepository productoRepository;

    public ProductoAuthorizationService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Comprueba si el usuario puede operar sobre una empresa.
     * Los administradores tienen alcance global; los emprendedores solo sobre
     * la empresa indicada en su JWT.
     */
    public boolean canManageCompany(Authentication authentication, Long empresaId) {
        if (hasAuthority(authentication, ROLE_ADMIN)) {
            return true;
        }

        if (!hasAuthority(authentication, ROLE_EMP) || empresaId == null) {
            return false;
        }

        return Objects.equals(getLongClaim(authentication, "empresaId"), empresaId);
    }

    /** Comprueba si el usuario puede operar sobre el producto indicado. */
    public boolean canManageProduct(Authentication authentication, Long productoId) {
        if (hasAuthority(authentication, ROLE_ADMIN)) {
            return true;
        }

        if (!hasAuthority(authentication, ROLE_EMP) || productoId == null) {
            return false;
        }

        Long empresaDelToken = getLongClaim(authentication, "empresaId");
        if (empresaDelToken == null) {
            return false;
        }

        Optional<Producto> producto = productoRepository.findById(productoId);
        return producto.map(value -> value.getEmpresa() != null
                        && Objects.equals(value.getEmpresa().getId(), empresaDelToken))
                .orElse(false);
    }

    /** Obtiene el usuario autenticado desde el claim userId del JWT. */
    public Optional<Long> getAuthenticatedUserId(Authentication authentication) {
        return Optional.ofNullable(getLongClaim(authentication, "userId"));
    }

    /** Obtiene la empresa asociada al usuario autenticado desde el JWT. */
    public Optional<Long> getAuthenticatedCompanyId(Authentication authentication) {
        return Optional.ofNullable(getLongClaim(authentication, "empresaId"));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private Long getLongClaim(Authentication authentication, String claimName) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            return null;
        }

        Object claim = jwtAuthenticationToken.getToken().getClaims().get(claimName);
        if (claim instanceof Number number) {
            return number.longValue();
        }

        if (claim instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}
