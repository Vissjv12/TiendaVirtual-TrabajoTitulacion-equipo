'use client';

import React, { useEffect, useState } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { ProgressSpinner } from 'primereact/progressspinner';
import cartService from '../../../../services/cartService';
import payphoneService from '../../../../services/payphoneService';
import { firmaCarrito, ventaPendienteReutilizable } from '../../../../services/payphoneCheckout';

interface PayphoneFormProps {
    onDataChange: (data: any, isValid: boolean) => void;
    primaryColor: string;
    totalAmount: number;
    clienteData: any;
    cartItems: any[];
}

type Destino = 'payphone' | 'tarjeta';

/**
 * Pago con Payphone (hallazgo C-02).
 * El token del comercio nunca llega al navegador (C-02). Primero se crea la venta y el backend
 * calcula el cobro a partir de ella (A-03); aquí solo se redirige al comprador a Payphone.
 */
const PayphoneForm: React.FC<PayphoneFormProps> = ({ onDataChange, totalAmount, clienteData, cartItems }) => {
    const [verificando, setVerificando] = useState(true);
    const [empresaId, setEmpresaId] = useState<number | null>(null);
    const [errorMsg, setErrorMsg] = useState('');
    const [redirigiendo, setRedirigiendo] = useState<Destino | null>(null);

    // El botón "Pagar" del checkout no aplica: el pago se inicia con los botones de este formulario
    useEffect(() => {
        onDataChange({ ready: true, method: 'payphone' }, false);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Disponibilidad: todo el carrito de UNA empresa con Payphone (falla cerrado)
    useEffect(() => {
        let cancelado = false;
        setVerificando(true);
        setErrorMsg('');
        payphoneService
            .verificarDisponibilidad(cartItems)
            .then((r) => {
                if (cancelado) return;
                if (r.disponible && r.empresaId) {
                    setEmpresaId(r.empresaId);
                } else {
                    setEmpresaId(null);
                    setErrorMsg(r.motivo || 'Payphone no está disponible para este carrito');
                }
            })
            .catch(() => {
                if (!cancelado) setErrorMsg('No se pudo verificar la configuración de Payphone');
            })
            .finally(() => {
                if (!cancelado) setVerificando(false);
            });
        return () => {
            cancelado = true;
        };
    }, [cartItems]);

    // Respaldar datos del cliente para crear la venta al volver de Payphone
    useEffect(() => {
        if (clienteData && Object.keys(clienteData).length > 0) {
            import('../../../../services/clientesService').then(({ default: service }) => {
                service.guardarDatosClienteLocal(clienteData);
            });
        }
    }, [clienteData]);

    /** Crea la venta PENDIENTE en el backend (precios, envío e IVA calculados allí) (A-03). */
    const crearVenta = async (): Promise<number> => {
        const resultado = await cartService.processCheckout(clienteData, 'TARJETA');
        if (!resultado.success || !resultado.data?.ventaId) {
            throw new Error(resultado.message || 'No se pudo registrar la orden');
        }
        return resultado.data.ventaId;
    };

    const pagar = async (destino: Destino) => {
        if (!empresaId) return;
        setErrorMsg('');
        setRedirigiendo(destino);
        try {
            const firma = firmaCarrito(cartService.getCart());
            // Reintento con el mismo carrito: reutilizar la venta pendiente en lugar de crear otra
            let ventaId = ventaPendienteReutilizable(payphoneService.leerPagoPendiente(), firma);
            let preparado;
            if (ventaId) {
                try {
                    preparado = await payphoneService.preparar(ventaId);
                } catch {
                    // La venta anterior ya no admite cobro (pagada, cancelada o ajena): crear otra
                    payphoneService.limpiarPagoPendiente();
                    ventaId = null;
                }
            }
            if (!ventaId || !preparado) {
                ventaId = await crearVenta();
                preparado = await payphoneService.preparar(ventaId);
            }
            const enlace = destino === 'tarjeta'
                ? preparado.payWithCard || preparado.payWithPayPhone
                : preparado.payWithPayPhone || preparado.payWithCard;
            if (!enlace) {
                throw new Error('Payphone no devolvió un enlace de pago');
            }
            payphoneService.guardarPagoPendiente({
                ventaId,
                clientTransactionId: preparado.clientTransactionId,
                firmaCarrito: firma
            });
            window.location.assign(enlace);
        } catch (error: any) {
            setErrorMsg(error?.message || 'No se pudo iniciar el pago con Payphone');
            setRedirigiendo(null);
        }
    };

    return (
        <Card className="mb-4">
            <div className="p-4 text-center">
                <i className="pi pi-mobile text-6xl mb-3" style={{ color: '#f97316' }}></i>
                <h3 className="text-xl font-semibold mb-2">Pago con Payphone</h3>
                <p className="text-600 mb-4">
                    Serás redirigido a la página segura de Payphone para completar el pago de{' '}
                    <strong>{cartService.formatPrice(totalAmount || 0)}</strong>.
                </p>

                {verificando && (
                    <div className="flex align-items-center justify-content-center flex-column py-3">
                        <ProgressSpinner style={{ width: '35px', height: '35px' }} strokeWidth="4" />
                        <small className="mt-2 text-600">Verificando Payphone...</small>
                    </div>
                )}

                {errorMsg && (
                    <div className="p-3 border-round text-sm mb-3" style={{ background: '#fff3f3', border: '1px solid #f5c6cb', color: '#721c24' }}>
                        <i className="pi pi-exclamation-triangle mr-2"></i>
                        {errorMsg}
                    </div>
                )}

                {!verificando && empresaId && (
                    <div className="flex flex-column md:flex-row gap-2 justify-content-center">
                        <Button
                            label="Pagar con app Payphone"
                            icon="pi pi-mobile"
                            onClick={() => pagar('payphone')}
                            loading={redirigiendo === 'payphone'}
                            disabled={redirigiendo !== null}
                            style={{ backgroundColor: '#f97316', borderColor: '#f97316' }}
                        />
                        <Button
                            label="Pagar con tarjeta"
                            icon="pi pi-credit-card"
                            className="p-button-outlined"
                            onClick={() => pagar('tarjeta')}
                            loading={redirigiendo === 'tarjeta'}
                            disabled={redirigiendo !== null}
                        />
                    </div>
                )}

                <div className="mt-4 text-sm text-500">
                    <i className="pi pi-lock mr-1"></i>
                    Tus datos de pago se ingresan directamente en Payphone.
                </div>
            </div>
        </Card>
    );
};

export default PayphoneForm;
