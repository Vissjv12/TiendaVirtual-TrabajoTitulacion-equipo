// services/payphoneService.ts
//
// Hallazgo C-02: el navegador NUNCA recibe el token de Payphone.
// - Preparar y confirmar el cobro lo hace el backend (auth-service) con el token del comercio.
// - El navegador solo recibe enlaces de pago de Payphone y un resultado saneado.
import authService from './authService';
import { GATEWAY_API_URL } from './apiConfig';
import { DisponibilidadMetodo, verificarDisponibilidadMetodo } from './checkoutEmpresa';
import { PAGO_PAYPHONE_PENDIENTE_KEY, PagoPayphonePendiente } from './payphoneCheckout';

export type { PagoPayphonePendiente };

const PAYPHONE_URL = `${GATEWAY_API_URL}/emprendedor/configuracion-pagos/payphone`;

export const MOTIVO_PAYPHONE_SIN_CONFIGURAR = 'El emprendedor aún no ha configurado Payphone';

export interface PayphonePrepararResponse {
    clientTransactionId: string;
    paymentId?: string;
    payWithPayPhone?: string;
    payWithCard?: string;
}

export interface PayphoneConfirmResponse {
    statusCode?: number; // 3: Aprobado, 2: Cancelado
    transactionStatus?: string;
    transactionId?: number;
    clientTransactionId?: string;
    amount?: number;
    currency?: string;
    cardType?: string;
    authorizationCode?: string;
    ventaId?: number;
    numeroFactura?: string;
    /** COMPLETADA | YA_PROCESADA | STOCK_PENDIENTE | MONTO_NO_COINCIDE | REQUIERE_REVISION */
    resultadoVenta?: string;
}

const leerError = async (response: Response, porDefecto: string): Promise<string> => {
    const mensaje = await authService.extractErrorMessage(response, porDefecto);
    return mensaje || porDefecto;
};

class PayphoneService {
    /** true si la empresa acepta Payphone. El endpoint público no expone credenciales. */
    async empresaAceptaPayphone(empresaId: number): Promise<boolean> {
        const response = await fetch(`${PAYPHONE_URL}/${empresaId}`);
        if (response.status === 404) return false;
        if (!response.ok) throw new Error('No se pudo verificar la configuración de Payphone');
        const data = await response.json();
        return data?.disponible === true;
    }

    /** Payphone disponible solo si todo el carrito es de UNA empresa con Payphone (falla cerrado). */
    verificarDisponibilidad(cartItems: Array<{ producto?: any }>): Promise<DisponibilidadMetodo<true>> {
        return verificarDisponibilidadMetodo<true>(
            cartItems,
            async (empresaId) => ((await this.empresaAceptaPayphone(empresaId)) ? true : null),
            MOTIVO_PAYPHONE_SIN_CONFIGURAR
        );
    }

    /**
     * Pide al backend que prepare el cobro de una venta ya creada (A-03).
     * No se envían montos: el backend los toma de la venta.
     */
    async preparar(ventaId: number): Promise<PayphonePrepararResponse> {
        const response = await fetch(`${PAYPHONE_URL}/ventas/${ventaId}/preparar`, {
            method: 'POST',
            headers: authService.getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error(await leerError(response, 'No se pudo iniciar el pago con Payphone'));
        }
        return (await response.json()) as PayphonePrepararResponse;
    }

    /** Pide al backend que confirme con Payphone la transacción y finalice la venta (A-04). */
    async confirmarPago(id: number, clientTxId: string): Promise<PayphoneConfirmResponse> {
        const response = await fetch(`${PAYPHONE_URL}/confirmar`, {
            method: 'POST',
            headers: authService.getAuthHeaders(),
            body: JSON.stringify({ id, clientTxId })
        });
        if (!response.ok) {
            throw new Error(await leerError(response, `Error al confirmar el pago (HTTP ${response.status})`));
        }
        return (await response.json()) as PayphoneConfirmResponse;
    }

    /** Guarda la venta y la transacción en curso, para reutilizar la venta si se reintenta. */
    guardarPagoPendiente(pendiente: PagoPayphonePendiente): void {
        try {
            sessionStorage.setItem(PAGO_PAYPHONE_PENDIENTE_KEY, JSON.stringify(pendiente));
        } catch {
            /* sessionStorage no disponible: la confirmación usará el carrito */
        }
    }

    leerPagoPendiente(): PagoPayphonePendiente | null {
        try {
            const crudo = sessionStorage.getItem(PAGO_PAYPHONE_PENDIENTE_KEY);
            return crudo ? (JSON.parse(crudo) as PagoPayphonePendiente) : null;
        } catch {
            return null;
        }
    }

    limpiarPagoPendiente(): void {
        try {
            sessionStorage.removeItem(PAGO_PAYPHONE_PENDIENTE_KEY);
        } catch {
            /* ignorar */
        }
    }
}

const payphoneService = new PayphoneService();
export default payphoneService;
