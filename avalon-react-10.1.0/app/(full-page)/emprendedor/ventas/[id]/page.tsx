'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Toast } from 'primereact/toast';
import { ProgressSpinner } from 'primereact/progressspinner';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Badge } from 'primereact/badge';
import { Divider } from 'primereact/divider';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import ventasService, { VentaResponse } from '../../../../../services/ventasService';
import authService from '../../../../../services/authService';

interface DetalleVentaPageProps {
    params: { id: string };
}

const DetalleVentaPage: React.FC<DetalleVentaPageProps> = ({ params }) => {
    const router = useRouter();
    const toast = useRef<Toast>(null);
    const ventaId = parseInt(params.id);

    // Estados
    const [loading, setLoading] = useState(true);
    const [venta, setVenta] = useState<VentaResponse | null>(null);

    useEffect(() => {
        cargarVenta();
    }, [ventaId]);

    const cargarVenta = async () => {
        setLoading(true);
        try {
            console.log('🔄 Cargando detalle de venta:', ventaId);

            // 🔒 Validar que la venta pertenezca al alcance del emprendedor si no es admin
            if (!authService.isAdmin()) {
                const empresaId = authService.getEmpresaId();
                if (!empresaId) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'Acceso denegado (403)',
                        detail: 'Tu cuenta no tiene una empresa asociada para consultar ventas.',
                        life: 3500
                    });
                    setTimeout(() => router.push('/emprendedor/ventas'), 1500);
                    return;
                }

                const ventasEmpRes = await ventasService.listarVentasPorEmprendedor(empresaId);
                const idsPermitidos = new Set((ventasEmpRes.data || []).map((v) => Number(v.id)));
                if (ventasEmpRes.success && !idsPermitidos.has(Number(ventaId))) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'Acceso denegado (403)',
                        detail: 'No tienes permisos para ver ni operar sobre una venta de otra empresa.',
                        life: 4000
                    });
                    setTimeout(() => router.push('/emprendedor/ventas'), 1500);
                    return;
                }
            }

            const response = await ventasService.obtenerVentaPorId(ventaId);

            if (response.success && response.data) {
                setVenta(response.data);
                console.log('✅ Venta cargada:', response.data);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando venta',
                    life: 3000
                });
                setTimeout(() => {
                    router.push('/emprendedor/ventas');
                }, 2000);
            }
        } catch (error) {
            console.error('❌ Error cargando venta:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
            setTimeout(() => {
                router.push('/emprendedor/ventas');
            }, 2000);
        } finally {
            setLoading(false);
        }
    };

    // Completar venta
    const completarVenta = async () => {
        if (!venta) return;

        try {
            const response = await ventasService.completarVenta(venta.id);
            
            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta completada exitosamente',
                    life: 3000
                });
                cargarVenta(); // Recargar datos
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message,
                    life: 3000
                });
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error completando venta',
                life: 3000
            });
        }
    };

    // Confirmar cancelación
    const confirmarCancelacion = () => {
        if (!venta) return;

        confirmDialog({
            message: `¿Estás seguro de que deseas cancelar la venta ${venta.numeroFactura}?`,
            header: 'Confirmar Cancelación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, cancelar',
            rejectLabel: 'No',
            accept: () => cancelarVenta()
        });
    };

    // Cancelar venta
    const cancelarVenta = async () => {
        if (!venta) return;

        try {
            const response = await ventasService.cancelarVenta(venta.id);
            
            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta cancelada exitosamente',
                    life: 3000
                });
                cargarVenta(); // Recargar datos
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message,
                    life: 3000
                });
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cancelando venta',
                life: 3000
            });
        }
    };

    // Templates para la tabla de detalles
    const productoBodyTemplate = (rowData: any) => {
        return (
            <div>
                <div className="font-bold">{rowData.nombreProducto}</div>
                <div className="text-sm text-600">ID: {rowData.productoId}</div>
            </div>
        );
    };

    const cantidadBodyTemplate = (rowData: any) => {
        return (
            <Badge value={rowData.cantidad} severity="info" />
        );
    };

    const precioBodyTemplate = (rowData: any) => {
        return (
            <span className="font-semibold">
                {ventasService.formatearPrecio(rowData.precioUnitario)}
            </span>
        );
    };

    const subtotalBodyTemplate = (rowData: any) => {
        return (
            <span className="font-bold text-primary">
                {ventasService.formatearPrecio(rowData.subtotal)}
            </span>
        );
    };

    if (loading) {
        return (
            <div className="flex align-items-center justify-content-center" style={{ height: '400px' }}>
                <ProgressSpinner />
            </div>
        );
    }

    if (!venta) {
        return (
            <Card>
                <div className="text-center p-4">
                    <i className="pi pi-exclamation-triangle text-4xl text-orange-500 mb-3"></i>
                    <h3>Venta no encontrada</h3>
                    <p className="text-600 mb-3">No se pudo cargar la información de la venta.</p>
                    <Button 
                        label="Volver" 
                        icon="pi pi-arrow-left" 
                        onClick={() => router.push('/emprendedor/ventas')}
                    />
                </div>
            </Card>
        );
    }

    return (
        <div className="detalle-venta-page">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="flex align-items-center justify-content-between mb-4">
                <div>
                    <h1 className="text-3xl font-bold text-900 mb-2">
                        <i className="pi pi-file-text text-primary mr-3"></i>
                        Detalle de Venta
                    </h1>
                    <p className="text-600 text-lg">
                        Factura: {venta.numeroFactura}
                    </p>
                </div>
                <Button 
                    label="Volver" 
                    icon="pi pi-arrow-left" 
                    className="p-button-outlined"
                    onClick={() => router.push('/emprendedor/ventas')}
                />
            </div>

            <div className="grid">
                {/* Información de la venta */}
                <div className="col-12 lg:col-4">
                    <Card title="Información de la Venta" className="h-full">
                        <div className="grid">
                            <div className="col-12">
                                <div className="mb-3">
                                    <label className="text-600 font-medium">Estado</label>
                                    <div className="mt-1 flex align-items-center gap-2">
                                        <i className={ventasService.obtenerIconoEstado(venta.estado)}></i>
                                        <Badge 
                                            value={ventasService.formatearEstado(venta.estado)} 
                                            severity={ventasService.obtenerColorEstado(venta.estado)}
                                            className="text-base"
                                        />
                                    </div>
                                </div>
                            </div>
                            
                            <div className="col-12">
                                <div className="mb-3">
                                    <label className="text-600 font-medium">Número de Factura</label>
                                    <div className="mt-1 font-bold text-primary">{venta.numeroFactura}</div>
                                </div>
                            </div>

                            <div className="col-12">
                                <div className="mb-3">
                                    <label className="text-600 font-medium">Fecha de Venta</label>
                                    <div className="mt-1">{ventasService.formatearFecha(venta.fechaVenta)}</div>
                                </div>
                            </div>

                            <div className="col-12">
                                <Divider />
                                <h4>Totales</h4>
                            </div>

                            <div className="col-12">
                                <div className="flex justify-content-between mb-2">
                                    <span className="text-600">Subtotal:</span>
                                    <span className="font-semibold">
                                        {ventasService.formatearPrecio(venta.subtotal)}
                                    </span>
                                </div>
                                <div className="flex justify-content-between mb-2">
                                    <span className="text-600">Impuesto (15%):</span>
                                    <span className="font-semibold">
                                        {ventasService.formatearPrecio(venta.impuesto)}
                                    </span>
                                </div>
                                <Divider />
                                <div className="flex justify-content-between">
                                    <span className="font-bold text-lg">Total:</span>
                                    <span className="font-bold text-lg text-primary">
                                        {ventasService.formatearPrecio(venta.total)}
                                    </span>
                                </div>
                            </div>

                            {venta.estado === 'PENDIENTE' && (
                                <div className="col-12 mt-3">
                                    <div className="flex gap-2">
                                        <Button 
                                            label="Completar Venta" 
                                            icon="pi pi-check" 
                                            className="p-button-success flex-1"
                                            onClick={completarVenta}
                                        />
                                        <Button 
                                            label="Cancelar" 
                                            icon="pi pi-times" 
                                            className="p-button-danger flex-1"
                                            onClick={confirmarCancelacion}
                                        />
                                    </div>
                                </div>
                            )}
                        </div>
                    </Card>
                </div>

                {/* Información del cliente */}
                <div className="col-12 lg:col-8">
                    <Card title="Información del Cliente" className="mb-3">
                        <div className="grid">
                            <div className="col-12 md:col-6">
                                <div className="mb-2">
                                    <strong>Nombre:</strong> {venta.cliente.nombre} {venta.cliente.apellido}
                                </div>
                                <div className="mb-2">
                                    <strong>Email:</strong> {venta.cliente.email}
                                </div>
                            </div>
                            <div className="col-12 md:col-6">
                                <div className="mb-2">
                                    <strong>Teléfono:</strong> {venta.cliente.telefono}
                                </div>
                                <div className="mb-2">
                                    <strong>Dirección:</strong> {venta.cliente.direccion}
                                </div>
                            </div>
                        </div>
                    </Card>

                    {/* Detalles de productos */}
                    <Card title="Productos">
                        <DataTable 
                            value={venta.detalles}
                            responsiveLayout="scroll"
                            className="p-datatable-sm"
                            emptyMessage="No hay productos en esta venta"
                        >
                            <Column 
                                field="nombreProducto" 
                                header="Producto" 
                                body={productoBodyTemplate}
                            />
                            <Column 
                                field="cantidad" 
                                header="Cantidad" 
                                body={cantidadBodyTemplate}
                                style={{ width: '100px' }}
                            />
                            <Column 
                                field="precioUnitario" 
                                header="Precio Unit." 
                                body={precioBodyTemplate}
                                style={{ width: '120px' }}
                            />
                            <Column 
                                field="subtotal" 
                                header="Subtotal" 
                                body={subtotalBodyTemplate}
                                style={{ width: '120px' }}
                            />
                        </DataTable>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default DetalleVentaPage;