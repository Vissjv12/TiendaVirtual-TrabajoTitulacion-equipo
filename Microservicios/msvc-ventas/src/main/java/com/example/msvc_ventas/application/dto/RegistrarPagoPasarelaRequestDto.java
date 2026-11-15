package com.example.msvc_ventas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pago aprobado y YA VERIFICADO con la pasarela por auth-service (A-04).
 * referenciaTransaccion es la clave de idempotencia (transactionId de Payphone).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagoPasarelaRequestDto {
    @NotBlank @Size(max = 100)
    private String referenciaTransaccion;
    @NotNull @Positive
    private Long montoCentavos;
    @NotBlank @Size(max = 30)
    private String pasarela;
    @Size(max = 50)
    private String clientTransactionId;
}
