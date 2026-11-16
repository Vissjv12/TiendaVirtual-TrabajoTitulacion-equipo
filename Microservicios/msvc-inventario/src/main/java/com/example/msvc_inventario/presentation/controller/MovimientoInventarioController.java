package com.example.msvc_inventario.presentation.controller;

import com.example.msvc_inventario.application.dto.MovimientoInventarioRequestDto;
import com.example.msvc_inventario.application.dto.MovimientoInventarioResponseDto;
import com.example.msvc_inventario.application.dto.PageResponseDto;
import com.example.msvc_inventario.application.mapper.MovimientoInventarioMapper;
import com.example.msvc_inventario.application.service.InventarioAuthorizationService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movimientos")
@Tag(name = "Movimientos de Inventario", description = "API para gestionar los movimientos de inventario")
public class MovimientoInventarioController {

    private final MovimientoInventarioService movimientoService;
    private final MovimientoInventarioMapper movimientoMapper;

    private final InventarioAuthorizationService autorizacion;

    public MovimientoInventarioController(
            MovimientoInventarioService movimientoService,
            MovimientoInventarioMapper movimientoMapper,
            InventarioAuthorizationService autorizacion) {
        this.movimientoService = movimientoService;
        this.movimientoMapper = movimientoMapper;
        this.autorizacion = autorizacion;
    }

    @PostMapping
    @Operation(summary = "Registrar un nuevo movimiento de inventario")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageInventario(authentication, #requestDto.inventarioId))")
    public ResponseEntity<MovimientoInventarioResponseDto> registrarMovimiento(
            @Valid @RequestBody MovimientoInventarioRequestDto requestDto) {

        MovimientoInventario movimiento = movimientoMapper.toEntity(requestDto);
        // El autor del movimiento es siempre el usuario del JWT, nunca un valor del cuerpo (A-01)
        movimiento.setUsuarioId(com.example.msvc_inventario.application.service.UsuarioAutenticado.id());
        MovimientoInventario movimientoRegistrado = movimientoService.registrarMovimiento(movimiento);

        return new ResponseEntity<>(
                movimientoMapper.toDto(movimientoRegistrado),
                HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un movimiento de inventario por su ID")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageMovimiento(authentication, #id))")
    public ResponseEntity<MovimientoInventarioResponseDto> obtenerMovimientoPorId(@PathVariable Long id) {
        MovimientoInventario movimiento = movimientoService.obtenerPorId(id);
        return ResponseEntity.ok(movimientoMapper.toDto(movimiento));
    }

    @GetMapping("/inventario/{inventarioId}")
    @Operation(summary = "Listar movimientos por ID de inventario")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageInventario(authentication, #inventarioId))")
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
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageInventario(authentication, #inventarioId))")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    public ResponseEntity<PageResponseDto<MovimientoInventarioResponseDto>> listarMovimientosPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaMovimiento,desc") String sort,
            @RequestParam(required = false) Long inventarioId,
            @RequestParam(required = false) List<Long> inventarioIds,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            Authentication authentication) {
        // T07: para ROLE_EMP los inventarios de su empresa se calculan en el servidor (empresa del JWT,
        // una llamada a productos y una consulta agrupada). Los filtros del navegador solo pueden
        // RESTRINGIR ese conjunto: un inventario de otra empresa sigue respondiendo 403.
        boolean sinResultados = false;
        if (!autorizacion.isAdmin(authentication)) {
            Set<Long> propios = autorizacion.inventariosDeEmpresaDelToken(authentication)
                    .orElseThrow(() -> new AccessDeniedException(
                            "No se pudo verificar la empresa de los inventarios"));
            if (inventarioId != null && !propios.contains(inventarioId)) {
                throw new AccessDeniedException("No puede consultar movimientos de inventario de otra empresa");
            }
            if (inventarioIds != null && !inventarioIds.isEmpty()) {
                if (inventarioIds.stream().anyMatch(id -> id == null || !propios.contains(id))) {
                    throw new AccessDeniedException("No puede consultar movimientos de inventario de otra empresa");
                }
            } else if (inventarioId == null) {
                inventarioIds = new ArrayList<>(propios);
                sinResultados = propios.isEmpty();
            }
        }
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

        // Una empresa sin inventarios no consulta la base: una lista vacía no debe convertirse en "sin filtro"
        Page<MovimientoInventario> result = sinResultados
                ? Page.empty(pageable)
                : movimientoService.listarPaginado(inventarioId, inventarioIds, tipoEnum, motivo, desde, hasta, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, movimientoMapper::toDto));
    }

    @GetMapping
    @Operation(summary = "Listar todos los movimientos de inventario")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<MovimientoInventarioResponseDto>> listarMovimientos() {
        List<MovimientoInventario> movimientos = movimientoService.listarTodos();

        List<MovimientoInventarioResponseDto> responseDtos = movimientos.stream()
                .map(movimientoMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }
}