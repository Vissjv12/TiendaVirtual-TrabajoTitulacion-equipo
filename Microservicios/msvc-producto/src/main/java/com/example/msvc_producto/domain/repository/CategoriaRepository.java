package com.example.msvc_producto.domain.repository;

import com.example.msvc_producto.domain.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository {
    Categoria save(Categoria categoria);
    Optional<Categoria> findById(Long id);
    List<Categoria> findAll();
    Page<Categoria> findPaginado(String q, Pageable pageable);
    void deleteById(Long id);
}