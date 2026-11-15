package com.example.msvc_producto.infrastructure.persistence.impl;

import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.repository.EmpresaRepository;
import com.example.msvc_producto.infrastructure.persistence.entity.EmpresaEntity;
import com.example.msvc_producto.infrastructure.persistence.mapper.EmpresaEntityMapper;
import com.example.msvc_producto.infrastructure.persistence.repository.EmpresaJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class EmpresaRepositoryImpl implements EmpresaRepository {

    private final EmpresaJpaRepository empresaJpaRepository;
    private final EmpresaEntityMapper empresaEntityMapper;

    public EmpresaRepositoryImpl(EmpresaJpaRepository empresaJpaRepository, EmpresaEntityMapper empresaEntityMapper) {
        this.empresaJpaRepository = empresaJpaRepository;
        this.empresaEntityMapper = empresaEntityMapper;
    }

    @Override
    public Empresa save(Empresa empresa) {
        EmpresaEntity entity = empresaEntityMapper.toEntity(empresa);
        entity = empresaJpaRepository.save(entity);
        return empresaEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Empresa> findById(Long id) {
        return empresaJpaRepository.findById(id)
                .map(empresaEntityMapper::toDomain);
    }

    @Override
    public List<Empresa> findAll() {
        return empresaJpaRepository.findAll().stream()
                .map(empresaEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Empresa> findPaginado(String q, Pageable pageable) {
        return empresaJpaRepository.findPaginado(q, pageable)
                .map(empresaEntityMapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        empresaJpaRepository.deleteById(id);
    }
}