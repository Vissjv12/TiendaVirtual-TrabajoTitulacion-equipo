'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputSwitch } from 'primereact/inputswitch';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import categoriasEmpresasService, { EmpresaRequest } from '../../../../../services/categoriasEmpresasService';
import authService from '../../../../../services/authService';

const CrearEmpresaPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    useEffect(() => {
        if (!authService.isAdmin()) {
            router.push('/emprendedor/empresas');
        }
    }, [router]);

    // Estados del formulario
    const [formData, setFormData] = useState<EmpresaRequest>({
        nombre: '',
        ruc: '',
        direccion: '',
        telefono: '',
        email: '',
        activo: true
    });

    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);

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

    // Crear empresa
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
            console.log('📤 Creando empresa:', formData);
            const response = await categoriasEmpresasService.crearEmpresa(formData);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Empresa creada exitosamente',
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
                    detail: response.message || 'Error creando empresa',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error creando empresa:', error);
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

    return (
        <div className="crear-empresa">
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
                        <i className="pi pi-plus text-primary mr-3"></i>
                        Nueva Empresa
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Registra una nueva empresa proveedora de productos
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
                                    label={loading ? 'Creando...' : 'Crear Empresa'}
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

                {/* Panel de ayuda */}
                <div className="col-12 lg:col-4">
                    <Card className="bg-primary-50 border-primary-200">
                        <div className="text-center">
                            <i className="pi pi-info-circle text-primary text-4xl mb-3"></i>
                            <h3 className="text-primary font-bold mb-3">Información</h3>
                            <div className="text-left">
                                <div className="mb-3">
                                    <strong className="text-primary">RUC:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Debe ser válido y tener exactamente 13 dígitos
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Campos opcionales:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Dirección, teléfono y email son opcionales pero recomendados
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Estado:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Solo empresas activas aparecen al crear productos
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Validaciones:</strong>
                                    <ul className="text-600 mt-1 mb-0 list-none p-0">
                                        <li className="mb-1">• Nombre: 3-100 caracteres</li>
                                        <li className="mb-1">• RUC: Exactamente 13 dígitos</li>
                                        <li className="mb-1">• Teléfono: 9-15 dígitos</li>
                                        <li className="mb-1">• Email: Formato válido</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </Card>

                    {/* Panel adicional con consejos */}
                    <Card className="bg-green-50 border-green-200 mt-4">
                        <div className="text-center">
                            <i className="pi pi-lightbulb text-green-600 text-3xl mb-3"></i>
                            <h4 className="text-green-700 font-bold mb-3">💡 Consejos</h4>
                            <div className="text-left">
                                <ul className="text-600 list-none p-0 m-0">
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Verifica el RUC en el SRI antes de registrar
                                    </li>
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Usa un email corporativo para contacto
                                    </li>
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Mantén los datos actualizados
                                    </li>
                                    <li className="mb-0">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Nombres descriptivos facilitan la búsqueda
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default CrearEmpresaPage;