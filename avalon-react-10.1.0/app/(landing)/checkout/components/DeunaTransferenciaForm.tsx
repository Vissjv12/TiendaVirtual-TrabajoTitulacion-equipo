'use client';

import React, { useState, useEffect, useRef } from 'react';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { FileUpload, ItemTemplateOptions } from 'primereact/fileupload';
import { Message } from 'primereact/message';
import { Image } from 'primereact/image';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import { Tag } from 'primereact/tag';
import cartService from '../../../../services/cartService';
import { GATEWAY_BASE_URL, resolveGatewayAssetUrl } from '../../../../services/apiConfig';

export interface DeunaTransferenciaData {
    qrImageUrl: string;
    numeroCuenta: string;
    numeroReferencia: string;
    comprobante?: File;
}

interface DeunaTransferenciaFormProps {
    onDataChange: (data: DeunaTransferenciaData, isValid: boolean) => void;
    primaryColor?: string;
}

const DeunaTransferenciaForm: React.FC<DeunaTransferenciaFormProps> = ({
    onDataChange,
    primaryColor = 'var(--primary-color)'
}) => {
    const fileUploadRef = useRef<FileUpload>(null);
    const [qrImageUrl, setQrImageUrl] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [numeroCuenta, setNumeroCuenta] = useState('');
    const [numeroReferencia, setNumeroReferencia] = useState('');
    const [comprobante, setComprobante] = useState<File | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    useEffect(() => {
        fetchDeunaQR();
    }, []);

    useEffect(() => {
        validateForm();
    }, [qrImageUrl, numeroCuenta, numeroReferencia, comprobante]);

    const validateForm = () => {
        const newErrors: Record<string, string> = {};
        let isValid = Boolean(qrImageUrl);

        if (!numeroCuenta || numeroCuenta.trim().length < 10) {
            newErrors.numeroCuenta = 'Ingresa un número de cuenta válido (mínimo 10 dígitos)';
            isValid = false;
        }

        if (!numeroReferencia || numeroReferencia.trim().length < 6) {
            newErrors.numeroReferencia = 'Ingresa el número de referencia o comprobante de Deuna';
            isValid = false;
        }

        setErrors(newErrors);

        onDataChange(
            {
                qrImageUrl: qrImageUrl || '',
                numeroCuenta: numeroCuenta.trim(),
                numeroReferencia: numeroReferencia.trim(),
                comprobante: comprobante || undefined
            },
            isValid
        );
    };

    const onFileSelect = (event: any) => {
        const file = event.files?.[0] || null;
        setComprobante(file);
    };

    const onFileRemove = () => {
        setComprobante(null);
        fileUploadRef.current?.clear();
    };

    const itemTemplate = (inFile: object, options: ItemTemplateOptions) => {
        const file = inFile as File;
        return (
            <div className="flex align-items-center justify-content-between w-full gap-3">
                <div className="flex align-items-center gap-3">
                    {options.previewElement || <i className="pi pi-file-pdf text-3xl text-red-500"></i>}
                    <div>
                        <div className="font-semibold text-900">{file.name}</div>
                        <div className="flex align-items-center gap-2 mt-1">
                            <span className="text-sm text-600">{options.formatSize}</span>
                            <Tag value="Adjunto" severity="success" />
                        </div>
                    </div>
                </div>
                {options.removeElement}
            </div>
        );
    };

    const fetchDeunaQR = async () => {
        setLoading(true);
        setError(null);

        try {
            // Llamada al API Gateway para obtener el QR del emprendedor (mitiga O-06)
            const cartItems = cartService.getCart();
            const firstProduct: any = cartItems.length > 0 ? cartItems[0].producto : null;
            const empresaId = firstProduct?.empresa?.id || firstProduct?.empresaId || firstProduct?.emprendedorId || 1;
            const response = await fetch(`${GATEWAY_BASE_URL}/api/emprendedor/configuracion-pagos/deuna-qr/${empresaId}`);

            if (!response.ok) {
                if (response.status === 404) {
                    throw new Error('El emprendedor aún no ha configurado su código QR de Deuna');
                }
                throw new Error('No se pudo cargar el código QR de Deuna');
            }

            const data = await response.json();

            if (data.qrUrl) {
                setQrImageUrl(resolveGatewayAssetUrl(data.qrUrl));
            } else {
                setError('El emprendedor no ha configurado su código QR de Deuna');
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
                    text="Escanea el código QR con la aplicación de Deuna y completa los datos del comprobante"
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

                        {/* Instrucciones */}
                        <div className="p-3 border-round mb-4" style={{ backgroundColor: '#9333ea10' }}>
                            <h5 className="mt-0 mb-3" style={{ color: '#9333ea' }}>
                                <i className="pi pi-list mr-2"></i>
                                Instrucciones para pagar
                            </h5>
                            <ol className="pl-3 m-0 text-sm line-height-3">
                                <li className="mb-2">
                                    <strong>Abre la aplicación de Deuna</strong> en tu dispositivo móvil
                                </li>
                                <li className="mb-2">
                                    <strong>Selecciona &quot;Escanear QR&quot;</strong> en el menú principal
                                </li>
                                <li className="mb-2">
                                    <strong>Escanea el código</strong> mostrado arriba
                                </li>
                                <li className="mb-2">
                                    <strong>Confirma el monto</strong> y realiza el pago
                                </li>
                                <li>
                                    <strong>Completa los datos del comprobante</strong> en el formulario inferior para su verificación
                                </li>
                            </ol>
                        </div>

                        <Divider />

                        {/* Formulario de verificación de pago Deuna */}
                        <div className="mb-3">
                            <h5 className="mt-0 mb-1" style={{ color: '#9333ea' }}>
                                <i className="pi pi-check-square mr-2"></i>
                                Datos para verificación del pago
                            </h5>
                            <p className="text-sm text-600 m-0">
                                Registra la referencia de tu pago en Deuna para que el emprendedor pueda validarlo y despachar tu pedido.
                            </p>
                        </div>

                        <div className="grid">
                            {/* Número de cuenta origen */}
                            <div className="col-12 md:col-6">
                                <label htmlFor="deunaNumeroCuenta" className="block font-semibold mb-2">
                                    <i className="pi pi-id-card mr-2"></i>
                                    Número de cuenta origen *
                                </label>
                                <InputText
                                    id="deunaNumeroCuenta"
                                    value={numeroCuenta}
                                    onChange={(e) => setNumeroCuenta(e.target.value)}
                                    placeholder="Ej: 2100123456"
                                    className={`w-full ${errors.numeroCuenta ? 'p-invalid' : ''}`}
                                    maxLength={20}
                                />
                                {errors.numeroCuenta && (
                                    <small className="p-error block mt-1">
                                        <i className="pi pi-exclamation-circle mr-1"></i>
                                        {errors.numeroCuenta}
                                    </small>
                                )}
                            </div>

                            {/* Número de referencia / Comprobante */}
                            <div className="col-12 md:col-6">
                                <label htmlFor="deunaNumeroReferencia" className="block font-semibold mb-2">
                                    <i className="pi pi-hashtag mr-2"></i>
                                    Número de referencia / Comprobante *
                                </label>
                                <InputText
                                    id="deunaNumeroReferencia"
                                    value={numeroReferencia}
                                    onChange={(e) => setNumeroReferencia(e.target.value)}
                                    placeholder="Ej: DU123456789"
                                    className={`w-full ${errors.numeroReferencia ? 'p-invalid' : ''}`}
                                    maxLength={50}
                                />
                                {errors.numeroReferencia && (
                                    <small className="p-error block mt-1">
                                        <i className="pi pi-exclamation-circle mr-1"></i>
                                        {errors.numeroReferencia}
                                    </small>
                                )}
                                <small className="block mt-1 text-600">
                                    Ingresa el número de comprobante que genera la app Deuna
                                </small>
                            </div>

                            {/* Adjuntar comprobante (opcional) */}
                            <div className="col-12">
                                <label className="block font-semibold mb-2">
                                    <i className="pi pi-image mr-2"></i>
                                    Adjuntar comprobante (opcional)
                                </label>
                                <FileUpload
                                    ref={fileUploadRef}
                                    mode="advanced"
                                    name="comprobante"
                                    accept="image/*,application/pdf"
                                    maxFileSize={5000000}
                                    onSelect={onFileSelect}
                                    onRemove={onFileRemove}
                                    onClear={() => setComprobante(null)}
                                    itemTemplate={itemTemplate}
                                    uploadOptions={{ className: 'hidden' }}
                                    emptyTemplate={
                                        <div className="flex align-items-center justify-content-center flex-column">
                                            <i className="pi pi-cloud-upload text-4xl mb-3" style={{ color: primaryColor }}></i>
                                            <p className="m-0 text-600">Arrastra y suelta tu captura o comprobante de Deuna aquí</p>
                                            <small className="text-500 mt-1">PNG, JPG, PDF (máx. 5MB)</small>
                                        </div>
                                    }
                                    chooseLabel="Seleccionar archivo"
                                    cancelLabel="Cancelar"
                                    uploadLabel="Subir"
                                    auto={false}
                                    customUpload
                                    className="w-full"
                                />
                                <small className="block mt-2 text-600">
                                    <i className="pi pi-info-circle mr-1"></i>
                                    Adjunta una captura o PDF de tu comprobante de Deuna para agilizar la verificación
                                </small>
                            </div>
                        </div>

                        {/* Aviso de verificación */}
                        <Message
                            severity="warn"
                            text="Tu pedido será procesado una vez verifiquemos tu pago por Deuna (usualmente en 24 horas)"
                            className="mt-4 w-full"
                            icon="pi pi-clock"
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

export default DeunaTransferenciaForm;
