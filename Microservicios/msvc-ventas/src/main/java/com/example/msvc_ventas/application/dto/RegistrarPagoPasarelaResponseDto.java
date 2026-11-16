package com.example.msvc_ventas.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de registrar un pago de pasarela.
 * resultado:
 *  - COMPLETADA: pago registrado y stock descontado en esta llamada.
 *  - YA_PROCESADA: la misma transacción ya se había registrado (idempotente, no se repite nada).
 *  - STOCK_PENDIENTE: pago aprobado registrado, pero inventario no pudo descontarse; reintentable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagoPasarelaResponseDto {
    public static final String COMPLETADA = "COMPLETADA";
    public static final String YA_PROCESADA = "YA_PROCESADA";
    public static final String STOCK_PENDIENTE = "STOCK_PENDIENTE";

    private Long ventaId;
    private String numeroFactura;
    private String estadoVenta;
    private String estadoPago;
    private String resultado;
}
