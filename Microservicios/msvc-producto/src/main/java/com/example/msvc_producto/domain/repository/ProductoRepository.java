package com.example.msvc_producto.domain.repository;

import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.domain.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    Producto save(Producto producto);
    Optional<Producto> findById(Long id);
    List<Producto> findAll();
    List<Producto> findByEmpresaId(Long empresaId);
    List<Producto> findByCategoriaId(Long categoriaId);
    void deleteById(Long id);

    // Nuevo método optimizado para listados
    List<ProductoListadoDto> findAllOptimized();

    Page<ProductoListadoDto> findListadoPaginado(String q, String categoria, String empresa, Pageable pageable);

    Page<Producto> findPaginado(String q, Long empresaId, Long categoriaId, Boolean activo, Pageable pageable);

}