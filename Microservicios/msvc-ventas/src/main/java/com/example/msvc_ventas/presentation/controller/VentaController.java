package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.PageResponseDto;
import com.example.msvc_ventas.application.dto.VentaRequestDto;
import com.example.msvc_ventas.application.dto.VentaResponseDto;
import com.example.msvc_ventas.application.mapper.VentaMapper;
import com.example.msvc_ventas.application.service.VentaApplicationService;
import com.example.msvc_ventas.application.util.PaginationUtils;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.service.ClienteService;
import com.example.msvc_ventas.domain.service.FacturaService;
import com.example.msvc_ventas.domain.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "API para gestionar ventas")
public class VentaController {

    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final VentaApplicationService ventaApplicationService;
    private final VentaMapper ventaMapper;
    private final FacturaService facturaService;

    @PostMapping
    @Operation(summary = "Crear una nueva venta")
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_CLIENTE', 'ROLE_EMP', 'ROLE_ADMIN') and (#requestDto.clienteId == null or @ventaAuthorizationService.canAccessCliente(authentication, #requestDto.clienteId))")
    public ResponseEntity<VentaResponseDto> crearVenta(@Valid @RequestBody VentaRequestDto requestDto) {
        // Delegamos la lógica de creación al servicio de aplicación que manejará
        // la comunicación con otros microservicios (productos e inventario)
        VentaResponseDto ventaCreada = ventaApplicationService.crearVenta(requestDto);
        return new ResponseEntity<>(ventaCreada, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una venta por su ID")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #id)")
    public ResponseEntity<VentaResponseDto> obtenerVenta(@PathVariable Long id) {
        Venta venta = ventaService.obtenerVentaPorId(id);
        return ResponseEntity.ok(ventaMapper.toDto(venta));
    }

    @GetMapping("/factura/{numeroFactura}")
    @Operation(summary = "Obtener una venta por su número de factura")
    @PreAuthorize("@ventaAuthorizationService.canViewVentaByFactura(authentication, #numeroFactura)")
    public ResponseEntity<VentaResponseDto> obtenerVentaPorNumeroFactura(@PathVariable String numeroFactura) {
        Venta venta = ventaService.obtenerVentaPorNumeroFactura(numeroFactura);
        return ResponseEntity.ok(ventaMapper.toDto(venta));
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar todas las ventas de un cliente")
    @PreAuthorize("@ventaAuthorizationService.canAccessCliente(authentication, #clienteId)")
    public ResponseEntity<List<VentaResponseDto>> listarVentasPorCliente(@PathVariable Long clienteId) {
        List<Venta> ventas = ventaService.listarVentasPorCliente(clienteId);
        List<VentaResponseDto> ventasDto = ventas.stream()
                .map(ventaMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ventasDto);
    }

    @GetMapping("/emprendedor/{emprendedorId}")
    @Operation(summary = "Listar todas las ventas de un emprendedor")
    @PreAuthorize("@ventaAuthorizationService.canManageCompany(authentication, #emprendedorId)")
    public ResponseEntity<List<VentaResponseDto>> listarVentasPorEmprendedor(@PathVariable Long emprendedorId) {
        List<Venta> ventas = ventaService.listarVentasPorEmprendedor(emprendedorId);
        List<VentaResponseDto> ventasDto = ventas.stream()
                .map(ventaMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ventasDto);
    }

    private static final Map<String, String> VENTA_SORT_FIELDS = Map.of(
            "id", "id",
            "numeroFactura", "numeroFactura",
            "total", "total",
            "estado", "estado",
            "fechaVenta", "fechaVenta",
            "fechaCreacion", "fechaCreacion"
    );

    @GetMapping("/paginado")
    @Operation(summary = "Listar ventas paginadas (contrato A-19)")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PageResponseDto<VentaResponseDto>> listarVentasPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaVenta,desc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado) {
        Venta.EstadoVenta estadoEnum = parseEstadoVenta(estado);
        if (estado != null && !estado.isBlank() && estadoEnum == null) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaVenta", Sort.Direction.DESC, VENTA_SORT_FIELDS);
        Page<Venta> ventas = ventaService.listarVentasPaginado(q, estadoEnum, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(ventas, ventaMapper::toDto));
    }

    @GetMapping("/cliente/{clienteId}/paginado")
    @Operation(summary = "Listar ventas de un cliente paginadas (contrato A-19)")
    @PreAuthorize("@ventaAuthorizationService.canAccessCliente(authentication, #clienteId)")
    public ResponseEntity<PageResponseDto<VentaResponseDto>> listarVentasPorClientePaginado(
            @PathVariable Long clienteId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaVenta,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaVenta", Sort.Direction.DESC, VENTA_SORT_FIELDS);
        Page<Venta> ventas = ventaService.listarVentasPorClientePaginado(clienteId, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(ventas, ventaMapper::toDto));
    }

    @GetMapping("/emprendedor/{emprendedorId}/paginado")
    @Operation(summary = "Listar ventas de un emprendedor paginadas (contrato A-19)")
    @PreAuthorize("@ventaAuthorizationService.canManageCompany(authentication, #emprendedorId)")
    public ResponseEntity<PageResponseDto<VentaResponseDto>> listarVentasPorEmprendedorPaginado(
            @PathVariable Long emprendedorId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "fechaVenta,desc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado) {
        Venta.EstadoVenta estadoEnum = parseEstadoVenta(estado);
        if (estado != null && !estado.isBlank() && estadoEnum == null) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "fechaVenta", Sort.Direction.DESC, VENTA_SORT_FIELDS);
        Page<Venta> ventas = ventaService.listarVentasPorEmprendedorPaginado(
                emprendedorId, q, estadoEnum, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(ventas, ventaMapper::toDto));
    }

