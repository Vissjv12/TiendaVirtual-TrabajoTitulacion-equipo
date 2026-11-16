package com.example.demo.application.dto;

/**
 * T11: estado de la vinculación emprendedor-empresa tras una operación.
 *
 * @param resultado VINCULADA | REASIGNADA | SIN_CAMBIOS | DESVINCULADA | CONSULTA
 * @param requiereNuevoLogin true cuando cambió la empresa: el JWT vigente del emprendedor conserva la
 *                           empresa anterior hasta que expire (1 h) o vuelva a iniciar sesión
 */
public record EmpresaVinculadaResponseDto(
        Long usuarioId,
        Long empresaId,
        String empresaNombre,
        Long empresaAnteriorId,
        String resultado,
        boolean requiereNuevoLogin) {
}
