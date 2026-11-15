package com.example.msvc_inventario.infrastructure.persistence.impl;

import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.repository.MovimientoInventarioRepository;
import com.example.msvc_inventario.infrastructure.persistence.entity.MovimientoInventarioEntity;
import com.example.msvc_inventario.infrastructure.persistence.mapper.MovimientoInventarioEntityMapper;
import com.example.msvc_inventario.infrastructure.persistence.repository.MovimientoInventarioJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MovimientoInventarioRepositoryImpl implements MovimientoInventarioRepository {

    private final MovimientoInventarioJpaRepository jpaRepository;
    private final MovimientoInventarioEntityMapper mapper;

    public MovimientoInventarioRepositoryImpl(
            MovimientoInventarioJpaRepository jpaRepository,
            MovimientoInventarioEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public MovimientoInventario save(MovimientoInventario movimiento) {
        MovimientoInventarioEntity entity = mapper.toEntity(movimiento);
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<MovimientoInventario> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<MovimientoInventario> findByInventarioId(Long inventarioId) {
        return jpaRepository.findByInventarioId(inventarioId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MovimientoInventario> findByInventarioId(Long inventarioId, Pageable pageable) {
        return jpaRepository.findByInventarioId(inventarioId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<MovimientoInventario> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MovimientoInventario> findPaginado(
            Long inventarioId,
            List<Long> inventarioIds,
            MovimientoInventario.TipoMovimiento tipo,
            String motivo,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Pageable pageable) {
        boolean filtrarInventarios = inventarioIds != null;
        if (filtrarInventarios && inventarioIds.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Long> safeIds = filtrarInventarios ? inventarioIds : Collections.singletonList(-1L);
        MovimientoInventarioEntity.TipoMovimiento entityTipo = tipo == null
                ? null
                : MovimientoInventarioEntity.TipoMovimiento.valueOf(tipo.name());
        return jpaRepository.findPaginado(
                        inventarioId,
                        filtrarInventarios,
                        safeIds,
                        entityTipo,
                        motivo == null ? "" : motivo.trim(),
                        fechaDesde,
                        fechaHasta,
                        pageable)
                .map(mapper::toDomain);
    }
}