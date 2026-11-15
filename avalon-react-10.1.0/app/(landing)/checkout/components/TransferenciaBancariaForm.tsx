'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { FileUpload, ItemTemplateOptions } from 'primereact/fileupload';
import { Message } from 'primereact/message';
import { Divider } from 'primereact/divider';
import { Tag } from 'primereact/tag';
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

    // Cuentas de ejemplo para mostrar (en producción vendrían del backend)
    const cuentasBancarias = {
        pichincha: '2100123456',
        guayaquil: '0123456789',
        pacifico: '1234567890'
    };

    useEffect(() => {
        validateForm();
    }, [banco, numeroCuenta, numeroReferencia, comprobante]);

    const validateForm = () => {
        const newErrors: any = {};
        let isValid = true;

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

                {/* Información de cuentas */}
                <div className="mb-4 p-4 border-round" style={{ backgroundColor: '#22c55e10' }}>
                    <div className="flex align-items-center mb-3">
                        <i className="pi pi-info-circle mr-2" style={{ color: '#22c55e' }}></i>
                        <span className="font-bold" style={{ color: '#22c55e' }}>
                            Nuestras Cuentas Bancarias
                        </span>
                    </div>
                    <div className="grid text-sm">
                        <div className="col-12 md:col-4">
                            <div className="font-semibold mb-1">Banco Pichincha</div>
                            <div className="text-600">Cuenta: {cuentasBancarias.pichincha}</div>
                            <div className="text-600">Tipo: Ahorros</div>
                        </div>
                        <div className="col-12 md:col-4">
                            <div className="font-semibold mb-1">Banco Guayaquil</div>
                            <div className="text-600">Cuenta: {cuentasBancarias.guayaquil}</div>
                            <div className="text-600">Tipo: Corriente</div>
                        </div>
                        <div className="col-12 md:col-4">
                            <div className="font-semibold mb-1">Banco del Pacífico</div>
                            <div className="text-600">Cuenta: {cuentasBancarias.pacifico}</div>
                            <div className="text-600">Tipo: Ahorros</div>
                        </div>
                    </div>
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
