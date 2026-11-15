package com.example.msvc_ventas.domain.repository;

import com.example.msvc_ventas.domain.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository {
    Cliente save(Cliente cliente);
    Optional<Cliente> findById(Long id);
    Optional<Cliente> findByEmail(String email);
    List<Cliente> findAll();
    Page<Cliente> findPaginado(Pageable pageable);
    void deleteById(Long id);
}