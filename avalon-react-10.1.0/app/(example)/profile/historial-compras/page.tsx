'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { DataView } from 'primereact/dataview';
import { Tag } from 'primereact/tag';
import { Button } from 'primereact/button';
import { Dialog } from 'primereact/dialog';
import { Divider } from 'primereact/divider';
import { ProgressSpinner } from 'primereact/progressspinner';
import { Message } from 'primereact/message';
import { Timeline } from 'primereact/timeline';
import { Image } from 'primereact/image';
import { Toast } from 'primereact/toast';
import { useRef } from 'react';
import { GATEWAY_BASE_URL } from '../../../../services/apiConfig';

interface DetalleVenta {
    id: number;
    productoId: number;
    nombreProducto: string;
    cantidad: number;
    precioUnitario: number;
    subtotal: number;
}

interface Venta {
    id: number;
    numeroFactura: string;
    clienteId: number;
    subtotal: number;
    impuesto: number;
    total: number;
    estado: 'PENDIENTE' | 'COMPLETADA' | 'CANCELADA';
    metodoPago: 'TRANSFERENCIA' | 'TARJETA' | 'DEUNA';
    estadoPago: 'PENDIENTE' | 'PROCESANDO' | 'APROBADO' | 'RECHAZADO';
    comprobantePagoUrl?: string;
    referenciaTransaccion?: string;
    fechaVenta: string;
    fechaPago?: string;
    detalles: DetalleVenta[];
}

