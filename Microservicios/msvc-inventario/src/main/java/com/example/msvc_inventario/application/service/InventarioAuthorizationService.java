package com.example.msvc_inventario.application.service;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioItemDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de autorización de propiedad sobre operaciones y consultas de inventario (A-24 / A-01 / T07).
 *
 * La empresa siempre sale del claim {@code empresaId} del JWT. Para varios inventarios a la vez se usa
 * UNA llamada a msvc-producto (ids de productos de esa empresa) y UNA consulta agrupada a la base de
 * inventario, en lugar de una llamada HTTP y una consulta por inventario.
 *
 * Cualquier fallo (sin claim, servicio de productos caído, inventario inexistente) deniega el acceso.
 */
@Service("inventarioAuthorizationService")
@RequiredArgsConstructor
public class InventarioAuthorizationService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_EMP = "ROLE_EMP";

    private final ProductoClient productoClient;
    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;

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

    /** Salida en lote: todos los productos deben ser de la empresa del JWT (una sola llamada a productos). */
    public boolean canManageSalidaLote(Authentication authentication, SalidaInventarioLoteRequestDto requestDto) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || requestDto == null || requestDto.getItems() == null
                || requestDto.getItems().isEmpty()) {
            return false;
        }
        Set<Long> productosSolicitados = new HashSet<>();
        for (SalidaInventarioItemDto item : requestDto.getItems()) {
            if (item == null || item.getProductoId() == null) {
                return false;
            }
            productosSolicitados.add(item.getProductoId());
        }
        return productosDeEmpresaDelToken(authentication)
                .map(propios -> propios.containsAll(productosSolicitados))
                .orElse(false);
    }

    public boolean canManageMovimiento(Authentication authentication, Long movimientoId) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || movimientoId == null) {
            return false;
        }
        return movimientoRepository.findById(movimientoId)
                .map(mov -> canManageInventario(authentication, mov.getInventarioId()))
                .orElse(false);
    }

    /**
     * Varios inventarios a la vez: una consulta agrupada para obtener su producto real y una llamada a
     * productos para la empresa del JWT. Si alguno no existe o es de otra empresa, se deniega todo.
     */
    public boolean canManageAllInventarios(Authentication authentication, Collection<Long> inventarioIds) {
        if (isAdmin(authentication)) {
            return true;
        }
        if (!isEmprendedor(authentication) || inventarioIds == null || inventarioIds.isEmpty()
                || inventarioIds.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        Set<Long> solicitados = new HashSet<>(inventarioIds);
        Map<Long, Long> productoPorInventario = inventarioRepository.findProductoIdPorInventarioId(solicitados);
        if (!productoPorInventario.keySet().containsAll(solicitados)
                || productoPorInventario.values().stream().anyMatch(Objects::isNull)) {
            return false;
        }
        return productosDeEmpresaDelToken(authentication)
                .map(propios -> propios.containsAll(productoPorInventario.values()))
                .orElse(false);
    }

    /**
     * Ids de los inventarios de la empresa del JWT, calculados en el servidor (T07): el navegador ya no
     * decide qué inventarios consultar. Vacío si no se puede determinar (sin claim o servicio caído).
     */
    public Optional<Set<Long>> inventariosDeEmpresaDelToken(Authentication authentication) {
        if (!isEmprendedor(authentication)) {
            return Optional.empty();
        }
        return productosDeEmpresaDelToken(authentication)
                .map(productos -> new HashSet<>(inventarioRepository.findIdsByProductoIdIn(productos)));
    }

    /** Una sola llamada a msvc-producto con la empresa del JWT. Vacío = no se pudo comprobar (deniega). */
    private Optional<Set<Long>> productosDeEmpresaDelToken(Authentication authentication) {
        Long empresaDelToken = getLongClaim(authentication, "empresaId");
        if (empresaDelToken == null) {
            return Optional.empty();
        }
        try {
            List<Long> ids = productoClient.obtenerIdsProductosPorEmpresa(empresaDelToken);
            if (ids == null) {
                return Optional.empty();
            }
            Set<Long> propios = new HashSet<>();
            ids.stream().filter(Objects::nonNull).forEach(propios::add);
            return Optional.of(propios);
        } catch (Exception ex) {
            return Optional.empty();
        }
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
