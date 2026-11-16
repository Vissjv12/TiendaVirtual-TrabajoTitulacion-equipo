package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.ActualizarSeguimientoDto;
import com.example.msvc_ventas.application.dto.PageResponseDto;
import com.example.msvc_ventas.application.dto.SeguimientoLogisticaDto;
import com.example.msvc_ventas.application.util.PaginationUtils;
import com.example.msvc_ventas.domain.model.SeguimientoLogistica;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seguimiento-logistica")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Seguimiento Logístico", description = "API para gestionar el seguimiento logístico de pedidos")
public class SeguimientoLogisticaController {

    private final SeguimientoLogisticaService seguimientoService;
    private final VentaRepository ventaRepository;

    @PostMapping
    @Operation(summary = "Crear una nueva actualización de seguimiento")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @ventaAuthorizationService.canManageVenta(authentication, #dto.ventaId))")
    public ResponseEntity<?> crearSeguimiento(@Valid @RequestBody ActualizarSeguimientoDto dto) {
        log.info("Creando seguimiento para venta ID: {} con estado: {}", dto.getVentaId(), dto.getEstadoLogistica());
        
        try {
            SeguimientoLogistica.EstadoLogistica estado = SeguimientoLogistica.EstadoLogistica.valueOf(dto.getEstadoLogistica());
            
            SeguimientoLogistica seguimiento = seguimientoService.crearSeguimiento(
                    dto.getVentaId(),
                    estado,
                    dto.getDescripcion(),
                    dto.getUbicacion(),
                    dto.getResponsable(),
                    dto.getObservaciones()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(seguimiento));
        } catch (IllegalArgumentException e) {
            log.error("Estado logístico inválido: {}", dto.getEstadoLogistica());
            return ResponseEntity.badRequest().body("Estado logístico inválido: " + dto.getEstadoLogistica());
        } catch (Exception e) {
            log.error("Error al crear seguimiento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear seguimiento: " + e.getMessage());
        }
    }

    @PutMapping
    @Operation(summary = "Actualizar el estado de seguimiento de un pedido")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @ventaAuthorizationService.canManageVenta(authentication, #dto.ventaId))")
    public ResponseEntity<?> actualizarSeguimiento(@Valid @RequestBody ActualizarSeguimientoDto dto) {
        log.info("Actualizando seguimiento para venta ID: {} a estado: {}", dto.getVentaId(), dto.getEstadoLogistica());
        
        try {
            SeguimientoLogistica.EstadoLogistica estado = SeguimientoLogistica.EstadoLogistica.valueOf(dto.getEstadoLogistica());
            
            SeguimientoLogistica seguimiento = seguimientoService.actualizarEstado(
                    dto.getVentaId(),
                    estado,
                    dto.getDescripcion(),
                    dto.getUbicacion(),
                    dto.getResponsable(),
                    dto.getObservaciones()
            );

            return ResponseEntity.ok(toDto(seguimiento));
        } catch (IllegalArgumentException e) {
            log.error("Estado logístico inválido: {}", dto.getEstadoLogistica());
            return ResponseEntity.badRequest().body("Estado logístico inválido: " + dto.getEstadoLogistica());
        } catch (IllegalStateException e) {
            log.error("Error de estado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error al actualizar seguimiento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar seguimiento: " + e.getMessage());
        }
    }

    @GetMapping("/venta/{ventaId}")
    @Operation(summary = "Obtener historial completo de seguimiento de una venta")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #ventaId)")
    public ResponseEntity<List<SeguimientoLogisticaDto>> obtenerHistorialPorVenta(@PathVariable Long ventaId) {
        log.info("Obteniendo historial de seguimiento para venta ID: {}", ventaId);
        
        List<SeguimientoLogistica> historial = seguimientoService.obtenerHistorialPorVenta(ventaId);
        List<SeguimientoLogisticaDto> dtos = historial.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/venta/{ventaId}/ultimo")
    @Operation(summary = "Obtener el último estado de seguimiento de una venta")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #ventaId)")
    public ResponseEntity<?> obtenerUltimoEstado(@PathVariable Long ventaId) {
        log.info("Obteniendo último estado de seguimiento para venta ID: {}", ventaId);
        
        SeguimientoLogistica seguimiento = seguimientoService.obtenerUltimoEstado(ventaId);
        
        if (seguimiento == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró seguimiento para la venta con ID: " + ventaId);
        }
        
        return ResponseEntity.ok(toDto(seguimiento));
    }

    private static final Map<String, String> SEGUIMIENTO_SORT_FIELDS = Map.of(
            "id", "id",
            "ventaId", "ventaId",
            "estadoLogistica", "estadoLogistica",
            "fechaActualizacion", "fechaActualizacion",
            "fechaCreacion", "fechaCreacion"
    );

    @GetMapping("/emprendedor/{emprendedorId}/paginado")
    @Operation(summary = "Obtener seguimientos de un emprendedor paginados (contrato A-19)")
    @PreAuthorize("@ventaAuthorizationService.canManageCompany(authentication, #emprendedorId)")
    public ResponseEntity<?> obtenerSeguimientosPorEmprendedorPaginado(
            @PathVariable Long emprendedorId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaCreacion,desc") String sort,
            @RequestParam(required = false) String estado) {
        SeguimientoLogistica.EstadoLogistica estadoEnum = null;
        if (estado != null && !estado.isBlank()) {
            try {
                estadoEnum = SeguimientoLogistica.EstadoLogistica.valueOf(estado.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Estado logístico inválido: " + estado);
            }
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaCreacion", Sort.Direction.DESC, SEGUIMIENTO_SORT_FIELDS);
        Page<SeguimientoLogistica> result = seguimientoService.obtenerSeguimientosPorEmprendedorPaginado(
                emprendedorId, estadoEnum, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, this::toDto));
    }

    @GetMapping("/emprendedor/{emprendedorId}")
    @Operation(summary = "Obtener todos los seguimientos de un emprendedor")
    @PreAuthorize("@ventaAuthorizationService.canManageCompany(authentication, #emprendedorId)")
    public ResponseEntity<List<SeguimientoLogisticaDto>> obtenerSeguimientosPorEmprendedor(@PathVariable Long emprendedorId) {
        log.info("Obteniendo seguimientos del emprendedor ID: {}", emprendedorId);
        
        List<SeguimientoLogistica> seguimientos = seguimientoService.obtenerSeguimientosPorEmprendedor(emprendedorId);
        List<SeguimientoLogisticaDto> dtos = seguimientos.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/emprendedor/{emprendedorId}/estado/{estado}")
    @Operation(summary = "Obtener seguimientos de un emprendedor en un estado específico")
    @PreAuthorize("@ventaAuthorizationService.canManageCompany(authentication, #emprendedorId)")
    public ResponseEntity<?> obtenerSeguimientosPorEmprendedorYEstado(
            @PathVariable Long emprendedorId,
            @PathVariable String estado) {
        log.info("Obteniendo seguimientos del emprendedor {} en estado {}", emprendedorId, estado);
        
        try {
            SeguimientoLogistica.EstadoLogistica estadoEnum = SeguimientoLogistica.EstadoLogistica.valueOf(estado);
            List<SeguimientoLogistica> seguimientos = seguimientoService.obtenerSeguimientosPorEmprendedorYEstado(emprendedorId, estadoEnum);
            List<SeguimientoLogisticaDto> dtos = seguimientos.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            log.error("Estado logístico inválido: {}", estado);
            return ResponseEntity.badRequest().body("Estado logístico inválido: " + estado);
        }
    }

    @GetMapping("/estados")
    @Operation(summary = "Obtener lista de todos los estados logísticos disponibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EstadoInfo>> obtenerEstadosDisponibles() {
        log.info("Obteniendo lista de estados logísticos disponibles");
        
        List<EstadoInfo> estados = List.of(
                new EstadoInfo("PAGO_APROBADO", "Pago aprobado", "El pago ha sido verificado y aprobado por el emprendedor"),
                new EstadoInfo("EN_PREPARACION", "En preparación", "El pedido está siendo preparado"),
                new EstadoInfo("LISTO_PARA_ENVIO", "Listo para envío", "El pedido está listo para ser enviado"),
                new EstadoInfo("EN_CAMINO", "En camino", "El pedido está en tránsito"),
                new EstadoInfo("EN_PUNTO_ENTREGA", "En punto de entrega", "El pedido ha llegado al punto de entrega"),
                new EstadoInfo("ENTREGADO", "Entregado", "El pedido ha sido entregado al cliente"),
                new EstadoInfo("CANCELADO", "Cancelado", "El pedido ha sido cancelado")
        );
        
        return ResponseEntity.ok(estados);
    }

    // Métodos auxiliares
    private SeguimientoLogisticaDto toDto(SeguimientoLogistica seguimiento) {
        // Obtener número de factura de la venta
        String numeroFactura = ventaRepository.findById(seguimiento.getVentaId())
                .map(venta -> venta.getNumeroFactura())
                .orElse("N/A");

        return SeguimientoLogisticaDto.builder()
                .id(seguimiento.getId())
                .ventaId(seguimiento.getVentaId())
                .numeroFactura(numeroFactura)
                .estadoLogistica(seguimiento.getEstadoLogistica().name())
                .estadoTitulo(seguimiento.getEstadoLogistica().getTitulo())
                .descripcion(seguimiento.getDescripcion())
                .ubicacion(seguimiento.getUbicacion())
                .responsable(seguimiento.getResponsable())
                .observaciones(seguimiento.getObservaciones())
                .fechaActualizacion(seguimiento.getFechaActualizacion())
                .fechaCreacion(seguimiento.getFechaCreacion())
                .build();
    }

    // Clase interna para información de estados
    public static class EstadoInfo {
        private final String codigo;
        private final String titulo;
        private final String descripcion;

        public EstadoInfo(String codigo, String titulo, String descripcion) {
            this.codigo = codigo;
            this.titulo = titulo;
            this.descripcion = descripcion;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getTitulo() {
            return titulo;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
