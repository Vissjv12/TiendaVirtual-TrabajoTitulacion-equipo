'use client';

import React, { useState, useEffect } from 'react';
import { Card } from 'primereact/card';
import { DataView } from 'primereact/dataview';
import { Paginator } from 'primereact/paginator';
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
import { 
    SeguimientoLogistica, 
    obtenerHistorialPorVenta,
    getEstadoSeverity as getLogisticaSeverity,
    getEstadoIcon
} from '../../../../services/logisticaService';
import ventasService from '../../../../services/ventasService';
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
    const [seguimientoLogistica, setSeguimientoLogistica] = useState<SeguimientoLogistica[]>([]);
    const [loadingSeguimiento, setLoadingSeguimiento] = useState(false);
    const [first, setFirst] = useState(0);
    const [rows, setRows] = useState(10);

    useEffect(() => {
        cargarHistorial();
        
        // Auto-actualizar cada 30 segundos
        const interval = setInterval(() => {
            cargarHistorial();
        }, 30000);
        
        return () => clearInterval(interval);
    }, []);

    const cargarHistorial = async () => {
        console.log('📥 Iniciando carga de historial...');
        setLoading(true);
        try {
            // Obtener el usuario actual
            const userInfo = typeof window !== 'undefined' ? localStorage.getItem('user_info') : null;
            if (!userInfo) {
                console.error('❌ No hay usuario autenticado');
                throw new Error('No hay usuario autenticado');
            }
            
            const user = JSON.parse(userInfo);
            console.log('👤 Usuario:', user);
            
            // Intentar obtener el cliente por email, si falla intentar por username
            const apiBase = GATEWAY_BASE_URL;
            console.log('🔍 Buscando cliente por email:', user.email || user.username);
            let clienteResponse = await fetch(`${apiBase}/api/clientes/email/${encodeURIComponent(user.email || user.username)}`);
            
            if (!clienteResponse.ok && user.username) {
                console.log('⚠️ Email no encontrado, intentando con username:', user.username);
                // Si falla con email, intentar con username (puede ser el email alternativo)
                clienteResponse = await fetch(`${apiBase}/api/clientes/email/${encodeURIComponent(user.username)}`);
            }
            
            if (!clienteResponse.ok) {
                console.log('⚠️ Cliente no encontrado en la base de datos (probablemente usuario nuevo sin compras).');
                setCompras([]);
                setLoading(false);
                return;
            }
            
            const cliente = await clienteResponse.json();
            console.log('✅ Cliente encontrado:', cliente);
            
            // Obtener las ventas del cliente
            console.log('🔍 Obteniendo ventas para cliente ID:', cliente.id);
            const response = await fetch(`${apiBase}/api/ventas/cliente/${cliente.id}`);
            
            if (response.ok) {
                const data = await response.json();
                console.log('✅ Ventas cargadas:', data.length, 'compras');
                setCompras(data);
                toast.current?.show({
                    severity: 'success',
                    summary: 'Actualizado',
                    detail: `${data.length} compra(s) encontrada(s)`,
                    life: 2000
                });
            } else {
                console.error('❌ Error al cargar ventas:', response.status);
                throw new Error('Error al cargar el historial');
            }
        } catch (error) {
            console.error('❌ Error completo:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: error instanceof Error ? error.message : 'No se pudo cargar el historial de compras',
                life: 3000
            });
        } finally {
            setLoading(false);
            console.log('✅ Carga finalizada');
        }
    };

    const descargarFactura = async (ventaId: number) => {
        try {
            toast.current?.show({
                severity: 'info',
                summary: 'Descargando',
                detail: 'Generando factura PDF...',
                life: 2000
            });
            
            await ventasService.descargarFacturaPDF(ventaId);
            
            toast.current?.show({
                severity: 'success',
                summary: 'Éxito',
                detail: 'Factura descargada correctamente',
                life: 3000
            });
        } catch (error) {
            console.error('Error descargando factura:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo descargar la factura',
                life: 3000
            });
        }
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

    const verDetalle = async (compra: Venta) => {
        setSelectedCompra(compra);
        setDialogVisible(true);
        setSeguimientoLogistica([]);
        
        console.log('🔍 Verificando seguimiento para compra:', {
            id: compra.id,
            numeroFactura: compra.numeroFactura,
            estado: compra.estado,
            estadoPago: compra.estadoPago
        });
        
        // Cargar seguimiento logístico si el estado de la venta es COMPLETADA
        // (cuando se aprueba un pago, la venta pasa a COMPLETADA)
        if (compra.estado === 'COMPLETADA' || compra.estadoPago === 'APROBADO') {
            setLoadingSeguimiento(true);
            try {
                console.log('📡 Obteniendo historial para venta ID:', compra.id);
                const historial = await obtenerHistorialPorVenta(compra.id);
                console.log('✅ Historial recibido:', historial);
                setSeguimientoLogistica(historial);
            } catch (error) {
                console.error('❌ Error al cargar seguimiento:', error);
            } finally {
                setLoadingSeguimiento(false);
            }
        } else {
            console.log('⚠️ No se carga seguimiento - Estado:', compra.estado, 'EstadoPago:', compra.estadoPago);
        }
    };

    const itemTemplate = (compra: Venta) => {
        return (
            <div className="col-12">
                <Card className="shadow-1 border-round-lg hover:shadow-3 transition-all transition-duration-300" 
                      style={{ height: '100%' }}>
                    <div className="flex flex-column md:flex-row align-items-start md:align-items-center justify-content-between gap-4">
                        {/* Información principal */}
                        <div className="flex align-items-center gap-3 flex-1">
                            <div className="flex align-items-center justify-content-center border-circle bg-primary-50" 
                                 style={{ width: '48px', height: '48px', minWidth: '48px' }}>
                                <i className="pi pi-shopping-cart text-primary text-2xl"></i>
                            </div>
                            <div className="flex-1">
                                <h4 className="m-0 mb-2 font-semibold text-900">{compra.numeroFactura}</h4>
                                <div className="text-500 text-sm mb-2">
                                    <i className="pi pi-calendar mr-2"></i>
                                    {formatFecha(compra.fechaVenta)}
                                </div>
                                <div className="flex align-items-center gap-2 flex-wrap">
                                    <Tag 
                                        value={compra.estado} 
                                        severity={getEstadoSeverity(compra.estado)}
                                        icon="pi pi-circle-fill"
                                    />
                                    <Tag 
                                        value={compra.estadoPago} 
                                        severity={getEstadoSeverity(compra.estadoPago)}
                                        icon="pi pi-circle-fill"
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Método de pago */}
                        <div className="flex align-items-center text-600 text-sm white-space-nowrap">
                            <i className={`pi ${getMetodoPagoIcon(compra.metodoPago)} mr-2`}></i>
                            {getMetodoPagoLabel(compra.metodoPago)}
                        </div>

                        {/* Total y acciones */}
                        <div className="flex flex-column align-items-end gap-3" style={{ minWidth: '150px' }}>
                            <div className="text-3xl font-bold text-primary white-space-nowrap">
                                ${compra.total.toFixed(2)}
                            </div>
                            <div className="flex gap-2 flex-nowrap">
                                <Button
                                    label="Detalle"
                                    icon="pi pi-eye"
                                    onClick={() => verDetalle(compra)}
                                    className="p-button-outlined p-button-sm"
                                />
                                {compra.estado === 'COMPLETADA' && (
                                    <Button
                                        icon="pi pi-file-pdf"
                                        onClick={() => descargarFactura(compra.id)}
                                        className="p-button-outlined p-button-help p-button-sm"
                                        tooltip="Descargar factura"
                                        tooltipOptions={{ position: 'top' }}
                                    />
                                )}
                            </div>
                        </div>
                    </div>
                </Card>
            </div>
        );
    };

    const renderDialogContent = () => {
        if (!selectedCompra) return null;

        // Crear eventos básicos
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

                {/* Seguimiento Logístico Detallado */}
                {(selectedCompra.estado === 'COMPLETADA' || selectedCompra.estadoPago === 'APROBADO') && (
                    <>
                        <h5>
                            <i className="pi pi-truck mr-2"></i>
                            Seguimiento del Pedido
                        </h5>
                        {loadingSeguimiento ? (
                            <div className="flex justify-content-center p-4">
                                <ProgressSpinner style={{ width: '50px', height: '50px' }} />
                            </div>
                        ) : seguimientoLogistica.length > 0 ? (
                            <Timeline 
                                value={seguimientoLogistica}
                                align="alternate"
                                opposite={(item) => (
                                    <small className="text-600">
                                        {new Date(item.fechaCreacion).toLocaleString('es-EC')}
                                    </small>
                                )}
                                content={(item) => (
                                    <Card className="shadow-2">
                                        <div className="flex align-items-center mb-2">
                                            <div
                                                className="flex align-items-center justify-content-center border-circle mr-2"
                                                style={{
                                                    width: '35px',
                                                    height: '35px',
                                                    backgroundColor: `${getLogisticaSeverity(item.estadoLogistica) === 'success' ? '#4CAF50' : 
                                                                        getLogisticaSeverity(item.estadoLogistica) === 'info' ? '#2196F3' :
                                                                        getLogisticaSeverity(item.estadoLogistica) === 'warning' ? '#FF9800' : '#F44336'}20`,
                                                    color: getLogisticaSeverity(item.estadoLogistica) === 'success' ? '#4CAF50' : 
                                                           getLogisticaSeverity(item.estadoLogistica) === 'info' ? '#2196F3' :
                                                           getLogisticaSeverity(item.estadoLogistica) === 'warning' ? '#FF9800' : '#F44336'
                                                }}
                                            >
                                                <i className={`pi ${getEstadoIcon(item.estadoLogistica)}`}></i>
                                            </div>
                                            <Tag 
                                                value={item.estadoTitulo} 
                                                severity={getLogisticaSeverity(item.estadoLogistica)}
                                            />
                                        </div>
                                        {item.descripcion && (
                                            <p className="text-700 mb-2">{item.descripcion}</p>
                                        )}
                                        {item.ubicacion && (
                                            <p className="text-600 text-sm mb-1">
                                                <i className="pi pi-map-marker mr-2" />
                                                {item.ubicacion}
                                            </p>
                                        )}
                                        {item.responsable && (
                                            <p className="text-600 text-sm">
                                                <i className="pi pi-user mr-2" />
                                                {item.responsable}
                                            </p>
                                        )}
                                    </Card>
                                )}
                                marker={(item) => (
                                    <span 
                                        className="flex w-2rem h-2rem align-items-center justify-content-center text-white border-circle z-1 shadow-1"
                                        style={{ 
                                            backgroundColor: getLogisticaSeverity(item.estadoLogistica) === 'success' ? '#4CAF50' : 
                                                           getLogisticaSeverity(item.estadoLogistica) === 'info' ? '#2196F3' :
                                                           getLogisticaSeverity(item.estadoLogistica) === 'warning' ? '#FF9800' : '#F44336'
                                        }}
                                    >
                                        <i className={`pi ${getEstadoIcon(item.estadoLogistica)}`}></i>
                                    </span>
                                )}
                            />
                        ) : (
                            <Message 
                                severity="info" 
                                text="El seguimiento logístico se iniciará una vez que el emprendedor procese tu pedido" 
                            />
                        )}
                        <Divider />
                    </>
                )}

                {/* Timeline de Estado Básico (solo si no hay seguimiento logístico) */}
                {(selectedCompra.estado !== 'COMPLETADA' && selectedCompra.estadoPago !== 'APROBADO') && (
                    <>
                        <h5>Estado del Pedido</h5>
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
                    </>
                )}

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

            <div className="card p-4">
                {/* Header mejorado */}
                <div className="flex align-items-center justify-content-between mb-5 pb-3 border-bottom-1 surface-border">
                    <div>
                        <h2 className="m-0 mb-2 text-900">
                            <i className="pi pi-shopping-bag mr-2 text-primary"></i>
                            Historial de Compras
                        </h2>
                        <p className="text-600 mt-0 mb-0">Gestiona y revisa todas tus compras</p>
                    </div>
                    <Button
                        icon="pi pi-refresh"
                        label="Actualizar"
                        onClick={() => {
                            console.log('🔄 Actualizando historial...');
                            cargarHistorial();
                        }}
                        loading={loading}
                        className="p-button-outlined p-button-rounded"
                        size="small"
                    />
                </div>

                {compras.length === 0 ? (
                    <div className="text-center p-5">
                        <i className="pi pi-shopping-cart text-400" style={{ fontSize: '4rem' }}></i>
                        <h3 className="text-600 mt-3">No tienes compras registradas</h3>
                        <p className="text-500">Comienza a explorar nuestros productos</p>
                    </div>
                ) : (
                    <>
                        <div className="grid">
                            {compras.slice(first, first + rows).map((compra) => (
                                <div key={compra.id} className="col-12">
                                    {itemTemplate(compra)}
                                </div>
                            ))}
                        </div>
                        <Paginator
                            first={first}
                            rows={rows}
                            totalRecords={compras.length}
                            rowsPerPageOptions={[5, 10, 20]}
                            onPageChange={(e) => {
                                setFirst(e.first);
                                setRows(e.rows);
                            }}
                        />
                    </>
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
