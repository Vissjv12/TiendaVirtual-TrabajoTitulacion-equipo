'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputSwitch } from 'primereact/inputswitch';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import { Skeleton } from 'primereact/skeleton';
import categoriasEmpresasService, { EmpresaRequest, Empresa } from '../../../../../../services/categoriasEmpresasService';
import authService from '../../../../../../services/authService';

const EditarEmpresaPage: React.FC = () => {
    const router = useRouter();
    const params = useParams();
    const toast = useRef<Toast>(null);
    const empresaId = Number(params.id);

    // Estados del formulario
    const [formData, setFormData] = useState<EmpresaRequest>({
        nombre: '',
        ruc: '',
        direccion: '',
        telefono: '',
        email: '',
        activo: true
    });

    const [originalData, setOriginalData] = useState<Empresa | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);
    const [loadingData, setLoadingData] = useState(true);

    // Cargar datos de la empresa
    useEffect(() => {
        if (empresaId) {
            if (!authService.canManageEmpresa(empresaId)) {
                toast.current?.show({
                    severity: 'warn',
                    summary: 'Acceso denegado (403)',
                    detail: 'No tienes permisos para editar los datos de otra empresa.',
                    life: 3500
                });
                setTimeout(() => router.push('/emprendedor/empresas'), 1500);
                return;
            }
            cargarEmpresa();
        }
    }, [empresaId]);

    const cargarEmpresa = async () => {
        setLoadingData(true);
        try {
            console.log('🔄 Cargando empresa ID:', empresaId);
            const response = await categoriasEmpresasService.obtenerEmpresaPorId(empresaId);

            if (response.success && response.data) {
                const empresa = response.data;
                setOriginalData(empresa);
                setFormData({
                    nombre: empresa.nombre,
                    ruc: empresa.ruc,
                    direccion: empresa.direccion || '',
                    telefono: empresa.telefono || '',
                    email: empresa.email || '',
                    activo: empresa.activo
                });
                console.log('✅ Empresa cargada:', empresa);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Empresa no encontrada',
                    life: 3000
                });
                router.push('/emprendedor/empresas');
            }
        } catch (error) {
            console.error('❌ Error cargando empresa:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
            router.push('/emprendedor/empresas');
        } finally {
            setLoadingData(false);
        }
    };

    // Validar formulario
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        // Validar nombre
        if (!formData.nombre.trim()) {
            newErrors.nombre = 'El nombre es obligatorio';
        } else if (formData.nombre.length < 3) {
            newErrors.nombre = 'El nombre debe tener al menos 3 caracteres';
        } else if (formData.nombre.length > 100) {
            newErrors.nombre = 'El nombre no puede exceder 100 caracteres';
        }

        // Validar RUC
        if (!formData.ruc.trim()) {
            newErrors.ruc = 'El RUC es obligatorio';
        } else if (!/^[0-9]{13}$/.test(formData.ruc)) {
            newErrors.ruc = 'El RUC debe tener exactamente 13 dígitos numéricos';
        }

        // Validar dirección (opcional)
        if (formData.direccion && formData.direccion.length > 200) {
            newErrors.direccion = 'La dirección no puede exceder 200 caracteres';
        }

        // Validar teléfono (opcional)
        if (formData.telefono && !/^[0-9]{9,15}$/.test(formData.telefono)) {
            newErrors.telefono = 'El teléfono debe tener entre 9 y 15 dígitos numéricos';
        }

        // Validar email (opcional)
        if (formData.email) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(formData.email)) {
                newErrors.email = 'El formato del email no es válido';
            } else if (formData.email.length > 100) {
                newErrors.email = 'El email no puede exceder 100 caracteres';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Manejar cambios en el formulario
    const handleInputChange = (field: keyof EmpresaRequest, value: any) => {
        setFormData(prev => ({
            ...prev,
            [field]: value
        }));
        
        // Limpiar error del campo cuando el usuario escribe
        if (errors[field]) {
            setErrors(prev => ({
                ...prev,
                [field]: ''
            }));
        }
    };

    // Formatear RUC mientras se escribe
    const handleRucChange = (value: string) => {
        // Solo permitir números
        const cleanValue = value.replace(/\D/g, '');
        // Limitar a 13 dígitos
        const limitedValue = cleanValue.slice(0, 13);
        handleInputChange('ruc', limitedValue);
    };

    // Formatear teléfono mientras se escribe
    const handleTelefonoChange = (value: string) => {
        // Solo permitir números
        const cleanValue = value.replace(/\D/g, '');
        // Limitar a 15 dígitos
        const limitedValue = cleanValue.slice(0, 15);
        handleInputChange('telefono', limitedValue);
    };

    // Actualizar empresa
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        
        if (!validateForm()) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Formulario Incompleto',
                detail: 'Por favor corrige los errores en el formulario',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            console.log('📤 Actualizando empresa:', formData);
            const response = await categoriasEmpresasService.actualizarEmpresa(empresaId, formData);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Empresa actualizada exitosamente',
                    life: 3000
                });
                
                // Redirigir después de un breve delay
                setTimeout(() => {
                    router.push('/emprendedor/empresas');
                }, 1500);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error actualizando empresa',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error actualizando empresa:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    // Cancelar y volver
    const handleCancel = () => {
        router.push('/emprendedor/empresas');
    };

    // Renderizar esqueleto mientras carga
    const renderSkeleton = () => (
        <Card>
            <div className="grid">
                <div className="col-12 md:col-6">
                    <Skeleton width="100%" height="2rem" className="mb-2" />
                    <Skeleton width="100%" height="3rem" className="mb-4" />
                </div>
                <div className="col-12 md:col-6">
                    <Skeleton width="100%" height="2rem" className="mb-2" />
                    <Skeleton width="100%" height="3rem" className="mb-4" />
                </div>
                <div className="col-12">
                    <Skeleton width="100%" height="2rem" className="mb-2" />
                    <Skeleton width="100%" height="6rem" className="mb-4" />
                </div>
            </div>
        </Card>
    );

    if (loadingData) {
        return (
            <div className="editar-empresa">
                <Toast ref={toast} />
                
                <div className="mb-4">
                    <div className="flex align-items-center gap-2 mb-2">
                        <Button
                            icon="pi pi-arrow-left"
                            className="p-button-text p-button-rounded"
                            onClick={handleCancel}
                        />
                        <h1 className="text-3xl font-bold text-900 m-0">
                            <i className="pi pi-pencil text-primary mr-3"></i>
                            Editar Empresa
                        </h1>
                    </div>
                    <p className="text-600 text-lg ml-6">Cargando datos...</p>
                </div>

                <div className="grid">
                    <div className="col-12 lg:col-8">
                        {renderSkeleton()}
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="editar-empresa">
            <Toast ref={toast} />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center gap-2 mb-2">
                    <Button
                        icon="pi pi-arrow-left"
                        className="p-button-text p-button-rounded"
                        onClick={handleCancel}
                        tooltip="Volver al listado"
                    />
                    <h1 className="text-3xl font-bold text-900 m-0">
                        <i className="pi pi-pencil text-primary mr-3"></i>
                        Editar Empresa
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Modifica los datos de la empresa "{originalData?.nombre}"
                </p>
            </div>

            {/* Formulario */}
            <div className="grid">
                <div className="col-12 lg:col-8">
                    <Card>
                        <form onSubmit={handleSubmit}>
                            <div className="grid">
                                {/* Nombre */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="nombre" className="block text-900 font-medium mb-2">
                                        Nombre <span className="text-red-500">*</span>
                                    </label>
                                    <InputText
                                        id="nombre"
                                        value={formData.nombre}
                                        onChange={(e) => handleInputChange('nombre', e.target.value)}
                                        placeholder="Nombre de la empresa"
                                        className={`w-full ${errors.nombre ? 'p-invalid' : ''}`}
                                        maxLength={100}
                                    />
                                    {errors.nombre && (
                                        <small className="p-error block mt-1">{errors.nombre}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {formData.nombre.length}/100 caracteres
                                    </small>
                                </div>

                                {/* RUC */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="ruc" className="block text-900 font-medium mb-2">
                                        RUC <span className="text-red-500">*</span>
                                    </label>
                                    <InputText
                                        id="ruc"
                                        value={formData.ruc}
                                        onChange={(e) => handleRucChange(e.target.value)}
                                        placeholder="1234567890123"
                                        className={`w-full ${errors.ruc ? 'p-invalid' : ''}`}
                                        maxLength={13}
                                    />
                                    {errors.ruc && (
                                        <small className="p-error block mt-1">{errors.ruc}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {formData.ruc.length}/13 dígitos
                                    </small>
                                </div>

                                {/* Dirección */}
                                <div className="col-12">
                                    <label htmlFor="direccion" className="block text-900 font-medium mb-2">
                                        Dirección
                                    </label>
                                    <InputTextarea
                                        id="direccion"
                                        value={formData.direccion || ''}
                                        onChange={(e) => handleInputChange('direccion', e.target.value)}
                                        placeholder="Dirección de la empresa"
                                        className={`w-full ${errors.direccion ? 'p-invalid' : ''}`}
                                        rows={3}
                                        maxLength={200}
                                    />
                                    {errors.direccion && (
                                        <small className="p-error block mt-1">{errors.direccion}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {(formData.direccion || '').length}/200 caracteres
                                    </small>
                                </div>

                                {/* Teléfono */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="telefono" className="block text-900 font-medium mb-2">
                                        Teléfono
                                    </label>
                                    <InputText
                                        id="telefono"
                                        value={formData.telefono || ''}
                                        onChange={(e) => handleTelefonoChange(e.target.value)}
                                        placeholder="0987654321"
                                        className={`w-full ${errors.telefono ? 'p-invalid' : ''}`}
                                        maxLength={15}
                                    />
                                    {errors.telefono && (
                                        <small className="p-error block mt-1">{errors.telefono}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {(formData.telefono || '').length}/15 dígitos
                                    </small>
                                </div>

                                {/* Email */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="email" className="block text-900 font-medium mb-2">
                                        Email
                                    </label>
                                    <InputText
                                        id="email"
                                        value={formData.email || ''}
                                        onChange={(e) => handleInputChange('email', e.target.value)}
                                        placeholder="empresa@ejemplo.com"
                                        className={`w-full ${errors.email ? 'p-invalid' : ''}`}
                                        maxLength={100}
                                        type="email"
                                    />
                                    {errors.email && (
                                        <small className="p-error block mt-1">{errors.email}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {(formData.email || '').length}/100 caracteres
                                    </small>
                                </div>

                                {/* Estado */}
                                <div className="col-12">
                                    <label htmlFor="activo" className="block text-900 font-medium mb-2">
                                        Estado
                                    </label>
                                    <div className="flex align-items-center gap-2">
                                        <InputSwitch
                                            id="activo"
                                            checked={formData.activo || false}
                                            onChange={(e) => handleInputChange('activo', e.value)}
                                        />
                                        <span className="text-600">
                                            {formData.activo ? 'Activo' : 'Inactivo'}
                                        </span>
                                    </div>
                                    <small className="text-600 block mt-1">
                                        Las empresas inactivas no aparecerán como opción al crear productos
                                    </small>
                                </div>
                            </div>

                            <Divider />

                            {/* Información adicional */}
                            {originalData && (
                                <div className="bg-blue-50 p-3 border-round mb-4">
                                    <div className="grid">
                                        <div className="col-6">
                                            <strong className="text-blue-700">ID:</strong>
                                            <span className="ml-2 text-600">#{originalData.id}</span>
                                        </div>
                                        <div className="col-6">
                                            <strong className="text-blue-700">Creado:</strong>
                                            <span className="ml-2 text-600">
                                                {categoriasEmpresasService.formatearFecha(originalData.fechaCreacion)}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Botones */}
                            <div className="flex justify-content-end gap-2">
                                <Button
                                    label="Cancelar"
                                    icon="pi pi-times"
                                    className="p-button-outlined"
                                    onClick={handleCancel}
                                    type="button"
                                    disabled={loading}
                                />
                                <Button
                                    label={loading ? 'Actualizando...' : 'Actualizar Empresa'}
                                    icon={loading ? undefined : 'pi pi-check'}
                                    className="p-button-success"
                                    type="submit"
                                    disabled={loading}
                                >
                                    {loading && <ProgressSpinner style={{ width: '20px', height: '20px' }} className="mr-2" />}
                                </Button>
                            </div>
                        </form>
                    </Card>
                </div>

                {/* Panel de información */}
                <div className="col-12 lg:col-4">
                    <Card className="bg-orange-50 border-orange-200">
                        <div className="text-center">
                            <i className="pi pi-pencil text-orange-500 text-4xl mb-3"></i>
                            <h3 className="text-orange-600 font-bold mb-3">Editando Empresa</h3>
                            <div className="text-left">
                                <div className="mb-3">
                                    <strong className="text-orange-600">Consejos:</strong>
                                    <ul className="text-600 mt-1 mb-0 list-none p-0">
                                        <li className="mb-1">• Verifica que el RUC sea único y válido</li>
                                        <li className="mb-1">• Mantén los datos de contacto actualizados</li>
                                        <li className="mb-1">• Desactiva solo si no tiene productos asociados</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default EditarEmpresaPage;