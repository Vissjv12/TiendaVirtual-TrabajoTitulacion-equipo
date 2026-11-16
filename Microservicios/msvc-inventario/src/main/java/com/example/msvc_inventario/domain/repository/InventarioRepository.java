package com.example.msvc_inventario.domain.repository;

import com.example.msvc_inventario.domain.model.Inventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface InventarioRepository {
    Inventario save(Inventario inventario);
    Optional<Inventario> findById(Long id);
    Optional<Inventario> findByProductoId(Long productoId);
    List<Inventario> findAll();
    Page<Inventario> findPaginado(Boolean soloActivos, Pageable pageable);
    void deleteById(Long id);
    List<Inventario> findByProductoIdIn(List<Long> productosIds);
    /** Pares (id, productoId) de los inventarios indicados, en una sola consulta (T07). */
    java.util.Map<Long, Long> findProductoIdPorInventarioId(java.util.Collection<Long> inventarioIds);
    /** Ids de los inventarios de los productos indicados, en una sola consulta (T07). */
    List<Long> findIdsByProductoIdIn(java.util.Collection<Long> productosIds);
}