    private Venta.EstadoVenta parseEstadoVenta(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return Venta.EstadoVenta.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping
    @Operation(summary = "Listar todas las ventas")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<VentaResponseDto>> listarVentas() {
        List<Venta> ventas = ventaService.listarVentas();
        List<VentaResponseDto> ventasDto = ventas.stream()
                .map(ventaMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ventasDto);
    }

    @PutMapping("/{id}/completar")
    @Operation(summary = "Completar una venta (actualiza el inventario)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP') and @ventaAuthorizationService.canManageVenta(authentication, #id)")
    public ResponseEntity<VentaResponseDto> completarVenta(@PathVariable Long id) {
        Venta venta = ventaService.completarVenta(id);
        return ResponseEntity.ok(ventaMapper.toDto(venta));
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una venta")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP') and @ventaAuthorizationService.canManageVenta(authentication, #id)")
    public ResponseEntity<VentaResponseDto> cancelarVenta(@PathVariable Long id) {
        Venta venta = ventaService.cancelarVenta(id);
        return ResponseEntity.ok(ventaMapper.toDto(venta));
    }

    @GetMapping("/{id}/factura")
    @Operation(summary = "Descargar factura en PDF de una venta")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #id)")
    public ResponseEntity<Resource> descargarFactura(@PathVariable Long id) {
        try {
            File facturaPdf = facturaService.obtenerFacturaPDF(id);
            Resource resource = new FileSystemResource(facturaPdf);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + facturaPdf.getName());
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(facturaPdf.length())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/factura/generar")
    @Operation(summary = "Generar factura en PDF para una venta")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP') and @ventaAuthorizationService.canManageVenta(authentication, #id)")
    public ResponseEntity<String> generarFactura(@PathVariable Long id) {
        try {
            String rutaArchivo = facturaService.generarFacturaPDF(id);
            return ResponseEntity.ok("Factura generada exitosamente: " + rutaArchivo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al generar factura: " + e.getMessage());
        }
    }
}