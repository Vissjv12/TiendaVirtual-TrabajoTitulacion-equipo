'use client';

import { useState, useRef } from 'react';
import { Button } from 'primereact/button';
import { Menu } from 'primereact/menu';
import { useRouter } from 'next/navigation';
import authService from '../../../../services/authService';

interface EmprendedorNavbarProps {
    layoutConfig: any;
    setLayoutConfig: (config: any) => void;
}

export default function EmprendedorNavbar({ layoutConfig, setLayoutConfig }: EmprendedorNavbarProps) {
    const [userMenuVisible, setUserMenuVisible] = useState(false);
    const userMenuRef = useRef<Menu>(null);
    const router = useRouter();
    const userInfo = authService.getUserInfo();

    const userMenuItems = [
        {
            label: 'Mi Perfil',
            icon: 'pi pi-user',
            command: () => router.push('/emprendedor/perfil')
        },
        {
            label: 'Información de la Cuenta',
            icon: 'pi pi-id-card',
            command: () => router.push('/emprendedor/perfil')
        },
        {
            label: 'Configuración',
            icon: 'pi pi-cog',
            command: () => router.push('/emprendedor/configuracion-pagos')
        },
        { separator: true },
        {
            label: 'Ver Tienda Pública',
            icon: 'pi pi-external-link',
            command: () => router.push('/')
        },
        {
            label: 'Ayuda y Soporte',
            icon: 'pi pi-question-circle',
            command: () => router.push('/emprendedor/ayuda')
        },
        { separator: true },
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

    return (
        <>
            <div 
                className="emprendedor-navbar-inner w-full flex align-items-center shadow-2"
                style={{
                    position: 'relative',
                    width: '100%',
                    backgroundColor: 'var(--surface-card)',
                    borderBottom: '1px solid var(--surface-border)',
                    height: '70px',
                    padding: '0 2rem'
                }}
            >
                <div className="flex align-items-center justify-content-end h-full w-full">
                    <div className="flex align-items-center gap-3">
                        
                        <div className="text-right mr-3 hidden md:block">
                            <div className="text-900 font-semibold text-sm">
                                ¡Hola, {userInfo?.nombre || userInfo?.username}!
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

                        <div 
                            className="mx-2 hidden md:block"
                            style={{ 
                                width: '1px', 
                                height: '30px', 
                                backgroundColor: 'var(--surface-border)' 
                            }}
                        ></div>

                        <div className="flex align-items-center">
                            <div className="text-right mr-2 hidden lg:block">
                                <div className="text-900 font-semibold text-xs">
                                    {userInfo?.nombre} {userInfo?.apellido}
                                </div>
                                <div className="text-600 text-xs">Emprendedor</div>
                            </div>
                            
                            <Button 
                                className="p-button-text p-button-plain p-button-rounded"
                                onClick={(event) => userMenuRef.current?.toggle(event)}
                                tooltip="Mi cuenta"
                                tooltipOptions={{ position: 'bottom' }}
                            >
                                <div className="flex align-items-center justify-content-center w-2rem h-2rem border-circle bg-primary text-white">
                                    <span className="font-bold text-sm">
                                        {(userInfo?.nombre?.[0] || userInfo?.username?.[0] || 'U').toUpperCase()}
                                    </span>
                                </div>
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            <Menu 
                model={userMenuItems} 
                popup 
                ref={userMenuRef}
                id="user-menu"
                style={{ minWidth: '200px' }}
            />
        </>
    );
}