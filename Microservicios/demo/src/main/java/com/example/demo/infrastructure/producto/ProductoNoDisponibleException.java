package com.example.demo.infrastructure.producto;

/** msvc-producto no respondió o falló: la vinculación no puede verificarse (503, fail-closed). */
public class ProductoNoDisponibleException extends RuntimeException {
    public ProductoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
