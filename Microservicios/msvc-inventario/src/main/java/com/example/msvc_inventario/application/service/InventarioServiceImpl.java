package com.example.msvc_inventario.application.service;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.ProductoDto;
import com.example.msvc_inventario.application.dto.SalidaInventarioItemDto;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import com.example.msvc_inventario.domain.service.InventarioService;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

@Service
public class InventarioServiceImpl implements InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoClient productoClient;
    private final InventarioAuthorizationService autorizacion;

    public InventarioServiceImpl(
            InventarioRepository inventarioRepository,
            MovimientoInventarioRepository movimientoRepository,
            @Qualifier("com.example.msvc_inventario.application.client.ProductoClient") ProductoClient productoClient,
            InventarioAuthorizationService autorizacion) {
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoClient = productoClient;
        this.autorizacion = autorizacion;
    }

    /**
     * Defensa en profundidad (A-01): además de @PreAuthorize en el controlador, el dominio exige
     * que quien escribe sea ADMIN, la credencial interna de servicio, o el EMP dueño del producto.
     * Así la regla no depende solo de la capa web.
     */
    private void exigirPropiedad(Long productoId) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (autorizacion.isAdmin(auth) || autorizacion.isServicioPagos(auth)) {
            return;
        }
        if (autorizacion.isEmprendedor(auth) && autorizacion.canManageProduct(auth, productoId)) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "No puede modificar el inventario de un producto de otra empresa");
    }

    /**
     * Registro INICIAL de inventario (A-01).
     * - Si el producto no tiene inventario, lo crea.
     * - Si solo existe el marcador automatico (stock 0, sin movimientos), fija la cantidad inicial.
     * - En cualquier otro caso NO sobrescribe: responde 409 y debe usarse el ajuste de stock.
     * Cada cambio de cantidad registra un movimiento con el delta y el usuario real.
     */
    @Override
    @Transactional
    public Inventario registrarInventario(Inventario inventario) {
        if (inventario.getCantidad() == null || inventario.getCantidad() < 0) {
            throw new IllegalArgumentException("La cantidad inicial debe ser mayor o igual a 0");
        }
        exigirPropiedad(inventario.getProductoId());
        verificarProductoExiste(inventario.getProductoId());

        Optional<Inventario> existenteOpt = inventarioRepository.findByProductoId(inventario.getProductoId());
        if (existenteOpt.isPresent()) {
            Inventario existente = existenteOpt.get();
            boolean esMarcadorInicial = (existente.getCantidad() == null || existente.getCantidad() == 0)
                    && movimientoRepository.findByInventarioId(existente.getId()).isEmpty();
            if (!esMarcadorInicial) {
                throw new InventarioExistenteException(
                        "El producto ya tiene inventario registrado; usa el ajuste de stock para modificarlo");
            }
            if (inventario.getUbicacion() != null && !inventario.getUbicacion().isBlank()) {
                existente.setUbicacion(inventario.getUbicacion());
            }
            existente.setCantidad(inventario.getCantidad());
            existente.setActivo(true);
            existente.setFechaActualizacion(LocalDateTime.now());
            Inventario guardado = inventarioRepository.save(existente);
            registrarMovimiento(guardado.getId(), MovimientoInventario.TipoMovimiento.ENTRADA,
                    guardado.getCantidad(), "Registro inicial de inventario");
            notificarStockProductoAsync(guardado.getProductoId(), guardado.getCantidad());
            return guardado;
        }

        inventario.setId(null);
        inventario.setFechaCreacion(LocalDateTime.now());
        inventario.setFechaActualizacion(LocalDateTime.now());
        inventario.setActivo(true);
        Inventario guardado = inventarioRepository.save(inventario);
        registrarMovimiento(guardado.getId(), MovimientoInventario.TipoMovimiento.ENTRADA,
                guardado.getCantidad(), "Registro inicial de inventario");
        if (guardado.getCantidad() > 0) {
            notificarStockProductoAsync(guardado.getProductoId(), guardado.getCantidad());
        }
        return guardado;
    }

    /**
     * Edicion de un inventario existente (A-01): no permite moverlo a otro producto, y un cambio
     * de cantidad queda registrado como AJUSTE con el delta y el usuario real.
     */
    @Override
    @Transactional
    public Inventario actualizarInventario(Long id, Inventario inventarioActualizado) {
        Inventario inventario = obtenerPorId(id);
        exigirPropiedad(inventario.getProductoId());

        if (inventarioActualizado.getProductoId() != null
                && !inventario.getProductoId().equals(inventarioActualizado.getProductoId())) {
            throw new IllegalArgumentException("No se puede reasignar un inventario a otro producto");
        }
        Integer nuevaCantidad = inventarioActualizado.getCantidad();
        if (nuevaCantidad != null && nuevaCantidad < 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 0");
        }

        int anterior = inventario.getCantidad() == null ? 0 : inventario.getCantidad();
        if (nuevaCantidad != null && nuevaCantidad != anterior) {
            inventario.setCantidad(nuevaCantidad);
            registrarMovimiento(inventario.getId(), MovimientoInventario.TipoMovimiento.AJUSTE,
                    nuevaCantidad - anterior, "Edicion de inventario: " + anterior + " -> " + nuevaCantidad);
        }
        if (inventarioActualizado.getUbicacion() != null) {
            inventario.setUbicacion(inventarioActualizado.getUbicacion());
        }
        if (inventarioActualizado.getActivo() != null) {
            inventario.setActivo(inventarioActualizado.getActivo());
        }
        inventario.setFechaActualizacion(LocalDateTime.now());

        Inventario guardado = inventarioRepository.save(inventario);
        notificarStockProductoAsync(guardado.getProductoId(), guardado.getCantidad());
        return guardado;
    }

    private void verificarProductoExiste(Long productoId) {
        ProductoDto producto;
        try {
            producto = productoClient.obtenerProducto(productoId);
        } catch (FeignException.NotFound e) {
            producto = null;
        } catch (FeignException e) {
            throw new IllegalStateException("No se pudo verificar el producto; intenta nuevamente");
        }
        if (producto == null) {
            throw new IllegalArgumentException("El producto no existe");
        }
    }

    /**
     * Movimiento con el DELTA aplicado y el usuario real del JWT (null para credenciales de servicio).
     * Para AJUSTE la cantidad es el delta con signo (nuevo - anterior).
     */
    private void registrarMovimiento(Long inventarioId, MovimientoInventario.TipoMovimiento tipo,
                                     int delta, String motivo) {
        if (delta == 0) {
            return;
        }
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setInventarioId(inventarioId);
        movimiento.setTipoMovimiento(tipo);
        movimiento.setCantidad(delta);
        movimiento.setMotivo(motivo != null && motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
        movimiento.setFechaMovimiento(LocalDateTime.now());
        movimiento.setUsuarioId(UsuarioAutenticado.id());
        movimientoRepository.save(movimiento);
    }

    @Override
    public Inventario obtenerPorId(Long id) {
        return inventarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Inventario no encontrado con ID: " + id));
    }

    @Override
    public Inventario obtenerPorProductoId(Long productoId) {
        return inventarioRepository.findByProductoId(productoId)
                .orElseThrow(() -> new NoSuchElementException("Inventario no encontrado para el producto con ID: " + productoId));
    }

    @Override
    public List<Inventario> listarTodos() {
        return inventarioRepository.findAll();
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Inventario inventario = obtenerPorId(id);
        exigirPropiedad(inventario.getProductoId());
        inventario.setActivo(false);
        inventario.setFechaActualizacion(LocalDateTime.now());
        inventarioRepository.save(inventario);
    }

    @Override
    @Transactional
    public Inventario actualizarStock(Long productoId, Integer cantidad, String tipoMovimientoStr, String motivo) {
        Inventario inventario = obtenerPorProductoId(productoId);
        exigirPropiedad(productoId);
        MovimientoInventario.TipoMovimiento tipoMovimiento;
        try {
            tipoMovimiento = MovimientoInventario.TipoMovimiento.valueOf(tipoMovimientoStr);
        } catch (IllegalArgumentException e) {
            tipoMovimiento = MovimientoInventario.TipoMovimiento.AJUSTE;
        }

        if (cantidad == null || cantidad < 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 0");
        }
        int anterior = inventario.getCantidad() == null ? 0 : inventario.getCantidad();
        int nuevaCantidad = calcularNuevaCantidad(anterior, cantidad, tipoMovimiento);

        // Movimiento con el delta real y el usuario del JWT (antes usuarioId fijo 1 y, en AJUSTE, el total).
        // ENTRADA/SALIDA guardan la cantidad movida; AJUSTE guarda el delta con signo (nuevo - anterior).
        int delta = tipoMovimiento == MovimientoInventario.TipoMovimiento.AJUSTE ? nuevaCantidad - anterior : cantidad;
        String motivoFinal = tipoMovimiento == MovimientoInventario.TipoMovimiento.AJUSTE
                ? (motivo == null || motivo.isBlank() ? "Ajuste" : motivo) + " (" + anterior + " -> " + nuevaCantidad + ")"
                : motivo;
        registrarMovimiento(inventario.getId(), tipoMovimiento, delta, motivoFinal);

        // Actualizar el inventario
        inventario.setCantidad(nuevaCantidad);
        inventario.setFechaActualizacion(LocalDateTime.now());

        // Actualizar también el stock en el microservicio de productos (no bloqueante)
        notificarStockProductoAsync(inventario.getProductoId(), nuevaCantidad);

        return inventarioRepository.save(inventario);
    }

    /**
     * Nuevo método para procesar salidas en lote (para integrarse con el microservicio de ventas)
     */
    @Override
    @Transactional
    public List<Inventario> procesarSalidaLote(List<SalidaInventarioItemDto> items, String motivo) {
        List<Inventario> inventariosActualizados = new ArrayList<>();

        // Procesamos cada item como una salida individual
        for (SalidaInventarioItemDto item : items) {
            try {
                Inventario inventario = actualizarStock(
                        item.getProductoId(),
                        item.getCantidad(),
                        MovimientoInventario.TipoMovimiento.SALIDA.name(),
                        motivo
                );
                inventariosActualizados.add(inventario);
            } catch (org.springframework.security.access.AccessDeniedException e) {
                // Producto de otra empresa: 403 (rollback de todo el lote), no un error 500 genérico
                throw e;
            } catch (Exception e) {
                // Si falla alguno, lanzar excepción para hacer rollback de toda la transacción
                throw new RuntimeException("Error al procesar salida para producto ID " +
                        item.getProductoId() + ": " + e.getMessage(), e);
            }
        }

        return inventariosActualizados;
    }

    /**
     * Implementación del método para obtener solo la cantidad de un producto
     */
    @Override
    public Integer obtenerCantidadPorProducto(Long productoId) {
        try {
            Optional<Inventario> inventario = inventarioRepository.findByProductoId(productoId);

            if (inventario.isPresent()) {
                Integer cantidad = inventario.get().getCantidad();
                return cantidad != null ? cantidad : 0;
            } else {
                // Si no existe inventario para este producto, devolver 0
                return 0;
            }
        } catch (Exception e) {
            // En caso de cualquier error, devolver 0
            System.err.println("Error obteniendo cantidad para producto " + productoId + ": " + e.getMessage());
            return 0;
        }
    }

    private int calcularNuevaCantidad(int cantidadActual, int cantidadMovimiento, MovimientoInventario.TipoMovimiento tipoMovimiento) {
        switch (tipoMovimiento) {
            case ENTRADA:
                return cantidadActual + cantidadMovimiento;
            case SALIDA:
                int nuevaCantidad = cantidadActual - cantidadMovimiento;
                if (nuevaCantidad < 0) {
                    throw new IllegalArgumentException("No hay suficiente stock disponible");
                }
                return nuevaCantidad;
            case AJUSTE:
                return cantidadMovimiento; // La cantidad proporcionada es el nuevo valor total
            default:
                throw new IllegalArgumentException("Tipo de movimiento no válido");
        }
    }

    private void notificarStockProductoAsync(Long productoId, Integer cantidad) {
        CompletableFuture.runAsync(() -> {
            try {
                productoClient.actualizarStockProducto(productoId, cantidad);
            } catch (FeignException e) {
                System.out.println("Error al actualizar stock en productos: " + e.getMessage());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Inventario> listarPaginado(Boolean soloActivos, Pageable pageable) {
        return inventarioRepository.findPaginado(soloActivos, pageable);
    }
}