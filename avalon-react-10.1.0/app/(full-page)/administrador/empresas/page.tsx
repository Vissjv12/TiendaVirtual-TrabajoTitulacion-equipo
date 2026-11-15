'use client';

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { DataTable, DataTableStateEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import { toApiSortParam } from '../../../../services/apiConfig';
import categoriasEmpresasService, { Empresa } from '../../../../services/categoriasEmpresasService';

const EmpresasPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados principales
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [debouncedSearch, setDebouncedSearch] = useState<string>('');
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('id');
    const [sortOrder, setSortOrder] = useState<1 | -1>(1);

    // Estados para el modal de detalles
    const [selectedEmpresa, setSelectedEmpresa] = useState<Empresa | null>(null);
    const [detailVisible, setDetailVisible] = useState(false);

    // Debounce búsqueda
    useEffect(() => {
        const timer = setTimeout(() => {
            setFirst(0);
            setDebouncedSearch(searchTerm);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    const cargarEmpresas = useCallback(async () => {
        setLoading(true);
        try {
            const page = Math.floor(first / rows);
            const sort = toApiSortParam(sortField, sortOrder, 'id,asc');
            const response = await categoriasEmpresasService.obtenerEmpresasPaginado({
                page,
                size: rows,
                sort,
                q: debouncedSearch.trim() || undefined
            });

            if (response.success && response.data) {
                setEmpresas(response.data.content || []);
                setTotalRecords(response.data.totalElements ?? 0);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando empresas',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando empresas:', error);
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

    // Cargar datos iniciales y cambios de paginación/filtro
    useEffect(() => {
        cargarEmpresas();
    }, [cargarEmpresas]);

    // Función de búsqueda
    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(e.target.value);
    };

    const handlePage = (event: DataTableStateEvent) => {
        setFirst(event.first ?? 0);
        setRows(event.rows ?? 10);
    };

    const handleSort = (event: DataTableStateEvent) => {
        setFirst(0);
        setSortField(event.sortField || 'id');
        setSortOrder((event.sortOrder === -1 ? -1 : 1) as 1 | -1);
    };

    // Confirmar eliminación
    const confirmarEliminacion = (empresa: Empresa) => {
        confirmDialog({
            message: `¿Estás seguro de que deseas eliminar la empresa "${empresa.nombre}"?`,
            header: 'Confirmar Eliminación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, eliminar',
            rejectLabel: 'Cancelar',
            accept: () => eliminarEmpresa(empresa.id)
        });
    };

    // Eliminar empresa
    const eliminarEmpresa = async (id: number) => {
        try {
            const response = await categoriasEmpresasService.eliminarEmpresa(id);
            
            if (response.success) {
                await cargarEmpresas();
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Empresa eliminada exitosamente',
                    life: 3000
                });
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
                detail: 'Error eliminando empresa',
                life: 3000
            });
        }
    };

    // Ver detalles
    const verDetalles = (empresa: Empresa) => {
        setSelectedEmpresa(empresa);
        setDetailVisible(true);
    };

    // Templates para las columnas
    const statusBodyTemplate = (rowData: Empresa) => {
        return (
            <Badge 
                value={rowData.activo ? 'Activo' : 'Inactivo'} 
                severity={rowData.activo ? 'success' : 'danger'}
            />
        );
    };

    const contactBodyTemplate = (rowData: Empresa) => {
        return (
            <div>
                {rowData.email && (
                    <div className="text-sm">
                        <i className="pi pi-envelope mr-1"></i>
                        {rowData.email}
                    </div>
                )}
                {rowData.telefono && (
                    <div className="text-sm text-600">
                        <i className="pi pi-phone mr-1"></i>
                        {rowData.telefono}
                    </div>
                )}
            </div>
        );
    };

    const actionBodyTemplate = (rowData: Empresa) => {
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-eye" 
                    className="p-button-rounded p-button-info p-button-sm" 
                    onClick={() => verDetalles(rowData)}
                    tooltip="Ver detalles"
                />
                <Button 
                    icon="pi pi-pencil" 
                    className="p-button-rounded p-button-success p-button-sm" 
                    onClick={() => router.push(`/emprendedor/empresas/${rowData.id}/editar`)}
                    tooltip="Editar"
                />
                <Button 
                    icon="pi pi-trash" 
                    className="p-button-rounded p-button-danger p-button-sm" 
                    onClick={() => confirmarEliminacion(rowData)}
                    tooltip="Eliminar"
                />
            </div>
        );
    };

    // Toolbar del listado
    const leftToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <Button 
                    label="Nueva Empresa" 
                    icon="pi pi-plus" 
                    className="p-button-success"
                    onClick={() => router.push('/emprendedor/empresas/crear')}
                />
                <Button 
                    label="Actualizar" 
                    icon="pi pi-refresh" 
                    className="p-button-outlined"
                    onClick={cargarEmpresas}
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
                        placeholder="Buscar empresas..." 
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

    return (
        <div className="emprendedor-empresas">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="mb-4">
                <h1 className="text-3xl font-bold text-900 mb-2">
                    <i className="pi pi-building text-primary mr-3"></i>
                    Gestión de Empresas
                </h1>
                <p className="text-600 text-lg">
                    Administra las empresas proveedoras de productos
                </p>
            </div>

            {/* Contenido principal */}
            <Card>
                <Toolbar 
                    className="mb-4" 
                    left={leftToolbarTemplate} 
                    right={rightToolbarTemplate}
                />

                {loading && empresas.length === 0 ? (
                    renderSkeleton()
                ) : (
                    <DataTable 
                        value={empresas}
                        lazy
                        loading={loading}
                        paginator
                        first={first}
                        rows={rows}
                        totalRecords={totalRecords}
                        rowsPerPageOptions={[5, 10, 25, 50]}
                        onPage={handlePage}
                        onSort={handleSort}
                        sortField={sortField}
                        sortOrder={sortOrder}
                        emptyMessage="No se encontraron empresas"
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} empresas"
                    >
                        <Column 
                            field="nombre" 
                            header="Nombre" 
                            sortable 
                            className="font-bold"
                        />
                        <Column 
                            field="ruc" 
                            header="RUC" 
                            sortable
                        />
                        <Column 
                            field="direccion" 
                            header="Dirección" 
                        />
                        <Column 
                            header="Contacto" 
                            body={contactBodyTemplate}
                        />
                        <Column 
                            field="activo" 
                            header="Estado" 
                            body={statusBodyTemplate} 
                            sortable
                        />
                        <Column 
                            field="fechaCreacion" 
                            header="Fecha Creación" 
                            sortable
                            body={(rowData) => categoriasEmpresasService.formatearFecha(rowData.fechaCreacion)}
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

            {/* Modal de detalles */}
            <Dialog
                header="Detalles de la Empresa"
                visible={detailVisible}
                style={{ width: '600px' }}
                breakpoints={{ '960px': '90vw', '641px': '95vw' }}
                modal
                onHide={() => setDetailVisible(false)}
            >
                {selectedEmpresa && (
                    <div className="grid">
                        <div className="col-12">
                            <h3 className="text-2xl font-bold text-900 mb-3">
                                {selectedEmpresa.nombre}
                            </h3>
                            
                            <div className="grid">
                                <div className="col-6">
                                    <strong>RUC:</strong>
                                    <div className="mt-1 text-600">{selectedEmpresa.ruc}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Estado:</strong>
                                    <div className="mt-1">
                                        <Badge 
                                            value={selectedEmpresa.activo ? 'Activo' : 'Inactivo'} 
                                            severity={selectedEmpresa.activo ? 'success' : 'danger'}
                                        />
                                    </div>
                                </div>
                                <div className="col-12">
                                    <strong>Dirección:</strong>
                                    <div className="mt-1 text-600">{selectedEmpresa.direccion || 'No especificada'}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Teléfono:</strong>
                                    <div className="mt-1 text-600">{selectedEmpresa.telefono || 'No especificado'}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Email:</strong>
                                    <div className="mt-1 text-600">{selectedEmpresa.email || 'No especificado'}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Creado:</strong>
                                    <div className="mt-1 text-600">
                                        {categoriasEmpresasService.formatearFecha(selectedEmpresa.fechaCreacion)}
                                    </div>
                                </div>
                                <div className="col-6">
                                    <strong>Actualizado:</strong>
                                    <div className="mt-1 text-600">
                                        {categoriasEmpresasService.formatearFecha(selectedEmpresa.fechaActualizacion)}
                                    </div>
                                </div>
                                <div className="col-12">
                                    <strong>ID:</strong>
                                    <div className="mt-1 font-bold">#{selectedEmpresa.id}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </Dialog>
        </div>
    );
};

export default EmpresasPage;