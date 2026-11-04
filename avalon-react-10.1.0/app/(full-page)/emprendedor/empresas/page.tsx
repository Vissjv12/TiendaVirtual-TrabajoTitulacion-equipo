'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import categoriasEmpresasService, { Empresa } from '../../../../services/categoriasEmpresasService';
import authService from '../../../../services/authService';

const EmpresasPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);
    const isAdmin = authService.isAdmin();

    // Estados principales
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [empresasFiltradas, setEmpresasFiltradas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');

    // Estados para el modal de detalles
    const [selectedEmpresa, setSelectedEmpresa] = useState<Empresa | null>(null);
    const [detailVisible, setDetailVisible] = useState(false);

    // Cargar datos iniciales
    useEffect(() => {
        cargarEmpresas();
    }, []);

    const cargarEmpresas = async () => {
        setLoading(true);
        try {
            console.log('🔄 Cargando empresas...');
            const response = await categoriasEmpresasService.obtenerEmpresas();

            if (response.success && response.data) {
                setEmpresas(response.data);
                setEmpresasFiltradas(response.data);
                console.log('✅ Empresas cargadas:', response.data.length);
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
    };

    // Función de búsqueda
    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value.toLowerCase();
        setSearchTerm(value);
        
        if (!value.trim()) {
            setEmpresasFiltradas(empresas);
            return;
        }
        
        const filtered = empresas.filter(empresa => 
            empresa.nombre?.toLowerCase().includes(value) ||
            empresa.ruc?.toLowerCase().includes(value) ||
            empresa.email?.toLowerCase().includes(value) ||
            empresa.telefono?.toLowerCase().includes(value) ||
            empresa.direccion?.toLowerCase().includes(value)
        );
        
        setEmpresasFiltradas(filtered);
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
                const updatedEmpresas = empresas.filter(e => e.id !== id);
                setEmpresas(updatedEmpresas);
                setEmpresasFiltradas(updatedEmpresas);
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
        const canEdit = authService.canManageEmpresa(rowData.id);
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-eye" 
                    className="p-button-rounded p-button-info p-button-sm" 
                    onClick={() => verDetalles(rowData)}
                    tooltip="Ver detalles"
                />
                {canEdit && (
                    <Button 
                        icon="pi pi-pencil" 
                        className="p-button-rounded p-button-success p-button-sm" 
                        onClick={() => router.push(`/emprendedor/empresas/${rowData.id}/editar`)}
                        tooltip="Editar"
                    />
                )}
                {isAdmin && (
                    <Button 
                        icon="pi pi-trash" 
                        className="p-button-rounded p-button-danger p-button-sm" 
                        onClick={() => confirmarEliminacion(rowData)}
                        tooltip="Eliminar"
                    />
                )}
            </div>
        );
    };

    // Toolbar del listado
    const leftToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                {isAdmin && (
                    <Button 
                        label="Nueva Empresa" 
                        icon="pi pi-plus" 
                        className="p-button-success"
                        onClick={() => router.push('/emprendedor/empresas/crear')}
                    />
                )}
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
                    {isAdmin ? 'Gestión de Empresas' : 'Mi Empresa'}
                </h1>
                <p className="text-600 text-lg">
                    {isAdmin ? 'Administra las empresas proveedoras de productos' : 'Información y datos de tu empresa asociada'}
                </p>
            </div>

            {/* Contenido principal */}
            <Card>
                <Toolbar 
                    className="mb-4" 
                    left={leftToolbarTemplate} 
                    right={rightToolbarTemplate}
                />

                {loading ? (
                    renderSkeleton()
                ) : (
                    <DataTable 
                        value={empresasFiltradas}
                        paginator
                        rows={10}
                        rowsPerPageOptions={[5, 10, 25, 50]}
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
                            sortable
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