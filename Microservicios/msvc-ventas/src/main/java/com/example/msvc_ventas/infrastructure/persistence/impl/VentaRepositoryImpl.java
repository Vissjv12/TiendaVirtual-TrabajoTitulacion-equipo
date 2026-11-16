package com.example.msvc_ventas.infrastructure.persistence.impl;

import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import com.example.msvc_ventas.infrastructure.persistence.mapper.VentaEntityMapper;
import com.example.msvc_ventas.infrastructure.persistence.repository.VentaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VentaRepositoryImpl implements VentaRepository {

    private final VentaJpaRepository jpaRepository;
    private final VentaEntityMapper mapper;

    @Override
    public Venta save(Venta venta) {
        VentaEntity entity = mapper.toEntity(venta);
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Venta> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Venta> findByNumeroFactura(String numeroFactura) {
        return jpaRepository.findByNumeroFactura(numeroFactura)
                .map(mapper::toDomain);
    }

    @Override
    public List<Venta> findByClienteId(Long clienteId) {
        return jpaRepository.findByClienteId(clienteId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Venta> findByEmprendedorId(Long emprendedorId) {
        return jpaRepository.findByEmprendedorId(emprendedorId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Venta> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Venta> findPaginado(
            String q,
            Long emprendedorId,
            Long clienteId,
            Venta.EstadoVenta estado,
            Pageable pageable) {
        VentaEntity.EstadoVenta entityEstado = estado == null
                ? null
                : VentaEntity.EstadoVenta.valueOf(estado.name());
        return jpaRepository.findPaginado(
                        q == null ? "" : q.trim(),
                        emprendedorId,
                        clienteId,
                        entityEstado,
                        pageable)
                .map(mapper::toDomain);
    }
}