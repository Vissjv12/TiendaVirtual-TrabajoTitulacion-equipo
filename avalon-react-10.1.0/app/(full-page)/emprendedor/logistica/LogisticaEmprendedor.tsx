'use client';

import React, { useState, useEffect } from 'react';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Dialog } from 'primereact/dialog';
import { Dropdown } from 'primereact/dropdown';
import { InputTextarea } from 'primereact/inputtextarea';
import { InputText } from 'primereact/inputtext';
import { Toast } from 'primereact/toast';
import { Card } from 'primereact/card';
import { Divider } from 'primereact/divider';
import { Timeline } from 'primereact/timeline';
import { 
    SeguimientoLogistica, 
    actualizarEstadoSeguimiento, 
    obtenerSeguimientosPorEmprendedor,
    obtenerEstadosDisponibles,
    obtenerHistorialPorVenta,
    EstadoLogistico,
    getEstadoSeverity,
    getEstadoIcon
} from '../../../../services/logisticaService';
import authService from '../../../../services/authService';

interface PedidoConSeguimiento {
    ventaId: number;
    numeroFactura: string;
    clienteNombre: string;
    total: number;
    fechaVenta: string;
    ultimoEstado: string;
    ultimoEstadoTitulo: string;
    ultimaActualizacion: string;
}

