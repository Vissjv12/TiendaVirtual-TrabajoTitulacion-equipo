package com.example.msvc_inventario.infrastructure.persistence.repository;

import com.example.msvc_inventario.infrastructure.persistence.entity.MovimientoInventarioEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioJpaRepository extends JpaRepository<MovimientoInventarioEntity, Long> {
    List<MovimientoInventarioEntity> findByInventarioId(Long inventarioId);

    Page<MovimientoInventarioEntity> findByInventarioId(Long inventarioId, Pageable pageable);

    @Query("SELECT m FROM MovimientoInventarioEntity m " +
           "WHERE (:inventarioId IS NULL OR m.inventarioId = :inventarioId) " +
           "AND (:filtrarInventarios = false OR m.inventarioId IN :inventarioIds) " +
           "AND (:tipo IS NULL OR m.tipoMovimiento = :tipo) " +
           "AND (:motivo = '' OR LOWER(COALESCE(m.motivo, '')) LIKE LOWER(CONCAT('%', :motivo, '%'))) " +
           "AND (:fechaDesde IS NULL OR m.fechaMovimiento >= :fechaDesde) " +
           "AND (:fechaHasta IS NULL OR m.fechaMovimiento <= :fechaHasta)")
    Page<MovimientoInventarioEntity> findPaginado(
            @Param("inventarioId") Long inventarioId,
            @Param("filtrarInventarios") boolean filtrarInventarios,
            @Param("inventarioIds") List<Long> inventarioIds,
            @Param("tipo") MovimientoInventarioEntity.TipoMovimiento tipo,
            @Param("motivo") String motivo,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable);
}