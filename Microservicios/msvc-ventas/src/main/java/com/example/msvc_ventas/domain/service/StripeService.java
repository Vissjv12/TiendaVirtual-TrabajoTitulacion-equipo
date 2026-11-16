package com.example.msvc_ventas.domain.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Stripe inactivo por defecto: el bean solo existe con stripe.enabled=true (ver StripeController). */
@Service
@Slf4j
@ConditionalOnProperty(name = "stripe.enabled", havingValue = "true")
public class StripeService {

    @Value("${stripe.api.key:}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new IllegalStateException("stripe.enabled=true requiere STRIPE_API_KEY");
        }
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API inicializado");
    }

    /**
     * Crear PaymentIntent para procesar pago con tarjeta
     * 
     * @param amount Monto en BigDecimal (dólares)
     * @param ventaId ID de la venta
     * @param clienteId ID del cliente
     * @return Map con client_secret y payment_intent_id
     */
    public Map<String, String> crearPaymentIntent(BigDecimal amount, Long ventaId, Long clienteId) {
        try {
            // Stripe trabaja con centavos (cents), multiplicar por 100
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setDescription("Pago de venta #" + ventaId)
                    .putMetadata("venta_id", ventaId.toString())
                    .putMetadata("cliente_id", clienteId.toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            
            log.info("PaymentIntent creado: {} para venta ID: {}, monto: ${}", 
                    paymentIntent.getId(), ventaId, amount);

            Map<String, String> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());
            
            return response;

        } catch (StripeException e) {
            log.error("Error al crear PaymentIntent: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar pago con Stripe: " + e.getMessage());
        }
    }

    /**
     * Confirmar el estado de un PaymentIntent
     * 
     * @param paymentIntentId ID del PaymentIntent
     * @return PaymentIntent con su estado actualizado
     */
    public PaymentIntent obtenerPaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            log.info("PaymentIntent {} obtenido con estado: {}", paymentIntentId, paymentIntent.getStatus());
            return paymentIntent;
        } catch (StripeException e) {
            log.error("Error al obtener PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
            throw new RuntimeException("Error al verificar pago: " + e.getMessage());
        }
    }

    /**
     * Cancelar un PaymentIntent
     * 
     * @param paymentIntentId ID del PaymentIntent a cancelar
     * @return PaymentIntent cancelado
     */
    public PaymentIntent cancelarPaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            PaymentIntent canceledIntent = paymentIntent.cancel();
            log.info("PaymentIntent {} cancelado", paymentIntentId);
            return canceledIntent;
        } catch (StripeException e) {
            log.error("Error al cancelar PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
            throw new RuntimeException("Error al cancelar pago: " + e.getMessage());
        }
    }

    /**
     * Verificar firma del webhook de Stripe
     * 
     * @param payload Cuerpo del webhook
     * @param signature Firma del webhook
     * @param webhookSecret Secret del webhook
     * @return true si la firma es válida
     */
    public boolean verificarWebhookSignature(String payload, String signature, String webhookSecret) {
        try {
            com.stripe.model.Event event = com.stripe.net.Webhook.constructEvent(
                    payload, signature, webhookSecret
            );
            log.info("Webhook verificado: {}", event.getType());
            return true;
        } catch (Exception e) {
            log.error("Error verificando webhook: {}", e.getMessage());
            return false;
        }
    }
}
