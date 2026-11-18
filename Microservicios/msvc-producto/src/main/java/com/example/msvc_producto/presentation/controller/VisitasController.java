package com.example.msvc_producto.presentation.controller;

import com.example.msvc_producto.application.dto.VisitasEstadisticasDto;
import com.example.msvc_producto.application.service.VisitasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visitas")
@Tag(name = "Visitas", description = "Contador agregado de visitas del sitio")
public class VisitasController {

    private final VisitasService visitasService;

    public VisitasController(VisitasService visitasService) {
        this.visitasService = visitasService;
    }

    @PostMapping
    @Operation(summary = "Registrar una visita válida")
    public ResponseEntity<Void> registrarVisita() {
        visitasService.registrarVisita();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/estadisticas")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Consultar visitas totales y del día")
    public ResponseEntity<VisitasEstadisticasDto> obtenerEstadisticas() {
        return ResponseEntity.ok(visitasService.obtenerEstadisticas());
    }
}
