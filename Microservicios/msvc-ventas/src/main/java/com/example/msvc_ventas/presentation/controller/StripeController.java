package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.*;
import com.example.msvc_ventas.application.mapper.PagoMapper;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.domain.service.StripeService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StripeController {

    private final StripeService stripeService;
    private final PagoService pagoService;
    private final PagoMapper pagoMapper;

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
     * Confirmar pago exitoso y crear registro en base de datos
     */
    @PostMapping("/confirm-payment")
    @PreAuthorize("@ventaAuthorizationService.canViewVenta(authentication, #request.ventaId)")
    public ResponseEntity<PagoResponseDto> confirmarPago(
            @RequestBody StripeConfirmPaymentDto request) {
        
        log.info("Confirmando pago para PaymentIntent: {}", request.getPaymentIntentId());
        
        try {
            // Verificar estado del PaymentIntent en Stripe
            PaymentIntent paymentIntent = stripeService.obtenerPaymentIntent(request.getPaymentIntentId());
            
            if (!"succeeded".equals(paymentIntent.getStatus())) {
                throw new IllegalStateException("El pago no fue exitoso. Estado: " + paymentIntent.getStatus());
            }

            // Crear registro de pago en base de datos con estado APROBADO
            PagoEntity pago = pagoService.procesarPagoTarjeta(
                    request.getVentaId(),
                    request.getPaymentIntentId(),
                    null // comprobanteUrl no necesario para Stripe
            );

            PagoResponseDto response = pagoMapper.toDto(pago);
            log.info("Pago confirmado exitosamente. Pago ID: {}", pago.getId());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error al confirmar pago: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Webhook de Stripe para eventos de pago
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        
        log.info("Webhook recibido de Stripe");
        
        try {
            // Aquí puedes implementar la lógica del webhook si lo necesitas
            // Por ejemplo, para manejar eventos de payment_intent.succeeded automáticamente
            
            return ResponseEntity.ok("Webhook procesado");
            
        } catch (Exception e) {
            log.error("Error procesando webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error procesando webhook");
        }
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
