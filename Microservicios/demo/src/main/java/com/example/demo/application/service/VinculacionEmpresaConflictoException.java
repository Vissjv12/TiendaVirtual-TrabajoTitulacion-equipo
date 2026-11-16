package com.example.demo.application.service;

/** T11: la vinculación choca con el estado actual (empresa inactiva o ya vinculada a otro emprendedor) -> 409. */
public class VinculacionEmpresaConflictoException extends RuntimeException {
    public VinculacionEmpresaConflictoException(String mensaje) {
        super(mensaje);
    }
}
