package com.example.msvc_ventas.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostoEnvioResponseDto {
    private Long empresaId;
    private BigDecimal costoEnvio;
}
