'use client';

import React, { useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputSwitch } from 'primereact/inputswitch';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import categoriasEmpresasService, { CategoriaRequest } from '../../../../../services/categoriasEmpresasService';

const CrearCategoriaPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados del formulario
    const [formData, setFormData] = useState<CategoriaRequest>({
        nombre: '',
        descripcion: '',
        activo: true
    });

    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);

    // Validar formulario
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!formData.nombre.trim()) {
            newErrors.nombre = 'El nombre es obligatorio';
        } else if (formData.nombre.length < 3) {
            newErrors.nombre = 'El nombre debe tener al menos 3 caracteres';
        } else if (formData.nombre.length > 100) {
            newErrors.nombre = 'El nombre no puede exceder 100 caracteres';
        }

        if (formData.descripcion && formData.descripcion.length > 250) {
            newErrors.descripcion = 'La descripción no puede exceder 250 caracteres';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Manejar cambios en el formulario
    const handleInputChange = (field: keyof CategoriaRequest, value: any) => {
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

    // Crear categoría
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
            console.log('📤 Creando categoría:', formData);
            const response = await categoriasEmpresasService.crearCategoria(formData);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Categoría creada exitosamente',
                    life: 3000
                });
                
                // Redirigir después de un breve delay
                setTimeout(() => {
                    router.push('/administrador/categorias');
                }, 1500);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error creando categoría',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error creando categoría:', error);
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
        router.push('/administrador/categorias');
    };

    return (
        <div className="crear-categoria">
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
                        Nueva Categoría
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Crea una nueva categoría para organizar tus productos
                </p>
            </div>

            {/* Formulario */}
            <div className="grid">
                <div className="col-12 lg:col-8">
                    <Card>
                        <form onSubmit={handleSubmit}>
                            <div className="grid">
                                {/* Nombre */}
                                <div className="col-12">
                                    <label htmlFor="nombre" className="block text-900 font-medium mb-2">
                                        Nombre <span className="text-red-500">*</span>
                                    </label>
                                    <InputText
                                        id="nombre"
                                        value={formData.nombre}
                                        onChange={(e) => handleInputChange('nombre', e.target.value)}
                                        placeholder="Ingresa el nombre de la categoría"
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

                                {/* Descripción */}
                                <div className="col-12">
                                    <label htmlFor="descripcion" className="block text-900 font-medium mb-2">
                                        Descripción
                                    </label>
                                    <InputTextarea
                                        id="descripcion"
                                        value={formData.descripcion || ''}
                                        onChange={(e) => handleInputChange('descripcion', e.target.value)}
                                        placeholder="Descripción opcional de la categoría"
                                        className={`w-full ${errors.descripcion ? 'p-invalid' : ''}`}
                                        rows={4}
                                        maxLength={250}
                                    />
                                    {errors.descripcion && (
                                        <small className="p-error block mt-1">{errors.descripcion}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        {(formData.descripcion || '').length}/250 caracteres
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
                                        Las categorías inactivas no se mostrarán en la tienda
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
                                    label={loading ? 'Creando...' : 'Crear Categoría'}
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
                                    <strong className="text-primary">Nombre:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Debe ser único y descriptivo. Ejemplo: &quot;Smartphones&quot;, &quot;Ropa Deportiva&quot;
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Descripción:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Ayuda a los clientes y empleados a entender el tipo de productos
                                    </p>
                                </div>
                                <div>
                                    <strong className="text-primary">Estado:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Solo las categorías activas aparecen en la tienda
                                    </p>
                                </div>
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default CrearCategoriaPage;