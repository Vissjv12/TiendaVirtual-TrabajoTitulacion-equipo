package com.example.demo.infrastructure.ventas;

/**
 * msvc-ventas rechazó registrar el pago (4xx): venta cancelada, ya pagada con otra transacción
 * o monto distinto. No es reintentable: requiere revisión manual (posible reembolso).
 */
public class VentaPagoRechazadoException extends RuntimeException {
    public VentaPagoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
