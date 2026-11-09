// services/stripeService.ts
import { GATEWAY_API_URL } from './apiConfig';
const API_BASE_URL = GATEWAY_API_URL;

export interface CreatePaymentIntentRequest {
    ventaId: number;
    clienteId: number;
    monto: number;
}

export interface PaymentIntentResponse {
    clientSecret: string;
    paymentIntentId: string;
    status: string;
    message: string;
}

export interface ConfirmPaymentRequest {
    paymentIntentId: string;
    ventaId: number;
}

export const crearPaymentIntent = async (request: CreatePaymentIntentRequest): Promise<PaymentIntentResponse> => {
    const response = await fetch(`${API_BASE_URL}/stripe/create-payment-intent`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    });

    if (!response.ok) {
        throw new Error('Error al crear PaymentIntent');
    }

    return response.json();
};

export const confirmarPago = async (request: ConfirmPaymentRequest): Promise<any> => {
    const response = await fetch(`${API_BASE_URL}/stripe/confirm-payment`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
    });

    if (!response.ok) {
        throw new Error('Error al confirmar pago');
    }

    return response.json();
};

export const obtenerEstadoPago = async (paymentIntentId: string): Promise<any> => {
    const response = await fetch(`${API_BASE_URL}/stripe/payment-intent/${paymentIntentId}`);

    if (!response.ok) {
        throw new Error('Error al obtener estado del pago');
    }

    return response.json();
};
