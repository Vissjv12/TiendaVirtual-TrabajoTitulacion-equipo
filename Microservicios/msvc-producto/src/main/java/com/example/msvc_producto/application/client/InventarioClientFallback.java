package com.example.msvc_producto.application.client;

import com.example.msvc_producto.application.dto.InventarioInfoDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class InventarioClientFallback implements InventarioClient {

    @Override
    public InventarioInfoDto crearInventarioParaProducto(Long productoId, Integer stock) {
        return new InventarioInfoDto(null, 0, "No disponible");
    }

    @Override
    public InventarioInfoDto actualizarStock(Long productoId, Integer cantidad) {
        return new InventarioInfoDto(null, 0, "No disponible");
    }

    @Override
    public InventarioInfoDto obtenerInventarioPorProductoId(Long productoId) {
        return new InventarioInfoDto(null, 0, "No disponible");
    }

    // Fallback para la consulta batch de cantidades
    @Override
    public Map<String, Integer> obtenerCantidadesPorProductos(List<Long> productosIds) {
        Map<String, Integer> fallbackInventarios = new HashMap<>();

        // Mantener un valor seguro si Inventario no está disponible.
        for (Long productoId : productosIds) {
            fallbackInventarios.put(productoId.toString(), 0);
        }

        return fallbackInventarios;
    }
}
