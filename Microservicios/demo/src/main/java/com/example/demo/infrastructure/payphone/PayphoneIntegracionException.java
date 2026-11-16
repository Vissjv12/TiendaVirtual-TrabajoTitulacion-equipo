package com.example.demo.infrastructure.payphone;

/**
 * Fallo al comunicarse con la API de Payphone. El mensaje es apto para el cliente:
 * nunca incluye el token ni el cuerpo crudo de la respuesta de Payphone.
 */
public class PayphoneIntegracionException extends RuntimeException {
    public PayphoneIntegracionException(String mensaje) {
        super(mensaje);
    }
}
