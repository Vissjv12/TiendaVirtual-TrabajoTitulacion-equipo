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
import authService from '../../../../services/authService';
import { exportVentasToCsv, fetchAllPaginatedRecords } from '../../../../services/csvExport';

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
    const [processingIds, setProcessingIds] = useState<Set<number>>(new Set());
    const [exporting, setExporting] = useState<boolean>(false);

    const primaryColor = 'var(--primary-color)';

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
            const userInfo = authService.getUserInfo();
            const emprendedorId = userInfo?.empresaId;

            if (!emprendedorId) {
                console.warn('⚠️ No se encontró empresaId del emprendedor');
                setVentas([]);
                setTotalRecords(0);
                toast.current?.show({
                    severity: 'warn',
                    summary: 'Atención',
                    detail: 'No se pudo identificar tu empresa para filtrar ventas',
                    life: 4000
                });
                return;
            }

            const page = Math.floor(first / rows);
            const sort = toApiSortParam(sortField, sortOrder, 'fechaVenta,desc');
            const response = await ventasService.listarVentasPorEmprendedorPaginado(emprendedorId, {
                page,
                size: rows,
                sort,
                q: debouncedSearch.trim() || undefined
            });

            if (response.success && response.data) {
                setVentas(response.data.content || []);
                setTotalRecords(response.data.totalElements ?? 0);
            } else {
                console.error('❌ Error cargando ventas:', response.message);
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando ventas',
                    life: 5000
                });
            }
        } catch (error: any) {
            console.error('❌ Error cargando ventas:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 5000
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

    // Completar venta con manejo de errores mejorado
    const completarVenta = async (venta: VentaResponse) => {
        if (!ventasService.puedeCompletarse(venta)) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Acción no disponible',
                detail: 'Esta venta no puede ser completada en su estado actual',
                life: 3000
            });
            return;
        }

        setProcessingIds((prev) => new Set(prev).add(venta.id));

        try {
            console.log('✅ Intentando completar venta:', venta.id);
            const response = await ventasService.completarVenta(venta.id);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta completada exitosamente',
                    life: 3000
                });
                window.dispatchEvent(new Event('pagosUpdated'));
                await cargarVentas(); // Recargar datos
            } else {
                console.error('❌ Error completando venta:', response.message);
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error completando la venta',
                    life: 5000
                });
            }
        } catch (error: any) {
            console.error('❌ Error en completarVenta:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión al completar la venta',
                life: 5000
            });
        } finally {
            setProcessingIds((prev) => {
                const newSet = new Set(prev);
                newSet.delete(venta.id);
                return newSet;
            });
        }
    };

    // Confirmar cancelación
    const confirmarCancelacion = (venta: VentaResponse) => {
        if (!ventasService.puedeCancelarse(venta)) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Acción no disponible',
                detail: 'Esta venta no puede ser cancelada en su estado actual',
                life: 3000
            });
            return;
        }

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

    // Cancelar venta con manejo de errores mejorado
    const cancelarVenta = async (venta: VentaResponse) => {
        setProcessingIds((prev) => new Set(prev).add(venta.id));

        try {
            console.log('❌ Intentando cancelar venta:', venta.id);
            const response = await ventasService.cancelarVenta(venta.id);

            if (response.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Venta cancelada exitosamente',
                    life: 3000
                });
                window.dispatchEvent(new Event('pagosUpdated'));
                await cargarVentas(); // Recargar datos
            } else {
                console.error('❌ Error cancelando venta:', response.message);
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cancelando la venta',
                    life: 5000
                });
            }
        } catch (error: any) {
            console.error('❌ Error en cancelarVenta:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión al cancelar la venta',
                life: 5000
            });
        } finally {
            setProcessingIds((prev) => {
                const newSet = new Set(prev);
                newSet.delete(venta.id);
                return newSet;
            });
        }
    };

    // Templates para las columnas
    const numeroFacturaBodyTemplate = (rowData: VentaResponse) => {
        return (
            <span className="font-bold" style={{ color: primaryColor }}>
                {rowData.numeroFactura}
            </span>
        );
    };

    const clienteBodyTemplate = (rowData: VentaResponse) => {
        return (
            <div>
                <div className="font-bold">
                    {rowData.cliente.nombre} {rowData.cliente.apellido}
                </div>
                <div className="text-sm text-600">{rowData.cliente.email}</div>
            </div>
        );
    };

    const totalBodyTemplate = (rowData: VentaResponse) => {
        return <span className="font-bold text-xl">{ventasService.formatearPrecio(rowData.total)}</span>;
    };

    const estadoBodyTemplate = (rowData: VentaResponse) => {
        return (
            <div className="flex align-items-center gap-2">
                <i className={ventasService.obtenerIconoEstado(rowData.estado)}></i>
                <Badge value={ventasService.formatearEstado(rowData.estado)} severity={ventasService.obtenerColorEstado(rowData.estado)} />
            </div>
        );
    };

    const fechaBodyTemplate = (rowData: VentaResponse) => {
        return ventasService.formatearFecha(rowData.fechaVenta);
    };

    const actionBodyTemplate = (rowData: VentaResponse) => {
        const isProcessing = processingIds.has(rowData.id);
        const puedeCompletar = ventasService.puedeCompletarse(rowData);
        const puedeCancelar = ventasService.puedeCancelarse(rowData);

        return (
            <div className="flex gap-2">
                <Button icon="pi pi-eye" className="p-button-rounded p-button-info p-button-sm" onClick={() => router.push(`/emprendedor/ventas/${rowData.id}`)} tooltip="Ver detalles" disabled={isProcessing} />

                {puedeCompletar && (
                    <Button icon={isProcessing ? 'pi pi-spin pi-spinner' : 'pi pi-check'} className="p-button-rounded p-button-success p-button-sm" onClick={() => completarVenta(rowData)} tooltip="Completar venta" disabled={isProcessing} />
                )}

                {puedeCancelar && (
                    <Button icon={isProcessing ? 'pi pi-spin pi-spinner' : 'pi pi-times'} className="p-button-rounded p-button-danger p-button-sm" onClick={() => confirmarCancelacion(rowData)} tooltip="Cancelar venta" disabled={isProcessing} />
                )}
            </div>
        );
    };

    const exportarVentas = async () => {
        const userInfo = authService.getUserInfo();
        const emprendedorId = userInfo?.empresaId;
        if (!emprendedorId) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Atención',
                detail: 'No se pudo identificar tu empresa para exportar ventas',
                life: 4000
            });
            return;
        }

        setExporting(true);
        try {
            const sort = toApiSortParam(sortField, sortOrder, 'fechaVenta,desc');
            const result = await fetchAllPaginatedRecords((page, size) =>
                ventasService.listarVentasPorEmprendedorPaginado(emprendedorId, {
                    page,
                    size,
                    sort,
                    q: debouncedSearch.trim() || undefined
                })
            );

            if (!result.success) {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: result.message || 'No se pudieron obtener las ventas para exportar',
                    life: 4000
                });
                return;
            }

            if (result.items.length === 0) {
                toast.current?.show({
                    severity: 'info',
                    summary: 'Sin datos',
                    detail: 'No hay ventas que coincidan con el filtro actual para exportar',
                    life: 3000
                });
                return;
            }

            const filename = exportVentasToCsv(result.items, 'ventas_emprendedor');
            toast.current?.show({
                severity: 'success',
                summary: 'Exportación completada',
                detail: `Se exportaron ${result.items.length} ventas a ${filename}`,
                life: 3500
            });
        } catch (error) {
            console.error('❌ Error exportando ventas:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Ocurrió un error al generar el archivo CSV',
                life: 4000
            });
        } finally {
            setExporting(false);
        }
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
                    disabled={loading || exporting}
                    style={{
                        borderColor: primaryColor,
                        color: primaryColor
                    }}
                />
                <Button
                    label={exporting ? 'Exportando...' : 'Exportar CSV'}
                    icon={exporting ? 'pi pi-spin pi-spinner' : 'pi pi-download'}
                    className="p-button-help"
                    onClick={exportarVentas}
                    disabled={loading || exporting}
                />
            </div>
        );
    };

    const rightToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <span className="p-input-icon-left">
                    <i className="pi pi-search" />
                    <InputText value={searchTerm} onChange={handleSearch} placeholder="Buscar ventas..." className="w-20rem" />
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
        const completadas = ventas.filter((v) => v.estado?.toUpperCase() === 'COMPLETADA' || v.estado?.toUpperCase() === 'COMPLETED').length;

        const pendientes = ventas.filter((v) => v.estado?.toUpperCase() === 'PENDIENTE' || v.estado?.toUpperCase() === 'PENDING').length;

        const canceladas = ventas.filter((v) => v.estado?.toUpperCase() === 'CANCELADA' || v.estado?.toUpperCase() === 'CANCELLED' || v.estado?.toUpperCase() === 'CANCELED').length;

        const totalVentas = ventas.reduce((sum, v) => {
            const esCompletada = v.estado?.toUpperCase() === 'COMPLETADA' || v.estado?.toUpperCase() === 'COMPLETED';
            return esCompletada ? sum + (v.total || 0) : sum;
        }, 0);

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
                    <i className="pi pi-shopping-cart mr-3" style={{ color: primaryColor }}></i>
                    Gestión de Ventas
                </h1>
                <p className="text-600 text-lg">Administra las ventas y pedidos de tu negocio</p>
            </div>

            {/* Cards de resumen */}
            <div className="grid mb-4">
                <div className="col-12 md:col-3">
                    <Card className="bg-green-50 border-green-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-green-600 font-bold text-2xl">{resumen.completadas}</div>
                                <div className="text-600">Completadas</div>
                            </div>
                            <i className="pi pi-check-circle text-green-600 text-4xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-orange-50 border-orange-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-orange-600 font-bold text-2xl">{resumen.pendientes}</div>
                                <div className="text-600">Pendientes</div>
                            </div>
                            <i className="pi pi-clock text-orange-600 text-4xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-red-50 border-red-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-red-600 font-bold text-2xl">{resumen.canceladas}</div>
                                <div className="text-600">Canceladas</div>
                            </div>
                            <i className="pi pi-times-circle text-red-600 text-4xl"></i>
                        </div>
                    </Card>
                </div>
                <div className="col-12 md:col-3">
                    <Card className="bg-blue-50 border-blue-500">
                        <div className="flex align-items-center justify-content-between">
                            <div>
                                <div className="text-blue-600 font-bold text-2xl">{ventasService.formatearPrecio(resumen.totalVentas)}</div>
                                <div className="text-600">Total Ventas</div>
                            </div>
                            <i className="pi pi-dollar text-blue-600 text-4xl"></i>
                        </div>
                    </Card>
                </div>
            </div>

            {/* Tabla de ventas */}
            <Card>
                <Toolbar className="mb-4" left={leftToolbarTemplate} right={rightToolbarTemplate} />

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
                        <Column field="numeroFactura" header="N° Factura" body={numeroFacturaBodyTemplate} sortable />
                        <Column field="cliente.nombre" header="Cliente" body={clienteBodyTemplate} />
                        <Column field="total" header="Total" body={totalBodyTemplate} sortable />
                        <Column field="estado" header="Estado" body={estadoBodyTemplate} sortable />
                        <Column field="fechaVenta" header="Fecha" body={fechaBodyTemplate} sortable />
                        <Column body={actionBodyTemplate} header="Acciones" style={{ width: '150px' }} sortable={false} />
                    </DataTable>
                )}
            </Card>
        </div>
    );
};

export default VentasPage;
