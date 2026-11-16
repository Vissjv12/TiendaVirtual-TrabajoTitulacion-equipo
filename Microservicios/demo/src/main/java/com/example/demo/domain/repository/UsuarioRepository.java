package com.example.demo.domain.repository;

import com.example.demo.domain.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {
    // =====================================
    // MÉTODOS EXISTENTES (NO TOCAR)
    // =====================================
    Usuario save(Usuario usuario);
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // =====================================
    // MÉTODOS NUEVOS PARA GESTIÓN ADMIN
    // =====================================
    List<Usuario> findAll();
    Optional<Usuario> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);
    List<Usuario> findByUsernameContainingOrNombreContainingOrApellidoContainingOrEmailContaining(
            String username, String nombre, String apellido, String email);
    List<Usuario> findByRolesContaining(String rol);
    long count();
    long countByActivoTrue();
    long countByActivoFalse();
    long countByRolesContaining(String rol);
    long countByRolAndActivoTrue(String rol);
    Page<Usuario> findPaginado(String q, String rol, Boolean activo, Pageable pageable);
}