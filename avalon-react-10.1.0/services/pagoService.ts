// services/pagoService.ts
import authService from './authService';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8083/api';

export interface PagoRequest {
    ventaId: number;
    metodoPago: 'TRANSFERENCIA' | 'TARJETA' | 'DEUNA';
    comprobanteUrl?: string;
}

export interface PagoResponse {
    id: number;
    ventaId: number;
    numeroFactura: string;
    monto: number;
    metodoPago: string;
    estadoPago: string;
    referenciaTransaccion?: string;
    comprobanteUrl?: string;
    fechaPago?: string;
    fechaCreacion: string;
    fechaActualizacion: string;
    clienteId: number;
    clienteNombre: string;
    emprendedorId: number;
}

export interface AprobarPagoRequest {
    emprendedorId: number;
    observaciones?: string;
}

export const crearPago = async (pago: PagoRequest): Promise<PagoResponse> => {
    const response = await fetch(`${API_BASE_URL}/pagos`, {
        method: 'POST',
        headers: {
            ...authService.getAuthHeaders(),
        },
        body: JSON.stringify(pago),
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al crear el pago');
        throw new Error(msg);
    }

    return response.json();
};

export const obtenerPagoPorVenta = async (ventaId: number): Promise<PagoResponse> => {
    const response = await fetch(`${API_BASE_URL}/pagos/venta/${ventaId}`, {
        headers: {
            ...authService.getAuthHeaders(),
        },
    });
    
    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al obtener el pago');
        throw new Error(msg);
    }

    return response.json();
};

export const aprobarPago = async (pagoId: number, request: AprobarPagoRequest): Promise<PagoResponse> => {
    const response = await fetch(`${API_BASE_URL}/pagos/${pagoId}/aprobar`, {
        method: 'POST',
        headers: {
            ...authService.getAuthHeaders(),
        },
        body: JSON.stringify(request),
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al aprobar el pago');
        throw new Error(msg);
    }

    return response.json();
};

export const rechazarPago = async (pagoId: number, request: AprobarPagoRequest): Promise<PagoResponse> => {
    const response = await fetch(`${API_BASE_URL}/pagos/${pagoId}/rechazar`, {
        method: 'POST',
        headers: {
            ...authService.getAuthHeaders(),
        },
        body: JSON.stringify(request),
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al rechazar el pago');
        throw new Error(msg);
    }

    return response.json();
};

export const subirComprobante = async (pagoId: number, comprobanteUrl: string): Promise<PagoResponse> => {
    const response = await fetch(`${API_BASE_URL}/pagos/${pagoId}/comprobante`, {
        method: 'POST',
        headers: {
            ...authService.getAuthHeaders(),
        },
        body: JSON.stringify({ comprobanteUrl }),
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al subir el comprobante');
        throw new Error(msg);
    }

    return response.json();
};

export const listarPagosPendientes = async (emprendedorId: number): Promise<PagoResponse[]> => {
    const response = await fetch(`${API_BASE_URL}/pagos/emprendedor/${emprendedorId}/pendientes`, {
        headers: {
            ...authService.getAuthHeaders(),
        },
    });
    
    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al listar pagos pendientes');
        throw new Error(msg);
    }

    return response.json();
};

export const listarPagosPorEmprendedor = async (emprendedorId: number): Promise<PagoResponse[]> => {
    const response = await fetch(`${API_BASE_URL}/pagos/emprendedor/${emprendedorId}`, {
        headers: {
            ...authService.getAuthHeaders(),
        },
    });
    
    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al listar pagos del emprendedor');
        throw new Error(msg);
    }

    return response.json();
};

export const procesarPagoSimulado = async (metodoPago: 'TARJETA' | 'DEUNA'): Promise<{
    success: boolean;
    transactionId: string;
    message: string;
}> => {
    console.log(`🎭 Simulando pago con ${metodoPago}...`);
    
    // Simular delay de procesamiento
    await new Promise(resolve => setTimeout(resolve, 1500));
    
    const mockTransactionId = `${metodoPago}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    return {
        success: true,
        transactionId: mockTransactionId,
        message: `Pago con ${metodoPago} procesado exitosamente (simulado)`
    };
};

export default {
    crearPago,
    obtenerPagoPorVenta,
    aprobarPago,
    rechazarPago,
    subirComprobante,
    listarPagosPendientes,
    listarPagosPorEmprendedor,
    procesarPagoSimulado
};
