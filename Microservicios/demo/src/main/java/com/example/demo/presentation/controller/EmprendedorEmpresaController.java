package com.example.demo.presentation.controller;

import com.example.demo.application.dto.EmpresaVinculadaResponseDto;
import com.example.demo.application.dto.VincularEmpresaRequestDto;
import com.example.demo.application.service.EmprendedorEmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * T11: vinculación administrativa emprendedor -> empresa. Solo ROLE_ADMIN.
 * El cambio se refleja en el JWT del emprendedor en su siguiente inicio de sesión.
 */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/empresa")
@RequiredArgsConstructor
@Tag(name = "Vinculación emprendedor-empresa", description = "Asocia un usuario ROLE_EMP con su empresa (T11)")
@PreAuthorize("hasRole('ADMIN')")
public class EmprendedorEmpresaController {

    private final EmprendedorEmpresaService service;

    @GetMapping
    @Operation(summary = "Empresa vinculada actualmente a un usuario")
    public ResponseEntity<EmpresaVinculadaResponseDto> consultar(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(service.consultar(usuarioId));
    }

    @PutMapping
    @Operation(summary = "Vincular (o reasignar) la empresa de un emprendedor")
    public ResponseEntity<EmpresaVinculadaResponseDto> vincular(
            @PathVariable Long usuarioId,
            @Valid @RequestBody VincularEmpresaRequestDto request) {
        return ResponseEntity.ok(service.vincular(usuarioId, request.getEmpresaId()));
    }

    @DeleteMapping
    @Operation(summary = "Desvincular la empresa de un emprendedor")
    public ResponseEntity<EmpresaVinculadaResponseDto> desvincular(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(service.desvincular(usuarioId));
    }
}
