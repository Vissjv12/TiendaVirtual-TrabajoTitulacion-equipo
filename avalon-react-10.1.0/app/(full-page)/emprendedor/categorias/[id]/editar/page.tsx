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
import categoriasEmpresasService, { CategoriaRequest, Categoria } from '../../../../../../services/categoriasEmpresasService';
import authService from '../../../../../../services/authService';

const EditarCategoriaPage: React.FC = () => {
    const router = useRouter();
    const params = useParams();
    const toast = useRef<Toast>(null);
    const categoriaId = Number(params.id);

    // Estados del formulario
    const [formData, setFormData] = useState<CategoriaRequest>({
        nombre: '',
        descripcion: '',
        activo: true
    });

    const [originalData, setOriginalData] = useState<Categoria | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);
    const [loadingData, setLoadingData] = useState(true);

    // Cargar datos de la categoría
    useEffect(() => {
        if (!authService.isAdmin()) {
            router.push('/emprendedor/categorias');
            return;
        }
        if (categoriaId) {
            cargarCategoria();
        }
    }, [categoriaId]);

    const cargarCategoria = async () => {
        setLoadingData(true);
        try {
            console.log('🔄 Cargando categoría ID:', categoriaId);
            const response = await categoriasEmpresasService.obtenerCategoriaPorId(categoriaId);

            if (response.success && response.data) {
                const categoria = response.data;
                setOriginalData(categoria);
                setFormData({
                    nombre: categoria.nombre,
                    descripcion: categoria.descripcion || '',
                    activo: categoria.activo
                });
                console.log('✅ Categoría cargada:', categoria);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Categoría no encontrada',
                    life: 3000
                });
                router.push('/emprendedor/categorias');
            }
        } catch (error) {
            console.error('❌ Error cargando categoría:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
            router.push('/emprendedor/categorias');
        } finally {
            setLoadingData(false);
        }
    };

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

    // Actualizar categoría
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
            console.log('📤 Actualizando categoría:', formData);
            const response = await categoriasEmpresasService.actualizarCategoria(categoriaId, formData);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Categoría actualizada exitosamente',
                    life: 3000
                });
                
                // Redirigir después de un breve delay
                setTimeout(() => {
                    router.push('/emprendedor/categorias');
                }, 1500);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error actualizando categoría',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error actualizando categoría:', error);
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
        router.push('/emprendedor/categorias');
    };

    // Renderizar esqueleto mientras carga
    const renderSkeleton = () => (
        <Card>
            <div className="grid">
                <div className="col-12">
                    <Skeleton width="100%" height="2rem" className="mb-2" />
                    <Skeleton width="100%" height="3rem" className="mb-4" />
                </div>
                <div className="col-12">
                    <Skeleton width="100%" height="2rem" className="mb-2" />
                    <Skeleton width="100%" height="6rem" className="mb-4" />
                </div>
                <div className="col-12">
                    <Skeleton width="150px" height="2rem" className="mb-4" />
                </div>
            </div>
        </Card>
    );

    if (loadingData) {
        return (
            <div className="editar-categoria">
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
                            Editar Categoría
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
        <div className="editar-categoria">
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
                        Editar Categoría
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Modifica los datos de la categoría "{originalData?.nombre}"
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
                                    label={loading ? 'Actualizando...' : 'Actualizar Categoría'}
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
                            <h3 className="text-orange-600 font-bold mb-3">Editando Categoría</h3>
                            <div className="text-left">
                                <div className="mb-3">
                                    <strong className="text-orange-600">Consejos:</strong>
                                    <ul className="text-600 mt-1 mb-0 list-none p-0">
                                        <li className="mb-1">• Verifica que el nombre sea único</li>
                                        <li className="mb-1">• Una buena descripción ayuda a los usuarios</li>
                                        <li className="mb-1">• Desactiva solo si no hay productos asociados</li>
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

export default EditarCategoriaPage;