package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion_metodos_pago")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionMetodosPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emprendedor_id", nullable = false, unique = true)
    private Long emprendedorId;

    // Datos bancarios para transferencias
    @Column(name = "banco", length = 100)
    private String banco;

    @Column(name = "tipo_cuenta", length = 50)
    private String tipoCuenta;

    @Column(name = "numero_cuenta", length = 50)
    private String numeroCuenta;

    @Column(name = "titular", length = 255)
    private String titular;

    @Column(name = "cedula_ruc", length = 20)
    private String cedulaRuc;

    @Column(name = "email", length = 255)
    private String email;

    // URL del QR de Deuna
    @Column(name = "qr_deuna_url", columnDefinition = "TEXT")
    private String qrDeunaUrl;

    // Cuota de envío configurada por el emprendedor (por defecto $5.00)
    @Column(name = "costo_envio", precision = 10, scale = 2)
    private BigDecimal costoEnvio;

    // Datos de Payphone
    @Column(name = "payphone_app_id", length = 255)
    private String payphoneAppId;

    // Secreto: excluido de toString() (C-02) y cifrado en reposo con AES-256-GCM
    @lombok.ToString.Exclude
    @Convert(converter = com.example.demo.infrastructure.security.PayphoneTokenCifradoConverter.class)
    @Column(name = "payphone_token", columnDefinition = "TEXT")
    private String payphoneToken;

    // Metadatos
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
