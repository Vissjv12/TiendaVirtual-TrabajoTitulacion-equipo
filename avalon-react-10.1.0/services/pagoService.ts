// services/pagoService.ts
import authService from './authService';
import { GATEWAY_API_URL, PageResponse, PaginationParams, buildPaginationQuery } from './apiConfig';

const API_BASE_URL = GATEWAY_API_URL;

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

export interface DatosBancariosPublico {
    empresaId?: number;
    empresaNombre?: string;
    banco: string;
    tipoCuenta: string;
    numeroCuenta: string;
    titular: string;
    cedulaRuc: string;
    email?: string;
}

const BANK_LABELS: Record<string, string> = {
    pichincha: 'Banco Pichincha',
    pacifico: 'Banco del Pacífico',
    guayaquil: 'Banco Guayaquil',
    internacional: 'Banco Internacional',
    produbanco: 'Produbanco',
    bolivariano: 'Banco Bolivariano',
    austro: 'Banco Austro',
    solidario: 'Banco Solidario',
    otros: 'Otro banco'
};

const ACCOUNT_TYPE_LABELS: Record<string, string> = {
    ahorros: 'Ahorros',
    corriente: 'Corriente'
};

export const formatBankLabel = (banco?: string): string => {
    if (!banco) return 'Banco no especificado';
    const key = banco.trim().toLowerCase();
    return BANK_LABELS[key] || banco;
};

export const formatAccountTypeLabel = (tipoCuenta?: string): string => {
    if (!tipoCuenta) return 'No especificado';
    const key = tipoCuenta.trim().toLowerCase();
    return ACCOUNT_TYPE_LABELS[key] || tipoCuenta;
};

/**
 * Obtiene las cuentas bancarias reales configuradas por los emprendedores de los productos del carrito.
 */
export const obtenerDatosBancariosCheckout = async (
    cartItems: Array<{ producto?: any }>
): Promise<DatosBancariosPublico[]> => {
    const empresasMap = new Map<number, string | undefined>();
    for (const item of cartItems || []) {
        const producto = item?.producto;
        const empresaId = Number(
            producto?.empresa?.id || producto?.empresaId || producto?.emprendedorId || 0
        );
        if (empresaId > 0 && !empresasMap.has(empresaId)) {
            empresasMap.set(empresaId, producto?.empresa?.nombre);
        }
    }

    if (empresasMap.size === 0) {
        return [];
    }

    const responses = await Promise.all(
        Array.from(empresasMap.entries()).map(async ([empresaId, empresaNombre]) => {
            const response = await fetch(
                `${API_BASE_URL}/emprendedor/configuracion-pagos/bancarios/${empresaId}`
            );

            if (response.status === 404) {
                return null;
            }
            if (!response.ok) {
                throw new Error('No se pudieron cargar los datos bancarios del emprendedor');
            }

            const data = await response.json();
            if (!data || !data.banco || !data.numeroCuenta) {
                return null;
            }

            return {
                empresaId,
                empresaNombre,
                banco: data.banco,
                tipoCuenta: data.tipoCuenta,
                numeroCuenta: data.numeroCuenta,
                titular: data.titular,
                cedulaRuc: data.cedulaRuc,
                email: data.email
            } as DatosBancariosPublico;
        })
    );

    const seen = new Set<string>();
    return responses.filter((cuenta): cuenta is DatosBancariosPublico => {
        if (!cuenta) return false;
        const key = `${cuenta.banco}|${cuenta.numeroCuenta}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
};

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

export const listarPagosPendientesPaginado = async (
    emprendedorId: number,
    params?: PaginationParams
): Promise<PageResponse<PagoResponse>> => {
    const query = buildPaginationQuery({
        page: params?.page ?? 0,
        size: params?.size ?? 10,
        sort: params?.sort ?? 'fechaCreacion,desc'
    });
    const response = await fetch(`${API_BASE_URL}/pagos/emprendedor/${emprendedorId}/pendientes/paginado${query}`, {
        headers: {
            ...authService.getAuthHeaders(),
        },
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al listar pagos pendientes paginados');
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

export const listarPagosPorEmprendedorPaginado = async (
    emprendedorId: number,
    params?: PaginationParams
): Promise<PageResponse<PagoResponse>> => {
    const query = buildPaginationQuery({
        page: params?.page ?? 0,
        size: params?.size ?? 10,
        sort: params?.sort ?? 'fechaCreacion,desc'
    });
    const response = await fetch(`${API_BASE_URL}/pagos/emprendedor/${emprendedorId}/paginado${query}`, {
        headers: {
            ...authService.getAuthHeaders(),
        },
    });

    if (!response.ok) {
        const msg = await authService.extractErrorMessage(response, 'Error al listar pagos paginados del emprendedor');
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
    listarPagosPendientesPaginado,
    listarPagosPorEmprendedor,
    listarPagosPorEmprendedorPaginado,
    procesarPagoSimulado,
    formatBankLabel,
    formatAccountTypeLabel,
    obtenerDatosBancariosCheckout
};
