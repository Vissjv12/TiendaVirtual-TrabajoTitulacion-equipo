package com.example.msvc_ventas.infrastructure.persistence.impl;

import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import com.example.msvc_ventas.domain.repository.SeguimientoLogisticaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.SeguimientoLogisticaEntity;
import com.example.msvc_ventas.infrastructure.persistence.mapper.SeguimientoLogisticaMapper;
import com.example.msvc_ventas.infrastructure.persistence.repository.JpaSeguimientoLogisticaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeguimientoLogisticaRepositoryImpl implements SeguimientoLogisticaRepository {

    private final JpaSeguimientoLogisticaRepository jpaRepository;
    private final SeguimientoLogisticaMapper mapper;

    @Override
    public SeguimientoLogistica save(SeguimientoLogistica seguimiento) {
        SeguimientoLogisticaEntity entity = mapper.toEntity(seguimiento);
        SeguimientoLogisticaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<SeguimientoLogistica> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<SeguimientoLogistica> findByVentaId(Long ventaId) {
        return jpaRepository.findByVentaIdOrderByFechaCreacionAsc(ventaId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SeguimientoLogistica> findUltimoEstadoByVentaId(Long ventaId) {
        return jpaRepository.findTopByVentaIdOrderByFechaCreacionDesc(ventaId)
                .map(mapper::toDomain);
    }

    @Override
    public List<SeguimientoLogistica> findByEstadoLogistica(SeguimientoLogistica.EstadoLogistica estadoLogistica) {
        SeguimientoLogisticaEntity.EstadoLogistica estadoEntity = 
            SeguimientoLogisticaEntity.EstadoLogistica.valueOf(estadoLogistica.name());
        return jpaRepository.findByEstadoLogistica(estadoEntity)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeguimientoLogistica> findByEmprendedorId(Long emprendedorId) {
        return jpaRepository.findByEmprendedorId(emprendedorId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeguimientoLogistica> findByEmprendedorIdAndEstado(Long emprendedorId, SeguimientoLogistica.EstadoLogistica estado) {
        SeguimientoLogisticaEntity.EstadoLogistica estadoEntity = 
            SeguimientoLogisticaEntity.EstadoLogistica.valueOf(estado.name());
        return jpaRepository.findByEmprendedorIdAndEstado(emprendedorId, estadoEntity)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<SeguimientoLogistica> findPaginadoPorEmprendedor(
            Long emprendedorId,
            SeguimientoLogistica.EstadoLogistica estado,
            Pageable pageable) {
        SeguimientoLogisticaEntity.EstadoLogistica estadoEntity = estado == null
                ? null
                : SeguimientoLogisticaEntity.EstadoLogistica.valueOf(estado.name());
        return jpaRepository.findPaginadoPorEmprendedor(emprendedorId, estadoEntity, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByVentaId(Long ventaId) {
        return jpaRepository.existsByVentaId(ventaId);
    }

    @Override
    public long countByVentaId(Long ventaId) {
        return jpaRepository.countByVentaId(ventaId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
