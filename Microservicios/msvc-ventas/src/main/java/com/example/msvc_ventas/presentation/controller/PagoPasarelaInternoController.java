package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaRequestDto;
import com.example.msvc_ventas.application.dto.RegistrarPagoPasarelaResponseDto;
import com.example.msvc_ventas.application.dto.VentaResumenPagoDto;
import com.example.msvc_ventas.application.service.PagoPasarelaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones INTERNAS de pago por pasarela (A-03 / A-04).
 *
 * Solo acepta la credencial de servicio emitida por auth-service (rol ROLE_SERVICIO_PAGOS y
 * claim tipo=servicio); ningún token de usuario, ni siquiera de ADMIN, puede invocarlas.
 * Además, el API Gateway bloquea /api/ventas/internal/** desde el exterior.
 */
@RestController
@RequestMapping("/api/ventas/internal/pagos-pasarela")
@RequiredArgsConstructor
public class PagoPasarelaInternoController {

    private final PagoPasarelaService pagoPasarelaService;

    @GetMapping("/{ventaId}")
    @PreAuthorize("@ventaAuthorizationService.isServicioPagos(authentication)")
    public ResponseEntity<VentaResumenPagoDto> obtenerResumen(@PathVariable Long ventaId) {
        return ResponseEntity.ok(pagoPasarelaService.obtenerResumen(ventaId));
    }

    @PostMapping("/{ventaId}/aprobar")
    @PreAuthorize("@ventaAuthorizationService.isServicioPagos(authentication)")
    public ResponseEntity<RegistrarPagoPasarelaResponseDto> registrarPagoAprobado(
            @PathVariable Long ventaId,
            @Valid @RequestBody RegistrarPagoPasarelaRequestDto solicitud) {
        return ResponseEntity.ok(pagoPasarelaService.registrarPagoAprobado(ventaId, solicitud));
    }
}
