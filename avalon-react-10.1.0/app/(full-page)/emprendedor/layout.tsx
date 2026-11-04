'use client';

import React, { useEffect, useState, useContext } from 'react';
import { LayoutProvider, LayoutContext } from '../../../layout/context/layoutcontext';
import { Button } from 'primereact/button';
import EmprendedorNavbar from './components/EmprendedorNavbar';
import EmprendedorSidebar from './components/EmprendedorSidebar';
import AppConfig from '../../../layout/AppConfig';
import authService from '../../../services/authService';
import { useRouter } from 'next/navigation';

interface EmprendedorLayoutProps {
    children: React.ReactNode;
}

const EmprendedorLayoutContent: React.FC<EmprendedorLayoutProps> = ({ children }) => {
    const [mounted, setMounted] = useState(false);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [sidebarActive, setSidebarActive] = useState(false);
    const { layoutConfig, setLayoutConfig, setLayoutState } = useContext(LayoutContext);
    const router = useRouter();

    useEffect(() => {
        const checkAuth = () => {
            const authenticated = authService.isAuthenticated();
            const isEmployee = authService.isEmployee();
            
            try {
                
                console.log('🔍 Verificando emprendedor:', { 
                    authenticated, 
                    isEmployee, 
                    userType: authService.getUserType(),
                    userInfo: authService.getUserInfo()
                });
                
                if (!authenticated) {
                    console.log('❌ No autenticado (401), redirigiendo a login...');
                    router.push('/auth/login2');
                    return;
                }

                if (!isEmployee) {
                    console.log('❌ Acceso denegado (403) para rol actual, redirigiendo a access2...');
                    router.push('/auth/access2');
                    return;
                }
                
                setIsAuthenticated(true);
                setMounted(true);
            } catch (error) {
                console.error('Error verificando autenticación:', error);
                router.push('/auth/login2');
            }
        };

        checkAuth();
    }, [router]);

    const toggleSidebar = () => {
        setSidebarActive(!sidebarActive);
    };

    const closeSidebar = () => {
        setSidebarActive(false);
    };

    const currentLayoutConfig = layoutConfig || {
        theme: 'lara-light-blue',
        colorScheme: 'light',
        menuMode: 'static',
        ripple: true,
        inputStyle: 'outlined'
    };

    if (!mounted || !isAuthenticated) {
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
                        Verificando permisos...
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="emprendedor-layout-wrapper">
            {/* Botón para abrir sidebar en móvil */}
            <button 
                className="emprendedor-sidebar-toggle"
                onClick={toggleSidebar}
                aria-label="Toggle Sidebar"
            >
                <i className="pi pi-bars"></i>
            </button>

            {/* Overlay para cerrar sidebar en móvil */}
            <div 
                className={`emprendedor-sidebar-overlay ${sidebarActive ? 'active' : ''}`}
                onClick={closeSidebar}
            ></div>

            {/* Sidebar */}
            <div className={`emprendedor-sidebar ${sidebarActive ? 'active' : ''}`}>
                <EmprendedorSidebar />
            </div>

            {/* Contenido principal */}
            <div className="emprendedor-content-wrapper">
                <div className="emprendedor-topbar">
                    <EmprendedorNavbar 
                        layoutConfig={currentLayoutConfig}
                        setLayoutConfig={setLayoutConfig}
                    />
                </div>

                <div className="emprendedor-main">
                    <div className="emprendedor-main-content">
                        {children}
                    </div>
                </div>
            </div>

            <AppConfig />
        </div>
    );
};

const EmprendedorLayout: React.FC<EmprendedorLayoutProps> = ({ children }) => {
    return (
        <LayoutProvider>
            <EmprendedorLayoutContent>
                {children}
            </EmprendedorLayoutContent>
        </LayoutProvider>
    );
};

export default EmprendedorLayout;