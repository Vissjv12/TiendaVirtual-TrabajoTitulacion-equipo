package com.example.msvc_inventario.infrastructure.persistence.impl;

import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.infrastructure.persistence.entity.InventarioEntity;
import com.example.msvc_inventario.infrastructure.persistence.mapper.InventarioEntityMapper;
import com.example.msvc_inventario.infrastructure.persistence.repository.InventarioJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class InventarioRepositoryImpl implements InventarioRepository {

    private final InventarioJpaRepository jpaRepository;
    private final InventarioEntityMapper mapper;

    public InventarioRepositoryImpl(InventarioJpaRepository jpaRepository, InventarioEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Inventario save(Inventario inventario) {
        InventarioEntity entity = mapper.toEntity(inventario);
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Inventario> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Inventario> findByProductoId(Long productoId) {
        return jpaRepository.findFirstByProductoId(productoId)
                .map(mapper::toDomain);
    }

    @Override
    public List<Inventario> findByProductoIdIn(List<Long> productosIds) {
        List<InventarioEntity> entities = jpaRepository.findByProductoIdIn(productosIds);
        return entities.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Inventario> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Inventario> findPaginado(Boolean soloActivos, Pageable pageable) {
        return jpaRepository.findPaginado(soloActivos, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}