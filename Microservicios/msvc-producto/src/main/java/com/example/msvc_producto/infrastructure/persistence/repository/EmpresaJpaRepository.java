package com.example.msvc_producto.infrastructure.persistence.repository;

import com.example.msvc_producto.infrastructure.persistence.entity.EmpresaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpresaJpaRepository extends JpaRepository<EmpresaEntity, Long> {

    @Query("SELECT e FROM EmpresaEntity e " +
           "WHERE (:q = '' OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "       OR LOWER(COALESCE(e.ruc, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "       OR LOWER(COALESCE(e.email, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "       OR LOWER(COALESCE(e.direccion, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<EmpresaEntity> findPaginado(@Param("q") String q, Pageable pageable);

    boolean existsByRuc(String ruc);

    boolean existsByRucAndIdNot(String ruc, Long id);
}