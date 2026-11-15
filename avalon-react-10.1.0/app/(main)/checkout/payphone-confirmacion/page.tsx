'use client';

import React, { useEffect, useState, useRef, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Toast } from 'primereact/toast';
import { Button } from 'primereact/button';
import payphoneService from '../../../../services/payphoneService';
import cartService from '../../../../services/cartService';
import { pedidoRegistrado, ventaIdDesdeClientTxId } from '../../../../services/payphoneCheckout';

function PayphoneConfirmationContent() {
    const searchParams = useSearchParams();
    const router = useRouter();
    const toast = useRef<Toast>(null);

    const [loading, setLoading] = useState(true);
    const [status, setStatus] = useState<'pending' | 'success' | 'error' | 'cancelled'>('pending');
    const [paymentDetails, setPaymentDetails] = useState<any>(null);
    const [errorMessage, setErrorMessage] = useState<string>('');
    
    // Evitar doble ejecución en StrictMode
    const confirmAttempted = useRef(false);

    useEffect(() => {
        const id = Number(searchParams.get('id'));
        const clientTxId = searchParams.get('clientTransactionId');

        if (!Number.isInteger(id) || id <= 0 || !clientTxId) {
            setStatus('error');
            setErrorMessage('Faltan parámetros de transacción en la URL.');
            setLoading(false);
            return;
        }

        // Solo se confirman transacciones generadas por esta tienda (FD-{ventaId}-...)
        if (ventaIdDesdeClientTxId(clientTxId) === null) {
            setStatus('error');
            setErrorMessage('No se pudo validar la transacción de Payphone. Si se realizó un cobro, contacta a soporte.');
            setLoading(false);
            return;
        }

        if (confirmAttempted.current) return;
        confirmAttempted.current = true;

        const confirmar = async () => {
            try {
                // El backend verifica el cobro con Payphone y finaliza la venta creada antes
                // del pago (monto autoritativo, stock idempotente). Aquí no se crea ni completa nada.
                const result = await payphoneService.confirmarPago(id, clientTxId);
                setPaymentDetails(result);

                if (pedidoRegistrado(result)) {
                    setStatus('success');
                    cartService.clearCart();
                    payphoneService.limpiarPagoPendiente();
                    if (result.resultadoVenta === 'STOCK_PENDIENTE') {
                        setErrorMessage('Tu pago fue registrado. El emprendedor confirmará la disponibilidad del stock.');
                    }
                } else if (result.statusCode === 3) {
                    // Cobro aprobado pero la venta no pudo registrarlo (monto distinto, venta cancelada...)
                    setStatus('error');
                    setErrorMessage(
                        `Tu pago fue recibido pero requiere revisión. No vuelvas a pagar; contacta a soporte ` +
                            `indicando la transacción ${clientTxId}.`
                    );
                } else if (result.statusCode === 2) {
                    // Se conserva la venta pendiente para reintentar con el mismo carrito
                    setStatus('cancelled');
                } else {
                    setStatus('error');
                    setErrorMessage('El pago no fue aprobado. Código: ' + result.statusCode);
                }
            } catch (error: any) {
                // Falla cerrado: si no se pudo confirmar con Payphone NO se da el pago por exitoso
                // ni se vacía el carrito (antes un 503 marcaba éxito sin confirmación).
                setStatus('error');
                setErrorMessage(
                    `${error?.message || 'No se pudo confirmar el pago.'} No vuelvas a pagar: si se realizó un cobro, ` +
                        `contacta a soporte indicando la transacción ${clientTxId}.`
                );
            } finally {
                setLoading(false);
            }
        };

        confirmar();
    }, [searchParams]);

    const primaryColor = 'var(--primary-color)';

    return (
        <div className="min-h-screen pt-4 pb-6 flex justify-content-center align-items-center" style={{ backgroundColor: 'var(--surface-ground)' }}>
            <Toast ref={toast} />
            <div className="w-full max-w-3xl px-4">
                <Card className="shadow-4 border-round-xl" style={{ backgroundColor: 'var(--surface-card)' }}>
                    <div className="text-center p-4">
                        {loading && (
                            <div className="py-6">
                                <ProgressSpinner style={{ width: '80px', height: '80px' }} strokeWidth="4" />
                                <h2 className="mt-4 mb-2 text-2xl font-bold" style={{ color: primaryColor }}>Confirmando tu pago...</h2>
                                <p className="text-600">Por favor, no cierres ni actualices esta ventana. Estamos verificando tu transacción con Payphone.</p>
                            </div>
                        )}

                        {!loading && status === 'success' && (
                            <div className="py-4">
                                <div className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                                     style={{ backgroundColor: '#10b98120', color: '#10b981', width: '100px', height: '100px' }}>
                                    <i className="pi pi-check-circle text-6xl"></i>
                                </div>
                                <h2 className="text-3xl font-bold mb-3 text-900">¡Pago Exitoso!</h2>
                                <p className="text-600 mb-4 text-lg">Tu transacción ha sido procesada y confirmada correctamente.</p>
                                {paymentDetails?.numeroFactura && (
                                    <p className="text-700 mb-3">Pedido: <strong>{paymentDetails.numeroFactura}</strong></p>
                                )}
                                {errorMessage && <p className="text-orange-600 font-medium mb-4">{errorMessage}</p>}
                                
                                {paymentDetails?.transactionId && (
                                    <div className="p-4 border-round mb-5 inline-block text-left" style={{ backgroundColor: `${primaryColor}10` }}>
                                        <div className="mb-2">
                                            <i className="pi pi-tag mr-2" style={{ color: primaryColor }}></i>
                                            <strong>ID Transacción Payphone:</strong> <span className="ml-2 font-medium">{paymentDetails.transactionId}</span>
                                        </div>
                                        {paymentDetails.amount && (
                                            <div className="mb-2">
                                                <i className="pi pi-dollar mr-2" style={{ color: primaryColor }}></i>
                                                <strong>Monto Pagado:</strong> <span className="ml-2 font-medium">${(paymentDetails.amount / 100).toFixed(2)} {paymentDetails.currency}</span>
                                            </div>
                                        )}
                                        {paymentDetails.cardType && (
                                            <div className="mb-0">
                                                <i className="pi pi-credit-card mr-2" style={{ color: primaryColor }}></i>
                                                <strong>Método:</strong> <span className="ml-2 font-medium">{paymentDetails.cardType}</span>
                                            </div>
                                        )}
                                    </div>
                                )}

                                <div>
                                    <Button label="Volver al Inicio" icon="pi pi-home" className="p-button-lg" onClick={() => router.push('/')} />
                                </div>
                            </div>
                        )}

                        {!loading && status === 'cancelled' && (
                            <div className="py-4">
                                <div className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                                     style={{ backgroundColor: '#f59e0b20', color: '#f59e0b', width: '100px', height: '100px' }}>
                                    <i className="pi pi-info-circle text-6xl"></i>
                                </div>
                                <h2 className="text-3xl font-bold mb-3 text-900">Pago Cancelado</h2>
                                <p className="text-600 mb-5 text-lg">La transacción fue cancelada y no se han realizado cargos a tu tarjeta.</p>
                                <Button label="Volver al Carrito" icon="pi pi-shopping-cart" className="p-button-outlined p-button-lg" onClick={() => router.push('/cart')} />
                            </div>
                        )}

                        {!loading && status === 'error' && (
                            <div className="py-4">
                                <div className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                                     style={{ backgroundColor: '#ef444420', color: '#ef4444', width: '100px', height: '100px' }}>
                                    <i className="pi pi-times-circle text-6xl"></i>
                                </div>
                                <h2 className="text-3xl font-bold mb-3 text-900">Error en el Pago</h2>
                                <p className="text-600 mb-3 text-lg">Hubo un problema al verificar la transacción.</p>
                                {errorMessage && <p className="text-red-500 font-medium mb-5">{errorMessage}</p>}
                                <Button label="Volver al Checkout" icon="pi pi-arrow-left" className="p-button-outlined p-button-lg" onClick={() => router.push('/checkout')} />
                            </div>
                        )}
                    </div>
                </Card>
            </div>
        </div>
    );
}

export default function PayphoneConfirmationPage() {
    return (
        <Suspense fallback={
            <div className="min-h-screen flex justify-content-center align-items-center" style={{ backgroundColor: 'var(--surface-ground)' }}>
                <ProgressSpinner />
            </div>
        }>
            <PayphoneConfirmationContent />
        </Suspense>
    );
}
