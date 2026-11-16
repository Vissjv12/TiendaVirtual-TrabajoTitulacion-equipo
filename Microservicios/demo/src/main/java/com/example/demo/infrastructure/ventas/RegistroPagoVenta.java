package com.example.demo.infrastructure.ventas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de msvc-ventas al registrar un pago de pasarela.
 * resultado: COMPLETADA | YA_PROCESADA | STOCK_PENDIENTE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistroPagoVenta(
        Long ventaId,
        String numeroFactura,
        String estadoVenta,
        String estadoPago,
        String resultado) {
}
