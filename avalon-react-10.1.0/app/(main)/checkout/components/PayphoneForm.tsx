'use client';

import React, { useEffect, useState, useRef } from 'react';
import { Card } from 'primereact/card';
import { ProgressSpinner } from 'primereact/progressspinner';
import cartService from '../../../../services/cartService';
import { GATEWAY_BASE_URL } from '../../../../services/apiConfig';

// Para TypeScript, definimos globalmente el objeto payphone
declare global {
    interface Window {
        payphone: any;
    }
}

interface PayphoneFormProps {
    onDataChange: (data: any, isValid: boolean) => void;
    primaryColor: string;
    totalAmount: number;
    clienteData: any;
    cartItems: any[];
}

const PayphoneForm: React.FC<PayphoneFormProps> = ({
    onDataChange,
    primaryColor,
    totalAmount,
    clienteData,
    cartItems
}) => {
    const [scriptLoaded, setScriptLoaded] = useState(false);
    const [buttonRendered, setButtonRendered] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const buttonContainerRef = useRef<HTMLDivElement>(null);
    const renderAttempted = useRef(false);

    // Lógica para asignar los tokens del emprendedor correspondiente
    const [payphoneAppId, setPayphoneAppId] = useState('');
    const [payphoneToken, setPayphoneToken] = useState('');
    const [loadingTokens, setLoadingTokens] = useState(true);

    useEffect(() => {
        const fetchTokens = async () => {
            try {
                // Determinar el emprendedor_id (empresa.id). En tu seed coinciden.
                const emprendedorId = cartItems.length > 0 ? cartItems[0].producto?.empresa?.id : null;
                
                if (emprendedorId) {
                    const apiBase = GATEWAY_BASE_URL;
                    const res = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/payphone/${emprendedorId}`);
                    if (res.ok) {

                        const data = await res.json();
                        if (data.payphoneAppId && data.payphoneToken) {
                            setPayphoneAppId(data.payphoneAppId);
                            setPayphoneToken(data.payphoneToken);
                            console.log(`[Payphone] Tokens obtenidos para el emprendedor ${emprendedorId}`);
                        }
                    }
                }
            } catch (error) {
                console.error('[Payphone] Error obteniendo tokens dinámicos:', error);
            } finally {
                setLoadingTokens(false);
            }
        };

        fetchTokens();
    }, [cartItems]);

    // Notificar al checkout que el método payphone está activo
    useEffect(() => {
        onDataChange({ ready: true, method: 'payphone' }, false);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Respaldar datos del cliente en localStorage para la página de confirmación
    useEffect(() => {
        if (clienteData && Object.keys(clienteData).length > 0) {
            const saveLocal = async () => {
                const service = (await import('../../../../services/clientesService')).default;
                service.guardarDatosClienteLocal(clienteData);
                console.log('[PayphoneForm] Datos del cliente respaldados para confirmación');
            };
            saveLocal();
        }
    }, [clienteData]);

    // Cargar el script del SDK de Payphone
    useEffect(() => {
        if (loadingTokens) return;

        if (!payphoneAppId) {
            setErrorMsg('El App ID de Payphone no está configurado para este emprendedor. Contacta al administrador.');
            return;
        }

        const scriptId = 'payphone-script';
        const expectedSrc = `https://pay.payphonetodoesposible.com/api/button/js?appId=${payphoneAppId}`;
        const existingScript = document.getElementById(scriptId) as HTMLScriptElement;

        // Si el script ya existe, verificar si es el mismo App ID
        if (existingScript) {
            if (existingScript.src === expectedSrc) {
                console.log('[Payphone] El script ya existe con el App ID correcto.');
                if (window.payphone && typeof window.payphone.Button === 'function') {
                    setScriptLoaded(true);
                } else {
                    // Reintentar un poco más si el objeto no está listo
                    const checkInterval = setInterval(() => {
                        if (window.payphone && typeof window.payphone.Button === 'function') {
                            setScriptLoaded(true);
                            clearInterval(checkInterval);
                        }
                    }, 200);
                    setTimeout(() => clearInterval(checkInterval), 3000);
                    return () => clearInterval(checkInterval);
                }
                return;
            } else {
                // App ID diferente: Eliminar script viejo y limpiar objeto global para recargar
                console.log('[Payphone] App ID cambió. Recargando SDK...');
                existingScript.remove();
                if (window.payphone) delete (window as any).payphone;
                setScriptLoaded(false);
                setButtonRendered(false);
                renderAttempted.current = false;
            }
        }

        // Crear nuevo script
        const script = document.createElement('script');
        script.id = scriptId;
        script.src = expectedSrc;
        script.async = true;

        script.onload = () => {
            // Dar margen para que el SDK inicialice window.payphone.Button
            let attempts = 0;
            const checkInterval = setInterval(() => {
                attempts++;
                if (window.payphone && typeof window.payphone.Button === 'function') {
                    console.log('[Payphone] SDK inicializado correctamente después de', attempts, 'intentos');
                    setScriptLoaded(true);
                    clearInterval(checkInterval);
                } else if (attempts > 20) { // 4 segundos max
                    console.error('[Payphone] El SDK cargó pero window.payphone.Button no está disponible.');
                    setErrorMsg('Error al inicializar el SDK de Payphone (Button no encontrado).');
                    clearInterval(checkInterval);
                }
            }, 200);
        };

        script.onerror = () => {
            setErrorMsg('No se pudo cargar el SDK de Payphone. Verifica tu conexión o el App ID.');
        };

        document.body.appendChild(script);
    }, [payphoneAppId, loadingTokens]);

    const cartItemsRef = useRef(cartItems);
    useEffect(() => {
        cartItemsRef.current = cartItems;
    }, [cartItems]);

    // Renderizar el botón cuando el script esté listo
    useEffect(() => {
        if (!scriptLoaded || buttonRendered || renderAttempted.current) return;
        if (!buttonContainerRef.current) return;

        if (!window.payphone) {
            setErrorMsg('El SDK de Payphone cargó pero no inicializó. Recarga la página.');
            return;
        }

        if (!payphoneToken) {
            setErrorMsg('El Token de Payphone no está configurado para este emprendedor. Contacta al administrador.');
            return;
        }

        renderAttempted.current = true;

        try {
            const uniqueTxId = `ORD-${Date.now()}`;
            
            // Usar el ref para obtener el emprendedorId más reciente en las funciones de callback
            const getEmprendedorId = () => {
                const items = cartItemsRef.current;
                return items.length > 0 ? items[0].producto?.empresa?.id : null;
            };

            const responseUrl = `${window.location.origin}/checkout/payphone-confirmacion?emprendedorId=${getEmprendedorId()}`;

            // Obtener el resumen actualizado del carrito para desglosar impuestos y envío
            const summary = cartService.getCartSummary();
            const amountInCents = Math.round(summary.total * 100);
            const amountWithTaxInCents = Math.round(summary.subtotal * 100);
            const taxInCents = Math.round(summary.impuestos * 100);
            const serviceInCents = Math.round(summary.envio * 100);

            console.log('[Payphone] Inicializando botón con desglose:', { 
                total: amountInCents, 
                base: amountWithTaxInCents, 
                iva: taxInCents, 
                envio: serviceInCents 
            });

            window.payphone.Button({
                token: payphoneToken,
                btnHorizontal: true,
                btnCard: true,
                createOrder: function(actions: any) {
                    const currentId = getEmprendedorId();
                    return actions.prepare({
                        amount: amountInCents,
                        amountWithoutTax: 0,
                        amountWithTax: amountWithTaxInCents,
                        tax: taxInCents,
                        service: serviceInCents,
                        tip: 0,
                        currency: 'USD',
                        clientTransactionId: uniqueTxId,
                        lang: 'es',
                        responseUrl: `${window.location.origin}/checkout/payphone-confirmacion?emprendedorId=${currentId}`,
                    });
                },
                onComplete: function(model: any) {
                    if (model && model.id && model.clientTxId) {
                        const currentId = getEmprendedorId();
                        console.log('[Payphone] Pago completado, redireccionando con emprendedorId:', currentId);
                        window.location.href = `/checkout/payphone-confirmacion?id=${model.id}&clientTransactionId=${model.clientTxId}&emprendedorId=${currentId}`;
                    }
                },
                onError: function(err: any) {
                    console.error('[Payphone] Error en el pago:', err);
                }
            }).render('#pp-button');

            setButtonRendered(true);
        } catch (error: any) {
            console.error('[Payphone] Error al renderizar el botón:', error);
            setErrorMsg(`Error al inicializar Payphone: ${error?.message || error}`);
            renderAttempted.current = false;
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [scriptLoaded, buttonRendered]);

    return (
        <Card className="mb-4">
            <div className="p-4 text-center">
                <i className="pi pi-mobile text-6xl mb-3" style={{ color: '#f97316' }}></i>
                <h3 className="text-xl font-semibold mb-2">Pago con Payphone</h3>
                <p className="text-600 mb-4">
                    Selecciona tu forma de pago para continuar con la compra de forma segura.
                </p>

                {/* Estado: Cargando */}
                {!scriptLoaded && !errorMsg && (
                    <div className="flex align-items-center justify-content-center flex-column py-3">
                        <ProgressSpinner style={{ width: '35px', height: '35px' }} strokeWidth="4" />
                        <small className="mt-2 text-600">Cargando pasarela segura de Payphone...</small>
                    </div>
                )}

                {/* Estado: Error */}
                {errorMsg && (
                    <div className="p-3 border-round text-sm" style={{ background: '#fff3f3', border: '1px solid #f5c6cb', color: '#721c24' }}>
                        <i className="pi pi-exclamation-triangle mr-2"></i>
                        {errorMsg}
                    </div>
                )}

                {/* Contenedor oficial del SDK — siempre en el DOM */}
                <div
                    id="pp-button"
                    ref={buttonContainerRef}
                    className="flex justify-content-center w-full mt-2"
                    style={{ minHeight: scriptLoaded && !errorMsg ? '60px' : '0px' }}
                ></div>

                <div className="mt-4 text-sm text-500">
                    <i className="pi pi-lock mr-1"></i>
                    Tus datos de pago están encriptados y protegidos por Payphone.
                </div>
            </div>
        </Card>
    );
};

export default PayphoneForm;
