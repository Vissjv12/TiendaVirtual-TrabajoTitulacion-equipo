package com.example.demo.infrastructure.persistence.impl;

import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.repository.UsuarioRepository;
import com.example.demo.infrastructure.persistence.entity.UsuarioEntity;
import com.example.demo.infrastructure.persistence.repository.UsuarioJpaRepository;
import com.example.demo.application.mapper.UsuarioMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UsuarioRepositoryImpl implements UsuarioRepository {

    private final UsuarioJpaRepository jpaRepository;
    private final UsuarioMapper mapper;

    // =====================================
    // MÉTODOS EXISTENTES (NO MODIFICADOS)
    // =====================================

    @Override
    public Usuario save(Usuario usuario) {
        UsuarioEntity entity = mapper.toEntity(usuario);
        UsuarioEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Usuario> findByUsername(String username) {
        return jpaRepository.findByUsername(username)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    // =====================================
    // MÉTODOS NUEVOS PARA GESTIÓN ADMIN
    // =====================================

    @Override
    public List<Usuario> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Usuario> findByUsernameContainingOrNombreContainingOrApellidoContainingOrEmailContaining(
            String username, String nombre, String apellido, String email) {
        return jpaRepository.findByUsernameContainingIgnoreCaseOrNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        username, nombre, apellido, email).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Usuario> findByRolesContaining(String rol) {
        return jpaRepository.findByRolesContaining(rol).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countByActivoTrue() {
        return jpaRepository.countByActivoTrue();
    }

    @Override
    public long countByActivoFalse() {
        return jpaRepository.countByActivoFalse();
    }

    @Override
    public long countByRolesContaining(String rol) {
        return jpaRepository.countByRolesContaining(rol);
    }

    @Override
    public Page<Usuario> findPaginado(String q, String rol, Boolean activo, Pageable pageable) {
        return jpaRepository.findPaginado(
                        q == null ? "" : q.trim(),
                        rol == null ? "" : rol.trim(),
                        activo,
                        pageable)
                .map(mapper::toDomain);
    }
}