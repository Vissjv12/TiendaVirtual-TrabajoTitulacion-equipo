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

    public InventarioServiceImpl(
            InventarioRepository inventarioRepository,
            MovimientoInventarioRepository movimientoRepository,
            @Qualifier("com.example.msvc_inventario.application.client.ProductoClient") ProductoClient productoClient) {
        this.inventarioRepository = inventarioRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoClient = productoClient;
    }

    @Override
    @Transactional
    public Inventario registrarInventario(Inventario inventario) {
        // Verificar que el producto existe
        try {
            ProductoDto producto = productoClient.obtenerProducto(inventario.getProductoId());
            if (producto == null) {
                System.out.println("Producto no encontrado en msvc-producto, creando inventario de todas formas");
            }
        } catch (FeignException e) {
            System.out.println("Error al verificar el producto, creando inventario de todas formas: " + e.getMessage());
        }

        // Verificar si ya existe un inventario para este producto (por ejemplo, el creado automáticamente con stock 0)
        Optional<Inventario> existenteOpt = inventarioRepository.findByProductoId(inventario.getProductoId());
        if (existenteOpt.isPresent()) {
            Inventario existente = existenteOpt.get();
            existente.setCantidad(inventario.getCantidad());
            if (inventario.getUbicacion() != null && !inventario.getUbicacion().isBlank()) {
                existente.setUbicacion(inventario.getUbicacion());
            }
            existente.setActivo(true);
            existente.setFechaActualizacion(LocalDateTime.now());

            Inventario guardado = inventarioRepository.save(existente);

            MovimientoInventario movimiento = new MovimientoInventario();
            movimiento.setInventarioId(guardado.getId());
            movimiento.setTipoMovimiento(MovimientoInventario.TipoMovimiento.ENTRADA);
            movimiento.setCantidad(guardado.getCantidad());
            movimiento.setMotivo("Registro inicial de inventario");
            movimiento.setFechaMovimiento(LocalDateTime.now());
            movimiento.setUsuarioId(1L);
            movimientoRepository.save(movimiento);

            notificarStockProductoAsync(guardado.getProductoId(), guardado.getCantidad());

            return guardado;
        }

        inventario.setFechaCreacion(LocalDateTime.now());
        inventario.setFechaActualizacion(LocalDateTime.now());
        inventario.setActivo(true);

        return inventarioRepository.save(inventario);
    }

    @Override
    @Transactional
    public Inventario actualizarInventario(Long id, Inventario inventarioActualizado) {
        Inventario inventario = obtenerPorId(id);

        // Verificar que el producto existe si es que se cambió
        if (!inventario.getProductoId().equals(inventarioActualizado.getProductoId())) {
            try {
                ProductoDto producto = productoClient.obtenerProducto(inventarioActualizado.getProductoId());
                if (producto == null) {
                    throw new IllegalArgumentException("El producto no existe");
                }
            } catch (FeignException e) {
                throw new IllegalArgumentException("Error al verificar el producto: " + e.getMessage());
            }
        }

        inventario.setProductoId(inventarioActualizado.getProductoId());
        inventario.setCantidad(inventarioActualizado.getCantidad());
        inventario.setUbicacion(inventarioActualizado.getUbicacion());
        inventario.setActivo(inventarioActualizado.getActivo());
        inventario.setFechaActualizacion(LocalDateTime.now());

        // Actualizar también el stock en el microservicio de productos (no bloqueante)
        notificarStockProductoAsync(inventario.getProductoId(), inventario.getCantidad());

        return inventarioRepository.save(inventario);
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
        inventario.setActivo(false);
        inventario.setFechaActualizacion(LocalDateTime.now());
        inventarioRepository.save(inventario);
    }

    @Override
    @Transactional
    public Inventario actualizarStock(Long productoId, Integer cantidad, String tipoMovimientoStr, String motivo) {
        Inventario inventario = obtenerPorProductoId(productoId);
        MovimientoInventario.TipoMovimiento tipoMovimiento;
        try {
            tipoMovimiento = MovimientoInventario.TipoMovimiento.valueOf(tipoMovimientoStr);
        } catch (IllegalArgumentException e) {
            tipoMovimiento = MovimientoInventario.TipoMovimiento.AJUSTE;
        }

        int nuevaCantidad = calcularNuevaCantidad(inventario.getCantidad(), cantidad, tipoMovimiento);

        // Registrar el movimiento
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setInventarioId(inventario.getId());
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setCantidad(cantidad);
        movimiento.setMotivo(motivo);
        movimiento.setFechaMovimiento(LocalDateTime.now());
        movimiento.setUsuarioId(1L); // Se podría obtener del contexto de seguridad

        movimientoRepository.save(movimiento);

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
}