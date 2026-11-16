package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.*;
import com.example.msvc_ventas.domain.service.StripeService;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stripe: INACTIVO por decisión del proyecto, conservado para una evaluación futura.
 *
 * El controlador solo se registra con {@code stripe.enabled=true} (variable STRIPE_ENABLED, por defecto false).
 * Deshabilitado, /api/stripe/** no existe: el servicio responde 401 sin token y 404 con token, y el Gateway
 * ya no enruta /api/stripe. El pago con tarjeta oficial es PayPhone (PagoPasarelaService).
 *
 * Aunque se active la bandera, este controlador NO puede aprobar pagos ni completar ventas: confirm-payment
 * responde 501 hasta integrarse con PagoPasarelaService (monto verificado, descuento de stock con credencial
 * interna, idempotencia y factura tras commit). El webhook, que respondía 200 sin verificar la firma, se retiró.
 */
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "stripe.enabled", havingValue = "true")
public class StripeController {

    private final StripeService stripeService;

    /**
     * Crear PaymentIntent para procesar pago con tarjeta
     */
    @PostMapping("/create-payment-intent")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #request.ventaId)")
    public ResponseEntity<StripePaymentIntentResponseDto> crearPaymentIntent(
            @RequestBody StripePaymentIntentRequestDto request) {
        
        log.info("Creando PaymentIntent para venta ID: {}", request.getVentaId());
        
        try {
            Map<String, String> paymentIntent = stripeService.crearPaymentIntent(
                    request.getMonto(),
                    request.getVentaId(),
                    request.getClienteId()
            );

            StripePaymentIntentResponseDto response = StripePaymentIntentResponseDto.builder()
                    .clientSecret(paymentIntent.get("clientSecret"))
                    .paymentIntentId(paymentIntent.get("paymentIntentId"))
                    .status("pending")
                    .message("PaymentIntent creado exitosamente")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error al crear PaymentIntent: {}", e.getMessage(), e);
            
            StripePaymentIntentResponseDto errorResponse = StripePaymentIntentResponseDto.builder()
                    .status("error")
                    .message("Error al procesar pago: " + e.getMessage())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Confirmación de pago: deshabilitada hasta integrarse con PagoPasarelaService.
     * El flujo anterior (procesarPagoTarjeta) completaba la venta sin descontar inventario y se eliminó.
     */
    @PostMapping("/confirm-payment")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #request.ventaId)")
    public ResponseEntity<Map<String, String>> confirmarPago(
            @RequestBody StripeConfirmPaymentDto request) {
        log.warn("confirm-payment de Stripe invocado para la venta {}: no soportado", request.getVentaId());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "message", "La confirmación con Stripe no está disponible; el pago con tarjeta se procesa con PayPhone"));
    }

    /**
     * Obtener estado de un PaymentIntent
     */
    @GetMapping("/payment-intent/{paymentIntentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> obtenerEstadoPago(
            @PathVariable String paymentIntentId) {
        
        log.info("Consultando estado de PaymentIntent: {}", paymentIntentId);
        
        try {
            PaymentIntent paymentIntent = stripeService.obtenerPaymentIntent(paymentIntentId);
            
            Map<String, String> response = Map.of(
                    "paymentIntentId", paymentIntent.getId(),
                    "status", paymentIntent.getStatus(),
                    "amount", String.valueOf(paymentIntent.getAmount())
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error al obtener PaymentIntent: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
