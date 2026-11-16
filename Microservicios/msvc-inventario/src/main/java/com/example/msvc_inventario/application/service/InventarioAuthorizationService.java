package com.example.msvc_inventario.application.service;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioItemDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Reglas de autorización de propiedad sobre operaciones y consultas de inventario (A-24 / T07).
 */
@Service("inventarioAuthorizationService")
public class InventarioAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_EMP = "ROLE_EMP";

    private final ProductoClient productoClient;
    private final InventarioRepository inventarioRepository;

    @Autowired(required = false)
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    public InventarioAuthorizationService(
            ProductoClient productoClient,
            InventarioRepository inventarioRepository) {
        this.productoClient = productoClient;
        this.inventarioRepository = inventarioRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, ROLE_ADMIN);
    }

    public boolean isEmprendedor(Authentication authentication) {
        return hasAuthority(authentication, ROLE_EMP);
    }

    /**
     * Credencial interna de servicio de pagos (auth-service -> msvc-ventas -> inventario) para
     * descontar el stock de una venta pagada por pasarela (A-04). Exige el rol de servicio y el
     * claim tipo=servicio, que los tokens de usuario nunca contienen.
     */
    public boolean isServicioPagos(Authentication authentication) {
        if (!hasAuthority(authentication, "ROLE_SERVICIO_PAGOS")
                || !(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return false;
        }
        Object tipo = jwtToken.getToken().getClaims().get("tipo");
        return "servicio".equals(tipo);
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

    /**
     * Operaciones por inventarioId (PUT/DELETE /api/inventarios/{id}, POST /api/movimientos):
     * se resuelve el producto REAL del inventario guardado y se valida su empresa contra el JWT.
     * Nunca se confía en el productoId del cuerpo de la petición (A-01).
     */
    public boolean canManageInventario(Authentication authentication, Long inventarioId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || inventarioId == null) {
            return false;
        }
        return inventarioRepository.findById(inventarioId)
                .map(inventario -> canManageProduct(authentication, inventario.getProductoId()))
                .orElse(false);
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

    public boolean canManageMovimiento(Authentication authentication, Long movimientoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || movimientoId == null || movimientoRepository == null) {
            return false;
        }
        return movimientoRepository.findById(movimientoId)
                .map(mov -> canManageInventario(authentication, mov.getInventarioId()))
                .orElse(false);
    }

    public boolean canManageAllInventarios(Authentication authentication, List<Long> inventarioIds) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || inventarioIds == null || inventarioIds.isEmpty()) {
            return false;
        }
        for (Long inventarioId : inventarioIds) {
            if (!canManageInventario(authentication, inventarioId)) {
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
