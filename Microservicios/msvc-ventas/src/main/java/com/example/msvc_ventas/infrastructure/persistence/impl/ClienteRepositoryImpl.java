package com.example.msvc_ventas.infrastructure.persistence.impl;

import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.infrastructure.persistence.entity.ClienteEntity;
import com.example.msvc_ventas.infrastructure.persistence.mapper.ClienteEntityMapper;
import com.example.msvc_ventas.infrastructure.persistence.repository.ClienteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClienteRepositoryImpl implements ClienteRepository {

    private final ClienteJpaRepository jpaRepository;
    private final ClienteEntityMapper mapper;

    @Override
    public Cliente save(Cliente cliente) {
        ClienteEntity entity = mapper.toEntity(cliente);
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Cliente> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public List<Cliente> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Cliente> findPaginado(Pageable pageable) {
        return jpaRepository.findAll(pageable)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}