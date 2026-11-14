package com.example.msvc_producto.presentation.controller;

import com.example.msvc_producto.application.dto.EmpresaRequestDto;
import com.example.msvc_producto.application.dto.EmpresaResponseDto;
import com.example.msvc_producto.application.mapper.EmpresaMapper;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/empresas")
@Tag(name = "Empresas", description = "API para gestionar empresas")
public class EmpresaController {

    private final EmpresaService empresaService;
    private final EmpresaMapper empresaMapper;

    public EmpresaController(EmpresaService empresaService, EmpresaMapper empresaMapper) {
        this.empresaService = empresaService;
        this.empresaMapper = empresaMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Crear una nueva empresa")
    public ResponseEntity<EmpresaResponseDto> crearEmpresa(@Valid @RequestBody EmpresaRequestDto requestDto) {
        Empresa empresa = empresaMapper.toEntity(requestDto);
        Empresa empresaCreada = empresaService.crearEmpresa(empresa);
        return new ResponseEntity<>(empresaMapper.toDto(empresaCreada), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Actualizar una empresa existente")
    public ResponseEntity<EmpresaResponseDto> actualizarEmpresa(
            @PathVariable Long id,
            @Valid @RequestBody EmpresaRequestDto requestDto) {
        Empresa empresa = empresaMapper.toEntity(requestDto, id);
        Empresa empresaActualizada = empresaService.actualizarEmpresa(id, empresa);
        return ResponseEntity.ok(empresaMapper.toDto(empresaActualizada));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una empresa por su ID")
    public ResponseEntity<EmpresaResponseDto> obtenerEmpresaPorId(@PathVariable Long id) {
        Empresa empresa = empresaService.obtenerEmpresaPorId(id);
        return ResponseEntity.ok(empresaMapper.toDto(empresa));
    }

    @GetMapping
    @Operation(summary = "Listar todas las empresas")
    public ResponseEntity<List<EmpresaResponseDto>> listarEmpresas() {
        List<Empresa> empresas = empresaService.listarEmpresas();
        List<EmpresaResponseDto> responseDtos = empresas.stream()
                .map(empresaMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Eliminar una empresa")
    public ResponseEntity<Void> eliminarEmpresa(@PathVariable Long id) {
        empresaService.eliminarEmpresa(id);
        return ResponseEntity.noContent().build();
    }
}
