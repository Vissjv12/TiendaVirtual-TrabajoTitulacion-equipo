package com.example.demo.infrastructure.ventas;

/**
 * msvc-ventas no respondió o falló (5xx / red). La operación es reintentable:
 * el webhook responde 503 para que Payphone vuelva a notificar.
 */
public class VentasNoDisponibleException extends RuntimeException {
    public VentasNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
