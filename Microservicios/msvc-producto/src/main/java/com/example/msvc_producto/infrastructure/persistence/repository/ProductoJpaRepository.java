package com.example.msvc_producto.infrastructure.persistence.repository;

import com.example.msvc_producto.infrastructure.persistence.entity.ProductoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoJpaRepository extends JpaRepository<ProductoEntity, Long> {
    List<ProductoEntity> findByEmpresaId(Long empresaId);
    List<ProductoEntity> findByCategoriaId(Long categoriaId);

    @Query(value = "SELECT p FROM ProductoEntity p " +
           "JOIN FETCH p.categoria c " +
           "JOIN FETCH p.empresa e " +
           "WHERE p.activo = true " +
           "AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(p.descripcion, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:categoria = '' OR LOWER(c.nombre) = LOWER(:categoria)) " +
           "AND (:empresa = '' OR LOWER(e.nombre) = LOWER(:empresa))",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
           "JOIN p.categoria c " +
           "JOIN p.empresa e " +
           "WHERE p.activo = true " +
           "AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(p.descripcion, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:categoria = '' OR LOWER(c.nombre) = LOWER(:categoria)) " +
           "AND (:empresa = '' OR LOWER(e.nombre) = LOWER(:empresa))")
    Page<ProductoEntity> findListadoPaginado(
            @Param("q") String q,
            @Param("categoria") String categoria,
            @Param("empresa") String empresa,
            Pageable pageable);

    @Query(value = "SELECT p FROM ProductoEntity p " +
           "LEFT JOIN FETCH p.empresa e " +
           "LEFT JOIN FETCH p.categoria c " +
           "WHERE (:empresaId IS NULL OR e.id = :empresaId) " +
           "AND (:categoriaId IS NULL OR c.id = :categoriaId) " +
           "AND (:activo IS NULL OR p.activo = :activo) " +
           "AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(p.descripcion, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(e.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
           "LEFT JOIN p.empresa e " +
           "LEFT JOIN p.categoria c " +
           "WHERE (:empresaId IS NULL OR e.id = :empresaId) " +
           "AND (:categoriaId IS NULL OR c.id = :categoriaId) " +
           "AND (:activo IS NULL OR p.activo = :activo) " +
           "AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(p.descripcion, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(e.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<ProductoEntity> findPaginado(
            @Param("q") String q,
            @Param("empresaId") Long empresaId,
            @Param("categoriaId") Long categoriaId,
            @Param("activo") Boolean activo,
            Pageable pageable);
    
    // ✅ SOLUCIÓN AL PROBLEMA N+1: JOIN FETCH carga todo en una sola query
    @Query("SELECT p FROM ProductoEntity p " +
           "LEFT JOIN FETCH p.empresa " +
           "LEFT JOIN FETCH p.categoria")
    List<ProductoEntity> findAllWithEagerLoading();
    
    @Query(value = """
    SELECT 
        p.id,
        p.nombre,
        p.descripcion,
        p.precio,
        p.imagen,
        c.nombre as categoriaNombre,
        e.nombre as empresaNombre,
        e.id as empresaId
    FROM productos p
    INNER JOIN categorias c ON c.id = p.categoria_id
    INNER JOIN empresas e ON e.id = p.empresa_id
    WHERE p.activo = true
    ORDER BY p.id ASC
    """, nativeQuery = true)
    List<Object[]> findAllProductosOptimized();
}