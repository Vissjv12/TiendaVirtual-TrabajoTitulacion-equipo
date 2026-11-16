package com.example.demo.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
// T11: una empresa pertenece a un solo emprendedor (MySQL admite varios NULL en un índice único).
// ddl-auto=validate no crea el índice: en bases existentes aplicar
// scripts/prod/migraciones/T11-emprendedores-empresa-unica.sql
@Table(name = "emprendedores",
        uniqueConstraints = @UniqueConstraint(name = "uk_emprendedor_empresa", columnNames = "empresa_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmprendedorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}
