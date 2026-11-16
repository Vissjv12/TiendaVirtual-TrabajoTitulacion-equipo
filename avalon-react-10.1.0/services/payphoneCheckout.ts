// services/payphoneCheckout.ts
//
// Lógica pura del flujo Payphone, sin imports para poder probarla con `npm test`.
// - C-02: el navegador no maneja tokens.
// - A-03: el navegador NO envía montos; la venta se crea antes y el backend calcula el cobro.

/** Datos guardados antes de redirigir a Payphone. */
export interface PagoPayphonePendiente {
    ventaId: number;
    clientTransactionId: string;
    /** Firma del carrito con el que se creó la venta, para reutilizarla si se reintenta. */
    firmaCarrito: string;
}

export const PAGO_PAYPHONE_PENDIENTE_KEY = 'payphone_pago_pendiente';

/** Resultados de la venta tras confirmar que permiten dar el pedido por registrado. */
export const RESULTADOS_VENTA_REGISTRADA = ['COMPLETADA', 'YA_PROCESADA', 'STOCK_PENDIENTE'];

/**
 * Firma estable del carrito (producto:cantidad ordenados). Si el carrito cambia, la venta
 * pendiente creada antes ya no corresponde y debe crearse otra.
 */
export const firmaCarrito = (cartItems: Array<{ producto?: any; cantidad?: number }> | null | undefined): string =>
    (Array.isArray(cartItems) ? cartItems : [])
        .map((item) => `${Number(item?.producto?.id) || 0}:${Number(item?.cantidad) || 0}`)
        .sort()
        .join('|');

/** Venta pendiente reutilizable para este carrito, o null si hay que crear una nueva. */
export const ventaPendienteReutilizable = (
    pendiente: PagoPayphonePendiente | null | undefined,
    firmaActual: string
): number | null => {
    if (!pendiente || !firmaActual) return null;
    const ventaId = Number(pendiente.ventaId);
    if (!Number.isInteger(ventaId) || ventaId <= 0) return null;
    return pendiente.firmaCarrito === firmaActual ? ventaId : null;
};

/** El backend genera clientTransactionId como "FD-{ventaId}-{aleatorio}". */
export const ventaIdDesdeClientTxId = (clientTxId: string | null | undefined): number | null => {
    const coincidencia = /^FD-(\d{1,18})-[A-Za-z0-9]{6,40}$/.exec(String(clientTxId || '').trim());
    if (!coincidencia) return null;
    const ventaId = Number(coincidencia[1]);
    return Number.isInteger(ventaId) && ventaId > 0 ? ventaId : null;
};

/** El pedido quedó registrado si Payphone aprobó (3) y la venta lo aceptó en el backend. */
export const pedidoRegistrado = (resultado: { statusCode?: number; resultadoVenta?: string } | null | undefined): boolean =>
    resultado?.statusCode === 3 && RESULTADOS_VENTA_REGISTRADA.includes(String(resultado?.resultadoVenta));
