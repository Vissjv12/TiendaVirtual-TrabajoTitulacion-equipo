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
import categoriasEmpresasService, { Categoria } from '../../../../services/categoriasEmpresasService';
import authService from '../../../../services/authService';

const CategoriasPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);
    const isAdmin = authService.isAdmin();

    // Estados principales
    const [categorias, setCategorias] = useState<Categoria[]>([]);
    const [categoriasFiltradas, setCategoriasFiltradas] = useState<Categoria[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');

    // Estados para el modal de detalles
    const [selectedCategoria, setSelectedCategoria] = useState<Categoria | null>(null);
    const [detailVisible, setDetailVisible] = useState(false);

    // Cargar datos iniciales
    useEffect(() => {
        cargarCategorias();
    }, []);

    const cargarCategorias = async () => {
        setLoading(true);
        try {
            console.log('🔄 Cargando categorías...');
            const response = await categoriasEmpresasService.obtenerCategorias();

            if (response.success && response.data) {
                setCategorias(response.data);
                setCategoriasFiltradas(response.data);
                console.log('✅ Categorías cargadas:', response.data.length);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando categorías',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando categorías:', error);
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
            setCategoriasFiltradas(categorias);
            return;
        }
        
        const filtered = categorias.filter(categoria => 
            categoria.nombre?.toLowerCase().includes(value) ||
            categoria.descripcion?.toLowerCase().includes(value)
        );
        
        setCategoriasFiltradas(filtered);
    };

    // Confirmar eliminación
    const confirmarEliminacion = (categoria: Categoria) => {
        confirmDialog({
            message: `¿Estás seguro de que deseas eliminar la categoría "${categoria.nombre}"?`,
            header: 'Confirmar Eliminación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, eliminar',
            rejectLabel: 'Cancelar',
            accept: () => eliminarCategoria(categoria.id)
        });
    };

    // Eliminar categoría
    const eliminarCategoria = async (id: number) => {
        try {
            const response = await categoriasEmpresasService.eliminarCategoria(id);
            
            if (response.success) {
                const updatedCategorias = categorias.filter(c => c.id !== id);
                setCategorias(updatedCategorias);
                setCategoriasFiltradas(updatedCategorias);
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: 'Categoría eliminada exitosamente',
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
                detail: 'Error eliminando categoría',
                life: 3000
            });
        }
    };

    // Ver detalles
    const verDetalles = (categoria: Categoria) => {
        setSelectedCategoria(categoria);
        setDetailVisible(true);
    };

    // Templates para las columnas
    const statusBodyTemplate = (rowData: Categoria) => {
        return (
            <Badge 
                value={rowData.activo ? 'Activo' : 'Inactivo'} 
                severity={rowData.activo ? 'success' : 'danger'}
            />
        );
    };

    const actionBodyTemplate = (rowData: Categoria) => {
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-eye" 
                    className="p-button-rounded p-button-info p-button-sm" 
                    onClick={() => verDetalles(rowData)}
                    tooltip="Ver detalles"
                />
                {isAdmin && (
                    <>
                        <Button 
                            icon="pi pi-pencil" 
                            className="p-button-rounded p-button-success p-button-sm" 
                            onClick={() => router.push(`/emprendedor/categorias/${rowData.id}/editar`)}
                            tooltip="Editar"
                        />
                        <Button 
                            icon="pi pi-trash" 
                            className="p-button-rounded p-button-danger p-button-sm" 
                            onClick={() => confirmarEliminacion(rowData)}
                            tooltip="Eliminar"
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
                    label="Nueva Categoría" 
                    icon="pi pi-plus" 
                    className="p-button-success"
                    onClick={() => router.push('/emprendedor/categorias/crear')}
                />
                <Button 
                    label="Actualizar" 
                    icon="pi pi-refresh" 
                    className="p-button-outlined"
                    onClick={cargarCategorias}
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
                        placeholder="Buscar categorías..." 
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
        <div className="emprendedor-categorias">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="mb-4">
                <h1 className="text-3xl font-bold text-900 mb-2">
                    <i className="pi pi-tags text-primary mr-3"></i>
                    Gestión de Categorías
                </h1>
                <p className="text-600 text-lg">
                    Administra las categorías de productos de tu negocio
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
                        value={categoriasFiltradas}
                        paginator
                        rows={10}
                        rowsPerPageOptions={[5, 10, 25, 50]}
                        emptyMessage="No se encontraron categorías"
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} categorías"
                    >
                        <Column 
                            field="nombre" 
                            header="Nombre" 
                            sortable 
                            className="font-bold"
                        />
                        <Column 
                            field="descripcion" 
                            header="Descripción" 
                            sortable
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
                header="Detalles de la Categoría"
                visible={detailVisible}
                style={{ width: '500px' }}
                modal
                onHide={() => setDetailVisible(false)}
            >
                {selectedCategoria && (
                    <div className="grid">
                        <div className="col-12">
                            <h3 className="text-2xl font-bold text-900 mb-3">
                                {selectedCategoria.nombre}
                            </h3>
                            
                            <div className="mb-3">
                                <strong>Descripción:</strong>
                                <p className="mt-1 text-600">{selectedCategoria.descripcion || 'Sin descripción'}</p>
                            </div>
                            
                            <div className="grid">
                                <div className="col-6">
                                    <strong>Estado:</strong>
                                    <div className="mt-1">
                                        <Badge 
                                            value={selectedCategoria.activo ? 'Activo' : 'Inactivo'} 
                                            severity={selectedCategoria.activo ? 'success' : 'danger'}
                                        />
                                    </div>
                                </div>
                                <div className="col-6">
                                    <strong>ID:</strong>
                                    <div className="mt-1 font-bold">#{selectedCategoria.id}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Creado:</strong>
                                    <div className="mt-1 text-600">
                                        {categoriasEmpresasService.formatearFecha(selectedCategoria.fechaCreacion)}
                                    </div>
                                </div>
                                <div className="col-6">
                                    <strong>Actualizado:</strong>
                                    <div className="mt-1 text-600">
                                        {categoriasEmpresasService.formatearFecha(selectedCategoria.fechaActualizacion)}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </Dialog>
        </div>
    );
};

export default CategoriasPage;