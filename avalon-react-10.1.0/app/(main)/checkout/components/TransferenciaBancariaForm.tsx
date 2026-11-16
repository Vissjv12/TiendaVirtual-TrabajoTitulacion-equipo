'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { FileUpload, ItemTemplateOptions } from 'primereact/fileupload';
import { Message } from 'primereact/message';
import { Divider } from 'primereact/divider';
import { Tag } from 'primereact/tag';
import { ProgressSpinner } from 'primereact/progressspinner';
import cartService from '../../../../services/cartService';
import {
    DatosBancariosPublico,
    formatAccountTypeLabel,
    formatBankLabel,
    verificarDisponibilidadTransferencia
} from '../../../../services/pagoService';
import {
    isValidBankAccount,
    isValidTransferReference,
    onlyDigits,
    sanitizeReferenceCode
} from '../../../../services/formValidations';

export interface TransferData {
    banco: string;
    numeroCuenta: string;
    numeroReferencia: string;
    comprobante?: File;
}

interface TransferenciaBancariaFormProps {
    onDataChange: (data: TransferData, isValid: boolean) => void;
    primaryColor?: string;
}

const TransferenciaBancariaForm: React.FC<TransferenciaBancariaFormProps> = ({
    onDataChange,
    primaryColor = 'var(--primary-color)'
}) => {
    const [banco, setBanco] = useState('');
    const [numeroCuenta, setNumeroCuenta] = useState('');
    const [numeroReferencia, setNumeroReferencia] = useState('');
    const [comprobante, setComprobante] = useState<File | null>(null);
    const [errors, setErrors] = useState<any>({});
    const [cuentasBancarias, setCuentasBancarias] = useState<DatosBancariosPublico[]>([]);
    const [loadingCuentas, setLoadingCuentas] = useState(true);
    const [cuentasError, setCuentasError] = useState<string | null>(null);

    const bancos = [
        { label: 'Banco Pichincha', value: 'pichincha' },
        { label: 'Banco Guayaquil', value: 'guayaquil' },
        { label: 'Banco del Pacífico', value: 'pacifico' },
        { label: 'Produbanco', value: 'produbanco' },
        { label: 'Banco Bolivariano', value: 'bolivariano' },
        { label: 'Banco Internacional', value: 'internacional' },
        { label: 'Banco Austro', value: 'austro' },
        { label: 'Otros', value: 'otros' }
    ];

    useEffect(() => {
        cargarCuentasBancarias();
    }, []);

    const cargarCuentasBancarias = async () => {
        setLoadingCuentas(true);
        setCuentasError(null);
        try {
            // Una sola empresa identificable por carrito; si no, se bloquea sin consultar (hallazgo C-01)
            const disponibilidad = await verificarDisponibilidadTransferencia(cartService.getCart());
            if (disponibilidad.disponible && disponibilidad.datos) {
                setCuentasBancarias([disponibilidad.datos]);
            } else {
                setCuentasBancarias([]);
                setCuentasError(disponibilidad.motivo || 'Transferencia bancaria no disponible para este carrito');
            }
        } catch (err: any) {
            setCuentasError(err?.message || 'No se pudieron cargar los datos bancarios del emprendedor');
            setCuentasBancarias([]);
        } finally {
            setLoadingCuentas(false);
        }
    };

    useEffect(() => {
        validateForm();
    }, [banco, numeroCuenta, numeroReferencia, comprobante, cuentasBancarias, cuentasError, loadingCuentas]);

    const validateForm = () => {
        const newErrors: any = {};
        // Sin una cuenta destino válida de la empresa del carrito no se puede pagar por transferencia
        let isValid = !loadingCuentas && !cuentasError && cuentasBancarias.length === 1;

        if (!banco) {
            newErrors.banco = 'Selecciona un banco';
            isValid = false;
        }

        if (!numeroCuenta || !isValidBankAccount(numeroCuenta)) {
            newErrors.numeroCuenta = 'Ingresa un número de cuenta numérico válido (10 a 20 dígitos)';
            isValid = false;
        }

        if (!numeroReferencia || !isValidTransferReference(numeroReferencia)) {
            newErrors.numeroReferencia = 'Ingresa una referencia alfanumérica válida (mínimo 6 caracteres, debe incluir números)';
            isValid = false;
        }

        setErrors(newErrors);

        // Notificar al componente padre
        onDataChange(
            {
                banco,
                numeroCuenta,
                numeroReferencia,
                comprobante: comprobante || undefined
            },
            isValid
        );
    };

    const onFileSelect = (event: any) => {
        const file = event.files[0];
        setComprobante(file);
    };

    const onFileRemove = () => {
        setComprobante(null);
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
                            backgroundColor: '#22c55e20',
                            color: '#22c55e',
                            width: '40px',
                            height: '40px'
                        }}
                    >
                        <i className="pi pi-building text-lg"></i>
                    </div>
                    <h4 className="text-xl font-bold m-0" style={{ color: '#22c55e' }}>
                        Datos de Transferencia Bancaria
                    </h4>
                </div>

                <Message
                    severity="info"
                    text="Realiza la transferencia a una de nuestras cuentas y completa los datos"
                    className="mb-4 w-full"
                />

                {/* Información de cuentas reales configuradas por el emprendedor */}
                <div className="mb-4 p-4 border-round" style={{ backgroundColor: '#22c55e10' }}>
                    <div className="flex align-items-center mb-3">
                        <i className="pi pi-info-circle mr-2" style={{ color: '#22c55e' }}></i>
                        <span className="font-bold" style={{ color: '#22c55e' }}>
                            Nuestras Cuentas Bancarias
                        </span>
                    </div>

                    {loadingCuentas ? (
                        <div className="flex align-items-center gap-2 text-600 text-sm">
                            <ProgressSpinner style={{ width: '20px', height: '20px' }} strokeWidth="6" />
                            <span>Cargando cuentas bancarias...</span>
                        </div>
                    ) : cuentasError ? (
                        <Message severity="error" text={cuentasError} className="w-full" />
                    ) : cuentasBancarias.length === 0 ? (
                        <Message
                            severity="warn"
                            text="Este emprendedor aún no ha configurado sus datos bancarios para transferencia."
                            className="w-full"
                        />
                    ) : (
                        <div className="grid text-sm">
                            {cuentasBancarias.map((cuenta, index) => (
                                <div
                                    key={`${cuenta.empresaId || index}-${cuenta.numeroCuenta}`}
                                    className={cuentasBancarias.length === 1 ? 'col-12' : 'col-12 md:col-6'}
                                >
                                    <div className="surface-card p-3 border-round border-1 border-green-200">
                                        {cuenta.empresaNombre && (
                                            <div className="text-xs font-semibold text-green-700 mb-1">
                                                {cuenta.empresaNombre}
                                            </div>
                                        )}
                                        <div className="font-semibold text-900 mb-1">
                                            {formatBankLabel(cuenta.banco)}
                                        </div>
                                        <div className="text-700">
                                            Cuenta: <span className="font-semibold text-900">{cuenta.numeroCuenta}</span>
                                        </div>
                                        <div className="text-600">
                                            Tipo: {formatAccountTypeLabel(cuenta.tipoCuenta)}
                                        </div>
                                        {cuenta.titular && (
                                            <div className="text-600">Titular: {cuenta.titular}</div>
                                        )}
                                        {cuenta.cedulaRuc && (
                                            <div className="text-600">Cédula / RUC: {cuenta.cedulaRuc}</div>
                                        )}
                                        {cuenta.email && (
                                            <div className="text-600">Email: {cuenta.email}</div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                <Divider />

                {/* Formulario */}
                <div className="grid">
                    {/* Banco */}
                    <div className="col-12 md:col-6">
                        <label htmlFor="banco" className="block font-semibold mb-2">
                            <i className="pi pi-building mr-2"></i>
                            Banco donde realizaste la transferencia *
                        </label>
                        <Dropdown
                            id="banco"
                            value={banco}
                            options={bancos}
                            onChange={(e) => setBanco(e.value)}
                            placeholder="Selecciona el banco"
                            className={`w-full ${errors.banco ? 'p-invalid' : ''}`}
                        />
                        {errors.banco && (
                            <small className="p-error block mt-1">
                                <i className="pi pi-exclamation-circle mr-1"></i>
                                {errors.banco}
                            </small>
                        )}
                    </div>

                    {/* Número de cuenta */}
                    <div className="col-12 md:col-6">
                        <label htmlFor="numeroCuenta" className="block font-semibold mb-2">
                            <i className="pi pi-id-card mr-2"></i>
                            Número de cuenta origen *
                        </label>
                        <InputText
                            id="numeroCuenta"
                            value={numeroCuenta}
                            onChange={(e) => setNumeroCuenta(onlyDigits(e.target.value, 20))}
                            keyfilter="pint"
                            inputMode="numeric"
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

                    {/* Número de referencia */}
                    <div className="col-12">
                        <label htmlFor="numeroReferencia" className="block font-semibold mb-2">
                            <i className="pi pi-hashtag mr-2"></i>
                            Número de referencia / Comprobante *
                        </label>
                        <InputText
                            id="numeroReferencia"
                            value={numeroReferencia}
                            onChange={(e) => setNumeroReferencia(sanitizeReferenceCode(e.target.value, 50))}
                            keyfilter={/[A-Za-z0-9-]/}
                            placeholder="Ej: TR123456789"
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
                            Ingresa el número de comprobante que aparece en tu transferencia
                        </small>
                    </div>

                    {/* Upload de comprobante (opcional) */}
                    <div className="col-12">
                        <label className="block font-semibold mb-2">
                            <i className="pi pi-image mr-2"></i>
                            Adjuntar comprobante (opcional)
                        </label>
                        <FileUpload
                            name="comprobante"
                            accept="image/*,application/pdf"
                            maxFileSize={5000000}
                            onSelect={onFileSelect}
                            onRemove={onFileRemove}
                            onClear={onFileRemove}
                            itemTemplate={itemTemplate}
                            uploadOptions={{ className: 'hidden' }}
                            emptyTemplate={
                                <div className="flex align-items-center justify-content-center flex-column">
                                    <i className="pi pi-cloud-upload text-4xl mb-3" style={{ color: primaryColor }}></i>
                                    <p className="m-0 text-600">Arrastra y suelta tu comprobante aquí</p>
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
                            Adjunta una foto o PDF de tu comprobante de transferencia para agilizar la verificación
                        </small>
                    </div>
                </div>

                <Message
                    severity="warn"
                    text="Tu pedido será procesado una vez verifiquemos la transferencia (usualmente en 24 horas)"
                    className="mt-4 w-full"
                />
            </div>
        </Card>
    );
};

export default TransferenciaBancariaForm;
