package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.*;
import com.example.msvc_ventas.application.mapper.PagoMapper;
import com.example.msvc_ventas.application.util.PaginationUtils;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pagos", description = "API para gestionar pagos de ventas")
public class PagoController {

    private final PagoService pagoService;
    private final PagoMapper pagoMapper;

    @PostMapping
    @Operation(summary = "Crear un nuevo pago para una venta")
    public ResponseEntity<PagoResponseDto> crearPago(@Valid @RequestBody PagoRequestDto requestDto) {
        log.info("Creando pago para venta ID: {} con método: {}", requestDto.getVentaId(), requestDto.getMetodoPago());
        
        PagoEntity.MetodoPago metodoPago;
        try {
            metodoPago = PagoEntity.MetodoPago.valueOf(requestDto.getMetodoPago().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        PagoEntity pago = pagoService.crearPago(
            requestDto.getVentaId(), 
            metodoPago, 
            requestDto.getComprobanteUrl()
        );
        
        return new ResponseEntity<>(pagoMapper.toDto(pago), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un pago por su ID")
    public ResponseEntity<PagoResponseDto> obtenerPago(@PathVariable Long id) {
        PagoEntity pago = pagoService.obtenerPagoPorId(id);
        return ResponseEntity.ok(pagoMapper.toDto(pago));
    }

    @GetMapping("/venta/{ventaId}")
    @Operation(summary = "Obtener el pago de una venta específica")
    public ResponseEntity<PagoResponseDto> obtenerPagoPorVenta(@PathVariable Long ventaId) {
        PagoEntity pago = pagoService.obtenerPagoPorVentaId(ventaId);
        return ResponseEntity.ok(pagoMapper.toDto(pago));
    }

    @PostMapping("/{id}/aprobar")
    @Operation(summary = "Aprobar un pago (solo emprendedor propietario)")
    public ResponseEntity<PagoResponseDto> aprobarPago(
            @PathVariable Long id,
            @Valid @RequestBody AprobarPagoDto aprobarDto) {
        log.info("Aprobando pago ID: {} por emprendedor ID: {}", id, aprobarDto.getEmprendedorId());
        
        PagoEntity pago = pagoService.aprobarPago(id, aprobarDto.getEmprendedorId());
        return ResponseEntity.ok(pagoMapper.toDto(pago));
    }

    @PostMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar un pago (solo emprendedor propietario)")
    public ResponseEntity<PagoResponseDto> rechazarPago(
            @PathVariable Long id,
            @Valid @RequestBody AprobarPagoDto aprobarDto) {
        log.info("Rechazando pago ID: {} por emprendedor ID: {}", id, aprobarDto.getEmprendedorId());
        
        PagoEntity pago = pagoService.rechazarPago(id, aprobarDto.getEmprendedorId());
        return ResponseEntity.ok(pagoMapper.toDto(pago));
    }

    @PostMapping("/{id}/comprobante")
    @Operation(summary = "Subir comprobante de transferencia")
    public ResponseEntity<PagoResponseDto> subirComprobante(
            @PathVariable Long id,
            @Valid @RequestBody SubirComprobanteDto comprobanteDto) {
        log.info("Subiendo comprobante para pago ID: {}", id);
        
        PagoEntity pago = pagoService.subirComprobante(id, comprobanteDto.getComprobanteUrl());
        return ResponseEntity.ok(pagoMapper.toDto(pago));
    }

    @GetMapping("/emprendedor/{emprendedorId}/pendientes")
    @Operation(summary = "Listar pagos pendientes de aprobación de un emprendedor")
    public ResponseEntity<List<PagoResponseDto>> listarPagosPendientes(@PathVariable Long emprendedorId) {
        List<PagoEntity> pagos = pagoService.listarPagosPendientesPorEmprendedor(emprendedorId);
        List<PagoResponseDto> pagosDto = pagos.stream()
                .map(pagoMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pagosDto);
    }

    @GetMapping("/emprendedor/{emprendedorId}")
    @Operation(summary = "Listar todos los pagos de un emprendedor")
    public ResponseEntity<List<PagoResponseDto>> listarPagosPorEmprendedor(@PathVariable Long emprendedorId) {
        List<PagoEntity> pagos = pagoService.listarPagosPorEmprendedor(emprendedorId);
        List<PagoResponseDto> pagosDto = pagos.stream()
                .map(pagoMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pagosDto);
    }

    private static final Map<String, String> PAGO_SORT_FIELDS = Map.of(
            "id", "id",
            "monto", "monto",
            "metodoPago", "metodoPago",
            "estadoPago", "estadoPago",
            "fechaPago", "fechaPago",
            "fechaCreacion", "fechaCreacion"
    );

    @GetMapping("/emprendedor/{emprendedorId}/pendientes/paginado")
    @Operation(summary = "Listar pagos pendientes de aprobación de un emprendedor paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<PagoResponseDto>> listarPagosPendientesPaginado(
            @PathVariable Long emprendedorId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaCreacion,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaCreacion", Sort.Direction.DESC, PAGO_SORT_FIELDS);
        Page<PagoEntity> pagos = pagoService.listarPagosPendientesPorEmprendedorPaginado(emprendedorId, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(pagos, pagoMapper::toDto));
    }

    @GetMapping("/emprendedor/{emprendedorId}/paginado")
    @Operation(summary = "Listar todos los pagos de un emprendedor paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<PagoResponseDto>> listarPagosPorEmprendedorPaginado(
            @PathVariable Long emprendedorId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaCreacion,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaCreacion", Sort.Direction.DESC, PAGO_SORT_FIELDS);
        Page<PagoEntity> pagos = pagoService.listarPagosPorEmprendedorPaginado(emprendedorId, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(pagos, pagoMapper::toDto));
    }

    @GetMapping("/estado/{estado}/paginado")
    @Operation(summary = "Listar pagos por estado paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<PagoResponseDto>> listarPagosPorEstadoPaginado(
            @PathVariable String estado,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaCreacion,desc") String sort) {
        PagoEntity.EstadoPago estadoPago;
        try {
            estadoPago = PagoEntity.EstadoPago.valueOf(estado.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaCreacion", Sort.Direction.DESC, PAGO_SORT_FIELDS);
        Page<PagoEntity> pagos = pagoService.listarPagosPorEstadoPaginado(estadoPago, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(pagos, pagoMapper::toDto));
    }

    @GetMapping("/estado/{estado}")
    @Operation(summary = "Listar pagos por estado (PENDIENTE, PROCESANDO, APROBADO, RECHAZADO)")
    public ResponseEntity<List<PagoResponseDto>> listarPagosPorEstado(@PathVariable String estado) {
        PagoEntity.EstadoPago estadoPago;
        try {
            estadoPago = PagoEntity.EstadoPago.valueOf(estado.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        List<PagoEntity> pagos = pagoService.listarPagosPorEstado(estadoPago);
        List<PagoResponseDto> pagosDto = pagos.stream()
                .map(pagoMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pagosDto);
    }
}
