'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { TabView, TabPanel } from 'primereact/tabview';
import { InputText } from 'primereact/inputtext';
import { InputNumber } from 'primereact/inputnumber';
import { Dropdown } from 'primereact/dropdown';
import { FileUpload, FileUploadHandlerEvent } from 'primereact/fileupload';
import { Button } from 'primereact/button';
import { Message } from 'primereact/message';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Image } from 'primereact/image';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';
import { GATEWAY_BASE_URL, resolveGatewayAssetUrl } from '../../../../services/apiConfig';

interface DatosBancarios {
    banco: string;
    tipoCuenta: string;
    numeroCuenta: string;
    titular: string;
    cedulaRuc: string;
    email: string;
}

interface PayphoneConfig {
    payphoneAppId: string;
    payphoneToken: string;
}

interface ConfiguracionPagos {
    id?: number;
    emprendedorId: number;
    datosBancarios?: DatosBancarios;
    qrDeunaUrl?: string;
    payphone?: PayphoneConfig;
    costoEnvio?: number;
}

const bancos = [
    { label: 'Banco Pichincha', value: 'pichincha' },
    { label: 'Banco del Pacífico', value: 'pacifico' },
    { label: 'Banco Guayaquil', value: 'guayaquil' },
    { label: 'Banco Internacional', value: 'internacional' },
    { label: 'Produbanco', value: 'produbanco' },
    { label: 'Banco Bolivariano', value: 'bolivariano' },
    { label: 'Banco Austro', value: 'austro' },
    { label: 'Banco Solidario', value: 'solidario' }
];

const tiposCuenta = [
    { label: 'Ahorros', value: 'ahorros' },
    { label: 'Corriente', value: 'corriente' }
];

