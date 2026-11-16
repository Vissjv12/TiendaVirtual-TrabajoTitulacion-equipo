package com.example.demo.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** T11: cuerpo de PUT /api/usuarios/{id}/empresa. Desvincular es DELETE, nunca empresaId=null. */
@Data
public class VincularEmpresaRequestDto {

    @NotNull(message = "empresaId es obligatorio; para desvincular use DELETE")
    @Positive(message = "empresaId debe ser un número positivo")
    private Long empresaId;
}
