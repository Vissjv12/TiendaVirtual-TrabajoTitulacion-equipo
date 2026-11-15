package com.example.msvc_ventas.infrastructure.persistence.repository;

import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<PagoEntity, Long> {
    
    Optional<PagoEntity> findByVentaId(Long ventaId);
    
    List<PagoEntity> findByEstadoPago(PagoEntity.EstadoPago estadoPago);

    @Query(value = "SELECT p FROM PagoEntity p JOIN FETCH p.venta v LEFT JOIN FETCH v.cliente WHERE p.estadoPago = :estadoPago",
           countQuery = "SELECT COUNT(p) FROM PagoEntity p WHERE p.estadoPago = :estadoPago")
    Page<PagoEntity> findByEstadoPago(@Param("estadoPago") PagoEntity.EstadoPago estadoPago, Pageable pageable);
    
    @Query("SELECT p FROM PagoEntity p JOIN p.venta v WHERE v.emprendedorId = :emprendedorId")
    List<PagoEntity> findByEmprendedorId(@Param("emprendedorId") Long emprendedorId);

    @Query(value = "SELECT p FROM PagoEntity p JOIN FETCH p.venta v LEFT JOIN FETCH v.cliente WHERE v.emprendedorId = :emprendedorId",
           countQuery = "SELECT COUNT(p) FROM PagoEntity p JOIN p.venta v WHERE v.emprendedorId = :emprendedorId")
    Page<PagoEntity> findByEmprendedorId(@Param("emprendedorId") Long emprendedorId, Pageable pageable);
    
    @Query("SELECT p FROM PagoEntity p JOIN p.venta v WHERE v.emprendedorId = :emprendedorId AND p.estadoPago = :estadoPago")
    List<PagoEntity> findByEmprendedorIdAndEstadoPago(
        @Param("emprendedorId") Long emprendedorId, 
        @Param("estadoPago") PagoEntity.EstadoPago estadoPago
    );

    @Query(value = "SELECT p FROM PagoEntity p JOIN FETCH p.venta v LEFT JOIN FETCH v.cliente WHERE v.emprendedorId = :emprendedorId AND p.estadoPago = :estadoPago",
           countQuery = "SELECT COUNT(p) FROM PagoEntity p JOIN p.venta v WHERE v.emprendedorId = :emprendedorId AND p.estadoPago = :estadoPago")
    Page<PagoEntity> findByEmprendedorIdAndEstadoPago(
        @Param("emprendedorId") Long emprendedorId,
        @Param("estadoPago") PagoEntity.EstadoPago estadoPago,
        Pageable pageable
    );
}
