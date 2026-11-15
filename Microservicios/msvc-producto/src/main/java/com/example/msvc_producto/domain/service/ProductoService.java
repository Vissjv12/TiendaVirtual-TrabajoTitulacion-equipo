package com.example.msvc_producto.domain.service;

import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.domain.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductoService {
    Producto crearProducto(Producto producto);
    Producto actualizarProducto(Long id, Producto producto);
    Producto obtenerProductoPorId(Long id);
    List<Producto> listarProductos();
    Page<Producto> listarProductosPaginado(String q, Long empresaId, Long categoriaId, Boolean activo, Pageable pageable);
    List<Producto> buscarProductosPorEmpresa(Long empresaId);
    List<Producto> buscarProductosPorCategoria(Long categoriaId);
    void eliminarProducto(Long id);

    @Transactional(readOnly = true)
    List<ProductoListadoDto> listarProductosOptimizado();

    @Transactional(readOnly = true)
    List<ProductoListadoDto> obtenerProductosOptimizado();

    @Transactional(readOnly = true)
    Page<ProductoListadoDto> obtenerProductosOptimizadoPaginado(
            String q,
            String categoria,
            String empresa,
            Pageable pageable);

}