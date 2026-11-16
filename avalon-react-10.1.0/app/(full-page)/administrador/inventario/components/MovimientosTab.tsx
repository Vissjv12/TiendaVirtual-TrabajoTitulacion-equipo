'use client';

import React, { useState, useEffect, useCallback, RefObject } from 'react';
import { Button } from 'primereact/button';
import { DataTable, DataTableStateEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { Calendar } from 'primereact/calendar';
import { Dropdown } from 'primereact/dropdown';
import { InputNumber } from 'primereact/inputnumber';
import { InputTextarea } from 'primereact/inputtextarea';
import { toApiSortParam } from '../../../../../services/apiConfig';
import inventarioService, { MovimientoInventario, MovimientoRequest } from '../../../../../services/inventarioService';

interface MovimientosTabProps {
    toast: RefObject<Toast>;
}

const formatDateParam = (date: Date | null): string | undefined => {
    if (!date) return undefined;
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
};

const MovimientosTab: React.FC<MovimientosTabProps> = ({ toast }) => {
    // Estados principales
    const [movimientos, setMovimientos] = useState<MovimientoInventario[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [debouncedSearch, setDebouncedSearch] = useState<string>('');
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('fechaMovimiento');
    const [sortOrder, setSortOrder] = useState<1 | -1>(-1);

    // Estados para filtros
    const [filtroTipo, setFiltroTipo] = useState<string>('');
    const [filtroFecha, setFiltroFecha] = useState<Date | null>(null);

    // Estados para modales
    const [selectedMovimiento, setSelectedMovimiento] = useState<MovimientoInventario | null>(null);
    const [detailVisible, setDetailVisible] = useState(false);
    const [nuevoMovimientoVisible, setNuevoMovimientoVisible] = useState(false);

    // Estados para nuevo movimiento
    const [nuevoMovimiento, setNuevoMovimiento] = useState<MovimientoRequest>({
        inventarioId: 0,
        tipoMovimiento: 'ENTRADA',
        cantidad: 0,
        motivo: '',
        usuarioId: 1 // Por defecto, se debería obtener del contexto de usuario
    });

    // Opciones para tipos de movimiento
    const tiposMovimiento = [
        { label: 'Todos', value: '' },
        { label: 'Entrada', value: 'ENTRADA' },
        { label: 'Salida', value: 'SALIDA' },
        { label: 'Ajuste', value: 'AJUSTE' },
        { label: 'Devolución', value: 'DEVOLUCION' },
        { label: 'Transferencia', value: 'TRANSFERENCIA' }
    ];

    const tiposMovimientoCrear = tiposMovimiento.slice(1); // Sin "Todos"

    // Debounce búsqueda por motivo
    useEffect(() => {
        const timer = setTimeout(() => {
            setFirst(0);
            setDebouncedSearch(searchTerm);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    const cargarMovimientos = useCallback(async () => {
        setLoading(true);
        try {
            const page = Math.floor(first / rows);
            const sort = toApiSortParam(sortField, sortOrder, 'fechaMovimiento,desc');
            const fechaParam = formatDateParam(filtroFecha);

            const response = await inventarioService.obtenerMovimientosPaginado({
                page,
                size: rows,
                sort,
                tipo: filtroTipo || undefined,
                motivo: debouncedSearch.trim() || undefined,
                fechaDesde: fechaParam,
                fechaHasta: fechaParam
            });

            if (response.success && response.data) {
                setMovimientos(response.data.content || []);
                setTotalRecords(response.data.totalElements ?? 0);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando movimientos',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando movimientos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    }, [first, rows, sortField, sortOrder, filtroTipo, debouncedSearch, filtroFecha, toast]);

    // Cargar movimientos paginados cuando cambie página, orden o filtros
    useEffect(() => {
        cargarMovimientos();
    }, [cargarMovimientos]);

    // Limpiar filtros
    const limpiarFiltros = () => {
        setSearchTerm('');
        setDebouncedSearch('');
        setFiltroTipo('');
        setFiltroFecha(null);
        setFirst(0);
    };

    const handlePage = (event: DataTableStateEvent) => {
        setFirst(event.first ?? 0);
        setRows(event.rows ?? 10);
    };

    const handleSort = (event: DataTableStateEvent) => {
        setFirst(0);
        setSortField(event.sortField || 'fechaMovimiento');
        setSortOrder((event.sortOrder === 1 ? 1 : -1) as 1 | -1);
    };

    // Ver detalles
    const verDetalles = (movimiento: MovimientoInventario) => {
        setSelectedMovimiento(movimiento);
        setDetailVisible(true);
    };

    // Abrir modal para nuevo movimiento
    const abrirNuevoMovimiento = () => {
        setNuevoMovimiento({
            inventarioId: 0,
            tipoMovimiento: 'ENTRADA',
            cantidad: 0,
            motivo: '',
            usuarioId: 1
        });
        setNuevoMovimientoVisible(true);
    };

    // Crear nuevo movimiento
    const crearMovimiento = async () => {
        try {
            if (nuevoMovimiento.inventarioId === 0) {
                toast.current?.show({
                    severity: 'warn',
                    summary: 'Advertencia',
                    detail: 'Selecciona un inventario válido',
                    life: 3000
                });
                return;
            }

            const response = await inventarioService.registrarMovimiento(nuevoMovimiento);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Movimiento registrado correctamente',
                    life: 3000
                });
                
                setNuevoMovimientoVisible(false);
                cargarMovimientos(); // Recargar datos
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error registrando movimiento',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error creando movimiento:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
        }
    };

    // Templates para las columnas
    const tipoBodyTemplate = (rowData: MovimientoInventario) => {
        const getSeverity = (tipo: string): "success" | "info" | "warning" | "danger" | null => {
            switch (tipo) {
                case 'ENTRADA': return 'success';
                case 'SALIDA': return 'danger';
                case 'AJUSTE': return 'warning';
                case 'DEVOLUCION': return 'info';
                case 'TRANSFERENCIA': return 'info'; // Cambiado de 'secondary' a 'info'
                default: return null;
            }
        };

        return (
            <Badge 
                value={inventarioService.formatearTipoMovimiento(rowData.tipoMovimiento)} 
                severity={getSeverity(rowData.tipoMovimiento)}
            />
        );
    };

    const cantidadBodyTemplate = (rowData: MovimientoInventario) => {
        const isPositive = rowData.tipoMovimiento === 'ENTRADA' || rowData.tipoMovimiento === 'DEVOLUCION';
        return (
            <span className={`font-bold ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
                {isPositive ? '+' : '-'}{Math.abs(rowData.cantidad)}
            </span>
        );
    };

    const actionBodyTemplate = (rowData: MovimientoInventario) => {
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-eye" 
                    className="p-button-rounded p-button-info p-button-sm" 
                    onClick={() => verDetalles(rowData)}
                    tooltip="Ver detalles"
                />
            </div>
        );
    };

    // Toolbar del listado
    const leftToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <Button 
                    label="Nuevo Movimiento" 
                    icon="pi pi-plus" 
                    className="p-button-success"
                    onClick={abrirNuevoMovimiento}
                />
                <Button 
                    label="Actualizar" 
                    icon="pi pi-refresh" 
                    className="p-button-outlined"
                    onClick={cargarMovimientos}
                />
            </div>
        );
    };

    const rightToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <Dropdown
                    value={filtroTipo}
                    options={tiposMovimiento}
                    onChange={(e) => {
                        setFirst(0);
                        setFiltroTipo(e.value);
                    }}
                    placeholder="Filtrar por tipo"
                    className="w-10rem"
                />
                <Calendar
                    value={filtroFecha}
                    onChange={(e) => {
                        setFirst(0);
                        setFiltroFecha((e.value as Date) ?? null);
                    }}
                    placeholder="Filtrar por fecha"
                    showIcon
                    className="w-10rem"
                />
                <Button
                    icon="pi pi-filter-slash"
                    className="p-button-outlined"
                    onClick={limpiarFiltros}
                    tooltip="Limpiar filtros"
                />
                <span className="p-input-icon-left">
                    <i className="pi pi-search" />
                    <InputText 
                        value={searchTerm} 
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Buscar por motivo..." 
                        className="w-15rem"
                    />
                </span>
            </div>
        );
    };

    // Renderizar esqueleto mientras carga
    const renderSkeleton = () => (
        <div className="grid">
            {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="col-12">
                    <Skeleton width="100%" height="4rem" className="mb-2" />
                </div>
            ))}
        </div>
    );

    return (
        <div className="movimientos-tab">
            <Toolbar 
                className="mb-4" 
                left={leftToolbarTemplate} 
                right={rightToolbarTemplate}
            />

            {loading && movimientos.length === 0 ? (
                renderSkeleton()
            ) : (
                <DataTable 
                    value={movimientos}
                    lazy
                    loading={loading}
                    paginator
                    first={first}
                    rows={rows}
                    totalRecords={totalRecords}
                    rowsPerPageOptions={[10, 15, 25, 50]}
                    onPage={handlePage}
                    onSort={handleSort}
                    emptyMessage="No se encontraron movimientos"
                    responsiveLayout="scroll"
                    className="p-datatable-sm"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} movimientos"
                    sortField={sortField}
                    sortOrder={sortOrder}
                >
                    <Column 
                        field="id" 
                        header="ID" 
                        sortable
                        style={{ width: '80px' }}
                    />
                    <Column 
                        field="tipoMovimiento" 
                        header="Tipo" 
                        body={tipoBodyTemplate}
                        sortable
                    />
                    <Column 
                        field="cantidad" 
                        header="Cantidad" 
                        body={cantidadBodyTemplate}
                        sortable
                    />
                    <Column 
                        field="motivo" 
                        header="Motivo" 
                        sortable
                    />
                    <Column 
                        field="fechaMovimiento" 
                        header="Fecha" 
                        sortable
                        body={(rowData) => inventarioService.formatearFecha(rowData.fechaMovimiento)}
                    />
                    <Column 
                        field="inventarioId" 
                        header="Inventario ID" 
                        sortable
                    />
                    <Column 
                        body={actionBodyTemplate} 
                        header="Acciones"
                        style={{ width: '100px' }}
                        sortable={false}
                    />
                </DataTable>
            )}

            {/* Modal de detalles */}
            <Dialog
                header="Detalles del Movimiento"
                visible={detailVisible}
                style={{ width: '500px' }}
                breakpoints={{ '960px': '90vw', '641px': '95vw' }}
                modal
                onHide={() => setDetailVisible(false)}
            >
                {selectedMovimiento && (
                    <div>
                        <div className="grid">
                            <div className="col-6">
                                <strong>ID:</strong>
                                <div className="mt-1 font-bold">#{selectedMovimiento.id}</div>
                            </div>
                            <div className="col-6">
                                <strong>Tipo:</strong>
                                <div className="mt-1">
                                    <Badge 
                                        value={inventarioService.formatearTipoMovimiento(selectedMovimiento.tipoMovimiento)} 
                                        severity={selectedMovimiento.tipoMovimiento === 'ENTRADA' || selectedMovimiento.tipoMovimiento === 'DEVOLUCION' ? 'success' : 'danger'}
                                    />
                                </div>
                            </div>
                            <div className="col-6">
                                <strong>Cantidad:</strong>
                                <div className="mt-1 text-xl font-bold">
                                    {selectedMovimiento.tipoMovimiento === 'ENTRADA' ? '+' : '-'}
                                    {Math.abs(selectedMovimiento.cantidad)}
                                </div>
                            </div>
                            <div className="col-6">
                                <strong>Inventario ID:</strong>
                                <div className="mt-1 text-600">#{selectedMovimiento.inventarioId}</div>
                            </div>
                            <div className="col-12">
                                <strong>Motivo:</strong>
                                <div className="mt-1 text-600">{selectedMovimiento.motivo || 'Sin motivo especificado'}</div>
                            </div>
                            <div className="col-6">
                                <strong>Fecha:</strong>
                                <div className="mt-1 text-600">
                                    {inventarioService.formatearFecha(selectedMovimiento.fechaMovimiento)}
                                </div>
                            </div>
                            <div className="col-6">
                                <strong>Usuario:</strong>
                                <div className="mt-1 text-600">ID: {selectedMovimiento.usuarioId}</div>
                            </div>
                        </div>
                    </div>
                )}
            </Dialog>

            {/* Modal para nuevo movimiento */}
            <Dialog
                header="Registrar Nuevo Movimiento"
                visible={nuevoMovimientoVisible}
                style={{ width: '500px' }}
                breakpoints={{ '960px': '90vw', '641px': '95vw' }}
                modal
                onHide={() => setNuevoMovimientoVisible(false)}
            >
                <div className="grid">
                    <div className="col-12">
                        <label htmlFor="inventarioId" className="block text-900 font-medium mb-2">
                            ID del Inventario <span className="text-red-500">*</span>
                        </label>
                        <InputNumber
                            id="inventarioId"
                            value={nuevoMovimiento.inventarioId}
                            onValueChange={(e) => setNuevoMovimiento(prev => ({ ...prev, inventarioId: e.value || 0 }))}
                            placeholder="ID del inventario"
                            className="w-full"
                            min={1}
                        />
                        <small className="text-600">ID del inventario que será afectado</small>
                    </div>

                    <div className="col-12">
                        <label htmlFor="tipoMovimiento" className="block text-900 font-medium mb-2">
                            Tipo de Movimiento <span className="text-red-500">*</span>
                        </label>
                        <Dropdown
                            id="tipoMovimiento"
                            value={nuevoMovimiento.tipoMovimiento}
                            options={tiposMovimientoCrear}
                            onChange={(e) => setNuevoMovimiento(prev => ({ ...prev, tipoMovimiento: e.value }))}
                            className="w-full"
                        />
                    </div>

                    <div className="col-12">
                        <label htmlFor="cantidad" className="block text-900 font-medium mb-2">
                            Cantidad <span className="text-red-500">*</span>
                        </label>
                        <InputNumber
                            id="cantidad"
                            value={nuevoMovimiento.cantidad}
                            onValueChange={(e) => setNuevoMovimiento(prev => ({ ...prev, cantidad: e.value || 0 }))}
                            placeholder="Cantidad del movimiento"
                            className="w-full"
                            min={1}
                        />
                    </div>

                    <div className="col-12">
                        <label htmlFor="motivo" className="block text-900 font-medium mb-2">
                            Motivo
                        </label>
                        <InputTextarea
                            id="motivo"
                            value={nuevoMovimiento.motivo}
                            onChange={(e) => setNuevoMovimiento(prev => ({ ...prev, motivo: e.target.value }))}
                            placeholder="Razón del movimiento..."
                            className="w-full"
                            rows={3}
                        />
                    </div>
                </div>

                <div className="flex justify-content-end gap-2 mt-4">
                    <Button
                        label="Cancelar"
                        icon="pi pi-times"
                        className="p-button-outlined"
                        onClick={() => setNuevoMovimientoVisible(false)}
                    />
                    <Button
                        label="Registrar Movimiento"
                        icon="pi pi-check"
                        className="p-button-success"
                        onClick={crearMovimiento}
                        disabled={nuevoMovimiento.inventarioId === 0 || nuevoMovimiento.cantidad === 0}
                    />
                </div>
            </Dialog>
        </div>
    );
};

export default MovimientosTab;