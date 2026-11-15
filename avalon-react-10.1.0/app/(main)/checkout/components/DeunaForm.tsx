'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { Message } from 'primereact/message';
import { Image } from 'primereact/image';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import cartService from '../../../../services/cartService';
import { resolveGatewayAssetUrl } from '../../../../services/apiConfig';
import { verificarDisponibilidadDeuna } from '../../../../services/pagoService';

export interface DeunaData {
    qrImageUrl: string;
}

interface DeunaFormProps {
    onDataChange: (data: DeunaData, isValid: boolean) => void;
    primaryColor?: string;
}

const DeunaForm: React.FC<DeunaFormProps> = ({
    onDataChange,
    primaryColor = 'var(--primary-color)'
}) => {
    const [qrImageUrl, setQrImageUrl] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetchDeunaQR();
    }, []);

    useEffect(() => {
        if (qrImageUrl) {
            onDataChange({ qrImageUrl }, true);
        } else {
            onDataChange({ qrImageUrl: '' }, false);
        }
    }, [qrImageUrl]);

    const fetchDeunaQR = async () => {
        setLoading(true);
        setError(null);

        try {
            // QR resuelto solo por la empresa del carrito; sin fallback a otro id (hallazgo C-01)
            const disponibilidad = await verificarDisponibilidadDeuna(cartService.getCart());

            if (disponibilidad.disponible && disponibilidad.datos) {
                setQrImageUrl(resolveGatewayAssetUrl(disponibilidad.datos));
            } else {
                setQrImageUrl(null);
                setError(disponibilidad.motivo || 'El emprendedor no ha configurado su código QR de Deuna');
            }
        } catch (err: any) {
            setError(err.message || 'Error al cargar el código QR');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card
            className="mt-4 shadow-3"
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
                            backgroundColor: '#9333ea20',
                            color: '#9333ea',
                            width: '40px',
                            height: '40px'
                        }}
                    >
                        <i className="pi pi-qrcode text-lg"></i>
                    </div>
                    <h4 className="text-xl font-bold m-0" style={{ color: '#9333ea' }}>
                        Pago con Deuna
                    </h4>
                </div>

                <Message
                    severity="info"
                    text="Escanea el código QR con la aplicación de Deuna para completar tu pago"
                    className="mb-4 w-full"
                    icon="pi pi-info-circle"
                />

                {loading && (
                    <div className="text-center py-5">
                        <ProgressSpinner
                            style={{ width: '50px', height: '50px' }}
                            strokeWidth="4"
                        />
                        <p className="text-600 mt-3">Cargando código QR...</p>
                    </div>
                )}

                {error && (
                    <Message
                        severity="error"
                        text={error}
                        className="mb-4 w-full"
                        icon="pi pi-exclamation-triangle"
                    />
                )}

                {!loading && !error && qrImageUrl && (
                    <>
                        {/* Código QR */}
                        <div className="text-center mb-4">
                            <div
                                className="inline-block p-4 border-round-lg shadow-3"
                                style={{
                                    backgroundColor: 'white',
                                    border: '2px solid #9333ea30'
                                }}
                            >
                                <Image
                                    src={qrImageUrl}
                                    alt="Código QR de Deuna"
                                    width="250"
                                    height="250"
                                    preview
                                />
                            </div>
                        </div>

                        <Divider />

                        {/* Instrucciones */}
                        <div className="p-3 border-round mb-3" style={{ backgroundColor: '#9333ea10' }}>
                            <h5 className="mt-0 mb-3" style={{ color: '#9333ea' }}>
                                <i className="pi pi-list mr-2"></i>
                                Instrucciones para pagar
                            </h5>
                            <ol className="pl-3 m-0 text-sm line-height-3">
                                <li className="mb-2">
                                    <strong>Abre la aplicación de Deuna</strong> en tu dispositivo móvil
                                </li>
                                <li className="mb-2">
                                    <strong>Selecciona "Escanear QR"</strong> en el menú principal
                                </li>
                                <li className="mb-2">
                                    <strong>Escanea el código</strong> mostrado arriba
                                </li>
                                <li className="mb-2">
                                    <strong>Confirma el monto</strong> y completa el pago
                                </li>
                                <li>
                                    <strong>Espera la confirmación</strong> en esta pantalla
                                </li>
                            </ol>
                        </div>

                        {/* Características de Deuna */}
                        <div className="grid">
                            <div className="col-12 md:col-4">
                                <div className="text-center p-3">
                                    <div
                                        className="inline-flex align-items-center justify-content-center border-circle mb-2"
                                        style={{
                                            width: '50px',
                                            height: '50px',
                                            backgroundColor: '#9333ea20',
                                            color: '#9333ea'
                                        }}
                                    >
                                        <i className="pi pi-bolt text-xl"></i>
                                    </div>
                                    <div className="font-semibold text-sm">Pago Instantáneo</div>
                                    <div className="text-xs text-600 mt-1">
                                        Confirmación inmediata
                                    </div>
                                </div>
                            </div>
                            <div className="col-12 md:col-4">
                                <div className="text-center p-3">
                                    <div
                                        className="inline-flex align-items-center justify-content-center border-circle mb-2"
                                        style={{
                                            width: '50px',
                                            height: '50px',
                                            backgroundColor: '#9333ea20',
                                            color: '#9333ea'
                                        }}
                                    >
                                        <i className="pi pi-shield text-xl"></i>
                                    </div>
                                    <div className="font-semibold text-sm">100% Seguro</div>
                                    <div className="text-xs text-600 mt-1">
                                        Transacción protegida
                                    </div>
                                </div>
                            </div>
                            <div className="col-12 md:col-4">
                                <div className="text-center p-3">
                                    <div
                                        className="inline-flex align-items-center justify-content-center border-circle mb-2"
                                        style={{
                                            width: '50px',
                                            height: '50px',
                                            backgroundColor: '#9333ea20',
                                            color: '#9333ea'
                                        }}
                                    >
                                        <i className="pi pi-mobile text-xl"></i>
                                    </div>
                                    <div className="font-semibold text-sm">Sin comisiones</div>
                                    <div className="text-xs text-600 mt-1">
                                        0% de cargos extra
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Nota de espera */}
                        <Message
                            severity="warn"
                            text="No cierres esta ventana hasta que el pago sea confirmado"
                            className="mt-3 w-full"
                            icon="pi pi-exclamation-circle"
                        />
                    </>
                )}

                {!loading && !error && !qrImageUrl && (
                    <Message
                        severity="warn"
                        text="El vendedor aún no ha configurado su método de pago Deuna"
                        className="w-full"
                        icon="pi pi-exclamation-triangle"
                    />
                )}
            </div>
        </Card>
    );
};

export default DeunaForm;
