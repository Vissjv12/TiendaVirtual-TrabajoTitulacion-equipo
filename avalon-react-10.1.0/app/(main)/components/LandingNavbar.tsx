'use client';

import React, { useState, useContext, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from 'primereact/button';
import { Menubar } from 'primereact/menubar';
import { Badge } from 'primereact/badge';
import { InputText } from 'primereact/inputtext';
import { Sidebar } from 'primereact/sidebar';
import { LayoutContext } from '../../../layout/context/layoutcontext';
import authService from '../../../services/authService';
import cartService from '../../../services/cartService';
import productService, { Categoria } from '../../../services/productService';
import RegistroModal from './RegistroModal';
import MiCuentaModal from './MiCuentaModal'; // ✅ NUEVO

const LandingNavbar: React.FC = () => {
    const { layoutConfig } = useContext(LayoutContext);
    const router = useRouter();
    const [searchTerm, setSearchTerm] = useState('');
    const [mobileMenuVisible, setMobileMenuVisible] = useState(false);
    const [registroModalVisible, setRegistroModalVisible] = useState(false);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [userInfo, setUserInfo] = useState<any>(null);
    const [cartItemCount, setCartItemCount] = useState(0);
    const [mounted, setMounted] = useState(false);
    const [miCuentaModalVisible, setMiCuentaModalVisible] = useState(false); // ✅ NUEVO
    const [categorias, setCategorias] = useState<Categoria[]>([]);

    useEffect(() => {
        setMounted(true);
    }, []);

    useEffect(() => {
        if (!mounted) return;
        let active = true;
        productService
            .obtenerCategorias()
            .then((res) => {
                if (active && res.success && res.data) {
                    setCategorias(res.data);
                }
            })
            .catch(() => {
                // Silencioso en navbar público
            });
        return () => {
            active = false;
        };
    }, [mounted]);

    useEffect(() => {
        if (!mounted) return;
        
        const checkAuth = () => {
            const authStatus = authService.isAuthenticated();
            const user = authService.getUserInfo();
            
            setIsAuthenticated(authStatus);
            setUserInfo(user);
        };

        checkAuth();
        
        const handleStorageChange = () => {
            checkAuth();
        };

        window.addEventListener('storage', handleStorageChange);
        const interval = setInterval(checkAuth, 3000);

        return () => {
            window.removeEventListener('storage', handleStorageChange);
            clearInterval(interval);
        };
    }, [mounted]);

    useEffect(() => {
        if (!mounted) return;
        
        const updateCartCount = () => {
            setCartItemCount(cartService.getItemCount());
        };

        updateCartCount();

        const handleCartUpdate = () => {
            updateCartCount();
        };

        window.addEventListener('cartUpdated', handleCartUpdate);
        const interval = setInterval(updateCartCount, 2000);

        return () => {
            window.removeEventListener('cartUpdated', handleCartUpdate);
            clearInterval(interval);
        };
    }, [mounted]);

    const handleLoginClick = () => {
        router.push('/auth/login2');
    };

    const handleRegistroClick = () => {
        setRegistroModalVisible(true);
    };

    const handleDashboardClick = () => {
        // ✅ CORREGIDO: Verificar si es empleado/admin para ir al panel, sino mostrar modal
        if (authService.isEmployee()) {
            const redirectPath = authService.getRedirectPath();
            router.push(redirectPath);
        } else {
            // Si es cliente normal, mostrar modal de Mi Cuenta
            setMiCuentaModalVisible(true);
        }
    };

    const handleCartClick = () => {
        if (!isAuthenticated) {
            handleLoginClick();
        } else {
            router.push('/cart');
        }
    };

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        if (searchTerm.trim()) {
            router.push(`/products?q=${encodeURIComponent(searchTerm)}`);
            setSearchTerm('');
        }
    };

    const handleLogout = () => {
        authService.logout();
        setIsAuthenticated(false);
        setUserInfo(null);
        setCartItemCount(0);
        // No hacemos router.push('/') porque logout() ya redirige
    };

    // ✅ CORREGIDO: Scroll a secciones en la MISMA página
    const scrollToSection = (sectionId: string) => {
        setMobileMenuVisible(false);
        
        // Si no estamos en la landing, ir primero a la landing
        if (window.location.pathname !== '/landing') {
            router.push('/landing');
            setTimeout(() => {
                const element = document.getElementById(sectionId);
                if (element) {
                    const offsetTop = element.offsetTop - 90;
                    window.scrollTo({
                        top: Math.max(0, offsetTop),
                        behavior: 'smooth'
                    });
                }
            }, 500);
        } else {
            // Ya estamos en la landing, hacer scroll directo
            setTimeout(() => {
                const element = document.getElementById(sectionId);
                if (element) {
                    const offsetTop = element.offsetTop - 90;
                    window.scrollTo({
                        top: Math.max(0, offsetTop),
                        behavior: 'smooth'
                    });
                } else {
                    console.warn(`Sección con ID "${sectionId}" no encontrada.`);
                }
            }, 100);
        }
    };

    const navigationItems = [
        {
            label: 'Inicio',
            icon: 'pi pi-home',
            command: () => router.push('/landing'), // ✅ CORREGIDO: Ruta correcta al landing
            className: 'nav-item-hover'
        },
        {
            label: 'Productos',
            icon: 'pi pi-shopping-bag',
            className: 'nav-item-hover',
            items: [
                {
                    label: 'Todos los productos',
                    icon: 'pi pi-list',
                    command: () => router.push('/products')
                },
                {
                    label: 'Categorías',
                    icon: 'pi pi-tags',
                    items:
                        categorias.length > 0
                            ? categorias.map((cat) => ({
                                  label: cat.nombre,
                                  icon: 'pi pi-tag',
                                  command: () => router.push(`/products?categoria=${encodeURIComponent(cat.nombre)}`)
                              }))
                            : [
                                  {
                                      label: 'Ver catálogo',
                                      icon: 'pi pi-eye',
                                      command: () => router.push('/products')
                                  }
                              ]
                }
            ]
        },
        {
            label: 'Nosotros',
            icon: 'pi pi-info-circle',
            command: () => scrollToSection('about-section'),
            className: 'nav-item-hover'
        },
        {
            label: 'Ubicación',
            icon: 'pi pi-map-marker',
            command: () => scrollToSection('contact-section'),
            className: 'nav-item-hover'
        }
    ];

    const start = (
        <div className="flex align-items-center">
            {/* ✅ CORREGIDO: Usar logo real de Imgur */}
            <div 
                className="flex align-items-center cursor-pointer transition-all duration-200 hover:scale-105"
                onClick={() => router.push('/landing')} // ✅ CORREGIDO: Redirigir a /landing
            >
                <img 
                    src="https://i.imgur.com/S2gkZOe.png"
                    alt="ECommerce Sigchos Logo"
                    height="40"
                    className="mr-3"
                    style={{ 
                        borderRadius: '8px',
                        transition: 'all 0.3s ease'
                    }}
                    onError={(e) => {
                        // ✅ Fallback si el logo de Imgur no carga
                        (e.target as HTMLImageElement).style.display = 'none';
                        // Mostrar el ícono como backup
                        const parent = (e.target as HTMLImageElement).parentElement;
                        if (parent) {
                            parent.innerHTML = `
                                <div 
                                    class="flex align-items-center justify-content-center w-3rem h-3rem border-circle mr-3"
                                    style="background: linear-gradient(135deg, var(--primary-color) 0%, var(--primary-color-text) 100%); color: white;"
                                >
                                    <i class="pi pi-shopping-bag text-xl font-bold"></i>
                                </div>
                            ` + parent.innerHTML;
                        }
                    }}
                />
                <div className="hidden sm:block">
                    <div 
                        className="text-2xl font-bold transition-colors duration-200 hover:opacity-80"
                        style={{ 
                            color: 'var(--primary-color)' // ✅ CORREGIDO: Solo color principal, sin degradado
                        }}
                    >
                        ECommerce Sigchos
                    </div>
                    <div className="text-xs text-600 font-medium">
                        Emprendimientos & Turismo Rural
                    </div>
                </div>
            </div>
        </div>
    );

    const end = (
        <div className="flex align-items-center gap-3">
            {/* Búsqueda mejorada */}
            <form onSubmit={handleSearch} className="hidden md:flex align-items-center">
                <div className="p-input-icon-right">
                    <i 
                        className="pi pi-search cursor-pointer transition-colors duration-200 hover:text-primary" 
                        onClick={handleSearch}
                        style={{ color: 'var(--primary-color)' }} 
                    />
                    <InputText
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Buscar productos..."
                        className="search-input"
                        style={{
                            width: '200px',
                            borderRadius: '25px',
                            paddingLeft: '15px',
                            paddingRight: '40px',
                            backgroundColor: 'var(--surface-ground)',
                            border: '2px solid transparent',
                            transition: 'all 0.3s ease'
                        }}
                        onFocus={(e) => {
                            e.target.style.borderColor = 'var(--primary-color)';
                            e.target.style.boxShadow = '0 0 0 0.2rem var(--primary-color-text)';
                        }}
                        onBlur={(e) => {
                            e.target.style.borderColor = 'transparent';
                            e.target.style.boxShadow = 'none';
                        }}
                    />
                </div>
            </form>

            {mounted && (
                <>
                    {/* Carrito mejorado */}
                    <div className="relative">
                        <Button
                            icon="pi pi-shopping-cart"
                            className="p-button-rounded p-button-text cart-button"
                            tooltip={isAuthenticated ? "Ver carrito" : "Iniciar sesión para ver carrito"}
                            onClick={handleCartClick}
                            style={{
                                position: 'relative',
                                background: cartItemCount > 0 ? 'var(--primary-color-text)' : 'transparent',
                                color: cartItemCount > 0 ? 'var(--primary-color)' : 'var(--text-color)',
                                transition: 'all 0.3s ease'
                            }}
                        />
                        {cartItemCount > 0 && (
                            <Badge 
                                value={cartItemCount > 99 ? '99+' : cartItemCount.toString()}
                                severity="danger" 
                                className="absolute animate-pulse"
                                style={{ 
                                    top: '-8px',
                                    right: '-8px',
                                    minWidth: '22px',
                                    height: '22px',
                                    fontSize: '0.75rem',
                                    borderRadius: '11px',
                                    backgroundColor: '#ef4444',
                                    border: '2px solid var(--surface-card)',
                                    fontWeight: 'bold'
                                }}
                            />
                        )}
                    </div>

                    {/* Separador */}
                    <div 
                        className="hidden lg:block"
                        style={{ 
                            width: '1px', 
                            height: '30px', 
                            backgroundColor: 'var(--surface-border)',
                            margin: '0 10px'
                        }}
                    />

                    {/* Botones de autenticación mejorados */}
                    <div className="hidden lg:flex align-items-center gap-3">
                        {isAuthenticated ? (
                            <>
                                {/* Información del usuario */}
                                <div className="text-right mr-2">
                                    <div className="text-900 font-semibold text-sm">
                                        ¡Hola, {userInfo?.nombre || userInfo?.username}!
                                    </div>
                                    <div className="text-600 text-xs">
                                        {authService.isEmployee() ? 'Panel disponible' : 'Cliente registrado'}
                                    </div>
                                </div>

                                <Button
                                    label={authService.isEmployee() ? "Mi Panel" : "Mi Cuenta"}
                                    icon={authService.isEmployee() ? "pi pi-cog" : "pi pi-user"}
                                    className="p-button-outlined dashboard-button"
                                    onClick={handleDashboardClick}
                                    style={{
                                        borderColor: 'var(--primary-color)',
                                        color: 'var(--primary-color)',
                                        borderRadius: '25px',
                                        fontWeight: '600',
                                        padding: '8px 20px'
                                    }}
                                />
                                
                                <Button
                                    label="Cerrar Sesión"
                                    icon="pi pi-sign-out"
                                    className="p-button-outlined logout-button"
                                    onClick={handleLogout}
                                    style={{
                                        borderColor: '#ef4444',
                                        color: '#ef4444',
                                        borderRadius: '25px',
                                        fontWeight: '600',
                                        padding: '8px 20px',
                                        transition: 'all 0.3s ease'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.backgroundColor = '#ef4444';
                                        e.currentTarget.style.color = 'white';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.backgroundColor = 'transparent';
                                        e.currentTarget.style.color = '#ef4444';
                                    }}
                                />
                            </>
                        ) : (
                            <>
                                <Button
                                    label="Iniciar Sesión"
                                    icon="pi pi-sign-in"
                                    className="p-button-outlined login-button"
                                    onClick={handleLoginClick}
                                    style={{
                                        borderColor: 'var(--primary-color)',
                                        color: 'var(--primary-color)',
                                        borderRadius: '25px',
                                        fontWeight: '600',
                                        padding: '8px 20px'
                                    }}
                                />
                                <Button
                                    label="Registrarse"
                                    icon="pi pi-user-plus"
                                    onClick={handleRegistroClick}
                                    style={{
                                        backgroundColor: 'var(--primary-color)', // ✅ CORREGIDO: Solo color principal
                                        border: 'none',
                                        borderRadius: '25px',
                                        fontWeight: '600',
                                        padding: '8px 20px',
                                        color: 'white'
                                    }}
                                />
                            </>
                        )}
                    </div>
                </>
            )}

            {/* Botón menú móvil */}
            <Button
                icon="pi pi-bars"
                className="p-button-rounded p-button-text lg:hidden"
                onClick={() => setMobileMenuVisible(true)}
                style={{
                    color: 'var(--primary-color)',
                    fontSize: '1.2rem'
                }}
            />
        </div>
    );

    if (!mounted) {
        return (
            <div 
                className="sticky top-0 z-5 shadow-3"
                style={{
                    backgroundColor: 'var(--surface-card)',
                    borderBottom: '2px solid var(--surface-border)',
                    height: '80px'
                }}
            >
                <div className="flex align-items-center justify-content-center h-full">
                    <div style={{ width: '200px', height: '40px' }}></div>
                </div>
            </div>
        );
    }

    return (
        <>
            {/* Navbar principal mejorado */}
            <div 
                className="sticky top-0 z-5 shadow-3"
                style={{
                    backgroundColor: 'var(--surface-card)',
                    borderBottom: '2px solid var(--primary-color-text)',
                    backdropFilter: 'blur(10px)'
                }}
            >
                <Menubar 
                    model={navigationItems} 
                    start={start} 
                    end={end}
                    className="border-none px-4 py-3 custom-menubar"
                    style={{ 
                        backgroundColor: 'transparent',
                        minHeight: '70px'
                    }}
                />
            </div>

            {/* Modal de registro */}
            <RegistroModal
                visible={registroModalVisible}
                onHide={() => setRegistroModalVisible(false)}
                onSuccess={() => {
                    // Opcional: mostrar mensaje de éxito o redirigir al login
                    console.log('✅ Usuario registrado exitosamente');
                }}
            />

            {/* Sidebar móvil mejorado */}
            <Sidebar
                visible={mobileMenuVisible}
                position="right"
                onHide={() => setMobileMenuVisible(false)}
                className="w-full sm:w-20rem mobile-sidebar"
                style={{
                    background: 'var(--surface-card)',
                }}
            >
                {mounted && (
                    <div className="flex flex-column gap-4 p-3">
                        {/* Logo en móvil */}
                        <div className="flex align-items-center justify-content-center mb-4 p-3 border-round-lg" style={{ backgroundColor: 'var(--surface-100)' }}>
                            <img 
                                src="https://i.imgur.com/S2gkZOe.png"
                                alt="ECommerce Sigchos Logo"
                                height="30"
                                className="mr-2"
                                style={{ borderRadius: '6px' }}
                                onError={(e) => {
                                    // Fallback para móvil
                                    (e.target as HTMLImageElement).style.display = 'none';
                                }}
                            />
                            <span className="text-lg font-bold" style={{ color: 'var(--primary-color)' }}>
                                ECommerce Sigchos
                            </span>
                        </div>

                        {/* Búsqueda en móvil */}
                        <form onSubmit={handleSearch} className="mb-3">
                            <div className="p-input-icon-right w-full">
                                <i className="pi pi-search" style={{ color: 'var(--primary-color)' }} />
                                <InputText
                                    value={searchTerm}
                                    onChange={(e) => setSearchTerm(e.target.value)}
                                    placeholder="Buscar productos..."
                                    className="w-full"
                                    style={{ borderRadius: '25px', paddingLeft: '15px' }}
                                />
                            </div>
                        </form>

                        {/* Carrito en móvil */}
                        <Button
                            label={`Carrito (${cartItemCount})`}
                            icon="pi pi-shopping-cart"
                            className="w-full justify-content-start mobile-nav-btn"
                            onClick={() => {
                                setMobileMenuVisible(false);
                                handleCartClick();
                            }}
                            style={{ borderRadius: '10px' }}
                        />

                        {/* Navegación en móvil */}
                        {navigationItems.map((item, index) => (
                            <div key={index} className="flex flex-column gap-1">
                                <Button
                                    label={item.label}
                                    icon={item.icon}
                                    className="w-full p-button-text justify-content-start mobile-nav-btn"
                                    onClick={() => {
                                        if (item.command) {
                                            setMobileMenuVisible(false);
                                            item.command();
                                        }
                                    }}
                                    style={{ 
                                        color: 'var(--text-color)',
                                        textAlign: 'left',
                                        borderRadius: '10px',
                                        padding: '12px'
                                    }}
                                />
                                {item.items && (
                                    <div className="pl-3 flex flex-column gap-1">
                                        {item.items.map((sub: any, subIdx: number) => (
                                            <div key={subIdx} className="flex flex-column gap-1">
                                                {sub.command ? (
                                                    <Button
                                                        label={sub.label}
                                                        icon={sub.icon}
                                                        className="w-full p-button-text p-button-sm justify-content-start"
                                                        onClick={() => {
                                                            setMobileMenuVisible(false);
                                                            sub.command();
                                                        }}
                                                        style={{ color: 'var(--text-color-secondary)', textAlign: 'left', borderRadius: '8px' }}
                                                    />
                                                ) : (
                                                    <>
                                                        <div className="px-3 py-1 text-xs font-semibold text-600 flex align-items-center gap-2">
                                                            <i className={sub.icon}></i>
                                                            <span>{sub.label}</span>
                                                        </div>
                                                        {sub.items?.map((catItem: any, catIdx: number) => (
                                                            <Button
                                                                key={catIdx}
                                                                label={catItem.label}
                                                                icon={catItem.icon}
                                                                className="w-full p-button-text p-button-sm justify-content-start pl-4"
                                                                onClick={() => {
                                                                    setMobileMenuVisible(false);
                                                                    catItem.command?.();
                                                                }}
                                                                style={{ color: 'var(--text-color-secondary)', textAlign: 'left', borderRadius: '8px' }}
                                                            />
                                                        ))}
                                                    </>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        ))}

                        {/* Botones de autenticación en móvil */}
                        <div className="mt-4 pt-4" style={{ borderTop: '2px solid var(--surface-border)' }}>
                            {isAuthenticated ? (
                                <div className="flex flex-column gap-3">
                                    <div className="text-center p-3 border-round-lg" style={{ backgroundColor: 'var(--surface-100)' }}>
                                        <div className="text-900 font-semibold">
                                            {userInfo?.nombre} {userInfo?.apellido}
                                        </div>
                                        <div className="text-600 text-sm">
                                            {authService.isEmployee() ? 'Administrador' : 'Cliente'}
                                        </div>
                                    </div>
                                    
                                    <Button
                                        label={authService.isEmployee() ? "Mi Panel" : "Mi Cuenta"}
                                        icon={authService.isEmployee() ? "pi pi-cog" : "pi pi-user"}
                                        className="w-full"
                                        onClick={() => {
                                            setMobileMenuVisible(false);
                                            handleDashboardClick();
                                        }}
                                        style={{
                                            backgroundColor: 'var(--primary-color)',
                                            border: 'none',
                                            borderRadius: '10px'
                                        }}
                                    />
                                    <Button
                                        label="Cerrar Sesión"
                                        icon="pi pi-sign-out"
                                        className="w-full"
                                        onClick={() => {
                                            setMobileMenuVisible(false);
                                            handleLogout();
                                        }}
                                        style={{
                                            backgroundColor: '#ef4444',
                                            border: 'none',
                                            borderRadius: '10px'
                                        }}
                                    />
                                </div>
                            ) : (
                                <div className="flex flex-column gap-3">
                                    <Button
                                        label="Iniciar Sesión"
                                        icon="pi pi-sign-in"
                                        className="w-full"
                                        onClick={() => {
                                            setMobileMenuVisible(false);
                                            handleLoginClick();
                                        }}
                                        style={{
                                            backgroundColor: 'transparent',
                                            border: '2px solid var(--primary-color)',
                                            color: 'var(--primary-color)',
                                            borderRadius: '10px'
                                        }}
                                    />
                                    <Button
                                        label="Registrarse"
                                        icon="pi pi-user-plus"
                                        className="w-full"
                                        onClick={() => {
                                            setMobileMenuVisible(false);
                                            handleRegistroClick();
                                        }}
                                        style={{
                                            background: 'linear-gradient(135deg, var(--primary-color) 0%, var(--pink-500) 100%)',
                                            border: 'none',
                                            borderRadius: '10px'
                                        }}
                                    />
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </Sidebar>

            {/* Modal de Mi Cuenta */}
            <MiCuentaModal
                visible={miCuentaModalVisible}
                onHide={() => setMiCuentaModalVisible(false)}
                userInfo={userInfo}
            />

            {/* Estilos CSS adicionales */}
            <style jsx>{`
                .custom-menubar .p-menubar-root-list > .p-menuitem > .p-menuitem-link {
                    border-radius: 25px;
                    padding: 8px 16px;
                    margin: 0 4px;
                    font-weight: 600;
                    transition: all 0.3s ease;
                }
                
                .custom-menubar .p-menubar-root-list > .p-menuitem > .p-menuitem-link:hover {
                    background-color: var(--primary-color-text);
                    color: var(--primary-color);
                    transform: translateY(-2px);
                }

                .cart-button:hover {
                    transform: scale(1.1);
                }

                .dashboard-button:hover {
                    background-color: var(--primary-color);
                    color: white;
                    transform: translateY(-2px);
                }

                .login-button:hover {
                    background-color: var(--primary-color);
                    color: white;
                    transform: translateY(-2px);
                }

                .mobile-nav-btn:hover {
                    background-color: var(--surface-100);
                    transform: translateX(5px);
                }

                .mobile-sidebar .p-sidebar-content {
                    padding: 0;
                }

                @keyframes pulse {
                    0%, 100% { transform: scale(1); }
                    50% { transform: scale(1.05); }
                }

                .animate-pulse {
                    animation: pulse 2s infinite;
                }
            `}</style>
        </>
    );
};

export default LandingNavbar;