'use client';

import { useState, useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { listarPagosPendientes } from '@/services/pagoService';
import authService from '@/services/authService';

export default function EmprendedorSidebar() {
    const router = useRouter();
    const pathname = usePathname();
    const [mounted, setMounted] = useState(true);
    const [pagosPendientes, setPagosPendientes] = useState(0);
    const isAdmin = authService.isAdmin();

    // Obtener el emprendedorId (empresaId) del usuario autenticado desde authService
    useEffect(() => {
        const cargarPagosPendientes = async () => {
            try {
                const emprendedorId = authService.getEmpresaId();
                if (!emprendedorId) {
                    setPagosPendientes(0);
                    return;
                }
                const pagos = await listarPagosPendientes(emprendedorId);
                setPagosPendientes(pagos.length);
            } catch (error) {
                console.error('Error al cargar pagos pendientes:', error);
            }
        };

        cargarPagosPendientes();
        
        // Actualizar cada 30 segundos
        const interval = setInterval(cargarPagosPendientes, 30000);
        
        return () => clearInterval(interval);
    }, []);

    const menuItems = [
        {
            label: 'Dashboard',
            icon: 'pi pi-user-edit',
            url: '/emprendedor',
            badge: null
        },
        {
            label: 'Productos',
            icon: 'pi pi-box',
            url: '/emprendedor/productos',
            badge: null
        },
        {
            label: 'Categorías',
            icon: 'pi pi-tags',
            url: '/emprendedor/categorias',
            badge: null
        },
        {
            label: isAdmin ? 'Empresas' : 'Mi Empresa',
            icon: 'pi pi-building',
            url: '/emprendedor/empresas',
            badge: null
        },
        {
            label: 'Inventario',
            icon: 'pi pi-database',
            url: '/emprendedor/inventario',
            badge: null
        },
        {
            label: 'Ventas',
            icon: 'pi pi-shopping-cart',
            url: '/emprendedor/ventas',
            badge: null
        },
        {
            label: 'Pagos',
            icon: 'pi pi-credit-card',
            url: '/emprendedor/pagos',
            badge: pagosPendientes > 0 ? pagosPendientes : null,
            highlight: pagosPendientes > 0
        },
        {
            label: 'Configurar Pagos',
            icon: 'pi pi-cog',
            url: '/emprendedor/configuracion-pagos',
            badge: null,
            highlight: false
        },
        {
            label: 'Logística',
            icon: 'pi pi-truck',
            url: '/emprendedor/logistica',
            badge: null
        }
    ];

    const isActive = (url: string) => {
        if (url === '/emprendedor') {
            return pathname === url;
        }
        return pathname.startsWith(url);
    };

    const handleNavigation = (url: string) => {
        router.push(url);
    };

    return (
        <div 
            className="layout-sidebar-content h-full flex flex-column"
            style={{ backgroundColor: 'var(--surface-card)' }}
        >
            {/* Header del sidebar */}
            <div 
                className="sidebar-header p-4 border-bottom-1 surface-border"
                style={{ borderColor: 'var(--surface-border)' }}
            >
                <div 
                    className="flex align-items-center cursor-pointer"
                    onClick={() => router.push('/emprendedor')}
                >
                    <div 
                        className="w-3rem h-3rem border-circle flex align-items-center justify-content-center mr-3"
                        style={{ 
                            backgroundColor: 'var(--primary-color)',
                            color: 'white'
                        }}
                    >
                        <i className="pi pi-shop text-xl"></i>
                    </div>
                    <div>
                        <div className="text-xl font-bold text-900">Panel de Control</div>
                        <div className="text-sm text-600">Emprendedor</div>
                    </div>
                </div>
            </div>

            {/* Navegación principal */}
            <div className="sidebar-menu flex-1 p-3">
                <ul className="list-none p-0 m-0">
                    {menuItems.map((item, index) => (
                        <li key={index} className="mb-1">
                            <Button
                                className={`w-full text-left p-3 border-round-lg border-none ${
                                    isActive(item.url) 
                                        ? 'bg-primary text-white' 
                                        : 'surface-hover text-color hover:bg-primary-50 hover:text-primary'
                                }`}
                                style={{
                                    justifyContent: 'flex-start',
                                    backgroundColor: isActive(item.url) 
                                        ? 'var(--primary-color)' 
                                        : 'transparent',
                                    color: isActive(item.url) 
                                        ? 'white' 
                                        : 'var(--text-color)',
                                    transition: 'all 0.2s ease'
                                }}
                                onClick={() => handleNavigation(item.url)}
                                onMouseEnter={(e) => {
                                    if (!isActive(item.url)) {
                                        e.currentTarget.style.backgroundColor = 'var(--primary-50)';
                                        e.currentTarget.style.color = 'var(--primary-color)';
                                    }
                                }}
                                onMouseLeave={(e) => {
                                    if (!isActive(item.url)) {
                                        e.currentTarget.style.backgroundColor = 'transparent';
                                        e.currentTarget.style.color = 'var(--text-color)';
                                    }
                                }}
                            >
                                <div className="flex align-items-center justify-content-between w-full">
                                    <div className="flex align-items-center">
                                        <i className={`${item.icon} mr-3 text-lg ${item.highlight && !isActive(item.url) ? 'text-orange-500' : ''}`}></i>
                                        <span className="font-medium">{item.label}</span>
                                    </div>
                                    {item.badge && (
                                        <Badge 
                                            value={item.badge} 
                                            severity={isActive(item.url) ? 'info' : item.highlight ? 'warning' : 'success'}
                                            style={{
                                                backgroundColor: isActive(item.url) 
                                                    ? 'rgba(255,255,255,0.3)' 
                                                    : item.highlight 
                                                        ? 'var(--orange-500)'
                                                        : 'var(--green-500)',
                                                animation: item.highlight && !isActive(item.url) ? 'pulse 2s infinite' : 'none'
                                            }}
                                        />
                                    )}
                                </div>
                            </Button>
                        </li>
                    ))}
                </ul>
            </div>

            {/* Footer del sidebar */}
            <div 
                className="sidebar-footer p-3 border-top-1 surface-border"
                style={{ borderColor: 'var(--surface-border)' }}
            >
                {pagosPendientes > 0 ? (
                    <div 
                        className="p-3 border-round-lg text-center cursor-pointer"
                        style={{ 
                            backgroundColor: 'var(--orange-50)',
                            border: `1px solid var(--orange-200)`
                        }}
                        onClick={() => router.push('/emprendedor/pagos')}
                    >
                        <i 
                            className="pi pi-exclamation-triangle text-2xl mb-2"
                            style={{ color: 'var(--orange-500)' }}
                        ></i>
                        <div 
                            className="font-bold mb-1"
                            style={{ color: 'var(--orange-600)' }}
                        >
                            {pagosPendientes} {pagosPendientes === 1 ? 'Pago Pendiente' : 'Pagos Pendientes'}
                        </div>
                        <div className="text-sm text-600 line-height-3">
                            Haz clic para revisar y aprobar
                        </div>
                    </div>
                ) : (
                    <div 
                        className="p-3 border-round-lg text-center"
                        style={{ 
                            backgroundColor: 'var(--primary-50)',
                            border: `1px solid var(--primary-200)`
                        }}
                    >
                        <i 
                            className="pi pi-lightbulb text-2xl mb-2"
                            style={{ color: 'var(--primary-color)' }}
                        ></i>
                        <div 
                            className="font-bold mb-1"
                            style={{ color: 'var(--primary-color)' }}
                        >
                            ¡Tip del día!
                        </div>
                        <div className="text-sm text-600 line-height-3">
                            Mantén tu inventario actualizado para evitar ventas perdidas.
                        </div>
                    </div>
                )}
            </div>

            <style jsx>{`
                @keyframes pulse {
                    0%, 100% {
                        opacity: 1;
                        transform: scale(1);
                    }
                    50% {
                        opacity: 0.8;
                        transform: scale(1.05);
                    }
                }
            `}</style>
        </div>
    );
}