export default function ConfiguracionMetodosPago() {
    const toast = useRef<Toast>(null);
    const [loading, setLoading] = useState(false);
    const [loadingData, setLoadingData] = useState(true);
    const [activeTab, setActiveTab] = useState(0);

    // Datos bancarios
    const [banco, setBanco] = useState('');
    const [tipoCuenta, setTipoCuenta] = useState('');
    const [numeroCuenta, setNumeroCuenta] = useState('');
    const [titular, setTitular] = useState('');
    const [cedulaRuc, setCedulaRuc] = useState('');
    const [email, setEmail] = useState('');

    // QR Deuna
    const [qrDeunaUrl, setQrDeunaUrl] = useState<string | null>(null);
    const [uploadingQR, setUploadingQR] = useState(false);

    // Payphone
    const [payphoneAppId, setPayphoneAppId] = useState('');
    const [payphoneToken, setPayphoneToken] = useState('');

    // Costo de envío (cuota fija, por defecto $5.00)
    const [costoEnvio, setCostoEnvio] = useState<number>(5);

    const [errors, setErrors] = useState<any>({});

    useEffect(() => {
        cargarConfiguracion();
    }, []);

    const cargarConfiguracion = async () => {
        setLoadingData(true);
        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            if (response.ok) {
                const data: ConfiguracionPagos = await response.json();

                if (data.datosBancarios) {
                    setBanco(data.datosBancarios.banco);
                    setTipoCuenta(data.datosBancarios.tipoCuenta);
                    setNumeroCuenta(data.datosBancarios.numeroCuenta);
                    setTitular(data.datosBancarios.titular);
                    setCedulaRuc(data.datosBancarios.cedulaRuc);
                    setEmail(data.datosBancarios.email);
                }

                if (data.qrDeunaUrl) {
                    setQrDeunaUrl(resolveGatewayAssetUrl(data.qrDeunaUrl));
                }

                if (data.payphone) {
                    setPayphoneAppId(data.payphone.payphoneAppId);
                    setPayphoneToken(data.payphone.payphoneToken);
                }

                if (data.costoEnvio !== undefined && data.costoEnvio !== null) {
                    setCostoEnvio(Number(data.costoEnvio));
                }
            }
        } catch (error) {
            console.error('Error al cargar configuración:', error);
        } finally {
            setLoadingData(false);
        }
    };

    const validarDatosBancarios = (): boolean => {
        const newErrors: any = {};
        let isValid = true;

        if (!banco) {
            newErrors.banco = 'Selecciona un banco';
            isValid = false;
        }

        if (!tipoCuenta) {
            newErrors.tipoCuenta = 'Selecciona el tipo de cuenta';
            isValid = false;
        }

        if (!numeroCuenta || numeroCuenta.length < 10) {
            newErrors.numeroCuenta = 'Número de cuenta inválido (mínimo 10 dígitos)';
            isValid = false;
        }

        if (!titular || titular.trim().length < 3) {
            newErrors.titular = 'Ingresa el nombre del titular';
            isValid = false;
        }

        if (!cedulaRuc || cedulaRuc.length < 10) {
            newErrors.cedulaRuc = 'Cédula o RUC inválido';
            isValid = false;
        }

        if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            newErrors.email = 'Email inválido';
            isValid = false;
        }

        setErrors(newErrors);
        return isValid;
    };

    const guardarDatosBancarios = async () => {
        if (!validarDatosBancarios()) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Por favor completa todos los campos correctamente',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/bancarios`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    banco,
                    tipoCuenta,
                    numeroCuenta,
                    titular,
                    cedulaRuc,
                    email
                })
            });

            if (response.ok) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Datos bancarios guardados correctamente',
                    life: 3000
                });
            } else {
                throw new Error('Error al guardar');
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudieron guardar los datos bancarios',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const onQRUpload = async (event: FileUploadHandlerEvent) => {
        const file = event.files[0];

        if (!file) return;

        // Validar que sea una imagen
        if (!file.type.startsWith('image/')) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Solo se permiten archivos de imagen',
                life: 3000
            });
            return;
        }

        // Validar tamaño (máximo 5MB)
        if (file.size > 5 * 1024 * 1024) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'La imagen no debe superar los 5MB',
                life: 3000
            });
            return;
        }

        setUploadingQR(true);

        const formData = new FormData();
        formData.append('qrImage', file);

        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/deuna-qr`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                body: formData
            });

            if (response.ok) {
                const data = await response.json();
                setQrDeunaUrl(resolveGatewayAssetUrl(data.qrUrl));

                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Código QR de Deuna guardado correctamente',
                    life: 3000
                });
            } else {
                throw new Error('Error al subir');
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo subir el código QR',
                life: 3000
            });
        } finally {
            setUploadingQR(false);
        }
    };

    const eliminarQR = async () => {
        if (!confirm('¿Estás seguro de eliminar el código QR de Deuna?')) return;

        setLoading(true);
        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/deuna-qr`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${token}` }
            });

            if (response.ok) {
                setQrDeunaUrl(null);
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Código QR eliminado',
                    life: 3000
                });
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo eliminar el código QR',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const guardarPayphone = async () => {
        if (!payphoneAppId || !payphoneToken) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Por favor completa el App ID y el Token',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/payphone`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    payphoneAppId,
                    payphoneToken
                })
            });

            if (response.ok) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Credenciales de Payphone guardadas correctamente',
                    life: 3000
                });
            } else {
                throw new Error('Error al guardar');
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudieron guardar las credenciales de Payphone',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const guardarCostoEnvio = async () => {
        if (costoEnvio === null || costoEnvio === undefined || costoEnvio < 0) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Ingresa un costo de envío válido (mayor o igual a $0.00)',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            const token = localStorage.getItem('auth_token');
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/emprendedor/configuracion-pagos/costo-envio`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({ costoEnvio })
            });

            if (response.ok) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: `Costo de envío actualizado a $${costoEnvio.toFixed(2)}`,
                    life: 3000
                });
            } else {
                throw new Error('Error al guardar');
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo actualizar el costo de envío',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    if (loadingData) {
        return (
            <div className="flex justify-content-center align-items-center" style={{ minHeight: '400px' }}>
                <ProgressSpinner />
            </div>
        );
    }

    return (
        <>
            <Toast ref={toast} />

            <Card title="Configuración de Métodos de Pago" className="shadow-3">
                <Message severity="info" text="Configura tus métodos de pago para que tus clientes puedan realizar compras" className="mb-4 w-full" />

                <TabView activeIndex={activeTab} onTabChange={(e) => setActiveTab(e.index)}>
                    {/* Tab 1: Transferencia Bancaria */}
                    <TabPanel header="Transferencia Bancaria" leftIcon="pi pi-building mr-2">
                        <div className="grid p-fluid">
                            <div className="col-12 md:col-6">
                                <label htmlFor="banco" className="block font-semibold mb-2">
                                    Banco *
                                </label>
                                <Dropdown id="banco" value={banco} options={bancos} onChange={(e) => setBanco(e.value)} placeholder="Selecciona tu banco" className={errors.banco ? 'p-invalid' : ''} />
                                {errors.banco && <small className="p-error">{errors.banco}</small>}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="tipoCuenta" className="block font-semibold mb-2">
                                    Tipo de Cuenta *
                                </label>
                                <Dropdown id="tipoCuenta" value={tipoCuenta} options={tiposCuenta} onChange={(e) => setTipoCuenta(e.value)} placeholder="Selecciona el tipo" className={errors.tipoCuenta ? 'p-invalid' : ''} />
                                {errors.tipoCuenta && <small className="p-error">{errors.tipoCuenta}</small>}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="numeroCuenta" className="block font-semibold mb-2">
                                    Número de Cuenta *
                                </label>
                                <InputText id="numeroCuenta" value={numeroCuenta} onChange={(e) => setNumeroCuenta(e.target.value)} placeholder="1234567890" className={errors.numeroCuenta ? 'p-invalid' : ''} />
                                {errors.numeroCuenta && <small className="p-error">{errors.numeroCuenta}</small>}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="titular" className="block font-semibold mb-2">
                                    Titular de la Cuenta *
                                </label>
                                <InputText id="titular" value={titular} onChange={(e) => setTitular(e.target.value)} placeholder="Juan Pérez" className={errors.titular ? 'p-invalid' : ''} />
                                {errors.titular && <small className="p-error">{errors.titular}</small>}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="cedulaRuc" className="block font-semibold mb-2">
                                    Cédula o RUC *
                                </label>
                                <InputText id="cedulaRuc" value={cedulaRuc} onChange={(e) => setCedulaRuc(e.target.value)} placeholder="1234567890" className={errors.cedulaRuc ? 'p-invalid' : ''} />
                                {errors.cedulaRuc && <small className="p-error">{errors.cedulaRuc}</small>}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="email" className="block font-semibold mb-2">
                                    Email de contacto *
                                </label>
                                <InputText id="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="correo@ejemplo.com" type="email" className={errors.email ? 'p-invalid' : ''} />
                                {errors.email && <small className="p-error">{errors.email}</small>}
                            </div>

                            <div className="col-12">
                                <Button label="Guardar Datos Bancarios" icon="pi pi-save" onClick={guardarDatosBancarios} loading={loading} className="mt-3" />
                            </div>
                        </div>
                    </TabPanel>

                    {/* Tab 2: Deuna QR */}
                    <TabPanel header="Deuna" leftIcon="pi pi-qrcode mr-2">
                        <div className="text-center">
                            {qrDeunaUrl ? (
                                <div>
                                    <Message severity="success" text="Ya tienes configurado tu código QR de Deuna" className="mb-4 w-full justify-content-start" />

                                    <div className="mb-4">
                                        <div className="inline-block p-4 border-round shadow-3" style={{ backgroundColor: 'white' }}>
                                            <Image src={qrDeunaUrl} alt="QR Deuna" width="250" height="250" preview />
                                        </div>
                                    </div>

                                    <Button label="Eliminar QR" icon="pi pi-trash" severity="danger" onClick={eliminarQR} loading={loading} outlined />
                                </div>
                            ) : (
                                <div>
                                    <Message severity="warn" text="Sube tu código QR de Deuna para que tus clientes puedan pagar" className="mb-4 w-full justify-content-start" />

                                    <div className="mb-3">
                                        <i className="pi pi-qrcode text-6xl mb-3" style={{ color: '#9333ea' }}></i>
                                        <p className="text-600">
                                            Sube una imagen de tu código QR de Deuna.
                                            <br />
                                            Formatos aceptados: JPG, PNG
                                            <br />
                                            Tamaño máximo: 5MB
                                        </p>
                                    </div>

                                    <FileUpload mode="basic" name="qrImage" accept="image/*" maxFileSize={5000000} customUpload uploadHandler={onQRUpload} auto chooseLabel="Seleccionar Imagen" className="p-button-outlined" disabled={uploadingQR} />

                                    {uploadingQR && (
                                        <div className="mt-3">
                                            <ProgressSpinner style={{ width: '30px', height: '30px' }} />
                                            <p className="text-600 mt-2">Subiendo imagen...</p>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </TabPanel>

                    {/* Tab 3: Payphone */}
                    <TabPanel header="Payphone" leftIcon="pi pi-mobile mr-2">
                        <div className="grid p-fluid">
                            <div className="col-12 mb-4">
                                <Message severity="info" text="Ingresa las credenciales de tu aplicación en Payphone Developer para recibir pagos directamente en tu cuenta." className="w-full justify-content-start" />
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="payphoneAppId" className="block font-semibold mb-2">
                                    Payphone App ID *
                                </label>
                                <InputText id="payphoneAppId" value={payphoneAppId} onChange={(e) => setPayphoneAppId(e.target.value)} placeholder="Ej: qB7M528y26Y6Qk7673924v" />
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="payphoneToken" className="block font-semibold mb-2">
                                    Payphone Token *
                                </label>
                                <InputText id="payphoneToken" value={payphoneToken} onChange={(e) => setPayphoneToken(e.target.value)} placeholder="Ingresa tu token de autorización" type="password" />
                            </div>

                            <div className="col-12">
                                <Button label="Guardar Credenciales Payphone" icon="pi pi-save" onClick={guardarPayphone} loading={loading} className="mt-3" style={{ backgroundColor: '#f97316', borderColor: '#f97316' }} />
                            </div>
                        </div>
                    </TabPanel>

                    {/* Tab 4: Costo de Envío (solo el Emprendedor puede modificarlo) */}
                    <TabPanel header="Costo de Envío" leftIcon="pi pi-truck mr-2">
                        <div className="grid p-fluid">
                            <div className="col-12 mb-4">
                                <Message
                                    severity="info"
                                    text="Define la cuota fija de envío que se cobrará a tus clientes en cada pedido. El valor por defecto es $5.00."
                                    className="w-full justify-content-start"
                                />
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="costoEnvio" className="block font-semibold mb-2">
                                    Cuota fija de envío (USD) *
                                </label>
                                <InputNumber
                                    id="costoEnvio"
                                    value={costoEnvio}
                                    onValueChange={(e) => setCostoEnvio(e.value ?? 0)}
                                    mode="currency"
                                    currency="USD"
                                    locale="en-US"
                                    min={0}
                                    max={100}
                                    minFractionDigits={2}
                                    maxFractionDigits={2}
                                />
                            </div>

                            <div className="col-12">
                                <Button
                                    label="Guardar Costo de Envío"
                                    icon="pi pi-save"
                                    onClick={guardarCostoEnvio}
                                    loading={loading}
                                    className="mt-3"
                                    style={{ backgroundColor: '#0ea5e9', borderColor: '#0ea5e9' }}
                                />
                            </div>
                        </div>
                    </TabPanel>
                </TabView>
            </Card>
        </>
    );
}
