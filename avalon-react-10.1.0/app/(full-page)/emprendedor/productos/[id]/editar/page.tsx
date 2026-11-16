'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter, useParams } from 'next/navigation';
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
import { Skeleton } from 'primereact/skeleton';
import emprendedorService, { ProductoRequest, ProductoEmprendedor, Categoria, Empresa } from '../../../../../../services/emprendedorService';
import authService from '../../../../../../services/authService';

const EditarProductoPage: React.FC = () => {
    const router = useRouter();
    const params = useParams();
    const toast = useRef<Toast>(null);
    const productoId = parseInt(params.id as string);

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
    const [productoOriginal, setProductoOriginal] = useState<ProductoEmprendedor | null>(null);
    const [categorias, setCategorias] = useState<Categoria[]>([]);
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(false);
    const [loadingData, setLoadingData] = useState(true);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const isAdmin = authService.isAdmin();

    // Cargar datos iniciales
    useEffect(() => {
        if (productoId) {
            cargarDatos();
        }
    }, [productoId]);

    const cargarDatos = async () => {
        try {
            setLoadingData(true);
            
            // Cargar producto, categorías y empresas en paralelo
            const [productoRes, categoriasRes, empresasRes] = await Promise.all([
                emprendedorService.obtenerProductoPorId(productoId),
                emprendedorService.obtenerCategorias(),
                emprendedorService.obtenerEmpresas()
            ]);

            if (productoRes.success && productoRes.data) {
                const producto = productoRes.data;

                // 🔒 Validar que el emprendedor tenga permisos sobre la empresa del producto
                if (!isAdmin && !authService.canManageEmpresa(producto.empresa?.id)) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'Acceso denegado (403)',
                        detail: 'El usuario no puede operar sobre este producto porque pertenece a otra empresa.',
                        life: 4000
                    });
                    setTimeout(() => router.push('/emprendedor/productos'), 1500);
                    return;
                }

                setProductoOriginal(producto);
                
                // Llenar el formulario con los datos del producto
                setFormData({
                    nombre: producto.nombre,
                    descripcion: producto.descripcion,
                    precio: producto.precio,
                    imagen: producto.imagen,
                    activo: producto.activo,
                    categoriaId: producto.categoria.id,
                    empresaId: producto.empresa.id
                });
            } else {
                if (productoRes.status === 401) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'No autenticado (401)',
                        detail: productoRes.message || 'Tu sesión ha expirado. Inicia sesión nuevamente.',
                        life: 3500
                    });
                    setTimeout(() => router.push('/auth/login2'), 1500);
                    return;
                }
                toast.current?.show({
                    severity: productoRes.status === 403 ? 'warn' : 'error',
                    summary: productoRes.status === 403 ? 'Acceso denegado (403)' : 'Error',
                    detail: productoRes.message || 'Producto no encontrado',
                    life: 3500
                });
                setTimeout(() => router.push('/emprendedor/productos'), 1500);
                return;
            }

            if (categoriasRes.success && categoriasRes.data) {
                setCategorias(categoriasRes.data);
            }

            if (empresasRes.success && empresasRes.data) {
                setEmpresas(empresasRes.data);
            }

        } catch (error) {
            console.error('Error cargando datos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando datos del producto',
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

        if (!formData.precio || formData.precio <= 0) {
            newErrors.precio = 'El precio debe ser mayor a 0';
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

    // Actualizar producto
    const actualizarProducto = async () => {
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
            const response = await emprendedorService.actualizarProducto(productoId, formData);
            
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

    // Verificar si hay cambios
    const hayCambios = (): boolean => {
        if (!productoOriginal) return false;
        
        return (
            formData.nombre !== productoOriginal.nombre ||
            formData.descripcion !== productoOriginal.descripcion ||
            formData.precio !== productoOriginal.precio ||
            formData.imagen !== productoOriginal.imagen ||
            formData.activo !== productoOriginal.activo ||
            formData.categoriaId !== productoOriginal.categoria.id ||
            formData.empresaId !== productoOriginal.empresa.id
        );
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
            <div className="editar-producto-loading">
                <Card>
                    <div className="grid">
                        <div className="col-12 lg:col-8">
                            <div className="mb-4">
                                <Skeleton width="300px" height="2rem" className="mb-2" />
                                <Skeleton width="500px" height="1rem" />
                            </div>
                            <div className="grid">
                                <div className="col-6">
                                    <Skeleton width="100%" height="3rem" className="mb-3" />
                                </div>
                                <div className="col-6">
                                    <Skeleton width="100%" height="3rem" className="mb-3" />
                                </div>
                                <div className="col-12">
                                    <Skeleton width="100%" height="6rem" className="mb-3" />
                                </div>
                            </div>
                        </div>
                        <div className="col-12 lg:col-4">
                            <Skeleton width="100%" height="300px" />
                        </div>
                    </div>
                </Card>
            </div>
        );
    }

    if (!productoOriginal) {
        return (
            <div className="text-center p-4">
                <i className="pi pi-exclamation-triangle text-4xl text-orange-500 mb-3"></i>
                <h3>Producto no encontrado</h3>
                <Button 
                    label="Volver a productos" 
                    onClick={() => router.push('/emprendedor/productos')}
                />
            </div>
        );
    }

    return (
        <div className="editar-producto">
            <Toast ref={toast} />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center justify-content-between">
                    <div>
                        <h1 className="text-3xl font-bold text-900 mb-2">
                            <i className="pi pi-pencil text-primary mr-3"></i>
                            Editar Producto
                        </h1>
                        <p className="text-600 text-lg">
                            Modificar información del producto: <strong>{productoOriginal.nombre}</strong>
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

            {/* Alerta de cambios */}
            {hayCambios() && (
                <div 
                    className="p-3 mb-4 border-round-lg flex align-items-center"
                    style={{
                        backgroundColor: 'var(--orange-50)',
                        border: '1px solid var(--orange-200)',
                        color: 'var(--orange-900)'
                    }}
                >
                    <i className="pi pi-exclamation-triangle mr-2"></i>
                    <span>Hay cambios sin guardar en el formulario</span>
                </div>
            )}

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
                                        Vinculado a tu empresa autorizada
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

                            {/* Info del stock si existe */}
                            {productoOriginal.inventario && (
                                <div className="mt-3 p-2 border-round-lg bg-blue-50 border-blue-200">
                                    <small className="text-blue-800">
                                        <i className="pi pi-warehouse mr-1"></i>
                                        Stock actual: {productoOriginal.inventario.cantidad} unidades
                                    </small>
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
                    label={loading ? 'Guardando...' : 'Guardar Cambios'} 
                    icon={loading ? 'pi pi-spin pi-spinner' : 'pi pi-check'} 
                    onClick={actualizarProducto}
                    loading={loading}
                    disabled={!hayCambios()}
                />
            </div>
        </div>
    );
};

export default EditarProductoPage;