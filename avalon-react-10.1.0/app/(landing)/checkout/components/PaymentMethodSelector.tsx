'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { RadioButton } from 'primereact/radiobutton';
import { Divider } from 'primereact/divider';

export type PaymentMethod = 'transferencia' | 'tarjeta' | 'deuna';

export interface PaymentMethodData {
    method: PaymentMethod;
    // Para transferencia bancaria
    banco?: string;
    numeroCuenta?: string;
    numeroReferencia?: string;
    // Para tarjeta
    cardNumber?: string;
    cardName?: string;
    cardExpiry?: string;
    cardCvv?: string;
    // Para Deuna
    deunaEmail?: string;
}

interface PaymentMethodSelectorProps {
    selectedMethod?: PaymentMethod;
    onMethodChange: (method: PaymentMethod) => void;
    onPaymentDataChange: (data: PaymentMethodData) => void;
    primaryColor?: string;
    /** Métodos no disponibles para este carrito, con el motivo que se muestra al comprador. */
    disabledMethods?: Partial<Record<PaymentMethod, string>>;
}

const PaymentMethodSelector: React.FC<PaymentMethodSelectorProps> = ({
    selectedMethod: controlledMethod = 'transferencia',
    onMethodChange,
    onPaymentDataChange,
    primaryColor = 'var(--primary-color)',
    disabledMethods = {}
}) => {
    const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>(controlledMethod);

    useEffect(() => {
        setSelectedMethod(controlledMethod);
    }, [controlledMethod]);

    const handleMethodChange = (method: PaymentMethod) => {
        if (disabledMethods[method]) return;
        setSelectedMethod(method);
        onMethodChange(method);
        onPaymentDataChange({ method });
    };

    const paymentOptions = [
        {
            value: 'transferencia' as PaymentMethod,
            label: 'Transferencia Bancaria',
            icon: 'pi-building',
            description: 'Realiza una transferencia y envíanos el comprobante',
            color: '#22c55e'
        },
        // Stripe/tarjeta oculto temporalmente (se conserva el código para reactivarlo a futuro)
        // {
        //     value: 'tarjeta' as PaymentMethod,
        //     label: 'Tarjeta de Crédito/Débito',
        //     icon: 'pi-credit-card',
        //     description: 'Paga de forma segura con tu tarjeta',
        //     color: '#3b82f6'
        // },
        {
            value: 'deuna' as PaymentMethod,
            label: 'Deuna',
            icon: 'pi-wallet',
            description: 'Paga con Deuna de forma rápida y segura',
            color: '#8b5cf6'
        }
    ];

    return (
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
                        <i className="pi pi-credit-card text-lg"></i>
                    </div>
                    <h3 className="text-xl font-bold m-0" style={{ color: primaryColor }}>
                        Método de Pago
                    </h3>
                </div>

                <div className="text-600 mb-4">
                    <i className="pi pi-info-circle mr-2"></i>
                    Selecciona tu método de pago preferido
                </div>

                <Divider />

                <div className="grid">
                    {paymentOptions.map((option) => {
                        const disabledReason = disabledMethods[option.value];
                        const isDisabled = Boolean(disabledReason);
                        return (
                        <div key={option.value} className="col-12">
                            <div
                                className={`p-4 border-round-lg transition-all transition-duration-200 ${
                                    isDisabled
                                        ? 'cursor-auto opacity-60'
                                        : selectedMethod === option.value
                                            ? 'cursor-pointer shadow-4'
                                            : 'cursor-pointer shadow-2 hover:shadow-3'
                                }`}
                                aria-disabled={isDisabled}
                                style={{
                                    backgroundColor: selectedMethod === option.value 
                                        ? `${option.color}10` 
                                        : 'var(--surface-ground)',
                                    border: `2px solid ${
                                        selectedMethod === option.value 
                                            ? option.color 
                                            : 'var(--surface-border)'
                                    }`
                                }}
                                onClick={() => handleMethodChange(option.value)}
                            >
                                <div className="flex align-items-center">
                                    <RadioButton
                                        inputId={option.value}
                                        name="paymentMethod"
                                        value={option.value}
                                        onChange={(e) => handleMethodChange(e.value)}
                                        checked={selectedMethod === option.value}
                                        disabled={isDisabled}
                                        style={{
                                            accentColor: option.color
                                        }}
                                    />
                                    <label
                                        htmlFor={option.value}
                                        className={`ml-3 flex-1 ${isDisabled ? 'cursor-auto' : 'cursor-pointer'}`}
                                    >
                                        <div className="flex align-items-center justify-content-between">
                                            <div className="flex align-items-center">
                                                <div
                                                    className="flex align-items-center justify-content-center border-circle mr-3"
                                                    style={{
                                                        backgroundColor: `${option.color}20`,
                                                        color: option.color,
                                                        width: '45px',
                                                        height: '45px'
                                                    }}
                                                >
                                                    <i className={`pi ${option.icon} text-xl`}></i>
                                                </div>
                                                <div>
                                                    <div 
                                                        className="font-bold text-lg mb-1"
                                                        style={{ 
                                                            color: selectedMethod === option.value 
                                                                ? option.color 
                                                                : 'var(--text-color)' 
                                                        }}
                                                    >
                                                        {option.label}
                                                    </div>
                                                    <div className="text-sm text-600">
                                                        {option.description}
                                                    </div>
                                                    {isDisabled && (
                                                        <div className="text-sm mt-1 text-orange-600">
                                                            <i className="pi pi-lock mr-1 text-xs"></i>
                                                            No disponible: {disabledReason}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                            {selectedMethod === option.value && (
                                                <i 
                                                    className="pi pi-check-circle text-2xl"
                                                    style={{ color: option.color }}
                                                ></i>
                                            )}
                                        </div>
                                    </label>
                                </div>
                            </div>
                        </div>
                        );
                    })}
                </div>
            </div>
        </Card>
    );
};

export default PaymentMethodSelector;