export default function HistorialCompras() {
    const toast = useRef<Toast>(null);
    const [compras, setCompras] = useState<Venta[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedCompra, setSelectedCompra] = useState<Venta | null>(null);
    const [dialogVisible, setDialogVisible] = useState(false);

    useEffect(() => {
        cargarHistorial();
    }, []);

    const cargarHistorial = async () => {
        setLoading(true);
        try {
            // Obtener el ID del cliente desde el localStorage o contexto de autenticación
            const clienteId = obtenerClienteId();
            
            const apiBase = GATEWAY_BASE_URL;
            const response = await fetch(`${apiBase}/api/ventas/cliente/${clienteId}`);
            
            if (response.ok) {
                const data = await response.json();
                setCompras(data);
            } else {
                throw new Error('Error al cargar el historial');
            }
        } catch (error) {
            console.error('Error:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo cargar el historial de compras',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const obtenerClienteId = (): number => {
        // Implementar según tu lógica de autenticación
        // Por ahora retorna un ID de ejemplo
        return 1;
    };

    const getEstadoSeverity = (estado: string): 'success' | 'info' | 'warning' | 'danger' => {
        switch (estado) {
            case 'COMPLETADA':
            case 'APROBADO':
                return 'success';
            case 'PROCESANDO':
                return 'info';
            case 'PENDIENTE':
                return 'warning';
            case 'RECHAZADO':
            case 'CANCELADA':
                return 'danger';
            default:
                return 'info';
        }
    };

    const getMetodoPagoIcon = (metodo: string): string => {
        switch (metodo) {
            case 'TRANSFERENCIA':
                return 'pi-building';
            case 'TARJETA':
                return 'pi-credit-card';
            case 'DEUNA':
                return 'pi-qrcode';
            default:
                return 'pi-money-bill';
        }
    };

    const getMetodoPagoLabel = (metodo: string): string => {
        switch (metodo) {
            case 'TRANSFERENCIA':
                return 'Transferencia Bancaria';
            case 'TARJETA':
                return 'Tarjeta';
            case 'DEUNA':
                return 'Deuna';
            default:
                return metodo;
        }
    };

    const formatFecha = (fecha: string): string => {
        const date = new Date(fecha);
        return date.toLocaleDateString('es-EC', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const verDetalle = (compra: Venta) => {
        setSelectedCompra(compra);
        setDialogVisible(true);
    };

    const itemTemplate = (compra: Venta) => {
        return (
            <Card className="mb-3 shadow-2">
                <div className="grid">
                    <div className="col-12 md:col-8">
                        <div className="flex align-items-center mb-2">
                            <i className="pi pi-shopping-cart mr-2 text-xl" style={{ color: 'var(--primary-color)' }}></i>
                            <h4 className="m-0">{compra.numeroFactura}</h4>
                        </div>

                        <div className="text-600 text-sm mb-2">
                            <i className="pi pi-calendar mr-2"></i>
                            {formatFecha(compra.fechaVenta)}
                        </div>

                        <div className="flex align-items-center gap-2 mb-2">
                            <Tag 
                                value={compra.estado} 
                                severity={getEstadoSeverity(compra.estado)}
                                icon="pi pi-circle-fill"
                            ></Tag>
                            <Tag 
                                value={compra.estadoPago} 
                                severity={getEstadoSeverity(compra.estadoPago)}
                                icon="pi pi-circle-fill"
                            ></Tag>
                        </div>

                        <div className="flex align-items-center text-600 text-sm">
                            <i className={`pi ${getMetodoPagoIcon(compra.metodoPago)} mr-2`}></i>
                            {getMetodoPagoLabel(compra.metodoPago)}
                        </div>
                    </div>

                    <div className="col-12 md:col-4 text-right">
                        <div className="text-3xl font-bold mb-3" style={{ color: 'var(--primary-color)' }}>
                            ${compra.total.toFixed(2)}
                        </div>
                        <Button
                            label="Ver Detalle"
                            icon="pi pi-eye"
                            onClick={() => verDetalle(compra)}
                            className="p-button-outlined"
                        />
                    </div>
                </div>
            </Card>
        );
    };

    const renderDialogContent = () => {
        if (!selectedCompra) return null;

        const eventos = [
            {
                status: 'Pedido Realizado',
                date: formatFecha(selectedCompra.fechaVenta),
                icon: 'pi pi-shopping-cart',
                color: '#9C27B0'
            }
        ];

        if (selectedCompra.estadoPago === 'APROBADO') {
            eventos.push({
                status: 'Pago Confirmado',
                date: selectedCompra.fechaPago ? formatFecha(selectedCompra.fechaPago) : 'Procesando',
                icon: 'pi pi-check-circle',
                color: '#4CAF50'
            });
        }

        if (selectedCompra.estado === 'COMPLETADA') {
            eventos.push({
                status: 'Pedido Completado',
                date: formatFecha(selectedCompra.fechaVenta),
                icon: 'pi pi-check',
                color: '#4CAF50'
            });
        }

        return (
            <div>
                {/* Información General */}
                <div className="grid mb-4">
                    <div className="col-6">
                        <div className="text-600 text-sm mb-1">Número de Factura</div>
                        <div className="font-semibold">{selectedCompra.numeroFactura}</div>
                    </div>
                    <div className="col-6">
                        <div className="text-600 text-sm mb-1">Fecha</div>
                        <div className="font-semibold">{formatFecha(selectedCompra.fechaVenta)}</div>
                    </div>
                    <div className="col-6 mt-3">
                        <div className="text-600 text-sm mb-1">Estado del Pedido</div>
                        <Tag 
                            value={selectedCompra.estado} 
                            severity={getEstadoSeverity(selectedCompra.estado)}
                        ></Tag>
                    </div>
                    <div className="col-6 mt-3">
                        <div className="text-600 text-sm mb-1">Estado del Pago</div>
                        <Tag 
                            value={selectedCompra.estadoPago} 
                            severity={getEstadoSeverity(selectedCompra.estadoPago)}
                        ></Tag>
                    </div>
                </div>

                <Divider />

                {/* Timeline de Estado */}
                <h5>Seguimiento del Pedido</h5>
                <Timeline 
                    value={eventos} 
                    opposite={(item) => item.date}
                    content={(item) => (
                        <div className="flex align-items-center">
                            <div
                                className="flex align-items-center justify-content-center border-circle mr-2"
                                style={{
                                    width: '35px',
                                    height: '35px',
                                    backgroundColor: `${item.color}20`,
                                    color: item.color
                                }}
                            >
                                <i className={item.icon}></i>
                            </div>
                            <span className="font-semibold">{item.status}</span>
                        </div>
                    )}
                />

                <Divider />

                {/* Información de Pago */}
                <h5>Información de Pago</h5>
                <div className="grid mb-4">
                    <div className="col-12">
                        <div className="flex align-items-center mb-2">
                            <i className={`pi ${getMetodoPagoIcon(selectedCompra.metodoPago)} mr-2`}></i>
                            <span className="font-semibold">{getMetodoPagoLabel(selectedCompra.metodoPago)}</span>
                        </div>
                    </div>
                    {selectedCompra.referenciaTransaccion && (
                        <div className="col-12">
                            <div className="text-600 text-sm mb-1">Referencia</div>
                            <div className="font-mono">{selectedCompra.referenciaTransaccion}</div>
                        </div>
                    )}
                    {selectedCompra.comprobantePagoUrl && (
                        <div className="col-12">
                            <div className="text-600 text-sm mb-2">Comprobante de Pago</div>
                            <Image 
                                src={selectedCompra.comprobantePagoUrl} 
                                alt="Comprobante" 
                                width="200" 
                                preview 
                            />
                        </div>
                    )}
                </div>

                <Divider />

                {/* Productos */}
                <h5>Productos</h5>
                {selectedCompra.detalles.map((detalle, index) => (
                    <div key={index} className="flex justify-content-between align-items-center mb-2 p-2 surface-50 border-round">
                        <div>
                            <div className="font-semibold">{detalle.nombreProducto}</div>
                            <div className="text-sm text-600">
                                Cantidad: {detalle.cantidad} x ${detalle.precioUnitario.toFixed(2)}
                            </div>
                        </div>
                        <div className="font-semibold">${detalle.subtotal.toFixed(2)}</div>
                    </div>
                ))}

                <Divider />

                {/* Totales */}
                <div className="grid">
                    <div className="col-6 text-right">
                        <div className="text-600 mb-2">Subtotal:</div>
                        <div className="text-600 mb-2">Impuesto (IVA):</div>
                        <div className="text-xl font-bold">Total:</div>
                    </div>
                    <div className="col-6 text-right">
                        <div className="mb-2">${selectedCompra.subtotal.toFixed(2)}</div>
                        <div className="mb-2">${selectedCompra.impuesto.toFixed(2)}</div>
                        <div className="text-xl font-bold" style={{ color: 'var(--primary-color)' }}>
                            ${selectedCompra.total.toFixed(2)}
                        </div>
                    </div>
                </div>
            </div>
        );
    };

    if (loading) {
        return (
            <div className="flex justify-content-center align-items-center" style={{ minHeight: '400px' }}>
                <ProgressSpinner />
            </div>
        );
    }

    return (
        <>
            <Toast ref={toast} />

            <div className="card">
                <div className="flex align-items-center justify-content-between mb-4">
                    <h2 className="m-0">
                        <i className="pi pi-shopping-bag mr-2"></i>
                        Historial de Compras
                    </h2>
                    <Button
                        icon="pi pi-refresh"
                        label="Actualizar"
                        onClick={cargarHistorial}
                        className="p-button-outlined"
                    />
                </div>

                {compras.length === 0 ? (
                    <Message
                        severity="info"
                        text="No tienes compras registradas aún"
                        className="w-full justify-content-start"
                    />
                ) : (
                    <DataView
                        value={compras}
                        itemTemplate={itemTemplate}
                        paginator
                        rows={5}
                        emptyMessage="No se encontraron compras"
                    />
                )}
            </div>

            <Dialog
                header="Detalle de la Compra"
                visible={dialogVisible}
                style={{ width: '700px' }}
                onHide={() => setDialogVisible(false)}
                maximizable
            >
                {renderDialogContent()}
            </Dialog>
        </>
    );
}
