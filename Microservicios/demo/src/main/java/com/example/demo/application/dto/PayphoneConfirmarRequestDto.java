package com.example.demo.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos que Payphone devuelve en la URL de respuesta (?id=...&clientTransactionId=...).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayphoneConfirmarRequestDto {
    @NotNull @Positive
    private Long id;
    @NotBlank @Size(max = 50)
    private String clientTxId;
}
