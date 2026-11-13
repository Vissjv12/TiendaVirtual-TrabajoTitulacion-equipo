'use client';

import React, { useState, useEffect, useContext, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Dialog } from 'primereact/dialog';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { LayoutContext } from '../../../layout/context/layoutcontext';
import productService, { ProductoResponse } from '../../../services/productService';
import authService from '../../../services/authService';
import cartService from '../../../services/cartService';

const FeaturedProducts: React.FC = () => {
    const { layoutConfig } = useContext(LayoutContext);
    const [productos, setProductos] = useState<ProductoResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [showLoginDialog, setShowLoginDialog] = useState(false);
    const [selectedProduct, setSelectedProduct] = useState<ProductoResponse | null>(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const router = useRouter();
    const toast = useRef<Toast>(null);

    const primaryColor = 'var(--primary-color)';

    // ✅ VERIFICAR AUTENTICACIÓN
    useEffect(() => {
        const checkAuth = () => {
            setIsAuthenticated(authService.isAuthenticated());
        };

        checkAuth();

        // Verificar cada 5 segundos
        const interval = setInterval(checkAuth, 5000);
        // Escuchar cambios en localStorage
        const handleStorageChange = () => checkAuth();
        window.addEventListener('storage', handleStorageChange);

        return () => {
            clearInterval(interval);
            window.removeEventListener('storage', handleStorageChange);
        };
    }, []);

    // ✅ CARGAR PRODUCTOS AL INICIAR
    useEffect(() => {
        cargarProductos();

        // ✅ ESCUCHAR EVENTOS DE ACTUALIZACIÓN DE STOCK
        const handleStockUpdate = (event: any) => {
            const { productoId, stock } = event.detail;
            console.log(`📦 Stock actualizado en FeaturedProducts para producto ${productoId}: ${stock}`);

            // Forzar re-render actualizando el estado
            setProductos((prevProductos) =>
                prevProductos.map((producto) => {
                    if (producto.id === productoId) {
                        console.log(`🔄 Actualizando UI producto ${producto.nombre}: stock ${stock}`);
                        return {
                            ...producto,
                            inventario: {
                                ...producto.inventario!,
                                cantidad: stock,
                                activo: stock > 0,
                                ubicacion: stock > 0 ? 'Disponible' : 'No disponible'
                            }
                        };
                    }
                    return producto;
                })
            );
        };

        window.addEventListener('stockUpdated', handleStockUpdate);

        return () => {
            window.removeEventListener('stockUpdated', handleStockUpdate);
        };
    }, []);

    /**
     * ✅ CARGAR PRODUCTOS (súper optimizado)
     */
    const cargarProductos = async () => {
        setLoading(true);
        try {
            console.log('🚀 FeaturedProducts - Cargando productos...');

            const response = await productService.obtenerProductos();

            if (response.success && response.data) {
                // Tomar solo los primeros 3 productos
                const productosDestacados = response.data.slice(0, 3);
                setProductos(productosDestacados);
                console.log(`✅ FeaturedProducts cargados: ${productosDestacados.length}`);
            } else {
                console.error('❌ Error cargando productos:', response.message);
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando productos destacados',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error inesperado:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error inesperado cargando productos',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    /**
     * ✅ MANEJAR AGREGAR AL CARRITO
     */
    const handleAddToCart = async (producto: ProductoResponse) => {
        if (!isAuthenticated) {
            setSelectedProduct(producto);
            setShowLoginDialog(true);
            return;
        }

        // ✅ VALIDAR STOCK antes de agregar
        const validacion = productService.puedeAgregarAlCarrito(producto, 1);

        if (!validacion.puede) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Stock insuficiente',
                detail: validacion.mensaje,
                life: 3000
            });
            return;
        }

        // ✅ AGREGAR AL CARRITO
        const result = cartService.addToCart(producto as any, 1);

        if (result.success) {
            toast.current?.show({
                severity: 'success',
                summary: 'Producto Agregado',
                detail: result.message || 'Producto agregado al carrito',
                life: 3000
            });
        } else if (result.errorType === 'VENDOR_MISMATCH') {
            confirmDialog({
                message: result.message,
                header: 'Conflicto de Empresa',
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: 'Sí, vaciar y agregar',
                rejectLabel: 'Cancelar',
                accept: () => {
                    const clearResult = cartService.addToCart(producto as any, 1, true);
                    if (clearResult.success) {
                        toast.current?.show({
                            severity: 'success',
                            summary: 'Carrito Actualizado',
                            detail: 'Se ha vaciado el carrito y agregado el nuevo producto',
                            life: 3000
                        });
                    }
                }
            });
        } else {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: result.message || 'Error agregando producto',
                life: 3000
            });
        }
    };

    const handleLoginRedirect = () => {
        setShowLoginDialog(false);
        router.push('/auth/login2');
    };

    /**
     * ✅ RENDERIZAR TARJETA DE PRODUCTO
     */
    const renderProductCard = (producto: ProductoResponse) => {
        // ✅ VALIDACIONES DE STOCK SIMPLIFICADAS
        const isConsultando = producto.inventario?.cantidad === -1;
        const hasStock = productService.tieneStock(producto);
        const nivelStock = productService.getNivelStock(producto);
        const mensajeStock = productService.getMensajeStock(producto);
        const cantidadStock = productService.getCantidadDisponible(producto);

        console.log(`🔍 Producto ${producto.nombre}: stock=${cantidadStock}, hasStock=${hasStock}, mensaje="${mensajeStock}"`);

        const header = (
            <div className="relative overflow-hidden">
                <img
                    src={producto.imagen}
                    onError={(e) => {
                        (e.target as HTMLImageElement).src = '/demo/images/product/product-placeholder.svg';
                    }}
                    alt={producto.nombre}
                    className="w-full object-cover transition-all transition-duration-300 hover:scale-105"
                    style={{ height: '250px' }}
                />

                {/* ✅ BADGES DE STOCK */}
                {!hasStock && <Badge value="No Disponible" severity="danger" className="absolute top-2 right-2" style={{ backgroundColor: '#ef4444', fontSize: '0.75rem' }} />}
                {hasStock && nivelStock === 'bajo' && <Badge value="¡Pocas!" severity="warning" className="absolute top-2 right-2" style={{ backgroundColor: '#f59e0b', fontSize: '0.75rem' }} />}
            </div>
        );

        const footer = (
            <div className="p-3">
                {!isAuthenticated || authService.canViewPrices() ? (
                    <div className="flex justify-content-between align-items-center">
                        <div className="text-xl font-bold" style={{ color: primaryColor }}>
                            {productService.formatearPrecio(producto.precio)}
                        </div>
                        <Button
                            icon="pi pi-shopping-cart"
                            label={authService.isEmployee() ? 'Gestionar' : isAuthenticated ? 'Agregar' : 'Iniciar sesión para comprar'}
                            className="p-button-sm"
                            disabled={isAuthenticated && !authService.isEmployee() && !hasStock}
                            style={{
                                backgroundColor: authService.isEmployee() || hasStock ? primaryColor : 'var(--surface-400)',
                                borderColor: authService.isEmployee() || hasStock ? primaryColor : 'var(--surface-400)',
                                color: 'white',
                                padding: '0.5rem 1rem',
                                fontSize: '0.9rem',
                                transition: 'all 0.3s ease',
                                opacity: authService.isEmployee() || hasStock ? 1 : 0.6
                            }}
                            onMouseEnter={(e) => {
                                if (authService.isEmployee() || hasStock) {
                                    e.currentTarget.style.backgroundColor = 'var(--primary-600)';
                                    e.currentTarget.style.transform = 'translateY(-1px)';
                                    e.currentTarget.style.boxShadow = `0 4px 12px ${primaryColor}40`;
                                }
                            }}
                            onMouseLeave={(e) => {
                                if (authService.isEmployee() || hasStock) {
                                    e.currentTarget.style.backgroundColor = primaryColor;
                                    e.currentTarget.style.transform = 'translateY(0)';
                                    e.currentTarget.style.boxShadow = 'none';
                                }
                            }}
                            onClick={() => {
                                if (!isAuthenticated) {
                                    handleAddToCart(producto);
                                } else if (authService.isEmployee()) {
                                    router.push(`/dashboard/productos/${producto.id}`);
                                } else {
                                    handleAddToCart(producto);
                                }
                            }}
                        />
                    </div>
                ) : (
                    <div className="text-center">
                        <div className="text-sm text-600 mb-2">
                            <i className="pi pi-lock mr-1" style={{ color: primaryColor }}></i>
                            Inicia sesión para comprar
                        </div>
                        <Button
                            label="Iniciar sesión para comprar"
                            icon="pi pi-sign-in"
                            className="p-button-sm p-button-outlined w-full"
                            style={{
                                borderColor: primaryColor,
                                color: primaryColor,
                                backgroundColor: 'transparent',
                                fontSize: '0.9rem',
                                transition: 'all 0.3s ease'
                            }}
                            onMouseEnter={(e) => {
                                e.currentTarget.style.backgroundColor = primaryColor;
                                e.currentTarget.style.color = 'white';
                                e.currentTarget.style.transform = 'translateY(-1px)';
                            }}
                            onMouseLeave={(e) => {
                                e.currentTarget.style.backgroundColor = 'transparent';
                                e.currentTarget.style.color = primaryColor;
                                e.currentTarget.style.transform = 'translateY(0)';
                            }}
                            onClick={() => handleAddToCart(producto)}
                        />
                    </div>
                )}
            </div>
        );

        return (
            <Card
                key={producto.id}
                header={header}
                footer={footer}
                className="h-full shadow-2 border-round-lg overflow-hidden transition-all transition-duration-300 hover:shadow-4"
                style={{
                    border: `1px solid var(--surface-border)`,
                    backgroundColor: 'var(--surface-card)',
                    transition: 'all 0.3s ease'
                }}
                onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-4px)';
                    e.currentTarget.style.boxShadow = `0 8px 25px rgba(0,0,0,0.15)`;
                }}
                onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = 'var(--shadow-2)';
                }}
            >
                <div className="p-3" style={{ paddingBottom: 0 }}>
                    <div className="flex align-items-center justify-content-between mb-2">
                        <Badge
                            value={producto.categoria.nombre}
                            className="text-xs"
                            style={{
                                backgroundColor: `${primaryColor}15`,
                                color: primaryColor,
                                border: `1px solid ${primaryColor}20`,
                                fontSize: '0.7rem'
                            }}
                        />
                        <div
                            className="text-xs font-bold px-2 py-1 border-round-xl"
                            style={{
                                backgroundColor: primaryColor,
                                color: 'white'
                            }}
                        >
                            {producto.empresa.nombre}
                        </div>
                    </div>

                    <h5 className="m-0 mb-2 text-900 font-bold line-height-3" style={{ fontSize: '1.1rem' }}>
                        {producto.nombre}
                    </h5>

                    <p className="text-600 line-height-3 text-sm mb-2">{producto.descripcion.length > 80 ? `${producto.descripcion.substring(0, 80)}...` : producto.descripcion}</p>

                    {/* ✅ INFORMACIÓN DE STOCK */}
                    <div
                        className="flex align-items-center p-2 border-round-md"
                        style={{
                            backgroundColor: isConsultando ? 'var(--surface-200)' : (hasStock ? `${primaryColor}08` : '#ef444408'),
                            border: `1px solid ${isConsultando ? 'var(--surface-400)' : (hasStock ? primaryColor : '#ef4444')}15`
                        }}
                    >
                        <i
                            className={`pi ${isConsultando ? 'pi-spin pi-spinner' : (hasStock ? 'pi-check-circle' : 'pi-times-circle')} mr-1 text-xs`}
                            style={{ color: isConsultando ? 'var(--text-color-secondary)' : (hasStock ? primaryColor : '#ef4444') }}
                        ></i>
                        <small className="text-xs font-medium" style={{ color: isConsultando ? 'var(--text-color-secondary)' : (hasStock ? primaryColor : '#ef4444') }}>
                            {mensajeStock}
                        </small>
                    </div>
                </div>
            </Card>
        );
    };

    /**
     * ✅ SKELETON MIENTRAS CARGA
     */
    const renderSkeleton = () => (
        <Card className="h-full shadow-2 border-round-lg" style={{ backgroundColor: 'var(--surface-card)' }}>
            <div className="p-3">
                <Skeleton width="100%" height="250px" className="mb-3 border-round-lg" />
                <div className="flex justify-content-between align-items-center mb-2">
                    <Skeleton width="25%" height="1rem" className="border-round-lg" />
                    <Skeleton width="35%" height="1rem" className="border-round-lg" />
                </div>
                <Skeleton width="70%" height="1.2rem" className="mb-2" />
                <Skeleton width="100%" height="0.8rem" className="mb-1" />
                <Skeleton width="80%" height="0.8rem" className="mb-3" />
                <div className="flex justify-content-between align-items-center">
                    <Skeleton width="40%" height="1.5rem" className="border-round-lg" />
                    <Skeleton width="35%" height="1.5rem" className="border-round-lg" />
                </div>
            </div>
        </Card>
    );

    return (
        <>
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* ✅ CSS OPTIMIZADO PARA GRID */}
            <style jsx>{`
                .products-grid {
                    display: grid;
                    gap: 1.5rem;
                    max-width: 1200px;
                    margin: 0 auto;
                }

                @media (max-width: 768px) {
                    .products-grid {
                        grid-template-columns: 1fr;
                    }
                }

                @media (min-width: 769px) and (max-width: 1024px) {
                    .products-grid {
                        grid-template-columns: repeat(2, 1fr);
                    }
                }

                @media (min-width: 1025px) {
                    .products-grid {
                        grid-template-columns: repeat(3, 1fr);
                    }
                }
            `}</style>

            <section id="productos-destacados" className="featured-products py-6" style={{ backgroundColor: 'var(--surface-ground)' }}>
                <div className="container mx-auto px-4 max-w-6xl">
                    {/* ✅ HEADER */}
                    <div className="text-center mb-6">
                        <h2
                            className="font-bold mb-3 line-height-2"
                            style={{
                                fontSize: 'clamp(1.8rem, 4vw, 3rem)',
                                color: 'var(--text-color)'
                            }}
                        >
                            Productos{' '}
                            <span
                                style={{
                                    background: `linear-gradient(135deg, ${primaryColor} 0%, var(--primary-600) 100%)`,
                                    WebkitBackgroundClip: 'text',
                                    WebkitTextFillColor: 'transparent'
                                }}
                            >
                                Destacados
                            </span>
                        </h2>

                        <p className="text-lg max-w-2xl mx-auto line-height-3" style={{ color: 'var(--text-color-secondary)' }}>
                            Descubre nuestra selección especial de productos locales más populares, creados por emprendedores de las comunidades de Sigchos
                        </p>
                    </div>

                    {/* ✅ GRID DE PRODUCTOS */}
                    <div className="products-grid">
                        {loading ? (
                            // Skeletons mientras carga
                            Array.from({ length: 3 }).map((_, index) => <div key={index}>{renderSkeleton()}</div>)
                        ) : productos.length > 0 ? (
                            // Productos cargados
                            productos.map((producto) => <div key={producto.id}>{renderProductCard(producto)}</div>)
                        ) : (
                            // No hay productos
                            <div className="text-center py-6" style={{ gridColumn: '1 / -1' }}>
                                <div
                                    className="p-4 border-round-2xl mx-auto max-w-md"
                                    style={{
                                        backgroundColor: 'var(--surface-card)',
                                        border: `2px dashed ${primaryColor}30`
                                    }}
                                >
                                    <i className="pi pi-exclamation-triangle text-4xl mb-3" style={{ color: primaryColor }}></i>
                                    <h3 className="text-900 mb-2 font-bold">No hay productos disponibles</h3>
                                    <p className="text-600 text-sm mb-3">Los productos se están preparando. Por favor, inténtalo más tarde</p>
                                    <Button
                                        label="Reintentar"
                                        icon="pi pi-refresh"
                                        className="p-button-outlined"
                                        onClick={cargarProductos}
                                        style={{
                                            borderColor: primaryColor,
                                            color: primaryColor
                                        }}
                                    />
                                </div>
                            </div>
                        )}
                    </div>

                    {/* ✅ BOTÓN VER MÁS */}
                    {productos.length > 0 && (
                        <div className="text-center mt-6">
                            <Button
                                label="Ver Todos los Productos"
                                icon="pi pi-arrow-right"
                                className="p-button-lg p-button-outlined"
                                style={{
                                    borderColor: primaryColor,
                                    color: primaryColor,
                                    backgroundColor: 'transparent',
                                    padding: '0.8rem 1.8rem',
                                    fontSize: '1rem',
                                    fontWeight: '600',
                                    borderRadius: '10px',
                                    transition: 'all 0.3s ease',
                                    border: `2px solid ${primaryColor}`
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.backgroundColor = primaryColor;
                                    e.currentTarget.style.color = 'white';
                                    e.currentTarget.style.transform = 'translateY(-2px)';
                                    e.currentTarget.style.boxShadow = `0 6px 20px ${primaryColor}40`;
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.backgroundColor = 'transparent';
                                    e.currentTarget.style.color = primaryColor;
                                    e.currentTarget.style.transform = 'translateY(0)';
                                    e.currentTarget.style.boxShadow = 'none';
                                }}
                                onClick={() => router.push('/products')}
                            />
                        </div>
                    )}
                </div>
            </section>

            {/* ✅ DIALOG PARA LOGIN */}
            <Dialog header="Iniciar Sesión Requerido" visible={showLoginDialog} onHide={() => setShowLoginDialog(false)} style={{ width: '400px' }} modal>
                <div className="text-center p-3">
                    <div
                        className="p-3 border-round-2xl mx-auto mb-3"
                        style={{
                            backgroundColor: `${primaryColor}15`,
                            width: 'fit-content'
                        }}
                    >
                        <i className="pi pi-info-circle text-4xl" style={{ color: primaryColor }}></i>
                    </div>
                    <h3 className="text-900 mb-3 font-bold">¡Inicia Sesión!</h3>
                    <p className="text-600 mb-4 line-height-3">Para agregar productos al carrito y comprar, necesitas iniciar sesión en tu cuenta.</p>
                    {selectedProduct && (
                        <div
                            className="p-3 border-round-xl mb-4"
                            style={{
                                backgroundColor: 'var(--surface-card)',
                                border: `1px solid ${primaryColor}20`
                            }}
                        >
                            <small className="text-600 block mb-1">Producto seleccionado:</small>
                            <div className="font-bold" style={{ color: primaryColor }}>
                                {selectedProduct.nombre}
                            </div>
                        </div>
                    )}
                    <div className="flex gap-3 justify-content-center">
                        <Button label="Cancelar" icon="pi pi-times" className="p-button-text" onClick={() => setShowLoginDialog(false)} />
                        <Button
                            label="Iniciar sesión para comprar"
                            icon="pi pi-sign-in"
                            style={{
                                backgroundColor: primaryColor,
                                borderColor: primaryColor
                            }}
                            onClick={handleLoginRedirect}
                        />
                    </div>
                </div>
            </Dialog>
        </>
    );
};

export default FeaturedProducts;
