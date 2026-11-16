package com.example.msvc_producto.infrastructure.persistence.impl;

import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.repository.CategoriaRepository;
import com.example.msvc_producto.infrastructure.persistence.entity.CategoriaEntity;
import com.example.msvc_producto.infrastructure.persistence.mapper.CategoriaEntityMapper;
import com.example.msvc_producto.infrastructure.persistence.repository.CategoriaJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CategoriaRepositoryImpl implements CategoriaRepository {

    private final CategoriaJpaRepository categoriaJpaRepository;
    private final CategoriaEntityMapper categoriaEntityMapper;

    public CategoriaRepositoryImpl(CategoriaJpaRepository categoriaJpaRepository, CategoriaEntityMapper categoriaEntityMapper) {
        this.categoriaJpaRepository = categoriaJpaRepository;
        this.categoriaEntityMapper = categoriaEntityMapper;
    }

    @Override
    public Categoria save(Categoria categoria) {
        CategoriaEntity entity = categoriaEntityMapper.toEntity(categoria);
        entity = categoriaJpaRepository.save(entity);
        return categoriaEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Categoria> findById(Long id) {
        return categoriaJpaRepository.findById(id)
                .map(categoriaEntityMapper::toDomain);
    }

    @Override
    public List<Categoria> findAll() {
        return categoriaJpaRepository.findAll().stream()
                .map(categoriaEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Categoria> findPaginado(String q, Pageable pageable) {
        return categoriaJpaRepository.findPaginado(q, pageable)
                .map(categoriaEntityMapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        categoriaJpaRepository.deleteById(id);
    }
}