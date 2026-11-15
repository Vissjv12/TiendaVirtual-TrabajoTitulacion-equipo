'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { DataTable, DataTableStateEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Tag } from 'primereact/tag';
import { Card } from 'primereact/card';
import { Toolbar } from 'primereact/toolbar';
import { confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { Dropdown } from 'primereact/dropdown';
import { Badge } from 'primereact/badge';
import { toApiSortParam } from '@/services/apiConfig';
import usuariosService, { Usuario, ROLES_DISPONIBLES, ESTADOS_USUARIO } from '@/services/usuariosService';

export default function UsuariosPage() {
    const router = useRouter();
    const [usuarios, setUsuarios] = useState<Usuario[]>([]);
    const [loading, setLoading] = useState(true);
    const [globalFilter, setGlobalFilter] = useState('');
    const [debouncedFilter, setDebouncedFilter] = useState('');
    const [selectedUsuarios, setSelectedUsuarios] = useState<Usuario[]>([]);
    const [filtroRol, setFiltroRol] = useState<string>('');
    const [filtroEstado, setFiltroEstado] = useState<boolean | null>(null);
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('id');
    const [sortOrder, setSortOrder] = useState<1 | -1>(-1);
    const toast = React.useRef<Toast>(null);

    useEffect(() => {
        const timer = setTimeout(() => {
            setFirst(0);
            setDebouncedFilter(globalFilter);
        }, 300);
        return () => clearTimeout(timer);
    }, [globalFilter]);

    const cargarUsuarios = useCallback(async () => {
        setLoading(true);
        try {
            const page = Math.floor(first / rows);
            const sort = toApiSortParam(sortField, sortOrder, 'id,desc');
            const response = await usuariosService.obtenerUsuariosPaginado({
                page,
                size: rows,
                sort,
                q: debouncedFilter.trim() || undefined,
                rol: filtroRol || undefined,
                activo: filtroEstado ?? undefined
            });
            if (response.success && response.data) {
                setUsuarios(response.data.content || []);
                setTotalRecords(response.data.totalElements ?? 0);
            } else {
                mostrarError('Error al cargar usuarios: ' + response.message);
            }
        } catch (error) {
            console.error('Error:', error);
            mostrarError('Error de conexión al cargar usuarios');
        } finally {
            setLoading(false);
        }
    }, [first, rows, sortField, sortOrder, debouncedFilter, filtroRol, filtroEstado]);

    useEffect(() => {
        cargarUsuarios();
    }, [cargarUsuarios]);

    const handlePage = (event: DataTableStateEvent) => {
        setFirst(event.first ?? 0);
        setRows(event.rows ?? 10);
    };

    const handleSort = (event: DataTableStateEvent) => {
        setFirst(0);
        setSortField(event.sortField || 'id');
        setSortOrder((event.sortOrder === 1 ? 1 : -1) as 1 | -1);
    };

    const mostrarExito = (mensaje: string) => {
        toast.current?.show({ severity: 'success', summary: 'Éxito', detail: mensaje });
    };

    const mostrarError = (mensaje: string) => {
        toast.current?.show({ severity: 'error', summary: 'Error', detail: mensaje });
    };

    const confirmarEliminar = (usuario: Usuario) => {
        confirmDialog({
            message: `¿Está seguro de eliminar al usuario "${usuario.username}"?`,
            header: 'Confirmar eliminación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            accept: () => eliminarUsuario(usuario.id),
        });
    };

    const eliminarUsuario = async (id: number) => {
        try {
            const response = await usuariosService.eliminarUsuario(id);
            if (response.success) {
                mostrarExito('Usuario eliminado exitosamente');
                cargarUsuarios();
            } else {
                mostrarError('Error al eliminar usuario: ' + response.message);
            }
        } catch (error) {
            mostrarError('Error al eliminar usuario');
        }
    };

    const cambiarEstado = async (usuario: Usuario) => {
        try {
            const nuevoEstado = !usuario.activo;
            const response = await usuariosService.cambiarEstadoUsuario(usuario.id, nuevoEstado);
            if (response.success) {
                mostrarExito(`Usuario ${nuevoEstado ? 'activado' : 'desactivado'} exitosamente`);
                cargarUsuarios();
            } else {
                mostrarError('Error al cambiar estado: ' + response.message);
            }
        } catch (error) {
            mostrarError('Error al cambiar estado del usuario');
        }
    };

    // =====================================
    // TEMPLATES PARA COLUMNAS
    // =====================================

    const usuarioTemplate = (rowData: Usuario) => (
        <div className="flex align-items-center gap-2">
            <div className="flex flex-column">
                <span className="font-bold">{rowData.username}</span>
                <span className="text-sm text-500">{rowData.nombre} {rowData.apellido}</span>
            </div>
        </div>
    );

    const rolesTemplate = (rowData: Usuario) => (
        <div className="flex flex-wrap gap-1">
            {rowData.roles.map((rol, index) => {
                const rolInfo = usuariosService.obtenerInfoRol(rol);
                return (
                    <Tag
                        key={index}
                        value={rolInfo.label}
                        severity={rolInfo.color as any}
                        className="text-xs"
                    />
                );
            })}
        </div>
    );

    const estadoTemplate = (rowData: Usuario) => (
        <Tag
            value={rowData.activo ? 'Activo' : 'Inactivo'}
            severity={rowData.activo ? 'success' : 'danger'}
        />
    );

    const accionesTemplate = (rowData: Usuario) => (
        <div className="flex gap-2">
            <Button
                icon="pi pi-eye"
                className="p-button-text p-button-info"
                tooltip="Ver detalles"
                onClick={() => router.push(`/administrador/usuarios/${rowData.id}`)}
            />
            <Button
                icon="pi pi-pencil"
                className="p-button-text p-button-warning"
                tooltip="Editar"
                onClick={() => router.push(`/administrador/usuarios/${rowData.id}/editar`)}
            />
            <Button
                icon={rowData.activo ? 'pi pi-times' : 'pi pi-check'}
                className={`p-button-text ${rowData.activo ? 'p-button-warning' : 'p-button-success'}`}
                tooltip={rowData.activo ? 'Desactivar' : 'Activar'}
                onClick={() => cambiarEstado(rowData)}
            />
            <Button
                icon="pi pi-trash"
                className="p-button-text p-button-danger"
                tooltip="Eliminar"
                onClick={() => confirmarEliminar(rowData)}
            />
        </div>
    );

    // =====================================
    // FILTROS Y TOOLBAR
    // =====================================

    const leftToolbarTemplate = () => (
        <div className="flex flex-wrap gap-2">
            <Button
                label="Nuevo Usuario"
                icon="pi pi-plus"
                onClick={() => router.push('/administrador/usuarios/crear')}
            />
            <Button
                label="Actualizar"
                icon="pi pi-refresh"
                className="p-button-outlined"
                onClick={cargarUsuarios}
            />
        </div>
    );

    const rightToolbarTemplate = () => (
        <div className="flex flex-wrap gap-2 align-items-center">
            <span className="p-input-icon-left">
                <i className="pi pi-search" />
                <InputText
                    value={globalFilter}
                    onChange={(e) => setGlobalFilter(e.target.value)}
                    placeholder="Buscar usuarios..."
                />
            </span>
            <Dropdown
                value={filtroRol}
                options={[
                    { label: 'Todos los roles', value: '' },
                    ...ROLES_DISPONIBLES.map(r => ({ label: r.label, value: r.value }))
                ]}
                onChange={(e) => {
                    setFirst(0);
                    setFiltroRol(e.value);
                }}
                placeholder="Filtrar por rol"
                className="w-12rem"
            />
            <Dropdown
                value={filtroEstado}
                options={[
                    { label: 'Todos los estados', value: null },
                    ...ESTADOS_USUARIO.map(e => ({ label: e.label, value: e.value }))
                ]}
                onChange={(e) => {
                    setFirst(0);
                    setFiltroEstado(e.value);
                }}
                placeholder="Filtrar por estado"
                className="w-12rem"
            />
        </div>
    );

    return (
        <div className="grid">
            <Toast ref={toast} />
            
            <div className="col-12">
                <Card>
                    <div className="flex justify-content-between align-items-center mb-4">
                        <div>
                            <h2 className="text-2xl font-bold text-900 m-0">Gestión de Usuarios</h2>
                            <p className="text-600 m-0 mt-1">Administre todos los usuarios del sistema</p>
                        </div>
                        <Badge 
                            value={totalRecords} 
                            severity="info" 
                            size="large"
                        />
                    </div>

                    <Toolbar 
                        className="mb-4" 
                        left={leftToolbarTemplate} 
                        right={rightToolbarTemplate}
                    />

                    <DataTable
                        value={usuarios}
                        lazy
                        first={first}
                        rows={rows}
                        totalRecords={totalRecords}
                        rowsPerPageOptions={[5, 10, 25, 50]}
                        onPage={handlePage}
                        onSort={handleSort}
                        sortField={sortField}
                        sortOrder={sortOrder}
                        paginator
                        loading={loading}
                        selection={selectedUsuarios}
                        onSelectionChange={(e) => setSelectedUsuarios(e.value as Usuario[])}
                        dataKey="id"
                        className="p-datatable-gridlines"
                        emptyMessage="No se encontraron usuarios"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} usuarios"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        selectionMode="multiple"
                    >
                        <Column 
                            selectionMode="multiple" 
                            headerStyle={{ width: '3em' }}
                        />
                        <Column
                            field="id"
                            header="ID"
                            sortable
                            style={{ width: '5rem' }}
                        />
                        <Column
                            header="Usuario"
                            body={usuarioTemplate}
                            sortable
                            sortField="username"
                        />
                        <Column
                            field="email"
                            header="Email"
                            sortable
                        />
                        <Column
                            header="Roles"
                            body={rolesTemplate}
                        />
                        <Column
                            header="Estado"
                            body={estadoTemplate}
                            sortable
                            sortField="activo"
                            style={{ width: '8rem' }}
                        />
                        <Column
                            header="Acciones"
                            body={accionesTemplate}
                            style={{ width: '12rem' }}
                        />
                    </DataTable>
                </Card>
            </div>
        </div>
    );
}