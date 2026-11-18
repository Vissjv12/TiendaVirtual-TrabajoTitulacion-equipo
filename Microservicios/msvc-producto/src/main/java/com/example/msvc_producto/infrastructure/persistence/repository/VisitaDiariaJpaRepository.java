package com.example.msvc_producto.infrastructure.persistence.repository;

import com.example.msvc_producto.infrastructure.persistence.entity.VisitaDiariaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface VisitaDiariaJpaRepository extends JpaRepository<VisitaDiariaEntity, LocalDate> {

    @Modifying
    @Query(value = "INSERT INTO visitas_diarias (fecha, cantidad) VALUES (:fecha, 1) "
            + "ON DUPLICATE KEY UPDATE cantidad = cantidad + 1", nativeQuery = true)
    int incrementar(@Param("fecha") LocalDate fecha);

    @Query("SELECT COALESCE(SUM(v.cantidad), 0) FROM VisitaDiariaEntity v")
    long sumarVisitas();
}
