package com.example.msvc_producto.domain.repository;

import com.example.msvc_producto.domain.model.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EmpresaRepository {
    Empresa save(Empresa empresa);
    Optional<Empresa> findById(Long id);
    List<Empresa> findAll();
    Page<Empresa> findPaginado(String q, Pageable pageable);
    void deleteById(Long id);
}