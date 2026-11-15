package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de preparar un cobro: enlaces de pago de Payphone e identificador de la transacción.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayphonePrepararResponseDto {
    private String clientTransactionId;
    private String paymentId;
    private String payWithPayPhone;
    private String payWithCard;
}
