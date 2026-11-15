'use client';

import React, { useState, useEffect, useContext, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Steps } from 'primereact/steps';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Dialog } from 'primereact/dialog';
import { Divider } from 'primereact/divider';
import { Badge } from 'primereact/badge';
import { LayoutContext } from '../../../layout/context/layoutcontext';
import cartService, { CartSummary } from '../../../services/cartService';
import authService from '../../../services/authService';
import clientesService from '../../../services/clientesService';
import CheckoutClienteForm from './components/CheckoutClienteForm';
import PaymentMethodSelector from './components/PaymentMethodSelector';
import TransferenciaBancariaForm from './components/TransferenciaBancariaForm';
import DeunaTransferenciaForm from './components/DeunaTransferenciaForm';
import { verificarDisponibilidadDeuna, verificarDisponibilidadTransferencia } from '../../../services/pagoService';
import StripeCheckoutForm from './components/StripeCheckoutForm';
import { crearPaymentIntent, confirmarPago } from '../../../services/stripeService';

const CheckoutPage: React.FC = () => {
    const { layoutConfig } = useContext(LayoutContext);
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados principales
    const [mounted, setMounted] = useState(false);
    const [currentStep, setCurrentStep] = useState(0);
    const [cartSummary, setCartSummary] = useState<CartSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [processing, setProcessing] = useState(false);
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    // Estados de cliente
    const [clienteData, setClienteData] = useState<any>(null);
    const [clienteFormValid, setClienteFormValid] = useState(false);

    // Estados de pago
    const [paymentProcessing, setPaymentProcessing] = useState(false);
    const [paymentResult, setPaymentResult] = useState<any>(null);
    const [showSuccessDialog, setShowSuccessDialog] = useState(false);
    const [orderNumber, setOrderNumber] = useState<string>('');

    // Estados para métodos de pago
    const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<string>('transferencia');
    const [paymentFormValid, setPaymentFormValid] = useState(false);
    const [paymentFormData, setPaymentFormData] = useState<any>(null);

    // Estados para Stripe
    const [stripeClientSecret, setStripeClientSecret] = useState<string>('');
    const [stripePaymentIntentId, setStripePaymentIntentId] = useState<string>('');
    const [showStripeForm, setShowStripeForm] = useState(false);

    const primaryColor = 'var(--primary-color)';

    // Transferencia y Deuna exigen que todo el carrito sea de UNA empresa identificable con su
    // configuración propia; si no, se deshabilitan sin consultar datos de pago (hallazgo C-01)
    const [metodosNoDisponibles, setMetodosNoDisponibles] = useState<Record<string, string>>({});

    useEffect(() => {
        if (!cartSummary || cartSummary.items.length === 0) return;
        let cancelado = false;
        const motivoDe = async (verificar: () => Promise<{ disponible: boolean; motivo?: string }>, motivoError: string) => {
            try {
                const resultado = await verificar();
                return resultado.disponible ? undefined : resultado.motivo || motivoError;
            } catch {
                return motivoError;
            }
        };
        Promise.all([
            motivoDe(() => verificarDisponibilidadTransferencia(cartSummary.items), 'No se pudo verificar la cuenta bancaria'),
            motivoDe(() => verificarDisponibilidadDeuna(cartSummary.items), 'No se pudo verificar la configuración de Deuna')
        ]).then(([transferencia, deuna]) => {
            if (cancelado) return;
            const bloqueados: Record<string, string> = {};
            if (transferencia) bloqueados.transferencia = transferencia;
            if (deuna) bloqueados.deuna = deuna;
            setMetodosNoDisponibles(bloqueados);
        });
        return () => {
            cancelado = true;
        };
    }, [cartSummary]);

    useEffect(() => {
        if (!metodosNoDisponibles[selectedPaymentMethod]) return;
        const alternativa = ['transferencia', 'deuna'].find((metodo) => !metodosNoDisponibles[metodo]);
        if (alternativa) {
            setSelectedPaymentMethod(alternativa);
        }
        setPaymentFormValid(false);
        setPaymentFormData(null);
    }, [metodosNoDisponibles, selectedPaymentMethod]);

    // Pasos del checkout con íconos
    const steps = [
        {
            label: 'Datos de Envío',
            icon: 'pi pi-user'
        },
        {
            label: 'Revisión',
            icon: 'pi pi-check-square'
        },
        {
            label: 'Pago',
            icon: 'pi pi-credit-card'
        },
        {
            label: 'Confirmación',
            icon: 'pi pi-check-circle'
        }
    ];

    // ✅ MONTAJE
    useEffect(() => {
        setMounted(true);
        window.scrollTo(0, 0);
    }, []);

    // ✅ VERIFICAR AUTENTICACIÓN
    useEffect(() => {
        if (!mounted) return;

        const checkAuth = () => {
            const authStatus = authService.isAuthenticated();
            setIsAuthenticated(authStatus);

            if (!authStatus) {
                toast.current?.show({
                    severity: 'warn',
                    summary: 'Acceso denegado',
                    detail: 'Debes iniciar sesión para acceder al checkout',
                    life: 3000
                });
                router.push('/auth/login2');
                return;
            }
        };

        checkAuth();
    }, [mounted, router]);

    // ✅ CARGAR CARRITO
    useEffect(() => {
        if (!mounted || !isAuthenticated) return;
        loadCartSummary();
    }, [mounted, isAuthenticated]);

    const loadCartSummary = () => {
        setLoading(true);
        try {
            const summary = cartService.getCartSummary();

            if (summary.totalItems === 0) {
                toast.current?.show({
                    severity: 'info',
                    summary: 'Carrito vacío',
                    detail: 'Tu carrito está vacío. Agrega productos antes de proceder.',
                    life: 3000
                });
                router.push('/products');
                return;
            }

            setCartSummary(summary);
        } catch (error) {
            console.error('Error cargando carrito:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando el carrito',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    // ✅ PROCESAR CHECKOUT CON MÉTODO DE PAGO
    const handleProcessCheckout = async () => {
        if (!cartSummary || !clienteData || !selectedPaymentMethod || !paymentFormData) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Datos incompletos',
                detail: 'Por favor completa todos los datos requeridos',
                life: 3000
            });
            return;
        }

        setProcessing(true);
        setPaymentProcessing(true);
        setCurrentStep(2); // Mover a paso de pago

        try {
            console.log('🚀 Iniciando checkout con método:', selectedPaymentMethod);
            console.log('📄 Datos del pago:', paymentFormData);

            // ✅ PASO 1: Validar carrito
            console.log('⚡ Validando carrito...');
            const validationResult = await cartService.validateCartForCheckout();

            if (!validationResult.success) {
                throw new Error(validationResult.message);
            }

            // ✅ PASO 2: Procesar usando el método de cartService con método de pago
            console.log('📝 Procesando checkout...');
            const metodoPago = selectedPaymentMethod === 'tarjeta' ? 'TARJETA' : selectedPaymentMethod === 'transferencia' ? 'TRANSFERENCIA' : 'DEUNA';
            const comprobanteUrl = selectedPaymentMethod === 'transferencia' ? paymentFormData.comprobanteUrl : undefined;
            const checkoutResult = await cartService.processCheckout(clienteData, metodoPago, comprobanteUrl);

            if (!checkoutResult.success || !checkoutResult.data) {
                throw new Error(checkoutResult.message || 'Error creando la orden');
            }

            const finalOrderNumber = checkoutResult.data.orderId;
            const ventaId = checkoutResult.data.ventaId;
            setOrderNumber(finalOrderNumber);
            console.log('✅ Orden creada:', finalOrderNumber, 'Venta ID:', ventaId);

            // ✅ PASO 3: Procesar según el método de pago
            if (selectedPaymentMethod === 'tarjeta') {
                // 💳 TARJETA: Crear PaymentIntent de Stripe
                console.log('💳 Creando PaymentIntent de Stripe...');

                try {
                    const userData = authService.getUserInfo();

                    const paymentIntentResponse = await crearPaymentIntent({
                        ventaId: ventaId || 0,
                        clienteId: userData?.id || 0,
                        monto: cartSummary.total
                    });

                    console.log('✅ PaymentIntent creado:', paymentIntentResponse.paymentIntentId);

                    // Guardar datos de Stripe y mostrar formulario
                    setStripeClientSecret(paymentIntentResponse.clientSecret);
                    setStripePaymentIntentId(paymentIntentResponse.paymentIntentId);
                    setShowStripeForm(true);
                    setPaymentProcessing(false); // Permitir interacción con formulario de Stripe
                } catch (error: any) {
                    console.error('❌ Error creando PaymentIntent:', error);
                    throw new Error('Error al inicializar el pago con Stripe');
                }
            } else {
                // 🏦 TRANSFERENCIA o 📱 DEUNA: Pago pendiente de confirmación
                console.log('📝 Orden creada con pago pendiente para método:', selectedPaymentMethod);

                const metodoPagoNombre = selectedPaymentMethod === 'transferencia' ? 'Transferencia Bancaria' : 'Deuna';

                console.log('🕐 Esperando 1 segundo antes de mostrar confirmación...');
                // Simular guardado de datos (1 segundo)
                await new Promise((resolve) => setTimeout(resolve, 1000));

                console.log('✅ Mostrando confirmación de pago pendiente');

                // Ir a confirmación con mensaje especial de pago pendiente
                setPaymentResult({
                    pending: true,
                    method: selectedPaymentMethod,
                    methodName: metodoPagoNombre
                });

                // Limpiar carrito una vez registrada la orden
                cartService.clearCart();

                // Mostrar mensaje de pago pendiente
                setCurrentStep(3);
                setShowSuccessDialog(true);

                toast.current?.show({
                    severity: 'info',
                    summary: 'Orden registrada',
                    detail: `Tu orden ${finalOrderNumber} ha sido registrada. El emprendedor procesará tu pago y serás notificado cuando se complete.`,
                    life: 8000
                });

                console.log('🎉 Flujo de pago pendiente completado');
            }
        } catch (error: any) {
            console.error('❌ Error en checkout:', error);

            // Volver al paso de revisión
            setCurrentStep(1);

            toast.current?.show({
                severity: 'error',
                summary: 'Error en el proceso',
                detail: error.message || 'Hubo un problema procesando tu orden. Intenta nuevamente.',
                life: 8000
            });
        } finally {
            setProcessing(false);
            setPaymentProcessing(false);
        }
    };

    // 💳 MANEJAR PAGO EXITOSO DE STRIPE
    const handleStripeSuccess = async (paymentIntentId: string) => {
        console.log('✅ Pago exitoso en Stripe:', paymentIntentId);
        setPaymentProcessing(true);

        try {
            const userData = authService.getUserInfo();
            const checkoutData = cartService.getCheckoutData();
            const ventaId = checkoutData?.ventaId;

            if (!ventaId) {
                throw new Error('No se encontró el ID de la venta');
            }

            // Confirmar pago en el backend
            await confirmarPago({
                paymentIntentId: paymentIntentId,
                ventaId: ventaId
            });

            console.log('✅ Pago confirmado en el backend');

            // Limpiar carrito
            cartService.clearCart();

            // Mostrar éxito
            setCurrentStep(3);
            setShowSuccessDialog(true);
            setShowStripeForm(false);

            toast.current?.show({
                severity: 'success',
                summary: '¡Pago exitoso!',
                detail: `Tu pago ha sido procesado correctamente. Orden: ${orderNumber}`,
                life: 5000
            });
        } catch (error: any) {
            console.error('❌ Error confirmando pago:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'El pago fue procesado pero hubo un error al confirmar la orden. Contacta con soporte.',
                life: 8000
            });
        } finally {
            setPaymentProcessing(false);
        }
    };

    // ❌ MANEJAR ERROR DE STRIPE
    const handleStripeError = (error: string) => {
        console.error('❌ Error en Stripe:', error);
        setShowStripeForm(false);
        setCurrentStep(1);

        toast.current?.show({
            severity: 'error',
            summary: 'Error en el pago',
            detail: error || 'Hubo un problema procesando tu pago. Intenta nuevamente.',
            life: 8000
        });

        setPaymentProcessing(false);
    };

    const handleSuccessDialogClose = () => {
        setShowSuccessDialog(false);
        clientesService.limpiarDatosClienteLocal();
        router.push('/');
    };

    const renderClienteForm = () => (
        <Card
            className="mb-4 shadow-3"
            style={{
                backgroundColor: 'var(--surface-card)',
                border: `1px solid ${primaryColor}20`
            }}
        >
            <div className="p-4">
                <div className="flex align-items-center mb-4">
                    <div
                        className="flex align-items-center justify-content-center border-circle mr-3"
                        style={{
                            backgroundColor: `${primaryColor}15`,
                            color: primaryColor,
                            width: '40px',
                            height: '40px'
                        }}
                    >
                        <i className="pi pi-user text-lg"></i>
                    </div>
                    <h3 className="text-xl font-bold m-0" style={{ color: primaryColor }}>
                        Información de Envío
                    </h3>
                </div>
                <CheckoutClienteForm
                    onClienteData={(data) => {
                        setClienteData(data);
                    }}
                    onValidated={(isValid) => {
                        setClienteFormValid(isValid);
                    }}
                />
            </div>
        </Card>
    );

    const renderCartReview = () => (
        <Card
            className="mb-4 shadow-3"
            style={{
                backgroundColor: 'var(--surface-card)',
                border: `1px solid ${primaryColor}20`
            }}
        >
            <div className="p-4">
                <div className="flex align-items-center justify-content-between mb-4">
                    <div className="flex align-items-center">
                        <div
                            className="flex align-items-center justify-content-center border-circle mr-3"
                            style={{
                                backgroundColor: `${primaryColor}15`,
                                color: primaryColor,
                                width: '40px',
                                height: '40px'
                            }}
                        >
                            <i className="pi pi-shopping-cart text-lg"></i>
                        </div>
                        <h3 className="text-xl font-bold m-0" style={{ color: primaryColor }}>
                            Resumen de tu Pedido
                        </h3>
                    </div>
                    <Badge value={cartSummary?.totalItems} severity="success" style={{ backgroundColor: primaryColor }} />
                </div>

                {/* Datos del cliente */}
                {clienteData && (
                    <div className="mb-4 p-4 border-round shadow-1" style={{ backgroundColor: `${primaryColor}08` }}>
                        <div className="flex align-items-center mb-3">
                            <i className="pi pi-map-marker mr-2" style={{ color: primaryColor }}></i>
                            <h4 className="font-bold m-0" style={{ color: primaryColor }}>
                                Datos de envío
                            </h4>
                        </div>
                        <div className="grid text-sm">
                            <div className="col-12 md:col-6">
                                <div className="mb-2">
                                    <i className="pi pi-user mr-2 text-600"></i>
                                    <strong>Nombre:</strong> {clienteData.nombre} {clienteData.apellido}
                                </div>
                                <div className="mb-2">
                                    <i className="pi pi-id-card mr-2 text-600"></i>
                                    <strong>Cédula:</strong> {clienteData.documento}
                                </div>
                            </div>
                            <div className="col-12 md:col-6">
                                <div className="mb-2">
                                    <i className="pi pi-envelope mr-2 text-600"></i>
                                    <strong>Email:</strong> {clienteData.email}
                                </div>
                                <div className="mb-2">
                                    <i className="pi pi-phone mr-2 text-600"></i>
                                    <strong>Teléfono:</strong> {clienteData.telefono}
                                </div>
                            </div>
                            <div className="col-12">
                                <div className="mb-0">
                                    <i className="pi pi-home mr-2 text-600"></i>
                                    <strong>Dirección:</strong> {clienteData.direccion}
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                <Divider />

                <div className="mb-4">
                    <h4 className="font-bold mb-3 text-900">
                        <i className="pi pi-list mr-2" style={{ color: primaryColor }}></i>
                        Productos en tu pedido
                    </h4>
                    {cartSummary?.items.map((item, index) => (
                        <div key={item.id} className="flex justify-content-between align-items-center py-3 px-2 border-round hover:surface-hover">
                            <div className="flex align-items-center">
                                <img src={item.producto.imagen || '/demo/images/product/product-placeholder.svg'} alt={item.producto.nombre} className="w-4rem h-4rem object-cover border-round shadow-2 mr-3" />
                                <div>
                                    <div className="font-bold text-900 mb-1">{item.producto.nombre}</div>
                                    <div className="text-600 text-sm mb-1">
                                        <i className="pi pi-tag mr-1"></i>
                                        Cantidad: {item.cantidad}
                                    </div>
                                    <div className="flex gap-2">
                                        <span
                                            className="px-2 py-1 border-round text-xs font-medium"
                                            style={{
                                                backgroundColor: `${primaryColor}15`,
                                                color: primaryColor
                                            }}
                                        >
                                            {item.producto.categoria.nombre}
                                        </span>
                                    </div>
                                </div>
                            </div>
                            <div className="text-right">
                                <div className="font-bold text-lg" style={{ color: primaryColor }}>
                                    {cartService.formatPrice(item.subtotal)}
                                </div>
                                <div className="text-600 text-sm">{cartService.formatPrice(item.producto.precio)} c/u</div>
                            </div>
                        </div>
                    ))}
                </div>

                <div className="border-top-1 surface-border pt-4">
                    <div className="grid">
                        <div className="col-12 md:col-6">
                            <div className="flex justify-content-between mb-2">
                                <span className="flex align-items-center">
                                    <i className="pi pi-calculator mr-2 text-600"></i>
                                    Subtotal:
                                </span>
                                <span className="font-bold">{cartService.formatPrice(cartSummary?.subtotal || 0)}</span>
                            </div>
                            <div className="flex justify-content-between mb-2">
                                <span className="flex align-items-center">
                                    <i className="pi pi-percentage mr-2 text-600"></i>
                                    Impuestos (15%):
                                </span>
                                <span className="font-bold">{cartService.formatPrice(cartSummary?.impuestos || 0)}</span>
                            </div>
                        </div>
                        <div className="col-12 md:col-6">
                            <div className="flex justify-content-between mb-3">
                                <span className="flex align-items-center">
                                    <i className="pi pi-send mr-2 text-600"></i>
                                    Envío:
                                </span>
                                <span className="font-bold">
                                    {cartSummary?.envio === 0 ? (
                                        <span style={{ color: primaryColor }}>
                                            <i className="pi pi-check mr-1"></i>
                                            ¡Gratis!
                                        </span>
                                    ) : (
                                        cartService.formatPrice(cartSummary?.envio || 0)
                                    )}
                                </span>
                            </div>
                        </div>
                        <div className="col-12">
                            <Divider />
                            <div className="flex justify-content-between text-xl">
                                <span className="font-bold flex align-items-center">
                                    <i className="pi pi-wallet mr-2" style={{ color: primaryColor }}></i>
                                    Total:
                                </span>
                                <span className="font-bold text-2xl" style={{ color: primaryColor }}>
                                    {cartService.formatPrice(cartSummary?.total || 0)}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Card>
    );

    const renderPaymentSection = () => (
        <div>
            {paymentProcessing ? (
                <Card
                    className="mb-4 shadow-3"
                    style={{
                        backgroundColor: 'var(--surface-card)',
                        border: `1px solid ${primaryColor}20`
                    }}
                >
                    <div className="text-center py-6">
                        <div
                            className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                            style={{
                                backgroundColor: `${primaryColor}15`,
                                width: '80px',
                                height: '80px'
                            }}
                        >
                            <i className="pi pi-spin pi-spinner text-4xl" style={{ color: primaryColor }}></i>
                        </div>
                        <h3 className="text-xl font-bold mb-3" style={{ color: primaryColor }}>
                            Procesando pago...
                        </h3>
                        <p className="text-600 text-sm">Por favor espera mientras procesamos tu pago de forma segura</p>
                    </div>
                </Card>
            ) : (
                <>
                    <PaymentMethodSelector
                        selectedMethod={(selectedPaymentMethod || 'transferencia') as any}
                        onMethodChange={(method) => {
                            console.log('Método seleccionado:', method);
                            setSelectedPaymentMethod(method);
                            setPaymentFormValid(false); // Resetear validación al cambiar método
                            setPaymentFormData(null);
                            setShowStripeForm(false);
                        }}
                        onPaymentDataChange={(data) => {
                            console.log('Datos del pago:', data);
                            setPaymentFormData(data);
                        }}
                        primaryColor={primaryColor}
                        disabledMethods={metodosNoDisponibles}
                    />
                </>
            )}

            {/* Formularios según el método seleccionado */}
            {!paymentProcessing && selectedPaymentMethod === 'transferencia' && (
                <TransferenciaBancariaForm
                    onDataChange={(data, isValid) => {
                        setPaymentFormData(data);
                        setPaymentFormValid(isValid);
                    }}
                    primaryColor={primaryColor}
                />
            )}

            {!paymentProcessing && selectedPaymentMethod === 'tarjeta' && !showStripeForm && (
                <Card className="mb-4">
                    <div className="p-4 text-center">
                        <i className="pi pi-credit-card text-6xl mb-3" style={{ color: primaryColor }}></i>
                        <h3 className="text-xl font-semibold mb-2">Pago con Tarjeta</h3>
                        <p className="text-600 mb-3">Procesa tu pago de forma segura con Stripe</p>
                        <Button
                            label="Continuar al Pago"
                            icon="pi pi-arrow-right"
                            onClick={() => {
                                setPaymentFormValid(true);
                                setPaymentFormData({ ready: true });
                            }}
                            className="p-button-lg"
                            disabled={!clienteFormValid}
                        />
                        {!clienteFormValid && <small className="text-red-500 block mt-2">Completa primero los datos de envío</small>}
                    </div>
                </Card>
            )}

            {/* Formulario de Stripe cuando hay clientSecret */}
            {showStripeForm && stripeClientSecret && <StripeCheckoutForm clientSecret={stripeClientSecret} onSuccess={handleStripeSuccess} onError={handleStripeError} monto={cartSummary?.total || 0} />}

            {!paymentProcessing && selectedPaymentMethod === 'deuna' && (
                <DeunaTransferenciaForm
                    onDataChange={(data, isValid) => {
                        setPaymentFormData(data);
                        setPaymentFormValid(isValid);
                    }}
                    primaryColor={primaryColor}
                />
            )}
        </div>
    );

    const getNextButtonLabel = () => {
        switch (currentStep) {
            case 0:
                return 'Continuar a revisión';
            case 1:
                return 'Continuar al pago';
            case 2:
                return `Pagar ${cartService.formatPrice(cartSummary?.total || 0)}`;
            default:
                return 'Continuar';
        }
    };

    const canProceedToNext = () => {
        const result = (() => {
            switch (currentStep) {
                case 0:
                    return clienteFormValid;
                case 1:
                    return true;
                case 2: {
                    const canProceed = selectedPaymentMethod && paymentFormValid;
                    console.log('🔍 Validación paso 2:', {
                        selectedPaymentMethod,
                        paymentFormValid,
                        paymentFormData,
                        canProceed
                    });
                    return canProceed;
                }
                default:
                    return false;
            }
        })();
        console.log(`✅ canProceedToNext (step ${currentStep}):`, result);
        return result;
    };

    const handleNext = () => {
        if (currentStep === 2) {
            // En el paso de pago, procesar checkout
            handleProcessCheckout();
        } else if (currentStep === 1) {
            // En la revisión, solo avanzar al paso de pago
            setCurrentStep(2);
        } else {
            setCurrentStep(currentStep + 1);
        }
    };

    return (
        <>
            <Toast ref={toast} />

            {!mounted ? (
                <div className="min-h-screen flex align-items-center justify-content-center">
                    <ProgressSpinner style={{ width: '50px', height: '50px' }} />
                </div>
            ) : (
                <div className="min-h-screen pt-4 pb-6" style={{ backgroundColor: 'var(--surface-ground)' }}>
                    <div className="container mx-auto px-4 max-w-6xl">
                        {/* Header mejorado */}
                        <div className="mb-6">
                            <div className="flex align-items-center mb-4">
                                <div
                                    className="flex align-items-center justify-content-center border-circle mr-4"
                                    style={{
                                        backgroundColor: `${primaryColor}15`,
                                        color: primaryColor,
                                        width: '60px',
                                        height: '60px'
                                    }}
                                >
                                    <i className="pi pi-shopping-cart text-2xl"></i>
                                </div>
                                <div>
                                    <h1 className="text-4xl font-bold mb-2" style={{ color: primaryColor }}>
                                        Finalizar Compra
                                    </h1>
                                    <p className="text-600 m-0">
                                        <i className="pi pi-shield mr-2"></i>
                                        Proceso seguro y protegido
                                    </p>
                                </div>
                            </div>

                            {/* Pasos */}
                            <Steps model={steps} activeIndex={currentStep} className="mb-4" />
                        </div>

                        {loading ? (
                            <div className="text-center py-8">
                                <ProgressSpinner style={{ width: '50px', height: '50px' }} />
                            </div>
                        ) : (
                            <div className="grid">
                                <div className="col-12 lg:col-8">
                                    {currentStep === 0 && renderClienteForm()}
                                    {currentStep === 1 && renderCartReview()}
                                    {currentStep === 2 && renderPaymentSection()}
                                    {currentStep === 3 && (
                                        <Card
                                            className="shadow-4"
                                            style={{
                                                backgroundColor: 'var(--surface-card)',
                                                border: `2px solid ${primaryColor}40`
                                            }}
                                        >
                                            <div className="text-center p-6">
                                                <div
                                                    className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                                                    style={{
                                                        backgroundColor: '#10b98120',
                                                        color: '#10b981',
                                                        width: '100px',
                                                        height: '100px'
                                                    }}
                                                >
                                                    <i className="pi pi-check-circle text-6xl"></i>
                                                </div>
                                                <h3 className="text-3xl font-bold mb-3 text-900">¡Pago Exitoso!</h3>
                                                <p className="text-600 mb-4 text-lg">Tu orden ha sido procesada correctamente.</p>
                                                {orderNumber && (
                                                    <div className="p-4 border-round mb-4" style={{ backgroundColor: `${primaryColor}10` }}>
                                                        <i className="pi pi-tag mr-2" style={{ color: primaryColor }}></i>
                                                        <strong>Número de orden:</strong>
                                                        <span className="font-bold ml-2" style={{ color: primaryColor }}>
                                                            {orderNumber}
                                                        </span>
                                                    </div>
                                                )}
                                            </div>
                                        </Card>
                                    )}
                                </div>

                                <div className="col-12 lg:col-4">
                                    {cartSummary && (
                                        <Card
                                            className="sticky top-4 shadow-3"
                                            style={{
                                                backgroundColor: 'var(--surface-card)',
                                                border: `1px solid ${primaryColor}20`
                                            }}
                                        >
                                            <div className="p-4">
                                                <div className="flex align-items-center justify-content-between mb-4">
                                                    <h4 className="font-bold m-0" style={{ color: primaryColor }}>
                                                        <i className="pi pi-wallet mr-2"></i>
                                                        Total del pedido
                                                    </h4>
                                                    <Badge value={cartSummary.totalItems} severity="success" style={{ backgroundColor: primaryColor }} />
                                                </div>
                                                <div className="text-center p-3 border-round mb-3" style={{ backgroundColor: `${primaryColor}08` }}>
                                                    <div className="text-3xl font-bold mb-2" style={{ color: primaryColor }}>
                                                        {cartService.formatPrice(cartSummary.total)}
                                                    </div>
                                                    <div className="text-sm text-600">
                                                        <i className="pi pi-box mr-1"></i>
                                                        {cartSummary.totalItems} {cartSummary.totalItems === 1 ? 'producto' : 'productos'}
                                                    </div>
                                                </div>
                                                {currentStep < 2 && (
                                                    <div className="text-center">
                                                        <i className="pi pi-shield text-2xl" style={{ color: primaryColor }}></i>
                                                        <p className="text-600 text-sm mt-2">Compra 100% segura</p>
                                                    </div>
                                                )}
                                            </div>
                                        </Card>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Botones de navegación mejorados */}
                        <div className="flex justify-content-between mt-6">
                            <Button
                                label={currentStep === 0 ? 'Volver al carrito' : 'Atrás'}
                                icon="pi pi-arrow-left"
                                className="p-button-outlined p-3 shadow-2"
                                onClick={() => {
                                    if (currentStep === 0) {
                                        router.push('/cart');
                                    } else {
                                        setCurrentStep(currentStep - 1);
                                    }
                                }}
                                disabled={processing || currentStep >= 3}
                                style={{
                                    borderColor: primaryColor,
                                    color: primaryColor
                                }}
                            />

                            {currentStep < 3 && (
                                <Button
                                    label={getNextButtonLabel()}
                                    icon="pi pi-arrow-right"
                                    iconPos="right"
                                    className="p-3 shadow-2"
                                    onClick={handleNext}
                                    disabled={processing || !canProceedToNext()}
                                    style={{
                                        backgroundColor: primaryColor,
                                        borderColor: primaryColor
                                    }}
                                />
                            )}
                        </div>
                    </div>

                    {/* Dialog de éxito mejorado */}
                    <Dialog
                        header={
                            <div className="flex align-items-center">
                                <i className={`pi ${paymentResult?.pending ? 'pi-clock' : 'pi-check-circle'} mr-2 ${paymentResult?.pending ? 'text-orange-500' : 'text-green-500'}`}></i>
                                {paymentResult?.pending ? '¡Orden registrada!' : '¡Compra realizada con éxito!'}
                            </div>
                        }
                        visible={showSuccessDialog}
                        onHide={handleSuccessDialogClose}
                        style={{ width: '500px' }}
                        modal
                        closable={false}
                    >
                        <div className="text-center p-4">
                            <div
                                className="flex align-items-center justify-content-center border-circle mx-auto mb-4"
                                style={{
                                    backgroundColor: paymentResult?.pending ? '#f59e0b20' : '#10b98120',
                                    color: paymentResult?.pending ? '#f59e0b' : '#10b981',
                                    width: '80px',
                                    height: '80px'
                                }}
                            >
                                <i className={`pi ${paymentResult?.pending ? 'pi-hourglass' : 'pi-gift'} text-4xl`}></i>
                            </div>

                            {paymentResult?.pending ? (
                                <>
                                    <h3 className="text-xl font-bold mb-3">¡Tu orden ha sido registrada!</h3>
                                    <p className="text-600 mb-4">
                                        El emprendedor está procesando tu pago con <strong>{paymentResult.methodName}</strong>. Serás notificado automáticamente cuando se complete.
                                    </p>
                                    {orderNumber && (
                                        <div className="p-3 border-round-lg mb-4" style={{ backgroundColor: `${primaryColor}10` }}>
                                            <i className="pi pi-tag mr-2" style={{ color: primaryColor }}></i>
                                            <strong>Número de orden: </strong>
                                            <span className="font-bold" style={{ color: primaryColor }}>
                                                {orderNumber}
                                            </span>
                                        </div>
                                    )}
                                    <div className="p-3 border-round-lg mb-4" style={{ backgroundColor: '#f59e0b10' }}>
                                        <div className="flex align-items-start text-left">
                                            <i className="pi pi-info-circle mr-2 text-orange-500 mt-1"></i>
                                            <div>
                                                <strong className="text-orange-700">Estado: Pago Pendiente</strong>
                                                <p className="text-sm text-600 m-0 mt-2">Puedes revisar el estado de tu orden en cualquier momento desde "Mi Cuenta" → "Historial de Compras"</p>
                                            </div>
                                        </div>
                                    </div>
                                </>
                            ) : (
                                <>
                                    <h3 className="text-xl font-bold mb-3">¡Gracias por tu compra!</h3>
                                    <p className="text-600 mb-4">Tu pago ha sido procesado exitosamente. Recibirás un correo de confirmación en breve.</p>
                                    {orderNumber && (
                                        <div className="p-3 border-round-lg mb-4" style={{ backgroundColor: `${primaryColor}10` }}>
                                            <i className="pi pi-tag mr-2" style={{ color: primaryColor }}></i>
                                            <strong>Número de orden: </strong>
                                            <span className="font-bold" style={{ color: primaryColor }}>
                                                {orderNumber}
                                            </span>
                                        </div>
                                    )}
                                </>
                            )}

                            <div className="flex gap-3 justify-content-center">
                                <Button
                                    label="Seguir comprando"
                                    icon="pi pi-shopping-bag"
                                    className="p-button-outlined"
                                    onClick={() => {
                                        setShowSuccessDialog(false);
                                        router.push('/products');
                                    }}
                                    style={{
                                        borderColor: primaryColor,
                                        color: primaryColor
                                    }}
                                />
                                <Button
                                    label="Ir al inicio"
                                    icon="pi pi-home"
                                    onClick={handleSuccessDialogClose}
                                    style={{
                                        backgroundColor: primaryColor,
                                        borderColor: primaryColor
                                    }}
                                />
                            </div>
                        </div>
                    </Dialog>
                </div>
            )}
        </>
    );
};

export default CheckoutPage;
