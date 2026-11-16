'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputNumber } from 'primereact/inputnumber';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import { AutoComplete } from 'primereact/autocomplete';
import inventarioService, { InventarioRequest } from '../../../../../services/inventarioService';
import emprendedorService, { ProductoEmprendedor } from '../../../../../services/emprendedorService';

const CrearInventarioPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados del formulario
    const [formData, setFormData] = useState<InventarioRequest>({
        productoId: 0,
        cantidad: 0,
        ubicacion: 'Almacén principal'
    });

    // Estados para productos
    const [productos, setProductos] = useState<ProductoEmprendedor[]>([]);
    const [filteredProductos, setFilteredProductos] = useState<ProductoEmprendedor[]>([]);
    const [selectedProducto, setSelectedProducto] = useState<ProductoEmprendedor | null>(null);
    const [searchProducto, setSearchProducto] = useState('');

    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(false);
    const [loadingProductos, setLoadingProductos] = useState(true);

    // Opciones de ubicación predefinidas
    const ubicacionesComunes = [
        'Almacén principal',
        'Bodega A',
        'Bodega B',
        'Estantería 1',
        'Estantería 2',
        'Área de recepción',
        'Zona de despacho'
    ];

    // Cargar productos disponibles
    useEffect(() => {
        cargarProductos();
    }, []);

    const cargarProductos = async () => {
        setLoadingProductos(true);
        try {
            const response = await emprendedorService.obtenerProductosEmprendedor();
            if (response.success && response.data) {
                setProductos(response.data);
            }
        } catch (error) {
            console.error('Error cargando productos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando lista de productos',
                life: 3000
            });
        } finally {
            setLoadingProductos(false);
        }
    };

    // Buscar productos
    const searchProductos = (event: any) => {
        const query = event.query.toLowerCase();
        const filtered = productos.filter(producto => 
            producto.nombre.toLowerCase().includes(query) ||
            producto.descripcion?.toLowerCase().includes(query)
        );
        setFilteredProductos(filtered);
    };

    // Seleccionar producto
    const onProductoSelect = (producto: ProductoEmprendedor) => {
        setSelectedProducto(producto);
        setFormData(prev => ({ ...prev, productoId: producto.id }));
        setSearchProducto(producto.nombre);
        
        // Limpiar error del producto si existe
        if (errors.productoId) {
            setErrors(prev => ({ ...prev, productoId: '' }));
        }
    };

    // Template para mostrar productos en el autocomplete
    const productoTemplate = (producto: ProductoEmprendedor) => {
        return (
            <div className="flex align-items-center">
                <img 
                    src={producto.imagen || 'https://via.placeholder.com/40x40?text=P'} 
                    alt={producto.nombre}
                    className="w-2rem h-2rem border-round mr-2"
                />
                <div>
                    <div className="font-medium">{producto.nombre}</div>
                    <div className="text-sm text-600">{producto.categoria?.nombre}</div>
                </div>
            </div>
        );
    };

    // Validar formulario
    const validateForm = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (formData.productoId === 0) {
            newErrors.productoId = 'Selecciona un producto';
        }

        if (formData.cantidad <= 0) {
            newErrors.cantidad = 'La cantidad debe ser mayor a 0';
        }

        if (!formData.ubicacion?.trim()) {
            newErrors.ubicacion = 'La ubicación es obligatoria';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    // Manejar cambios en el formulario
    const handleInputChange = (field: keyof InventarioRequest, value: any) => {
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

    // Crear inventario
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
            console.log('📤 Creando inventario:', formData);
            const response = await inventarioService.crearInventario(formData);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Inventario creado exitosamente',
                    life: 3000
                });
                
                // Redirigir después de un breve delay
                setTimeout(() => {
                    router.push('/administrador/inventario');
                }, 1500);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error creando inventario',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error creando inventario:', error);
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
        router.push('/administrador/inventario');
    };

    return (
        <div className="crear-inventario">
            <Toast ref={toast} />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center gap-2 mb-2">
                    <Button
                        icon="pi pi-arrow-left"
                        className="p-button-text p-button-rounded"
                        onClick={handleCancel}
                        tooltip="Volver al inventario"
                    />
                    <h1 className="text-3xl font-bold text-900 m-0">
                        <i className="pi pi-plus text-primary mr-3"></i>
                        Nuevo Inventario
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Registra un nuevo producto en el inventario
                </p>
            </div>

            {/* Formulario */}
            <div className="grid">
                <div className="col-12 lg:col-8">
                    <Card>
                        <form onSubmit={handleSubmit}>
                            <div className="grid">
                                {/* Selección de producto */}
                                <div className="col-12">
                                    <label htmlFor="producto" className="block text-900 font-medium mb-2">
                                        Producto <span className="text-red-500">*</span>
                                    </label>
                                    <AutoComplete
                                        id="producto"
                                        value={searchProducto}
                                        suggestions={filteredProductos}
                                        completeMethod={searchProductos}
                                        field="nombre"
                                        itemTemplate={productoTemplate}
                                        onChange={(e) => setSearchProducto(e.value)}
                                        onSelect={(e) => onProductoSelect(e.value)}
                                        placeholder="Buscar producto..."
                                        className={`w-full ${errors.productoId ? 'p-invalid' : ''}`}
                                        disabled={loadingProductos}
                                        dropdown
                                    />
                                    {errors.productoId && (
                                        <small className="p-error block mt-1">{errors.productoId}</small>
                                    )}
                                    {loadingProductos && (
                                        <small className="text-600 block mt-1">Cargando productos...</small>
                                    )}
                                </div>

                                {/* Vista previa del producto seleccionado */}
                                {selectedProducto && (
                                    <div className="col-12">
                                        <div className="p-3 border-round bg-blue-50">
                                            <div className="flex align-items-center">
                                                <img 
                                                    src={selectedProducto.imagen || 'https://via.placeholder.com/60x60?text=P'} 
                                                    alt={selectedProducto.nombre}
                                                    className="w-4rem h-4rem border-round mr-3"
                                                />
                                                <div>
                                                    <div className="font-bold text-blue-900">{selectedProducto.nombre}</div>
                                                    <div className="text-blue-600">{selectedProducto.categoria?.nombre}</div>
                                                    <div className="text-blue-600">Precio: {emprendedorService.formatearPrecio(selectedProducto.precio)}</div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* Cantidad inicial */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="cantidad" className="block text-900 font-medium mb-2">
                                        Cantidad Inicial <span className="text-red-500">*</span>
                                    </label>
                                    <InputNumber
                                        id="cantidad"
                                        value={formData.cantidad}
                                        onValueChange={(e) => handleInputChange('cantidad', e.value || 0)}
                                        placeholder="Cantidad inicial"
                                        className={`w-full ${errors.cantidad ? 'p-invalid' : ''}`}
                                        min={0}
                                        showButtons
                                        buttonLayout="horizontal"
                                        incrementButtonIcon="pi pi-plus"
                                        decrementButtonIcon="pi pi-minus"
                                    />
                                    {errors.cantidad && (
                                        <small className="p-error block mt-1">{errors.cantidad}</small>
                                    )}
                                </div>

                                {/* Ubicación */}
                                <div className="col-12 md:col-6">
                                    <label htmlFor="ubicacion" className="block text-900 font-medium mb-2">
                                        Ubicación <span className="text-red-500">*</span>
                                    </label>
                                    <Dropdown
                                        id="ubicacion"
                                        value={formData.ubicacion}
                                        options={ubicacionesComunes.map(ub => ({ label: ub, value: ub }))}
                                        onChange={(e) => handleInputChange('ubicacion', e.value)}
                                        placeholder="Selecciona una ubicación"
                                        className={`w-full ${errors.ubicacion ? 'p-invalid' : ''}`}
                                        editable
                                    />
                                    {errors.ubicacion && (
                                        <small className="p-error block mt-1">{errors.ubicacion}</small>
                                    )}
                                    <small className="text-600 block mt-1">
                                        Puedes seleccionar una ubicación predefinida o escribir una nueva
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
                                    label={loading ? 'Creando...' : 'Crear Inventario'}
                                    icon={loading ? undefined : 'pi pi-check'}
                                    className="p-button-success"
                                    type="submit"
                                    disabled={loading || loadingProductos}
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
                                    <strong className="text-primary">Producto:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Busca y selecciona el producto del catálogo existente
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Cantidad:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Stock inicial que tendrás disponible para venta
                                    </p>
                                </div>
                                <div className="mb-3">
                                    <strong className="text-primary">Ubicación:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Lugar físico donde se almacena el producto
                                    </p>
                                </div>
                                <div>
                                    <strong className="text-primary">Nota:</strong>
                                    <p className="text-600 mt-1 mb-0">
                                        Solo puedes crear inventario para productos ya registrados
                                    </p>
                                </div>
                            </div>
                        </div>
                    </Card>

                    {/* Panel adicional con estadísticas */}
                    <Card className="bg-green-50 border-green-200 mt-4">
                        <div className="text-center">
                            <i className="pi pi-chart-line text-green-600 text-3xl mb-3"></i>
                            <h4 className="text-green-700 font-bold mb-3">💡 Consejos</h4>
                            <div className="text-left">
                                <ul className="text-600 list-none p-0 m-0">
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Define ubicaciones claras y consistentes
                                    </li>
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Cuenta el stock físico antes de registrar
                                    </li>
                                    <li className="mb-2">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Utiliza códigos o nombres descriptivos
                                    </li>
                                    <li className="mb-0">
                                        <i className="pi pi-check-circle text-green-600 mr-2"></i>
                                        Registra movimientos por cada cambio
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

export default CrearInventarioPage;