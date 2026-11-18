package com.example.msvc_producto.application.dto;

import java.time.LocalDate;

public record VisitasEstadisticasDto(
        long total,
        long hoy,
        LocalDate fecha) {
}
