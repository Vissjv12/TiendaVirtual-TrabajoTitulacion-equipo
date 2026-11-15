package com.example.msvc_producto.infrastructure.persistence.impl;

import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.repository.ProductoRepository;
import com.example.msvc_producto.infrastructure.persistence.entity.ProductoEntity;
import com.example.msvc_producto.infrastructure.persistence.mapper.ProductoEntityMapper;
import com.example.msvc_producto.infrastructure.persistence.repository.ProductoJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ProductoRepositoryImpl implements ProductoRepository {

    @Autowired
    private ProductoJpaRepository jpaRepository;

    @Autowired
    private ProductoEntityMapper mapper;

    @Override
    public Producto save(Producto producto) {
        ProductoEntity entity = mapper.toEntity(producto);
        ProductoEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Producto> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Producto> findAll() {
        // ✅ SOLUCIÓN AL PROBLEMA N+1: Usar JOIN FETCH en lugar de findAll()
        return jpaRepository.findAllWithEagerLoading().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Producto> findByEmpresaId(Long empresaId) {
        return jpaRepository.findByEmpresaId(empresaId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Producto> findByCategoriaId(Long categoriaId) {
        return jpaRepository.findByCategoriaId(categoriaId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    // ✅ CORREGIDO: Índices correctos para los 7 campos de la query
    @Override
    public List<ProductoListadoDto> findAllOptimized() {
        List<Object[]> results = jpaRepository.findAllProductosOptimized();

        return results.stream()
                .map(row -> new ProductoListadoDto(
                        ((Number) row[0]).longValue(),  // id
                        (String) row[1],                 // nombre
                        (String) row[2],                 // descripcion
                        ((Number) row[3]).doubleValue(), // precio
                        (String) row[4],                 // imagen
                        (String) row[5],                 // categoriaNombre (índice 5)
                        (String) row[6],                 // empresaNombre (índice 6)
                        ((Number) row[7]).longValue()    // empresaId (índice 7)
                ))
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductoListadoDto> findListadoPaginado(
            String q,
            String categoria,
            String empresa,
            Pageable pageable) {
        return jpaRepository.findListadoPaginado(q, categoria, empresa, pageable)
                .map(entity -> new ProductoListadoDto(
                        entity.getId(),
                        entity.getNombre(),
                        entity.getDescripcion(),
                        entity.getPrecio() != null ? entity.getPrecio().doubleValue() : 0.0,
                        entity.getImagen(),
                        entity.getCategoria() != null ? entity.getCategoria().getNombre() : null,
                        entity.getEmpresa() != null ? entity.getEmpresa().getNombre() : null,
                        entity.getEmpresa() != null ? entity.getEmpresa().getId() : null
                ));
    }

    @Override
    public Page<Producto> findPaginado(
            String q,
            Long empresaId,
            Long categoriaId,
            Boolean activo,
            Pageable pageable) {
        return jpaRepository.findPaginado(q, empresaId, categoriaId, activo, pageable)
                .map(mapper::toDomain);
    }
}