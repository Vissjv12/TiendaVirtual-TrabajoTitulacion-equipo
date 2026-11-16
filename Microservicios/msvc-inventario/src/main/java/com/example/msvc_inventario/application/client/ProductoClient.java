package com.example.msvc_inventario.application.client;

import com.example.msvc_inventario.application.dto.ProductoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "msvc-producto", url = "${app.msvc-producto.url}", fallback = ProductoClientFallback.class)
public interface ProductoClient {

    @GetMapping("/api/productos/{id}")
    ProductoDto obtenerProducto(@PathVariable("id") Long id);

    /** Ids de todos los productos de una empresa en una sola llamada (T07). Fallback: null (deniega). */
    @GetMapping("/api/productos/empresa/{empresaId}/ids")
    java.util.List<Long> obtenerIdsProductosPorEmpresa(@PathVariable("empresaId") Long empresaId);

    @PutMapping("/api/productos/{id}/sincronizar-stock")
    void actualizarStockProducto(@PathVariable("id") Long id, @RequestParam("stock") Integer stock);

}