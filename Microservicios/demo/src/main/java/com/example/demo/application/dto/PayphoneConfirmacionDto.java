package com.example.demo.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de confirmar un cobro y de finalizar su venta. Solo campos de una lista blanca: nunca se reenvía
 * la respuesta cruda de Payphone ni datos de credenciales.
 * statusCode: 3 = aprobado, 2 = cancelado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayphoneConfirmacionDto {
    private Integer statusCode;
    private String transactionStatus;
    private Long transactionId;
    private String clientTransactionId;
    private Long amount;
    private String currency;
    private String cardType;
    private String authorizationCode;
    /** Venta asociada y resultado de su finalización: COMPLETADA, YA_PROCESADA, STOCK_PENDIENTE,
     *  MONTO_NO_COINCIDE o REQUIERE_REVISION; null si el pago no fue aprobado. */
    private Long ventaId;
    private String numeroFactura;
    private String resultadoVenta;
}
