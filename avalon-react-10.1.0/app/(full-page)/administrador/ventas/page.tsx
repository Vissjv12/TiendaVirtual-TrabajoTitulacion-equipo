'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { DataTable, DataTableStateEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { InputText } from 'primereact/inputtext';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import { toApiSortParam } from '../../../../services/apiConfig';
import ventasService, { VentaResponse } from '../../../../services/ventasService';

const VentasPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados principales
    const [ventas, setVentas] = useState<VentaResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [debouncedSearch, setDebouncedSearch] = useState<string>('');
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('fechaVenta');
    const [sortOrder, setSortOrder] = useState<1 | -1>(-1);

    useEffect(() => {
        const timer = setTimeout(() => {
            setFirst(0);
            setDebouncedSearch(searchTerm);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    const cargarVentas = useCallback(async () => {
        setLoading(true);
        try {
            const page = Math.floor(first / rows);
            const sort = toApiSortParam(sortField, sortOrder, 'fechaVenta,desc');
            const response = await ventasService.listarVentasPaginado({
                page,
                size: rows,
                sort,
                q: debouncedSearch.trim() || undefined
            });

            if (response.success && response.data) {
                setVentas(response.data.content || []);
                setTotalRecords(response.data.totalElements ?? 0);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando ventas',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando ventas:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    }, [first, rows, sortField, sortOrder, debouncedSearch]);

    // Cargar datos iniciales y cambios de paginación
    useEffect(() => {
        cargarVentas();
    }, [cargarVentas]);

    // Función de filtro
    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(e.target.value);
    };

    const handlePage = (event: DataTableStateEvent) => {
        setFirst(event.first ?? 0);
        setRows(event.rows ?? 10);
    };

    const handleSort = (event: DataTableStateEvent) => {
        setFirst(0);
        setSortField(event.sortField || 'fechaVenta');
        setSortOrder((event.sortOrder === 1 ? 1 : -1) as 1 | -1);
    };

    // Completar venta
    const completarVenta = async (venta: VentaResponse) => {
        try {
            const response = await ventasService.completarVenta(venta.id);
            
            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta completada exitosamente',
                    life: 3000
                });
                cargarVentas(); // Recargar datos
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
    const confirmarCancelacion = (venta: VentaResponse) => {
        confirmDialog({
            message: `¿Estás seguro de que deseas cancelar la venta ${venta.numeroFactura}?`,
            header: 'Confirmar Cancelación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, cancelar',
            rejectLabel: 'No',
            accept: () => cancelarVenta(venta)
        });
    };

    // Cancelar venta
    const cancelarVenta = async (venta: VentaResponse) => {
        try {
            const response = await ventasService.cancelarVenta(venta.id);
            
            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta cancelada exitosamente',
                    life: 3000
                });
                cargarVentas(); // Recargar datos
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

    // Templates para las columnas
    const numeroFacturaBodyTemplate = (rowData: VentaResponse) => {
        return (
            <span className="font-bold text-primary">
                {rowData.numeroFactura}
            </span>
        );
    };

    const clienteBodyTemplate = (rowData: VentaResponse) => {
        return (
            <div>
                <div className="font-bold">{rowData.cliente.nombre} {rowData.cliente.apellido}</div>
                <div className="text-sm text-600">{rowData.cliente.email}</div>
            </div>
        );
    };

    const totalBodyTemplate = (rowData: VentaResponse) => {
        return (
            <span className="font-bold text-xl">
                {ventasService.formatearPrecio(rowData.total)}
            </span>
        );
    };

    const estadoBodyTemplate = (rowData: VentaResponse) => {
        return (
            <div className="flex align-items-center gap-2">
                <i className={ventasService.obtenerIconoEstado(rowData.estado)}></i>
                <Badge 
                    value={ventasService.formatearEstado(rowData.estado)} 
                    severity={ventasService.obtenerColorEstado(rowData.estado)}
                />
            </div>
        );
    };

    const fechaBodyTemplate = (rowData: VentaResponse) => {
        return ventasService.formatearFecha(rowData.fechaVenta);
    };

    const actionBodyTemplate = (rowData: VentaResponse) => {
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-eye" 
                    className="p-button-rounded p-button-info p-button-sm" 
                    onClick={() => router.push(`/emprendedor/ventas/${rowData.id}`)}
                    tooltip="Ver detalles"
                />
                {rowData.estado === 'PENDIENTE' && (
                    <>
                        <Button 
                            icon="pi pi-check" 
                            className="p-button-rounded p-button-success p-button-sm" 
                            onClick={() => completarVenta(rowData)}
                            tooltip="Completar venta"
                        />
                        <Button 
                            icon="pi pi-times" 
                            className="p-button-rounded p-button-danger p-button-sm" 
                            onClick={() => confirmarCancelacion(rowData)}
                            tooltip="Cancelar venta"
                        />
                    </>
                )}
            </div>
        );
    };

    // Toolbar del listado
    const leftToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <Button 
                    label="Actualizar" 
                    icon="pi pi-refresh" 
                    className="p-button-outlined"
                    onClick={cargarVentas}
                />
                <Button 
                    label="Exportar" 
                    icon="pi pi-download" 
                    className="p-button-help"
                    onClick={() => toast.current?.show({
                        severity: 'info',
                        summary: 'En desarrollo',
                        detail: 'Función de exportación próximamente',
                        life: 3000
                    })}
                />
            </div>
        );
    };

    const rightToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <span className="p-input-icon-left">
                    <i className="pi pi-search" />
                    <InputText 
                        value={searchTerm} 
                        onChange={handleSearch}
                        placeholder="Buscar ventas..." 
                        className="w-20rem"
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

    // Calcular resumen
    const calcularResumen = () => {
        const completadas = ventas.filter(v => v.estado === 'COMPLETADA').length;
        const pendientes = ventas.filter(v => v.estado === 'PENDIENTE').length;
        const canceladas = ventas.filter(v => v.estado === 'CANCELADA').length;
        const totalVentas = ventas.reduce((sum, v) => v.estado === 'COMPLETADA' ? sum + v.total : sum, 0);

        return { completadas, pendientes, canceladas, totalVentas };
    };

    const resumen = calcularResumen();

    return (
        <div className="ventas-page">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="mb-4">
                <h1 className="text-3xl font-bold text-900 mb-2">
                    <i className="pi pi-shopping-cart text-primary mr-3"></i>
                    Gestión de Ventas
                </h1>
                <p className="text-600 text-lg">
                    Administra las ventas y pedidos de tu negocio
                </p>
            </div>

            {/* Cards de resumen */}
            <div className="grid mb-4">
                <div className="col-12 md:col-3">
                    <Card className="bg-green-50 border-green-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-green-600 font-bold text-xl">
                                    {resumen.completadas}
                                </div>
                                <div className="text-600">Completadas</div>
                            </div>
                            <i className="pi pi-check-circle text-green-600 text-3xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-orange-50 border-orange-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-orange-600 font-bold text-xl">
                                    {resumen.pendientes}
                                </div>
                                <div className="text-600">Pendientes</div>
                            </div>
                            <i className="pi pi-clock text-orange-600 text-3xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-red-50 border-red-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-red-600 font-bold text-xl">
                                    {resumen.canceladas}
                                </div>
                                <div className="text-600">Canceladas</div>
                            </div>
                            <i className="pi pi-times-circle text-red-600 text-3xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-blue-50 border-blue-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-blue-600 font-bold text-xl">
                                    {ventasService.formatearPrecio(resumen.totalVentas)}
                                </div>
                                <div className="text-600">Total Ventas</div>
                            </div>
                            <i className="pi pi-dollar text-blue-600 text-3xl"></i>
                        </div>
                    </Card>
                </div>
            </div>

            {/* Tabla de ventas */}
            <Card>
                <Toolbar 
                    className="mb-4" 
                    left={leftToolbarTemplate} 
                    right={rightToolbarTemplate}
                />

                {loading && ventas.length === 0 ? (
                    renderSkeleton()
                ) : (
                    <DataTable 
                        value={ventas}
                        lazy
                        loading={loading}
                        paginator
                        first={first}
                        rows={rows}
                        totalRecords={totalRecords}
                        rowsPerPageOptions={[5, 10, 25, 50]}
                        onPage={handlePage}
                        onSort={handleSort}
                        emptyMessage="No se encontraron ventas"
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} ventas"
                        sortField={sortField}
                        sortOrder={sortOrder}
                    >
                        <Column 
                            field="numeroFactura" 
                            header="N° Factura" 
                            body={numeroFacturaBodyTemplate}
                            sortable
                        />
                        <Column 
                            field="cliente.nombre" 
                            header="Cliente" 
                            body={clienteBodyTemplate}
                        />
                        <Column 
                            field="total" 
                            header="Total" 
                            body={totalBodyTemplate}
                            sortable
                        />
                        <Column 
                            field="estado" 
                            header="Estado" 
                            body={estadoBodyTemplate}
                            sortable
                        />
                        <Column 
                            field="fechaVenta" 
                            header="Fecha" 
                            body={fechaBodyTemplate}
                            sortable
                        />
                        <Column 
                            body={actionBodyTemplate} 
                            header="Acciones"
                            style={{ width: '150px' }}
                            sortable={false}
                        />
                    </DataTable>
                )}
            </Card>
        </div>
    );
};

export default VentasPage;