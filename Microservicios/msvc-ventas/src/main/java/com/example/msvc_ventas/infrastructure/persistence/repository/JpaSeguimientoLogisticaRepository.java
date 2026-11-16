package com.example.msvc_ventas.infrastructure.persistence.repository;

import com.example.msvc_ventas.infrastructure.persistence.entity.SeguimientoLogisticaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaSeguimientoLogisticaRepository extends JpaRepository<SeguimientoLogisticaEntity, Long> {

    /**
     * Encuentra todos los registros de seguimiento de una venta ordenados por fecha
     */
    List<SeguimientoLogisticaEntity> findByVentaIdOrderByFechaCreacionAsc(Long ventaId);

    /**
     * Encuentra el último estado de seguimiento de una venta
     */
    Optional<SeguimientoLogisticaEntity> findTopByVentaIdOrderByFechaCreacionDesc(Long ventaId);

    /**
     * Encuentra todos los seguimientos por estado
     */
    List<SeguimientoLogisticaEntity> findByEstadoLogistica(SeguimientoLogisticaEntity.EstadoLogistica estadoLogistica);

    /**
     * Encuentra todos los seguimientos de un emprendedor específico
     */
    @Query("SELECT s FROM SeguimientoLogisticaEntity s " +
           "WHERE s.ventaId IN (" +
           "SELECT v.id FROM VentaEntity v WHERE v.emprendedorId = :emprendedorId" +
           ") " +
           "ORDER BY s.fechaCreacion DESC")
    List<SeguimientoLogisticaEntity> findByEmprendedorId(@Param("emprendedorId") Long emprendedorId);

    /**
     * Encuentra seguimientos de un emprendedor en un estado específico
     */
    @Query("SELECT s FROM SeguimientoLogisticaEntity s " +
           "WHERE s.ventaId IN (" +
           "SELECT v.id FROM VentaEntity v WHERE v.emprendedorId = :emprendedorId" +
           ") " +
           "AND s.estadoLogistica = :estado " +
           "ORDER BY s.fechaCreacion DESC")
    List<SeguimientoLogisticaEntity> findByEmprendedorIdAndEstado(
            @Param("emprendedorId") Long emprendedorId,
            @Param("estado") SeguimientoLogisticaEntity.EstadoLogistica estado);

    @Query(value = "SELECT s FROM SeguimientoLogisticaEntity s " +
                   "WHERE s.ventaId IN (SELECT v.id FROM VentaEntity v WHERE v.emprendedorId = :emprendedorId) " +
                   "AND (:estado IS NULL OR s.estadoLogistica = :estado)",
           countQuery = "SELECT COUNT(s) FROM SeguimientoLogisticaEntity s " +
                        "WHERE s.ventaId IN (SELECT v.id FROM VentaEntity v WHERE v.emprendedorId = :emprendedorId) " +
                        "AND (:estado IS NULL OR s.estadoLogistica = :estado)")
    Page<SeguimientoLogisticaEntity> findPaginadoPorEmprendedor(
            @Param("emprendedorId") Long emprendedorId,
            @Param("estado") SeguimientoLogisticaEntity.EstadoLogistica estado,
            Pageable pageable);

    /**
     * Verifica si existe al menos un seguimiento para una venta
     */
    boolean existsByVentaId(Long ventaId);

    /**
     * Cuenta cuántos seguimientos tiene una venta
     */
    long countByVentaId(Long ventaId);
}