export default function LogisticaEmprendedor() {
    const [pedidos, setPedidos] = useState<PedidoConSeguimiento[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedPedido, setSelectedPedido] = useState<PedidoConSeguimiento | null>(null);
    const [showActualizarDialog, setShowActualizarDialog] = useState(false);
    const [showHistorialDialog, setShowHistorialDialog] = useState(false);
    const [historial, setHistorial] = useState<SeguimientoLogistica[]>([]);
    const [estadosDisponibles, setEstadosDisponibles] = useState<EstadoLogistico[]>([]);
    const [toast, setToast] = useState<any>(null);

    // Formulario de actualización
    const [estadoSeleccionado, setEstadoSeleccionado] = useState<string>('');
    const [descripcion, setDescripcion] = useState('');
    const [ubicacion, setUbicacion] = useState('');
    const [responsable, setResponsable] = useState('');
    const [observaciones, setObservaciones] = useState('');

    useEffect(() => {
        cargarPedidos();
        cargarEstadosDisponibles();
    }, []);

    const cargarEstadosDisponibles = async () => {
        try {
            const estados = await obtenerEstadosDisponibles();
            setEstadosDisponibles(estados);
        } catch (error) {
            console.error('Error al cargar estados:', error);
        }
    };

    const cargarPedidos = async () => {
        setLoading(true);
        try {
            // Obtener empresaId del usuario autenticado
            const emprendedorId = authService.getEmpresaId();

            if (!emprendedorId) {
                console.warn('⚠️ No se encontró empresaId en el usuario autenticado');
                setPedidos([]);
                toast?.current?.show({
                    severity: 'warn',
                    summary: 'Acceso restringido',
                    detail: 'No se encontró una empresa asociada a tu usuario para mostrar pedidos.',
                    life: 4000
                });
                return;
            }

            console.log('📦 Cargando pedidos para empresa ID:', emprendedorId);
            const seguimientos = await obtenerSeguimientosPorEmprendedor(emprendedorId);

            // Agrupar por venta y obtener el último estado de cada una
            const pedidosMap = new Map<number, SeguimientoLogistica>();
            
            seguimientos.forEach((seg) => {
                const existing = pedidosMap.get(seg.ventaId);
                if (!existing || new Date(seg.fechaCreacion) > new Date(existing.fechaCreacion)) {
                    pedidosMap.set(seg.ventaId, seg);
                }
            });

            // Convertir a formato de tabla
            const pedidosData: PedidoConSeguimiento[] = Array.from(pedidosMap.values()).map((seg) => ({
                ventaId: seg.ventaId,
                numeroFactura: seg.numeroFactura,
                clienteNombre: 'Cliente', // TODO: Obtener de la API
                total: 0, // TODO: Obtener de la API
                fechaVenta: seg.fechaCreacion,
                ultimoEstado: seg.estadoLogistica,
                ultimoEstadoTitulo: seg.estadoTitulo,
                ultimaActualizacion: seg.fechaActualizacion
            }));

            setPedidos(pedidosData);
        } catch (error) {
            console.error('Error al cargar pedidos:', error);
            toast?.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudieron cargar los pedidos',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const abrirDialogActualizar = (pedido: PedidoConSeguimiento) => {
        setSelectedPedido(pedido);
        setEstadoSeleccionado('');
        setDescripcion('');
        setUbicacion('');
        setResponsable('');
        setObservaciones('');
        setShowActualizarDialog(true);
    };

    const abrirDialogHistorial = async (pedido: PedidoConSeguimiento) => {
        setSelectedPedido(pedido);
        setShowHistorialDialog(true);
        
        try {
            const hist = await obtenerHistorialPorVenta(pedido.ventaId);
            setHistorial(hist);
        } catch (error) {
            console.error('Error al cargar historial:', error);
            toast?.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'No se pudo cargar el historial',
                life: 3000
            });
        }
    };

    const actualizarEstado = async () => {
        if (!selectedPedido || !estadoSeleccionado) {
            toast?.current?.show({
                severity: 'warn',
                summary: 'Advertencia',
                detail: 'Seleccione un estado',
                life: 3000
            });
            return;
        }

        try {
            await actualizarEstadoSeguimiento({
                ventaId: selectedPedido.ventaId,
                estadoLogistica: estadoSeleccionado,
                descripcion: descripcion || undefined,
                ubicacion: ubicacion || undefined,
                responsable: responsable || undefined,
                observaciones: observaciones || undefined
            });

            toast?.current?.show({
                severity: 'success',
                summary: 'Éxito',
                detail: 'Estado actualizado correctamente',
                life: 3000
            });

            setShowActualizarDialog(false);
            cargarPedidos();
        } catch (error: any) {
            console.error('Error al actualizar:', error);
            toast?.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: error.message || 'Error al actualizar el estado',
                life: 3000
            });
        }
    };

    // Templates para DataTable
    const estadoTemplate = (rowData: PedidoConSeguimiento) => {
        return (
            <Tag 
                value={rowData.ultimoEstadoTitulo} 
                severity={getEstadoSeverity(rowData.ultimoEstado)}
                icon={`pi ${getEstadoIcon(rowData.ultimoEstado)}`}
            />
        );
    };

    const accionesTemplate = (rowData: PedidoConSeguimiento) => {
        return (
            <div className="flex gap-2">
                <Button
                    icon="pi pi-history"
                    rounded
                    outlined
                    severity="info"
                    tooltip="Ver historial"
                    tooltipOptions={{ position: 'top' }}
                    onClick={() => abrirDialogHistorial(rowData)}
                />
                <Button
                    icon="pi pi-refresh"
                    rounded
                    severity="success"
                    tooltip="Actualizar estado"
                    tooltipOptions={{ position: 'top' }}
                    onClick={() => abrirDialogActualizar(rowData)}
                />
            </div>
        );
    };

    const fechaTemplate = (rowData: PedidoConSeguimiento) => {
        return new Date(rowData.ultimaActualizacion).toLocaleString('es-EC');
    };

    // Template para timeline
    const timelineContent = (item: SeguimientoLogistica) => {
        return (
            <Card className="mb-3">
                <div className="flex justify-content-between align-items-center mb-2">
                    <Tag 
                        value={item.estadoTitulo} 
                        severity={getEstadoSeverity(item.estadoLogistica)}
                        icon={`pi ${getEstadoIcon(item.estadoLogistica)}`}
                    />
                    <small className="text-600">
                        {new Date(item.fechaCreacion).toLocaleString('es-EC')}
                    </small>
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
                {item.observaciones && (
                    <p className="text-500 text-sm italic mt-2">{item.observaciones}</p>
                )}
            </Card>
        );
    };

    return (
        <div className="grid">
            <Toast ref={setToast} />

            <div className="col-12">
                <Card title="Gestión de Logística de Pedidos">
                    <DataTable
                        value={pedidos}
                        loading={loading}
                        paginator
                        rows={10}
                        dataKey="ventaId"
                        emptyMessage="No hay pedidos con seguimiento"
                    >
                        <Column field="numeroFactura" header="Nº Factura" sortable />
                        <Column field="clienteNombre" header="Cliente" sortable />
                        <Column field="ultimoEstado" header="Estado Actual" body={estadoTemplate} sortable />
                        <Column field="ultimaActualizacion" header="Última Actualización" body={fechaTemplate} sortable />
                        <Column header="Acciones" body={accionesTemplate} />
                    </DataTable>
                </Card>
            </div>

            {/* Dialog para actualizar estado */}
            <Dialog
                visible={showActualizarDialog}
                style={{ width: '600px' }}
                header={`Actualizar Estado - ${selectedPedido?.numeroFactura}`}
                modal
                onHide={() => setShowActualizarDialog(false)}
                footer={
                    <div>
                        <Button 
                            label="Cancelar" 
                            icon="pi pi-times" 
                            onClick={() => setShowActualizarDialog(false)} 
                            className="p-button-text"
                        />
                        <Button 
                            label="Actualizar" 
                            icon="pi pi-check" 
                            onClick={actualizarEstado}
                            autoFocus
                        />
                    </div>
                }
            >
                <div className="flex flex-column gap-3">
                    <div className="field">
                        <label htmlFor="estado">Estado Logístico *</label>
                        <Dropdown
                            id="estado"
                            value={estadoSeleccionado}
                            options={estadosDisponibles}
                            onChange={(e) => setEstadoSeleccionado(e.value)}
                            optionLabel="titulo"
                            optionValue="codigo"
                            placeholder="Seleccione un estado"
                            className="w-full"
                        />
                    </div>

                    <div className="field">
                        <label htmlFor="descripcion">Descripción</label>
                        <InputTextarea
                            id="descripcion"
                            value={descripcion}
                            onChange={(e) => setDescripcion(e.target.value)}
                            rows={3}
                            className="w-full"
                            placeholder="Descripción opcional de la actualización"
                        />
                    </div>

                    <div className="field">
                        <label htmlFor="ubicacion">Ubicación</label>
                        <InputText
                            id="ubicacion"
                            value={ubicacion}
                            onChange={(e) => setUbicacion(e.target.value)}
                            className="w-full"
                            placeholder="Ej: Bodega Central, En ruta..."
                        />
                    </div>

                    <div className="field">
                        <label htmlFor="responsable">Responsable</label>
                        <InputText
                            id="responsable"
                            value={responsable}
                            onChange={(e) => setResponsable(e.target.value)}
                            className="w-full"
                            placeholder="Nombre del responsable"
                        />
                    </div>

                    <div className="field">
                        <label htmlFor="observaciones">Observaciones</label>
                        <InputTextarea
                            id="observaciones"
                            value={observaciones}
                            onChange={(e) => setObservaciones(e.target.value)}
                            rows={2}
                            className="w-full"
                            placeholder="Observaciones adicionales"
                        />
                    </div>
                </div>
            </Dialog>

            {/* Dialog para ver historial */}
            <Dialog
                visible={showHistorialDialog}
                style={{ width: '800px' }}
                header={`Historial de Seguimiento - ${selectedPedido?.numeroFactura}`}
                modal
                onHide={() => setShowHistorialDialog(false)}
            >
                {historial.length > 0 ? (
                    <Timeline 
                        value={historial} 
                        content={timelineContent}
                        className="customized-timeline"
                    />
                ) : (
                    <p className="text-center text-500">No hay historial disponible</p>
                )}
            </Dialog>
        </div>
    );
}
