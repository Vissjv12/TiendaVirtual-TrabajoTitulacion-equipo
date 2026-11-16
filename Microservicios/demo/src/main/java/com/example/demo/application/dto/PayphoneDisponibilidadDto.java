package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta PÚBLICA del checkout: solo indica si la empresa acepta Payphone.
 * No expone token ni App ID (hallazgo C-02).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayphoneDisponibilidadDto {
    private Long empresaId;
    private boolean disponible;
}
