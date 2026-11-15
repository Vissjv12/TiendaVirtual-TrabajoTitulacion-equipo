package com.example.msvc_inventario.infrastructure.persistence.repository;

import com.example.msvc_inventario.infrastructure.persistence.entity.InventarioEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioJpaRepository extends JpaRepository<InventarioEntity, Long> {

    Optional<InventarioEntity> findFirstByProductoId(Long productoId);

    List<InventarioEntity> findByProductoIdIn(List<Long> productosIds);

    @Query("SELECT i FROM InventarioEntity i WHERE i.activo = true")
    List<InventarioEntity> findAllActive();

    @Query("SELECT i.cantidad FROM InventarioEntity i WHERE i.productoId = :productoId")
    Optional<Integer> findCantidadByProductoId(@Param("productoId") Long productoId);

    @Query("SELECT i FROM InventarioEntity i WHERE (:soloActivos IS NULL OR i.activo = :soloActivos)")
    Page<InventarioEntity> findPaginado(@Param("soloActivos") Boolean soloActivos, Pageable pageable);
}