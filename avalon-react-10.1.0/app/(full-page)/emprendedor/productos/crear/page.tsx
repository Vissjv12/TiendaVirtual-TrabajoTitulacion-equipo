'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputNumber } from 'primereact/inputnumber';
import { Dropdown } from 'primereact/dropdown';
import { Checkbox } from 'primereact/checkbox';
import { Toast } from 'primereact/toast';
import { Image } from 'primereact/image';
import { ProgressBar } from 'primereact/progressbar';
import emprendedorService, { ProductoRequest, Categoria, Empresa } from '../../../../../services/emprendedorService';
import authService from '../../../../../services/authService';

const CrearProductoPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados del formulario
    const [formData, setFormData] = useState<ProductoRequest>({
        nombre: '',
        descripcion: '',
        precio: 0,
        imagen: '',
        activo: true,
        categoriaId: 0,
        empresaId: 0
    });

    // Estados adicionales
    const [categorias, setCategorias] = useState<Categoria[]>([]);
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(false);
    const [loadingData, setLoadingData] = useState(true);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const isAdmin = authService.isAdmin();
    const userEmpresaId = authService.getEmpresaId();

    // Cargar categorías y empresas
    useEffect(() => {
        cargarDatosIniciales();
    }, []);

    const cargarDatosIniciales = async () => {
        try {
            setLoadingData(true);
            
            const [categoriasRes, empresasRes] = await Promise.all([
                emprendedorService.obtenerCategorias(),
                emprendedorService.obtenerEmpresas()
            ]);

            if (categoriasRes.success && categoriasRes.data) {
                setCategorias(categoriasRes.data);
            }

            if (empresasRes.success && empresasRes.data) {
                setEmpresas(empresasRes.data);
                // 🔒 Si es ROLE_EMP, preseleccionar y bloquear su propia empresa
                if (!isAdmin && userEmpresaId) {
                    setFormData(prev => ({ ...prev, empresaId: userEmpresaId }));
                } else if (empresasRes.data.length === 1) {
                    setFormData(prev => ({ ...prev, empresaId: empresasRes.data![0].id }));
                }
            }

        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando datos iniciales',
                life: 3000
            });
        } finally {
            setLoadingData(false);
        }
    };

    // Manejar cambios en el formulario
    const handleChange = (field: keyof ProductoRequest, value: any) => {
        setFormData(prev => ({
            ...prev,
            [field]: value
        }));
        
        // Limpiar error del campo
        if (errors[field]) {
            setErrors(prev => ({
                ...prev,
                [field]: ''
            }));
        }
    };

    // Validar formulario
    const validarFormulario = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (!formData.nombre.trim()) {
            newErrors.nombre = 'El nombre es obligatorio';
        } else if (formData.nombre.length < 3) {
            newErrors.nombre = 'El nombre debe tener al menos 3 caracteres';
        }

        if (!formData.descripcion.trim()) {
            newErrors.descripcion = 'La descripción es obligatoria';
        }

        if (!formData.categoriaId || formData.categoriaId === 0) {
            newErrors.categoriaId = 'Debe seleccionar una categoría';
        }

        if (!formData.empresaId || formData.empresaId === 0) {
            newErrors.empresaId = 'Debe seleccionar una empresa';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Guardar producto
    const guardarProducto = async () => {
        if (!validarFormulario()) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Formulario incompleto',
                detail: 'Por favor, complete todos los campos obligatorios',
                life: 3000
            });
            return;
        }

        setLoading(true);
        try {
            const response = await emprendedorService.crearProducto(formData);
            
            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: response.message,
                    life: 3000
                });
                
                // Redirigir después de un breve delay
                setTimeout(() => {
                    router.push('/emprendedor/productos');
                }, 1500);
            } else {
                if (response.status === 401) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'No autenticado (401)',
                        detail: response.message || 'Tu sesión ha expirado. Inicia sesión nuevamente.',
                        life: 3500
                    });
                    setTimeout(() => router.push('/auth/login2'), 1500);
                    return;
                }
                toast.current?.show({
                    severity: response.status === 403 ? 'warn' : 'error',
                    summary: response.status === 403 ? 'Acceso denegado (403)' : 'Error',
                    detail: response.message,
                    life: 4000
                });
            }
        } catch (error) {
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

    // Opciones para dropdowns
    const categoriaOptions = categorias.map(cat => ({
        label: cat.nombre,
        value: cat.id
    }));

    const empresaOptions = empresas.map(emp => ({
        label: emp.nombre,
        value: emp.id
    }));

    if (loadingData) {
        return (
            <div className="crear-producto-loading">
                <Card>
                    <div className="text-center p-4">
                        <ProgressBar mode="indeterminate" style={{ height: '6px' }} />
                        <p className="mt-3 text-600">Cargando formulario...</p>
                    </div>
                </Card>
            </div>
        );
    }

    return (
        <div className="crear-producto">
            <Toast ref={toast} />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center justify-content-between">
                    <div>
                        <h1 className="text-3xl font-bold text-900 mb-2">
                            <i className="pi pi-plus text-primary mr-3"></i>
                            Crear Nuevo Producto
                        </h1>
                        <p className="text-600 text-lg">
                            Agrega un nuevo producto al catálogo
                        </p>
                    </div>
                    <Button 
                        label="Volver" 
                        icon="pi pi-arrow-left" 
                        className="p-button-outlined"
                        onClick={() => router.push('/emprendedor/productos')}
                    />
                </div>
            </div>

            {/* Formulario */}
            <div className="grid">
                <div className="col-12 lg:col-8">
                    <Card title="Información del Producto">
                        <div className="grid">
                            {/* Nombre */}
                            <div className="col-12 md:col-6">
                                <label className="block mb-2 font-semibold text-900">
                                    Nombre del Producto *
                                </label>
                                <InputText
                                    value={formData.nombre}
                                    onChange={(e) => handleChange('nombre', e.target.value)}
                                    placeholder="Ej: iPhone 15 Pro"
                                    className={`w-full ${errors.nombre ? 'p-invalid' : ''}`}
                                />
                                {errors.nombre && (
                                    <small className="p-error block mt-1">{errors.nombre}</small>
                                )}
                            </div>

                            {/* Precio */}
                            <div className="col-12 md:col-6">
                                <label className="block mb-2 font-semibold text-900">
                                    Precio (USD) *
                                </label>
                                <InputNumber
                                    value={formData.precio}
                                    onValueChange={(e) => handleChange('precio', e.value || 0)}
                                    mode="currency"
                                    currency="USD"
                                    locale="en-US"
                                    placeholder="0.00"
                                    className={`w-full ${errors.precio ? 'p-invalid' : ''}`}
                                />
                                {errors.precio && (
                                    <small className="p-error block mt-1">{errors.precio}</small>
                                )}
                            </div>

                            {/* Descripción */}
                            <div className="col-12">
                                <label className="block mb-2 font-semibold text-900">
                                    Descripción *
                                </label>
                                <InputTextarea
                                    value={formData.descripcion}
                                    onChange={(e) => handleChange('descripcion', e.target.value)}
                                    placeholder="Describe las características del producto..."
                                    rows={4}
                                    className={`w-full ${errors.descripcion ? 'p-invalid' : ''}`}
                                />
                                {errors.descripcion && (
                                    <small className="p-error block mt-1">{errors.descripcion}</small>
                                )}
                            </div>

                            {/* Categoría */}
                            <div className="col-12 md:col-6">
                                <label className="block mb-2 font-semibold text-900">
                                    Categoría *
                                </label>
                                <Dropdown
                                    value={formData.categoriaId}
                                    onChange={(e) => handleChange('categoriaId', e.value)}
                                    options={categoriaOptions}
                                    placeholder="Seleccionar categoría"
                                    className={`w-full ${errors.categoriaId ? 'p-invalid' : ''}`}
                                />
                                {errors.categoriaId && (
                                    <small className="p-error block mt-1">{errors.categoriaId}</small>
                                )}
                            </div>

                            {/* Empresa */}
                            <div className="col-12 md:col-6">
                                <label className="block mb-2 font-semibold text-900">
                                    Empresa *
                                </label>
                                <Dropdown
                                    value={formData.empresaId}
                                    onChange={(e) => handleChange('empresaId', e.value)}
                                    options={empresaOptions}
                                    placeholder="Seleccionar empresa"
                                    className={`w-full ${errors.empresaId ? 'p-invalid' : ''}`}
                                    disabled={!isAdmin}
                                />
                                {!isAdmin && (
                                    <small className="text-600 block mt-1">
                                        Vinculado automáticamente a tu empresa autorizada
                                    </small>
                                )}
                                {errors.empresaId && (
                                    <small className="p-error block mt-1">{errors.empresaId}</small>
                                )}
                            </div>

                            {/* URL de imagen */}
                            <div className="col-12">
                                <label className="block mb-2 font-semibold text-900">
                                    URL de la Imagen
                                </label>
                                <InputText
                                    value={formData.imagen}
                                    onChange={(e) => handleChange('imagen', e.target.value)}
                                    placeholder="https://ejemplo.com/imagen-producto.jpg"
                                    className="w-full"
                                />
                                <small className="text-600 block mt-1">
                                    Opcional: URL de la imagen del producto
                                </small>
                            </div>

                            {/* Estado activo */}
                            <div className="col-12">
                                <div className="flex align-items-center">
                                    <Checkbox
                                        inputId="activo"
                                        checked={formData.activo || false}
                                        onChange={(e) => handleChange('activo', e.checked || false)}
                                    />
                                    <label htmlFor="activo" className="ml-2 font-semibold text-900">
                                        Producto activo
                                    </label>
                                </div>
                                <small className="text-600 block mt-1">
                                    Los productos activos aparecerán en la tienda pública
                                </small>
                            </div>
                        </div>
                    </Card>
                </div>

                {/* Vista previa */}
                <div className="col-12 lg:col-4">
                    <Card title="Vista Previa">
                        <div className="text-center">
                            {formData.imagen ? (
                                <Image
                                    src={formData.imagen}
                                    alt="Vista previa"
                                    width="100%"
                                    className="border-round-lg shadow-2 mb-3"
                                    onError={(e) => {
                                        (e.target as HTMLImageElement).src = 'https://via.placeholder.com/300x300?text=Error+de+imagen';
                                    }}
                                    preview
                                />
                            ) : (
                                <div 
                                    className="flex align-items-center justify-content-center border-round-lg mb-3"
                                    style={{
                                        height: '200px',
                                        backgroundColor: 'var(--surface-100)',
                                        border: '2px dashed var(--surface-300)'
                                    }}
                                >
                                    <div className="text-center">
                                        <i className="pi pi-image text-4xl text-400 mb-2"></i>
                                        <p className="text-600 m-0">Sin imagen</p>
                                    </div>
                                </div>
                            )}

                            <h3 className="text-xl font-bold text-900 mb-2">
                                {formData.nombre || 'Nombre del producto'}
                            </h3>

                            {formData.precio > 0 && (
                                <div className="text-2xl font-bold text-primary mb-3">
                                    {emprendedorService.formatearPrecio(formData.precio)}
                                </div>
                            )}

                            <p className="text-600 line-height-3">
                                {formData.descripcion || 'Descripción del producto...'}
                            </p>

                            {(formData.categoriaId > 0 || formData.empresaId > 0) && (
                                <div className="flex justify-content-center gap-2 mt-3">
                                    {formData.categoriaId > 0 && (
                                        <span className="p-tag p-tag-rounded">
                                            {categorias.find(c => c.id === formData.categoriaId)?.nombre}
                                        </span>
                                    )}
                                    {formData.empresaId > 0 && (
                                        <span className="p-tag p-tag-rounded p-tag-primary">
                                            {empresas.find(e => e.id === formData.empresaId)?.nombre}
                                        </span>
                                    )}
                                </div>
                            )}
                        </div>
                    </Card>
                </div>
            </div>

            {/* Botones de acción */}
            <div className="flex justify-content-end gap-2 mt-4">
                <Button 
                    label="Cancelar" 
                    icon="pi pi-times" 
                    className="p-button-outlined"
                    onClick={() => router.push('/emprendedor/productos')}
                    disabled={loading}
                />
                <Button 
                    label={loading ? 'Guardando...' : 'Guardar Producto'} 
                    icon={loading ? 'pi pi-spin pi-spinner' : 'pi pi-check'} 
                    onClick={guardarProducto}
                    loading={loading}
                />
            </div>
        </div>
    );
};

export default CrearProductoPage;