package com.example.demo.infrastructure.ventas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Datos autoritativos de una venta, leídos de msvc-ventas por el canal interno (A-03).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VentaResumenPago(
        Long ventaId,
        String numeroFactura,
        Long empresaId,
        String clienteEmail,
        BigDecimal subtotal,
        BigDecimal impuesto,
        BigDecimal total,
        String estado,
        String estadoPago,
        String metodoPago,
        String referenciaTransaccion) {
}
