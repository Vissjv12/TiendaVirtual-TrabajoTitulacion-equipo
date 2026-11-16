'use client';

import React, { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Tag } from 'primereact/tag';
import { Toast } from 'primereact/toast';
import { Divider } from 'primereact/divider';
import { Skeleton } from 'primereact/skeleton';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import { Badge } from 'primereact/badge';
import usuariosService, { Usuario } from '@/services/usuariosService';

export default function DetalleUsuarioPage() {
    const router = useRouter();
    const params = useParams();
    const usuarioId = Number(params.id);
    
    const [loading, setLoading] = useState(true);
    const [usuario, setUsuario] = useState<Usuario | null>(null);
    const [totalAdmins, setTotalAdmins] = useState<number>(0);
    const [activeAdmins, setActiveAdmins] = useState<number>(0);
    const toast = React.useRef<Toast>(null);

    useEffect(() => {
        if (usuarioId) {
            cargarUsuario();
        }
    }, [usuarioId]);

    const cargarUsuario = async () => {
        setLoading(true);
        try {
            const [response, statsRes] = await Promise.all([
                usuariosService.obtenerUsuarioPorId(usuarioId),
                usuariosService.obtenerEstadisticas()
            ]);
            if (statsRes.success && statsRes.data) {
                setTotalAdmins(statsRes.data.administradores ?? 0);
                setActiveAdmins(statsRes.data.administradoresActivos ?? statsRes.data.administradores ?? 0);
            }
            if (response.success && response.data) {
                setUsuario(response.data);
            } else {
                mostrarError('Error al cargar usuario: ' + response.message);
                router.push('/administrador/usuarios');
            }
        } catch (error) {
            console.error('Error:', error);
            mostrarError('Error de conexión al cargar usuario');
            router.push('/administrador/usuarios');
        } finally {
            setLoading(false);
        }
    };

    const mostrarExito = (mensaje: string) => {
        toast.current?.show({ severity: 'success', summary: 'Éxito', detail: mensaje });
    };

    const mostrarError = (mensaje: string) => {
        toast.current?.show({ severity: 'error', summary: 'Error', detail: mensaje });
    };

    const confirmarEliminar = () => {
        if (!usuario) return;
        
        confirmDialog({
            message: `¿Está seguro de eliminar lógicamente al usuario "${usuario.username}"? El usuario pasará a estado inactivo sin borrarse de la base de datos.`,
            header: 'Confirmar eliminación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, eliminar',
            rejectLabel: 'Cancelar',
            accept: eliminarUsuario,
        });
    };

    const eliminarUsuario = async () => {
        if (!usuario) return;

        try {
            const response = await usuariosService.eliminarUsuario(usuario.id);
            if (response.success) {
                mostrarExito('Usuario eliminado lógicamente (desactivado) exitosamente');
                setTimeout(() => {
                    router.push('/administrador/usuarios');
                }, 1500);
            } else {
                mostrarError('Error al eliminar usuario: ' + response.message);
            }
        } catch (error) {
            mostrarError('Error al eliminar usuario');
        }
    };

    const cambiarEstado = async () => {
        if (!usuario) return;

        try {
            const nuevoEstado = !usuario.activo;
            const response = await usuariosService.cambiarEstadoUsuario(usuario.id, nuevoEstado);
            if (response.success) {
                mostrarExito(`Usuario ${nuevoEstado ? 'activado' : 'desactivado'} exitosamente`);
                cargarUsuario(); // Recargar para obtener el estado actualizado
            } else {
                mostrarError('Error al cambiar estado: ' + response.message);
            }
        } catch (error) {
            mostrarError('Error al cambiar estado del usuario');
        }
    };

    if (loading) {
        return (
            <div className="grid">
                <div className="col-12">
                    <Card>
                        <div className="flex justify-content-between align-items-center mb-4">
                            <Skeleton width="20rem" height="2rem" />
                            <div className="flex gap-2">
                                <Skeleton width="8rem" height="3rem" />
                                <Skeleton width="8rem" height="3rem" />
                                <Skeleton width="8rem" height="3rem" />
                            </div>
                        </div>
                        <div className="grid">
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="2rem" className="mb-3" />
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="2rem" className="mb-3" />
                            </div>
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="2rem" className="mb-3" />
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="2rem" className="mb-3" />
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        );
    }

    if (!usuario) {
        return (
            <div className="grid">
                <div className="col-12">
                    <Card>
                        <div className="text-center p-4">
                            <i className="pi pi-exclamation-triangle text-6xl text-orange-500 mb-3"></i>
                            <h3 className="text-xl font-bold text-900 mb-2">Usuario no encontrado</h3>
                            <p className="text-600 mb-4">El usuario solicitado no existe o no tienes permisos para verlo.</p>
                            <Button 
                                label="Volver a usuarios" 
                                icon="pi pi-arrow-left"
                                onClick={() => router.push('/administrador/usuarios')}
                            />
                        </div>
                    </Card>
                </div>
            </div>
        );
    }

    const protegerAdmin = usuario.roles.includes('ROLE_ADMIN') &&
        (totalAdmins <= 1 || (usuario.activo && activeAdmins <= 1));

    return (
        <div className="grid">
            <Toast ref={toast} />
            <ConfirmDialog />
            
            <div className="col-12">
                <Card>
                    <div className="flex justify-content-between align-items-center mb-4">
                        <div>
                            <h2 className="text-2xl font-bold text-900 m-0 flex align-items-center gap-2">
                                <i className="pi pi-user"></i>
                                {usuario.username}
                                <Tag
                                    value={usuario.activo ? 'Activo' : 'Inactivo'}
                                    severity={usuario.activo ? 'success' : 'danger'}
                                />
                            </h2>
                            <p className="text-600 m-0 mt-1">
                                Detalles completos del usuario
                            </p>
                        </div>
                        <div className="flex gap-2">
                            <Button 
                                label="Volver" 
                                icon="pi pi-arrow-left" 
                                className="p-button-outlined"
                                onClick={() => router.push('/administrador/usuarios')}
                            />
                            <Button 
                                label="Editar" 
                                icon="pi pi-pencil" 
                                className="p-button-warning"
                                onClick={() => router.push(`/administrador/usuarios/${usuario.id}/editar`)}
                            />
                            {!protegerAdmin && (
                                <>
                                    <Button 
                                        label={usuario.activo ? 'Desactivar' : 'Activar'}
                                        icon={usuario.activo ? 'pi pi-times' : 'pi pi-check'}
                                        className={usuario.activo ? 'p-button-warning' : 'p-button-success'}
                                        onClick={cambiarEstado}
                                    />
                                    <Button 
                                        label="Eliminar" 
                                        icon="pi pi-trash" 
                                        className="p-button-danger"
                                        onClick={confirmarEliminar}
                                    />
                                </>
                            )}
                        </div>
                    </div>

                    <div className="grid">
                        {/* Información básica */}
                        <div className="col-12 md:col-6">
                            <Card className="h-full">
                                <h3 className="text-lg font-semibold text-900 mb-3 flex align-items-center gap-2">
                                    <i className="pi pi-user text-blue-500"></i>
                                    Información Personal
                                </h3>
                                
                                <div className="flex flex-column gap-3">
                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-1">ID del Usuario</label>
                                        <div className="flex align-items-center gap-2">
                                            <Badge value={usuario.id} severity="info" />
                                        </div>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-1">Nombre Completo</label>
                                        <p className="text-900 font-medium text-lg m-0">
                                            {usuario.nombre} {usuario.apellido}
                                        </p>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-1">Username</label>
                                        <p className="text-900 font-medium m-0">
                                            <i className="pi pi-at mr-1"></i>
                                            {usuario.username}
                                        </p>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-1">Email</label>
                                        <p className="text-900 font-medium m-0">
                                            <i className="pi pi-envelope mr-1"></i>
                                            {usuario.email}
                                        </p>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-1">Estado</label>
                                        <div className="flex align-items-center gap-2">
                                            <i className={`pi ${usuario.activo ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'}`}></i>
                                            <span className={`font-medium ${usuario.activo ? 'text-green-700' : 'text-red-700'}`}>
                                                {usuario.activo ? 'Usuario Activo' : 'Usuario Inactivo'}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </Card>
                        </div>

                        {/* Roles y permisos */}
                        <div className="col-12 md:col-6">
                            <Card className="h-full">
                                <h3 className="text-lg font-semibold text-900 mb-3 flex align-items-center gap-2">
                                    <i className="pi pi-shield text-orange-500"></i>
                                    Roles y Permisos
                                </h3>

                                <div className="flex flex-column gap-3">
                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-2">Roles Asignados</label>
                                        <div className="flex flex-wrap gap-2">
                                            {usuario.roles.map((rol, index) => {
                                                const rolInfo = usuariosService.obtenerInfoRol(rol);
                                                return (
                                                    <div key={index} className="border-1 border-300 border-round p-3 bg-gray-50 flex-1 min-w-12rem">
                                                        <div className="flex align-items-start gap-2">
                                                            <i className="pi pi-shield text-600 mt-1"></i>
                                                            <div className="flex-1">
                                                                <div className="font-bold text-900 mb-1">{rolInfo.label}</div>
                                                                <div className="text-sm text-600">{rolInfo.description}</div>
                                                                <Tag 
                                                                    value={rol}
                                                                    severity={rolInfo.color as any}
                                                                    className="mt-2 text-xs"
                                                                />
                                                            </div>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                        {usuario.roles.length === 0 && (
                                            <div className="text-center p-4 border-1 border-300 border-round bg-gray-50">
                                                <i className="pi pi-info-circle text-3xl text-600 mb-2"></i>
                                                <p className="text-600 m-0">Sin roles asignados</p>
                                            </div>
                                        )}
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-2">Rol Principal</label>
                                        <div className="border-1 border-300 border-round p-3 bg-blue-50">
                                            <div className="flex align-items-center gap-2">
                                                <i className="pi pi-star text-blue-600"></i>
                                                <span className="font-bold text-blue-900">
                                                    {usuariosService.obtenerInfoRol(usuariosService.obtenerRolPrincipal(usuario)).label}
                                                </span>
                                            </div>
                                        </div>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-600 mb-2">Permisos</label>
                                        <div className="flex flex-column gap-2">
                                            {usuario.roles.includes('ROLE_ADMIN') && (
                                                <div className="flex align-items-center gap-2 p-2 bg-red-50 border-round">
                                                    <i className="pi pi-shield text-red-600"></i>
                                                    <span className="text-red-900 font-medium">Administrador Total</span>
                                                </div>
                                            )}
                                            {(usuario.roles.includes('ROLE_EMP') || usuario.roles.includes('ROLE_EMPRENDEDOR')) && (
                                                <div className="flex align-items-center gap-2 p-2 bg-orange-50 border-round">
                                                    <i className="pi pi-briefcase text-orange-600"></i>
                                                    <span className="text-orange-900 font-medium">Gestión de Productos y Ventas</span>
                                                </div>
                                            )}
                                            {usuario.roles.includes('ROLE_USER') && (
                                                <div className="flex align-items-center gap-2 p-2 bg-blue-50 border-round">
                                                    <i className="pi pi-shopping-cart text-blue-600"></i>
                                                    <span className="text-blue-900 font-medium">Usuario Cliente</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </Card>
                        </div>

                        {/* Acciones rápidas */}
                        <div className="col-12">
                            <Divider />
                            <h3 className="text-lg font-semibold text-900 mb-3 flex align-items-center gap-2">
                                <i className="pi pi-cog text-gray-600"></i>
                                Acciones Rápidas
                            </h3>
                            
                            <div className="flex flex-wrap gap-2">
                                <Button
                                    label="Editar Usuario"
                                    icon="pi pi-pencil"
                                    className="p-button-outlined"
                                    onClick={() => router.push(`/administrador/usuarios/${usuario.id}/editar`)}
                                />
                                {!protegerAdmin && (
                                    <Button
                                        label={usuario.activo ? 'Desactivar Usuario' : 'Activar Usuario'}
                                        icon={usuario.activo ? 'pi pi-times' : 'pi pi-check'}
                                        className={`p-button-outlined ${usuario.activo ? 'p-button-warning' : 'p-button-success'}`}
                                        onClick={cambiarEstado}
                                    />
                                )}
                                <Button
                                    label="Ver Historial"
                                    icon="pi pi-history"
                                    className="p-button-outlined p-button-info"
                                    disabled
                                    tooltip="Funcionalidad próximamente"
                                />
                                {!protegerAdmin && (
                                    <Button
                                        label="Eliminar Usuario"
                                        icon="pi pi-trash"
                                        className="p-button-outlined p-button-danger"
                                        onClick={confirmarEliminar}
                                    />
                                )}
                            </div>
                        </div>
                    </div>
                </Card>
            </div>
        </div>
    );
}