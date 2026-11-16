package com.example.msvc_ventas.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Datos AUTORITATIVOS de una venta para cobrarla en una pasarela (A-03).
 * Solo lo consume auth-service mediante el endpoint interno con credencial de servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentaResumenPagoDto {
    private Long ventaId;
    private String numeroFactura;
    /** En la tabla ventas, emprendedor_id guarda el id de la EMPRESA dueña de la venta. */
    private Long empresaId;
    private String clienteEmail;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private String estado;
    private String estadoPago;
    private String metodoPago;
    private String referenciaTransaccion;
}
