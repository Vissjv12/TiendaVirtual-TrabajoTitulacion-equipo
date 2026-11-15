package com.example.msvc_inventario.application.service;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioItemDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioLoteRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Reglas de autorización de propiedad sobre operaciones de stock de inventario (A-24).
 */
@Service("inventarioAuthorizationService")
@RequiredArgsConstructor
public class InventarioAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_EMP = "ROLE_EMP";

    private final ProductoClient productoClient;

    public boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, ROLE_ADMIN);
    }

    public boolean isEmprendedor(Authentication authentication) {
        return hasAuthority(authentication, ROLE_EMP);
    }

    public boolean canManageProduct(Authentication authentication, Long productoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || productoId == null) {
            return false;
        }
        Long empresaDelToken = getLongClaim(authentication, "empresaId");
        if (empresaDelToken == null) {
            return false;
        }
        try {
            ProductoDto producto = productoClient.obtenerProducto(productoId);
            if (producto == null || producto.getEmpresa() == null || producto.getEmpresa().getId() == null) {
                return false;
            }
            return Objects.equals(producto.getEmpresa().getId(), empresaDelToken);
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean canManageSalidaLote(Authentication authentication, SalidaInventarioLoteRequestDto requestDto) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || requestDto == null || requestDto.getItems() == null || requestDto.getItems().isEmpty()) {
            return false;
        }
        for (SalidaInventarioItemDto item : requestDto.getItems()) {
            if (item == null || !canManageProduct(authentication, item.getProductoId())) {
                return false;
            }
        }
        return true;
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
}
