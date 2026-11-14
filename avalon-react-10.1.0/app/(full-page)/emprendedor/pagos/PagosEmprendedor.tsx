'use client';

import React, { useEffect, useState } from 'react';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Dialog } from 'primereact/dialog';
import { Toast } from 'primereact/toast';
import { Card } from 'primereact/card';
import { Badge } from 'primereact/badge';
import { listarPagosPendientes, listarPagosPorEmprendedor, aprobarPago, rechazarPago, PagoResponse } from '@/services/pagoService';

interface PagosEmprendedorProps {
    emprendedorId: number;
}

export default function PagosEmprendedor({ emprendedorId }: PagosEmprendedorProps) {
    const [pagosPendientes, setPagosPendientes] = useState<PagoResponse[]>([]);
    const [todosPagos, setTodosPagos] = useState<PagoResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [showDialog, setShowDialog] = useState(false);
    const [pagoSeleccionado, setPagoSeleccionado] = useState<PagoResponse | null>(null);
    const [accion, setAccion] = useState<'aprobar' | 'rechazar'>('aprobar');
    const toast = React.useRef<Toast>(null);

    useEffect(() => {
        cargarPagos();
    }, [emprendedorId]);

    const cargarPagos = async () => {
        try {
            setLoading(true);
            const [pendientes, todos] = await Promise.all([
                listarPagosPendientes(emprendedorId),
                listarPagosPorEmprendedor(emprendedorId)
            ]);
            setPagosPendientes(pendientes);
            setTodosPagos(todos);
        } catch (error) {
            console.error('Error al cargar pagos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudieron cargar los pagos',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const confirmarAccion = (pago: PagoResponse, accionParam: 'aprobar' | 'rechazar') => {
        setPagoSeleccionado(pago);
        setAccion(accionParam);
        setShowDialog(true);
    };

    const ejecutarAccion = async () => {
        if (!pagoSeleccionado) return;

        try {
            const request = { emprendedorId };
            
            if (accion === 'aprobar') {
                await aprobarPago(pagoSeleccionado.id, request);
                toast.current?.show({
                    severity: 'success',
                    summary: 'Pago Aprobado',
                    detail: `Pago #${pagoSeleccionado.id} aprobado exitosamente`,
                    life: 3000
                });
            } else {
                await rechazarPago(pagoSeleccionado.id, request);
                toast.current?.show({
                    severity: 'warn',
                    summary: 'Pago Rechazado',
                    detail: `Pago #${pagoSeleccionado.id} rechazado`,
                    life: 3000
                });
            }

            setShowDialog(false);
            setPagoSeleccionado(null);
            window.dispatchEvent(new Event('pagosUpdated'));
            cargarPagos();
        } catch (error) {
            console.error('Error al procesar pago:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo procesar el pago',
                life: 3000
            });
        }
    };

    const metodoPagoTemplate = (rowData: PagoResponse) => {
        const metodoPagoConfig: Record<string, { icon: string; color: string }> = {
            TRANSFERENCIA: { icon: 'pi-money-bill', color: 'info' },
            TARJETA: { icon: 'pi-credit-card', color: 'success' },
            DEUNA: { icon: 'pi-wallet', color: 'warning' }
        };

        const config = metodoPagoConfig[rowData.metodoPago] || { icon: 'pi-question', color: 'secondary' };

        return (
            <div className="flex align-items-center gap-2">
                <i className={`pi ${config.icon}`}></i>
                <Tag value={rowData.metodoPago} severity={config.color as any} />
            </div>
        );
    };

    const estadoPagoTemplate = (rowData: PagoResponse) => {
        const estadoConfig: Record<string, { severity: 'success' | 'warning' | 'danger' | 'info' }> = {
            PENDIENTE: { severity: 'warning' },
            PROCESANDO: { severity: 'info' },
            APROBADO: { severity: 'success' },
            RECHAZADO: { severity: 'danger' }
        };

        const config = estadoConfig[rowData.estadoPago] || { severity: 'info' };

        return <Tag value={rowData.estadoPago} severity={config.severity} />;
    };

    const montoTemplate = (rowData: PagoResponse) => {
        return <span className="font-semibold">${rowData.monto.toFixed(2)}</span>;
    };

    const comprobanteTemplate = (rowData: PagoResponse) => {
        if (!rowData.comprobanteUrl) {
            return <span className="text-gray-400">-</span>;
        }

        return (
            <Button
                icon="pi pi-file"
                label="Ver"
                size="small"
                outlined
                onClick={() => window.open(rowData.comprobanteUrl, '_blank')}
            />
        );
    };

    const accionesTemplate = (rowData: PagoResponse) => {
        if (rowData.estadoPago !== 'PENDIENTE') {
            return <span className="text-gray-400">-</span>;
        }

        return (
            <div className="flex gap-2">
                <Button
                    icon="pi pi-check"
                    severity="success"
                    size="small"
                    tooltip="Aprobar"
                    tooltipOptions={{ position: 'top' }}
                    onClick={() => confirmarAccion(rowData, 'aprobar')}
                />
                <Button
                    icon="pi pi-times"
                    severity="danger"
                    size="small"
                    tooltip="Rechazar"
                    tooltipOptions={{ position: 'top' }}
                    onClick={() => confirmarAccion(rowData, 'rechazar')}
                />
            </div>
        );
    };

    const fechaTemplate = (rowData: PagoResponse) => {
        return new Date(rowData.fechaCreacion).toLocaleDateString('es-ES', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    return (
        <div className="grid">
            <Toast ref={toast} />

            {/* Card de Pagos Pendientes */}
            <div className="col-12">
                <Card
                    title={
                        <div className="flex align-items-center justify-content-between">
                            <span>Pagos Pendientes de Aprobación</span>
                            <Badge value={pagosPendientes.length} severity="warning" />
                        </div>
                    }
                >
                    <DataTable
                        value={pagosPendientes}
                        loading={loading}
                        emptyMessage="No hay pagos pendientes"
                        paginator
                        rows={5}
                        responsiveLayout="scroll"
                    >
                        <Column field="id" header="ID" style={{ width: '5%' }} />
                        <Column field="numeroFactura" header="Factura" />
                        <Column field="clienteNombre" header="Cliente" />
                        <Column header="Método" body={metodoPagoTemplate} />
                        <Column header="Monto" body={montoTemplate} />
                        <Column header="Fecha" body={fechaTemplate} />
                        <Column header="Comprobante" body={comprobanteTemplate} />
                        <Column header="Acciones" body={accionesTemplate} style={{ width: '10%' }} />
                    </DataTable>
                </Card>
            </div>

            {/* Card de Historial de Pagos */}
            <div className="col-12">
                <Card title="Historial de Pagos">
                    <DataTable
                        value={todosPagos}
                        loading={loading}
                        emptyMessage="No hay pagos registrados"
                        paginator
                        rows={10}
                        responsiveLayout="scroll"
                    >
                        <Column field="id" header="ID" style={{ width: '5%' }} />
                        <Column field="numeroFactura" header="Factura" />
                        <Column field="clienteNombre" header="Cliente" />
                        <Column header="Método" body={metodoPagoTemplate} />
                        <Column header="Estado" body={estadoPagoTemplate} />
                        <Column header="Monto" body={montoTemplate} />
                        <Column header="Fecha" body={fechaTemplate} />
                        <Column header="Comprobante" body={comprobanteTemplate} />
                    </DataTable>
                </Card>
            </div>

            {/* Dialog de Confirmación */}
            <Dialog
                header={accion === 'aprobar' ? 'Aprobar Pago' : 'Rechazar Pago'}
                visible={showDialog}
                style={{ width: '450px' }}
                onHide={() => setShowDialog(false)}
                footer={
                    <div>
                        <Button label="Cancelar" icon="pi pi-times" onClick={() => setShowDialog(false)} className="p-button-text" />
                        <Button
                            label={accion === 'aprobar' ? 'Aprobar' : 'Rechazar'}
                            icon={accion === 'aprobar' ? 'pi pi-check' : 'pi pi-times'}
                            onClick={ejecutarAccion}
                            severity={accion === 'aprobar' ? 'success' : 'danger'}
                        />
                    </div>
                }
            >
                {pagoSeleccionado && (
                    <div className="confirmation-content">
                        <i className={`pi ${accion === 'aprobar' ? 'pi-check-circle' : 'pi-exclamation-triangle'} mr-3`} style={{ fontSize: '2rem' }} />
                        <span>
                            ¿Está seguro de {accion === 'aprobar' ? 'aprobar' : 'rechazar'} el pago <strong>#{pagoSeleccionado.id}</strong>?
                        </span>
                        <div className="mt-3">
                            <p><strong>Cliente:</strong> {pagoSeleccionado.clienteNombre}</p>
                            <p><strong>Monto:</strong> ${pagoSeleccionado.monto.toFixed(2)}</p>
                            <p><strong>Método:</strong> {pagoSeleccionado.metodoPago}</p>
                            <p><strong>Factura:</strong> {pagoSeleccionado.numeroFactura}</p>
                        </div>
                    </div>
                )}
            </Dialog>
        </div>
    );
}
