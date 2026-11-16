package com.example.msvc_inventario.application.service;

/**
 * El producto ya tiene un inventario con stock o historial: el "registro inicial" no puede
 * sobrescribirlo (A-01). Debe usarse la operación explícita de ajuste de stock. HTTP 409.
 */
public class InventarioExistenteException extends RuntimeException {
    public InventarioExistenteException(String mensaje) {
        super(mensaje);
    }
}
