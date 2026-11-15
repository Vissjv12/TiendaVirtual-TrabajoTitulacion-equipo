package com.example.msvc_inventario.presentation.controller;

import com.example.msvc_inventario.application.dto.MovimientoInventarioRequestDto;
import com.example.msvc_inventario.application.dto.MovimientoInventarioResponseDto;
import com.example.msvc_inventario.application.dto.PageResponseDto;
import com.example.msvc_inventario.application.mapper.MovimientoInventarioMapper;
import com.example.msvc_inventario.application.util.PaginationUtils;
import com.example.msvc_inventario.domain.model.MovimientoInventario;
import com.example.msvc_inventario.domain.service.MovimientoInventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/movimientos")
@Tag(name = "Movimientos de Inventario", description = "API para gestionar los movimientos de inventario")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final MovimientoInventarioMapper movimientoMapper;

    public MovimientoInventarioController(
            MovimientoInventarioService movimientoService,
            MovimientoInventarioMapper movimientoMapper) {
        this.movimientoService = movimientoService;
        this.movimientoMapper = movimientoMapper;
    }

    @PostMapping
    @Operation(summary = "Registrar un nuevo movimiento de inventario")
    public ResponseEntity<MovimientoInventarioResponseDto> registrarMovimiento(
            @Valid @RequestBody MovimientoInventarioRequestDto requestDto) {

        MovimientoInventario movimiento = movimientoMapper.toEntity(requestDto);
        MovimientoInventario movimientoRegistrado = movimientoService.registrarMovimiento(movimiento);

        return new ResponseEntity<>(
                movimientoMapper.toDto(movimientoRegistrado),
                HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un movimiento de inventario por su ID")
    public ResponseEntity<MovimientoInventarioResponseDto> obtenerMovimientoPorId(@PathVariable Long id) {
        MovimientoInventario movimiento = movimientoService.obtenerPorId(id);
        return ResponseEntity.ok(movimientoMapper.toDto(movimiento));
    }

    @GetMapping("/inventario/{inventarioId}")
    @Operation(summary = "Listar movimientos por ID de inventario")
    public ResponseEntity<List<MovimientoInventarioResponseDto>> listarMovimientosPorInventarioId(
            @PathVariable Long inventarioId) {

        List<MovimientoInventario> movimientos = movimientoService.listarPorInventarioId(inventarioId);

        List<MovimientoInventarioResponseDto> responseDtos = movimientos.stream()
                .map(movimientoMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    private static final Map<String, String> MOVIMIENTO_SORT_FIELDS = Map.of(
            "id", "id",
            "inventarioId", "inventarioId",
            "tipoMovimiento", "tipoMovimiento",
            "cantidad", "cantidad",
            "fechaMovimiento", "fechaMovimiento",
            "usuarioId", "usuarioId"
    );

    @GetMapping("/inventario/{inventarioId}/paginado")
    @Operation(summary = "Listar movimientos por ID de inventario paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<MovimientoInventarioResponseDto>> listarMovimientosPorInventarioIdPaginado(
            @PathVariable Long inventarioId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaMovimiento,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaMovimiento", Sort.Direction.DESC, MOVIMIENTO_SORT_FIELDS);
        Page<MovimientoInventario> result = movimientoService.listarPorInventarioIdPaginado(inventarioId, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, movimientoMapper::toDto));
    }

    @GetMapping("/paginado")
    @Operation(summary = "Listar movimientos de inventario paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<MovimientoInventarioResponseDto>> listarMovimientosPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaMovimiento,desc") String sort,
            @RequestParam(required = false) Long inventarioId,
            @RequestParam(required = false) List<Long> inventarioIds,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaMovimiento", Sort.Direction.DESC, MOVIMIENTO_SORT_FIELDS);

        MovimientoInventario.TipoMovimiento tipoEnum = null;
        if (tipo != null && !tipo.isBlank()) {
            try {
                tipoEnum = MovimientoInventario.TipoMovimiento.valueOf(tipo.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }

        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.atTime(23, 59, 59, 999_999_999) : null;

        Page<MovimientoInventario> result = movimientoService.listarPaginado(
                inventarioId, inventarioIds, tipoEnum, motivo, desde, hasta, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, movimientoMapper::toDto));
    }

    @GetMapping
    @Operation(summary = "Listar todos los movimientos de inventario")
    public ResponseEntity<List<MovimientoInventarioResponseDto>> listarMovimientos() {
        List<MovimientoInventario> movimientos = movimientoService.listarTodos();

        List<MovimientoInventarioResponseDto> responseDtos = movimientos.stream()
                .map(movimientoMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }
}