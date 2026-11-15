'use client';

import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from 'primereact/button';
import { Menu } from 'primereact/menu';
import { MenuItem } from 'primereact/menuitem';
import authService from '../../../../services/authService';

export default function AdminHeader() {
    const router = useRouter();
    const [usuario, setUsuario] = useState<any>(null);
    const [mounted, setMounted] = useState(false);
    const menuRef = useRef<Menu>(null);

    useEffect(() => {
        setMounted(true);
        try {
            // ✅ Usar authService en lugar de localStorage directo
            const userInfo = authService.getUserInfo();
            setUsuario(userInfo);
        } catch (error) {
            console.error('Error al obtener usuario:', error);
        }
    }, []);

    // ✅ SIEMPRE mostrar "Panel Admin" - no cambiar según ruta
    const contexto = {
        titulo: 'Panel Admin',
        rol: 'Administrador',
        icono: 'pi-shield',
        color: 'var(--red-500)'
    };

    const menuItems: MenuItem[] = [
        {
            label: 'Mi Perfil',
            icon: 'pi pi-user',
            command: () => router.push('/administrador/perfil')
        },
        {
            label: 'Configuración',
            icon: 'pi pi-cog',
            command: () => router.push('/administrador/usuarios')
        },
        {
            label: 'Ver Tienda Pública',
            icon: 'pi pi-external-link',
            command: () => router.push('/')
        },
        {
            separator: true
        },
        {
            label: 'Cerrar Sesión',
            icon: 'pi pi-sign-out',
            command: () => {
                // ✅ CORREGIDO: Usar authService y ir al login morado correcto
                authService.logout();
                router.push('/auth/login2');
            }
        }
    ];

    if (!mounted) {
        return <div style={{ height: '70px' }}></div>;
    }

    return (
        <div 
            className="admin-navbar-inner w-full flex align-items-center shadow-2"
            style={{
                position: 'relative',
                width: '100%',
                backgroundColor: 'var(--surface-card)',
                borderBottom: '1px solid var(--surface-border)',
                height: '70px',
                padding: '0 2rem'
            }}
        >
            {/* ✅ CONTENIDO COMPLETAMENTE A LA DERECHA - IGUAL QUE EMPRENDEDOR */}
            <div className="flex align-items-center justify-content-end h-full w-full">
                <div className="flex align-items-center gap-3">
                    
                    {/* Información del usuario - Solo desktop */}
                    <div className="text-right mr-3 hidden md:block">
                        <div className="text-900 font-semibold text-sm">
                            ¡Hola, {usuario?.nombre || usuario?.username || 'Admin'}!
                        </div>
                        <div className="text-600 text-xs">
                            {new Date().toLocaleDateString('es-ES', { 
                                weekday: 'long', 
                                year: 'numeric', 
                                month: 'long', 
                                day: 'numeric' 
                            })}
                        </div>
                    </div>

                    {/* Separador */}
                    <div 
                        className="mx-2 hidden md:block"
                        style={{ 
                            width: '1px', 
                            height: '30px', 
                            backgroundColor: 'var(--surface-border)' 
                        }}
                    ></div>

                    {/* Avatar y menú de usuario */}
                    <div className="flex align-items-center">
                        <div className="text-right mr-2 hidden lg:block">
                            <div className="text-900 font-semibold text-xs">
                                {usuario?.nombre} {usuario?.apellido}
                            </div>
                            <div className="text-600 text-xs">Administrador</div>
                        </div>
                        
                        <Button 
                            className="p-button-text p-button-plain p-button-rounded"
                            onClick={(event) => menuRef.current?.toggle(event)}
                            tooltip="Mi cuenta"
                            tooltipOptions={{ position: 'bottom' }}
                        >
                            <div 
                                className="flex align-items-center justify-content-center w-2rem h-2rem border-circle text-white"
                                style={{ backgroundColor: 'var(--red-500)' }}
                            >
                                <span className="font-bold text-sm">
                                    {(usuario?.nombre || usuario?.username || 'A').charAt(0).toUpperCase()}
                                </span>
                            </div>
                        </Button>
                    </div>
                </div>
            </div>

            {/* Menu de usuario */}
            <Menu 
                model={menuItems} 
                popup 
                ref={menuRef}
                id="admin-user-menu"
                style={{ minWidth: '200px' }}
            />
        </div>
    );
}