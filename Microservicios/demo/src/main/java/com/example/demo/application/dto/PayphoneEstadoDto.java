package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado de Payphone que ve el propio emprendedor en su configuración.
 * Nunca incluye el token: solo indica si está configurado (hallazgo C-02).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayphoneEstadoDto {
    private String payphoneAppId;
    private boolean tokenConfigurado;
}
