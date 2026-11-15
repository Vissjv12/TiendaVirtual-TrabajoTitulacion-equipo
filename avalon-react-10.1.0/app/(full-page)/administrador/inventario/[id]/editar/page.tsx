'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputNumber } from 'primereact/inputnumber';
import { InputText } from 'primereact/inputtext';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Divider } from 'primereact/divider';
import { Tag } from 'primereact/tag';
import { Image } from 'primereact/image';
import inventarioService from '../../../../../../services/inventarioService';
import emprendedorService, { ProductoEmprendedor } from '../../../../../../services/emprendedorService';

interface EditarInventarioPageProps {
    params: { id: string };
}

const EditarInventarioPage: React.FC<EditarInventarioPageProps> = ({ params }) => {
    const router = useRouter();
    const toast = useRef<Toast>(null);
    const productoId = parseInt(params.id);

    // Estados
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [producto, setProducto] = useState<ProductoEmprendedor | null>(null);

    // Estados del formulario
    const [cantidad, setCantidad] = useState<number>(0);
    const [ubicacion, setUbicacion] = useState<string>('');
    const [stockAnterior, setStockAnterior] = useState<number>(0);

    // Opciones comunes de ubicación para sugerencias (pero el campo será libre)
    const ubicacionesSugeridas = ['Almacén Principal', 'Bodega A', 'Bodega B', 'Estantería 1', 'Estantería 2', 'Área de Exhibición', 'Zona de Despacho', 'Disponible', 'Reservado'];

    useEffect(() => {
        cargarDatos();
    }, [productoId]);

    const cargarDatos = async () => {
        setLoading(true);
        try {
            console.log('🔄 Cargando datos para editar inventario del producto:', productoId);

            // ✅ PRIMERO: Obtener el producto básico
            const productosRes = await emprendedorService.obtenerProductosEmprendedor();

            if (productosRes.success && productosRes.data) {
                // Buscar el producto específico
                const productoEncontrado = productosRes.data.find((p) => p.id === productoId);

                if (productoEncontrado) {
                    setProducto(productoEncontrado);

                    // ✅ SEGUNDO: Obtener el stock REAL directamente del inventario
                    console.log('🔍 Obteniendo stock real del inventario...');

                    try {
                        // Usar el método individual para obtener el stock más actualizado
                        const stockReal = await inventarioService.obtenerStockProducto(productoId);

                        console.log(`✅ Stock real obtenido: ${stockReal}`);

                        // Actualizar los estados con el stock real
                        setCantidad(stockReal);
                        setStockAnterior(stockReal);

                        // También actualizar el producto en memoria
                        if (productoEncontrado.inventario) {
                            productoEncontrado.inventario.cantidad = stockReal;
                        }
                    } catch (error) {
                        console.log('⚠️ Error obteniendo stock real, usando valor del producto:', error);
                        // Si falla, usar el valor que viene del producto
                        const stockFallback = productoEncontrado.inventario?.cantidad || 0;
                        setCantidad(stockFallback);
                        setStockAnterior(stockFallback);
                    }

                    // Establecer ubicación
                    const ubicacionActual = productoEncontrado.inventario?.ubicacion || 'Disponible';
                    setUbicacion(ubicacionActual);

                    console.log('✅ Datos finales cargados:', {
                        id: productoEncontrado.id,
                        nombre: productoEncontrado.nombre,
                        stockAnterior: stockAnterior,
                        ubicacionActual
                    });
                } else {
                    throw new Error('Producto no encontrado en la lista');
                }
            } else {
                throw new Error('Error cargando lista de productos');
            }
        } catch (error) {
            console.error('❌ Error cargando datos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando datos del producto',
                life: 3000
            });
            setTimeout(() => {
                router.push('/administrador/inventario');
            }, 2000);
        } finally {
            setLoading(false);
        }
    };

    const guardarCambios = async () => {
        if (!producto) return;

        setSaving(true);
        try {
            console.log('💾 Guardando cambios de inventario:', {
                productoId,
                cantidadAnterior: stockAnterior,
                cantidadNueva: cantidad,
                ubicacion
            });

            // ✅ USAR EL SERVICIO DE INVENTARIO para actualizar stock
            const response = await inventarioService.actualizarStock(productoId, cantidad, 'AJUSTE', `Ajuste manual: ${stockAnterior} -> ${cantidad} unidades`);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Inventario actualizado correctamente',
                    life: 3000
                });

                // ✅ DISPARAR EVENTO PARA ACTUALIZAR OTROS COMPONENTES
                window.dispatchEvent(
                    new CustomEvent('stockUpdatedEmprendedor', {
                        detail: {
                            productoId,
                            stock: cantidad,
                            producto: {
                                ...producto,
                                inventario: {
                                    ...producto.inventario,
                                    cantidad: cantidad,
                                    ubicacion: ubicacion
                                }
                            }
                        }
                    })
                );

                // Navegar de vuelta
                setTimeout(() => {
                    router.push('/administrador/inventario');
                }, 1500);
            } else {
                throw new Error(response.message);
            }
        } catch (error) {
            console.error('❌ Error guardando cambios:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error guardando cambios en el inventario',
                life: 3000
            });
        } finally {
            setSaving(false);
        }
    };

    const calcularDiferencia = () => {
        return cantidad - stockAnterior;
    };

    const obtenerSeveridadDiferencia = () => {
        const diff = calcularDiferencia();
        if (diff > 0) return 'success';
        if (diff < 0) return 'danger';
        return 'info';
    };

    if (loading) {
        return (
            <div className="flex align-items-center justify-content-center" style={{ height: '400px' }}>
                <ProgressSpinner />
            </div>
        );
    }

    if (!producto) {
        return (
            <Card>
                <div className="text-center p-4">
                    <i className="pi pi-exclamation-triangle text-4xl text-orange-500 mb-3"></i>
                    <h3>Producto no encontrado</h3>
                    <p className="text-600 mb-3">No se pudo cargar la información del producto.</p>
                    <Button label="Volver" icon="pi pi-arrow-left" onClick={() => router.push('/administrador/inventario')} />
                </div>
            </Card>
        );
    }

    return (
        <div className="editar-inventario-page">
            <Toast ref={toast} />

            {/* Header */}
            <div className="flex align-items-center justify-content-between mb-4">
                <div>
                    <h1 className="text-3xl font-bold text-900 mb-2">
                        <i className="pi pi-pencil text-primary mr-3"></i>
                        Editar Inventario
                    </h1>
                    <p className="text-600 text-lg">Modifica el stock y ubicación del producto</p>
                </div>
                <Button label="Volver" icon="pi pi-arrow-left" className="p-button-outlined" onClick={() => router.push('/administrador/inventario')} />
            </div>

            <div className="grid">
                {/* Información del Producto */}
                <div className="col-12 lg:col-4">
                    <Card title="Información del Producto" className="h-full">
                        <div className="text-center mb-4">
                            <Image src={producto.imagen || 'https://via.placeholder.com/200x200?text=Producto'} alt={producto.nombre} width="200" className="border-round shadow-2" />
                        </div>

                        <div className="space-y-3">
                            <div>
                                <strong>Nombre:</strong>
                                <div className="mt-1">{producto.nombre}</div>
                            </div>
                            <div>
                                <strong>Categoría:</strong>
                                <div className="mt-1">{producto.categoria?.nombre || 'Sin categoría'}</div>
                            </div>
                            <div>
                                <strong>Empresa:</strong>
                                <div className="mt-1">{producto.empresa?.nombre || 'Sin empresa'}</div>
                            </div>
                            <div>
                                <strong>Precio:</strong>
                                <div className="mt-1 text-primary font-bold">{emprendedorService.formatearPrecio(producto.precio)}</div>
                            </div>
                        </div>
                    </Card>
                </div>

                {/* Formulario de Edición */}
                <div className="col-12 lg:col-8">
                    <Card title="Ajustar Inventario">
                        {/* Stock Actual vs Nuevo */}
                        <div className="grid mb-4">
                            <div className="col-12 md:col-4">
                                <div className="bg-blue-50 border-round p-3 text-center">
                                    <div className="text-blue-600 font-bold text-xl">{stockAnterior}</div>
                                    <div className="text-600 text-sm">Stock Anterior</div>
                                </div>
                            </div>
                            <div className="col-12 md:col-4">
                                <div className="bg-green-50 border-round p-3 text-center">
                                    <div className="text-green-600 font-bold text-xl">{cantidad}</div>
                                    <div className="text-600 text-sm">Stock Nuevo</div>
                                </div>
                            </div>
                            <div className="col-12 md:col-4">
                                <div className="bg-gray-50 border-round p-3 text-center">
                                    <Tag value={calcularDiferencia() > 0 ? `+${calcularDiferencia()}` : calcularDiferencia().toString()} severity={obtenerSeveridadDiferencia()} className="text-base" />
                                    <div className="text-600 text-sm mt-1">Diferencia</div>
                                </div>
                            </div>
                        </div>

                        <Divider />

                        {/* Formulario */}
                        <div className="grid">
                            <div className="col-12 md:col-6">
                                <label htmlFor="cantidad" className="block text-900 font-medium mb-2">
                                    Nueva Cantidad <span className="text-red-500">*</span>
                                </label>
                                <InputNumber id="cantidad" value={cantidad} onValueChange={(e) => setCantidad(e.value || 0)} min={0} className="w-full" placeholder="Ingrese la nueva cantidad" />
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="ubicacion" className="block text-900 font-medium mb-2">
                                    Ubicación
                                </label>
                                <InputText id="ubicacion" value={ubicacion} onChange={(e) => setUbicacion(e.target.value)} placeholder="Ej: Almacén Principal, Bodega A, Estantería 1..." className="w-full" />
                                <small className="text-600 block mt-1">Escribe la ubicación donde se encuentra el producto</small>
                            </div>
                        </div>

                        <Divider />

                        {/* Botones de acción */}
                        <div className="flex gap-2 justify-content-end">
                            <Button label="Cancelar" icon="pi pi-times" className="p-button-outlined" onClick={() => router.push('/administrador/inventario')} disabled={saving} />
                            <Button label={saving ? 'Guardando...' : 'Guardar Cambios'} icon={saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'} className="p-button-success" onClick={guardarCambios} disabled={saving || cantidad < 0} />
                        </div>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default EditarInventarioPage;
