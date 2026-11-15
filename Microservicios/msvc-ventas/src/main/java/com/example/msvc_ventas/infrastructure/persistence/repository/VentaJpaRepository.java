package com.example.msvc_ventas.infrastructure.persistence.repository;

import com.example.msvc_ventas.infrastructure.persistence.entity.VentaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VentaJpaRepository extends JpaRepository<VentaEntity, Long> {
    Optional<VentaEntity> findByNumeroFactura(String numeroFactura);
    List<VentaEntity> findByClienteId(Long clienteId);
    List<VentaEntity> findByEmprendedorId(Long emprendedorId);

    @Query(value = "SELECT v FROM VentaEntity v LEFT JOIN FETCH v.cliente c " +
           "WHERE (:emprendedorId IS NULL OR v.emprendedorId = :emprendedorId) " +
           "AND (:clienteId IS NULL OR c.id = :clienteId) " +
           "AND (:estado IS NULL OR v.estado = :estado) " +
           "AND (:q = '' OR LOWER(v.numeroFactura) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.apellido, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(CAST(v.estado AS string)) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(v) FROM VentaEntity v LEFT JOIN v.cliente c " +
           "WHERE (:emprendedorId IS NULL OR v.emprendedorId = :emprendedorId) " +
           "AND (:clienteId IS NULL OR c.id = :clienteId) " +
           "AND (:estado IS NULL OR v.estado = :estado) " +
           "AND (:q = '' OR LOWER(v.numeroFactura) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(COALESCE(c.apellido, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(CAST(v.estado AS string)) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<VentaEntity> findPaginado(
            @Param("q") String q,
            @Param("emprendedorId") Long emprendedorId,
            @Param("clienteId") Long clienteId,
            @Param("estado") VentaEntity.EstadoVenta estado,
            Pageable pageable);
}