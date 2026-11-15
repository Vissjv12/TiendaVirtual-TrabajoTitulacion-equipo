package com.example.msvc_producto.infrastructure.persistence.repository;

import com.example.msvc_producto.infrastructure.persistence.entity.CategoriaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaJpaRepository extends JpaRepository<CategoriaEntity, Long> {

    @Query("SELECT c FROM CategoriaEntity c " +
           "WHERE (:q = '' OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "       OR LOWER(COALESCE(c.descripcion, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<CategoriaEntity> findPaginado(@Param("q") String q, Pageable pageable);
}