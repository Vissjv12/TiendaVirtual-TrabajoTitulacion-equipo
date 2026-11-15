package com.example.demo.infrastructure.persistence.repository;

import com.example.demo.infrastructure.persistence.entity.UsuarioEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, Long> {
    // =====================================
    // MÉTODOS EXISTENTES (NO TOCAR)
    // =====================================
    Optional<UsuarioEntity> findByUsername(String username);
    Optional<UsuarioEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // =====================================
    // MÉTODOS NUEVOS PARA GESTIÓN ADMIN
    // =====================================
    List<UsuarioEntity> findByUsernameContainingIgnoreCaseOrNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String nombre, String apellido, String email);

    List<UsuarioEntity> findByRolesContaining(String rol);

    long countByActivoTrue();
    long countByActivoFalse();
    long countByRolesContaining(String rol);

    @Query("SELECT u FROM UsuarioEntity u " +
           "WHERE (:activo IS NULL OR u.activo = :activo) " +
           "AND (:rol = '' OR :rol MEMBER OF u.roles) " +
           "AND (:q = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<UsuarioEntity> findPaginado(
            @Param("q") String q,
            @Param("rol") String rol,
            @Param("activo") Boolean activo,
            Pageable pageable);
}