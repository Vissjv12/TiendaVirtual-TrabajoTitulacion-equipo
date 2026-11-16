'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import AdministradorSidebar from './components/AdministradorSidebar';
import AdminHeader from './components/AdminHeader';
import authService from '../../../services/authService';

export default function AdministradorLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [tieneAcceso, setTieneAcceso] = useState(false);
    const [sidebarActive, setSidebarActive] = useState(false);

    useEffect(() => {
        const verificarAcceso = () => {
            try {
                if (!authService.isAuthenticated()) {
                    router.replace('/auth/login2');
                    return;
                }

                if (!authService.isAdmin()) {
                    router.replace('/auth/access2');
                    return;
                }

                setTieneAcceso(true);
            } catch (error) {
                console.error('Error verificando acceso administrativo:', error);
                authService.clearSession();
                router.replace('/auth/login2');
            } finally {
                setLoading(false);
            }
        };

        verificarAcceso();
    }, [router]);

    if (loading) {
        return (
            <div 
                className="min-h-screen flex align-items-center justify-content-center"
                style={{ backgroundColor: 'var(--surface-ground)' }}
            >
                <div className="text-center">
                    <i 
                        className="pi pi-spin pi-spinner text-4xl mb-3"
                        style={{ color: 'var(--primary-color)' }}
                    ></i>
                    <div style={{ color: 'var(--text-color)' }}>
                        Cargando panel...
                    </div>
                </div>
            </div>
        );
    }

    if (!tieneAcceso) {
        return (
            <div 
                className="min-h-screen flex align-items-center justify-content-center"
                style={{ backgroundColor: 'var(--surface-ground)' }}
            >
                <div className="text-center">
                    <i className="pi pi-lock text-6xl text-red-500 mb-4"></i>
                    <div className="text-xl font-bold text-900 mb-2">Sesión requerida</div>
                    <div className="text-600 mb-4">Necesitas iniciar sesión para acceder</div>
                    <a href="/auth/login2" className="p-button p-button-primary">
                        Ir a Login
                    </a>
                </div>
            </div>
        );
    }

    const toggleSidebar = () => setSidebarActive((prev) => !prev);
    const closeSidebar = () => setSidebarActive(false);

    return (
        <div className="admin-layout-wrapper">
            {/* Botón para abrir sidebar en móvil */}
            <button
                className="admin-sidebar-toggle"
                onClick={toggleSidebar}
                aria-label="Abrir menú"
            >
                <i className="pi pi-bars"></i>
            </button>

            {/* Overlay para cerrar sidebar en móvil */}
            <div
                className={`admin-sidebar-overlay ${sidebarActive ? 'active' : ''}`}
                onClick={closeSidebar}
            ></div>

            {/* Sidebar */}
            <div className={`admin-sidebar ${sidebarActive ? 'active' : ''}`}>
                <AdministradorSidebar onNavigate={closeSidebar} />
            </div>

            {/* Contenido principal */}
            <div className="admin-content-wrapper">
                <div className="admin-topbar">
                    <AdminHeader />
                </div>

                <div className="admin-main">
                    <div className="admin-main-content">
                        {children}
                    </div>
                </div>
            </div>
        </div>
    );
}
