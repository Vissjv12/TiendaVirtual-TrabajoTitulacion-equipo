'use client';

import React, { useState, useEffect, useContext, useRef, useMemo } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { Skeleton } from 'primereact/skeleton';
import { Dialog } from 'primereact/dialog';
import { ConfirmDialog, confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { Sidebar } from 'primereact/sidebar';
import { Divider } from 'primereact/divider';
import { Chip } from 'primereact/chip';
// ✅ CORREGIDO: Remover importación de LandingNavbar
import productService, { ProductoResponse, Categoria, Empresa } from '../../../services/productService';
import authService from '../../../services/authService';
import cartService from '../../../services/cartService';

type SortOption = 'nombre-asc' | 'nombre-desc' | 'precio-asc' | 'precio-desc';

interface SortOptionLabel {
    label: string;
    value: SortOption;
}

const ProductosPage: React.FC = () => {
    const router = useRouter();
    const searchParams = useSearchParams();
    const toast = useRef<Toast>(null);

    // Estado de montaje para evitar hidratación
    const [mounted, setMounted] = useState(false);

    // Estados principales
    const [productos, setProductos] = useState<ProductoResponse[]>([]);
    const [categorias, setCategorias] = useState<Categoria[]>([]);
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(true);

    // Estados de filtros - ✅ CORREGIDO: Filtrar por NOMBRE, no ID
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedCategoriaNombre, setSelectedCategoriaNombre] = useState<string | null>(null);
    const [selectedEmpresaNombre, setSelectedEmpresaNombre] = useState<string | null>(null);
    const [sortBy, setSortBy] = useState<SortOption>('nombre-asc');

    // Estados de UI
    const [showLoginDialog, setShowLoginDialog] = useState(false);
    const [selectedProduct, setSelectedProduct] = useState<ProductoResponse | null>(null);
    const [sidebarVisible, setSidebarVisible] = useState(false);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [productModalVisible, setProductModalVisible] = useState(false); // ✅ NUEVO: Modal de producto

    // Configuración del tema
    const primaryColor = 'var(--primary-color)';

    // Opciones de ordenamiento
    const sortOptions: SortOptionLabel[] = [
        { label: 'Nombre A-Z', value: 'nombre-asc' },
        { label: 'Nombre Z-A', value: 'nombre-desc' },
        { label: 'Precio menor a mayor', value: 'precio-asc' },
        { label: 'Precio mayor a menor', value: 'precio-desc' }
    ];

    // ✅ CORREGIDO: Filtros por NOMBRE
    const productosFiltrados = useMemo(() => {
        if (!mounted || productos.length === 0) return [];

        let resultado = [...productos];

        // Filtrar por búsqueda
        if (searchTerm.trim()) {
            resultado = productService.filtrarProductos(resultado, searchTerm);
        }

        // ✅ CORREGIDO: Filtrar por NOMBRE de categoría
        if (selectedCategoriaNombre) {
            resultado = resultado.filter((p) => p.categoria.nombre === selectedCategoriaNombre);
        }

        // ✅ CORREGIDO: Filtrar por NOMBRE de empresa
        if (selectedEmpresaNombre) {
            resultado = resultado.filter((p) => p.empresa.nombre === selectedEmpresaNombre);
        }

        // Ordenar
        switch (sortBy) {
            case 'nombre-asc':
                resultado.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
                break;
            case 'nombre-desc':
                resultado.sort((a, b) => b.nombre.localeCompare(a.nombre, 'es'));
                break;
            case 'precio-asc':
                resultado.sort((a, b) => a.precio - b.precio);
                break;
            case 'precio-desc':
                resultado.sort((a, b) => b.precio - a.precio);
                break;
        }

        return resultado;
    }, [productos, searchTerm, selectedCategoriaNombre, selectedEmpresaNombre, sortBy, mounted]);

    useEffect(() => {
        const initializePage = async () => {
            setMounted(true);
            window.scrollTo(0, 0);

            setIsAuthenticated(authService.isAuthenticated());

            // ✅ CORREGIDO: Procesar parámetros por NOMBRE
            const categoria = searchParams.get('categoria');
            const empresa = searchParams.get('empresa');
            const busqueda = searchParams.get('q');

            if (categoria) setSelectedCategoriaNombre(categoria);
            if (empresa) setSelectedEmpresaNombre(empresa);
            if (busqueda) setSearchTerm(busqueda);

            await cargarDatos();
        };

        initializePage();
    }, [searchParams]);

    useEffect(() => {
        if (!mounted) return;

        const checkAuth = () => {
            setIsAuthenticated(authService.isAuthenticated());
        };

        const interval = setInterval(checkAuth, 5000);
        return () => clearInterval(interval);
    }, [mounted]);

    const cargarDatos = async () => {
        setLoading(true);
        try {
            console.log('🚀 Iniciando carga de datos (ProductosPage)...');

            const productosRes = await productService.obtenerProductos();
            console.log('📦 Productos cargados:', productosRes.success);

            if (productosRes.success && productosRes.data) {
                setProductos(productosRes.data);
                console.log('✅ Productos seteados:', productosRes.data.length);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: productosRes.message || 'Error cargando productos',
                    life: 3000
                });
            }

            const [categoriasRes, empresasRes] = await Promise.all([productService.obtenerCategorias(), productService.obtenerEmpresas()]);

            if (categoriasRes.success && categoriasRes.data) {
                setCategorias(categoriasRes.data);
            }

            if (empresasRes.success && empresasRes.data) {
                setEmpresas(empresasRes.data);
            }
        } catch (error) {
            console.error('❌ Error cargando datos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error cargando datos',
                life: 3000
            });
        } finally {
            setLoading(false);
            console.log('✅ Carga completada (ProductosPage)');
        }
    };

    const limpiarFiltros = () => {
        setSearchTerm('');
        setSelectedCategoriaNombre(null);
        setSelectedEmpresaNombre(null);
        setSortBy('nombre-asc');
        router.push('/products');
    };

    // ✅ CORREGIDO: Función sin errores TypeScript
    const handleAddToCart = async (producto: ProductoResponse) => {
        if (!isAuthenticated) {
            setSelectedProduct(producto);
            setShowLoginDialog(true);
        } else {
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

            // ✅ CORREGIDO: Convertir ProductoResponse a Producto para cartService
            const productoParaCarrito = {
                id: producto.id,
                nombre: producto.nombre,
                descripcion: producto.descripcion,
                precio: producto.precio,
                imagen: producto.imagen,
                activo: true, // Asumir que está activo si está en la lista
                categoria: producto.categoria,
                empresa: producto.empresa,
                fechaCreacion: producto.fechaCreacion,
                fechaActualizacion: producto.fechaActualizacion
            };

            const result = cartService.addToCart(productoParaCarrito, 1);

            if (result.success) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Producto Agregado',
                    detail: result.message,
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
                        const clearResult = cartService.addToCart(productoParaCarrito, 1, true);
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
                    detail: result.message,
                    life: 3000
                });
            }
        }
    };

    const handleLoginRedirect = () => {
        setShowLoginDialog(false);
        router.push('/auth/login2');
    };

    // ✅ NUEVO: Función para mostrar modal de producto
    const handleProductClick = (producto: ProductoResponse) => {
        setSelectedProduct(producto);
        setProductModalVisible(true);
    };

    // ✅ CORREGIDO: Render de tarjeta con diseño consistente
    const renderProductCard = (producto: ProductoResponse) => {
        const hasStock = productService.tieneStock(producto);
        const nivelStock = productService.getNivelStock(producto);
        const mensajeStock = productService.getMensajeStock(producto);

        const header = (
            <div className="relative overflow-hidden">
                <img
                    src={producto.imagen || '/demo/images/product/product-placeholder.svg'}
                    onError={(e) => {
                        (e.target as HTMLImageElement).src = '/demo/images/product/product-placeholder.svg';
                    }}
                    alt={producto.nombre}
                    className="w-full object-cover transition-all transition-duration-300 hover:scale-105 cursor-pointer"
                    style={{ height: '200px' }}
                    onClick={() => handleProductClick(producto)}
                />

                {!hasStock && (
                    <Badge
                        value="Sin Stock"
                        severity="danger"
                        className="absolute top-2 right-2"
                        style={{
                            backgroundColor: '#ef4444',
                            fontSize: '0.75rem'
                        }}
                    />
                )}
                {hasStock && nivelStock === 'bajo' && (
                    <Badge
                        value="¡Pocas!"
                        severity="warning"
                        className="absolute top-2 right-2"
                        style={{
                            backgroundColor: '#f59e0b',
                            fontSize: '0.75rem'
                        }}
                    />
                )}
            </div>
        );

        return (
            <Card
                key={producto.id}
                header={header}
                className="h-full shadow-2 border-round-lg overflow-hidden transition-all transition-duration-300 hover:shadow-4 product-card"
                style={{
                    border: `1px solid var(--surface-border)`,
                    backgroundColor: 'var(--surface-card)',
                    transition: 'all 0.3s ease',
                    display: 'flex',
                    flexDirection: 'column'
                }}
                onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = `0 8px 25px rgba(0,0,0,0.12)`;
                }}
                onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = 'var(--shadow-2)';
                }}
            >
                {/* ✅ CORREGIDO: Contenido con altura fija y flex */}
                <div className="flex flex-column h-full">
                    {/* Contenido superior */}
                    <div className="p-3 flex-1" style={{ paddingBottom: 0 }}>
                        {/* Badges */}
                        <div className="flex align-items-center justify-content-between mb-2 flex-wrap gap-1">
                            <Badge
                                value={producto.categoria.nombre}
                                className="text-xs cursor-pointer"
                                style={{
                                    backgroundColor: `${primaryColor}15`,
                                    color: primaryColor,
                                    border: `1px solid ${primaryColor}20`,
                                    fontSize: '0.7rem',
                                    padding: '2px 6px'
                                }}
                                onClick={() => {
                                    setSelectedCategoriaNombre(producto.categoria.nombre);
                                    setSidebarVisible(false);
                                }}
                            />
                            <Badge
                                value={producto.empresa.nombre.length > 12 ? `${producto.empresa.nombre.substring(0, 12)}...` : producto.empresa.nombre}
                                className="text-xs cursor-pointer"
                                style={{
                                    backgroundColor: primaryColor,
                                    color: 'white',
                                    fontSize: '0.7rem',
                                    padding: '2px 6px'
                                }}
                                onClick={() => {
                                    setSelectedEmpresaNombre(producto.empresa.nombre);
                                    setSidebarVisible(false);
                                }}
                                title={producto.empresa.nombre} // ✅ Tooltip con nombre completo
                            />
                        </div>

                        {/* Nombre del producto - altura fija */}
                        <h5
                            className="m-0 mb-2 text-900 font-bold cursor-pointer hover:text-primary transition-colors transition-duration-200"
                            style={{
                                fontSize: '1rem',
                                lineHeight: '1.3',
                                height: '2.6rem', // ✅ Altura fija para 2 líneas
                                overflow: 'hidden',
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical'
                            }}
                            onClick={() => handleProductClick(producto)}
                            title={producto.nombre} // ✅ Tooltip con nombre completo
                        >
                            {producto.nombre}
                        </h5>

                        {/* Descripción - altura fija */}
                        <p
                            className="text-600 text-sm mb-2"
                            style={{
                                lineHeight: '1.4',
                                height: '2.8rem', // ✅ Altura fija para 2 líneas
                                overflow: 'hidden',
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical'
                            }}
                            title={producto.descripcion} // ✅ Tooltip con descripción completa
                        >
                            {producto.descripcion}
                        </p>

                        {/* Stock info */}
                        <div
                            className="flex align-items-center p-2 border-round-md mb-3"
                            style={{
                                backgroundColor: hasStock ? `${primaryColor}08` : '#ef444408',
                                border: `1px solid ${hasStock ? primaryColor : '#ef4444'}15`
                            }}
                        >
                            <i className={`pi ${hasStock ? 'pi-check-circle' : 'pi-times-circle'} mr-1 text-xs`} style={{ color: hasStock ? primaryColor : '#ef4444' }}></i>
                            <small className="text-xs font-medium" style={{ color: hasStock ? primaryColor : '#ef4444' }}>
                                {mensajeStock}
                            </small>
                        </div>
                    </div>

                    {/* ✅ Footer siempre en la parte inferior */}
                    <div className="p-3 pt-0">
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
                                        transition: 'all 0.3s ease',
                                        opacity: authService.isEmployee() || hasStock ? 1 : 0.6,
                                        fontSize: '0.8rem',
                                        padding: '0.5rem 1rem'
                                    }}
                                    onClick={() => {
                                        if (!isAuthenticated) {
                                            handleAddToCart(producto);
                                        } else if (authService.isEmployee()) {
                                            router.push(`/products/${producto.id}/gestionar`);
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
                                        transition: 'all 0.3s ease',
                                        fontSize: '0.8rem'
                                    }}
                                    onClick={() => handleAddToCart(producto)}
                                />
                            </div>
                        )}
                    </div>
                </div>
            </Card>
        );
    };

    const renderSkeleton = () => (
        <Card className="h-full shadow-2 border-round-lg" style={{ backgroundColor: 'var(--surface-card)' }}>
            <div className="p-3">
                <Skeleton width="100%" height="200px" className="mb-3 border-round-lg" />
                <div className="flex justify-content-between align-items-center mb-2">
                    <Skeleton width="30%" height="1rem" />
                    <Skeleton width="25%" height="1rem" />
                </div>
                <Skeleton width="60%" height="1.2rem" className="mb-2" />
                <Skeleton width="100%" height="0.8rem" className="mb-1" />
                <Skeleton width="80%" height="0.8rem" className="mb-3" />
                <div className="flex justify-content-between align-items-center">
                    <Skeleton width="40%" height="1.5rem" />
                    <Skeleton width="30%" height="1.5rem" />
                </div>
            </div>
        </Card>
    );

    const renderFiltros = () => (
        <div className="p-4">
            <h3 className="text-lg font-bold mb-4" style={{ color: primaryColor }}>
                <i className="pi pi-filter mr-2"></i>
                Filtros
            </h3>

            <div className="mb-4">
                <label className="block mb-2 font-semibold text-sm text-900">Buscar productos</label>
                <span className="p-input-icon-left w-full">
                    <i className="pi pi-search" />
                    <InputText value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} placeholder="Buscar por nombre, descripción..." className="w-full" />
                </span>
            </div>

            {/* ✅ CORREGIDO: Dropdown por NOMBRE */}
            <div className="mb-4">
                <label className="block mb-2 font-semibold text-sm text-900">Categoría</label>
                <Dropdown
                    value={selectedCategoriaNombre}
                    onChange={(e) => setSelectedCategoriaNombre(e.value)}
                    options={[{ label: 'Todas las categorías', value: null }, ...categorias.map((cat) => ({ label: cat.nombre, value: cat.nombre }))]}
                    placeholder="Seleccionar categoría"
                    className="w-full"
                    showClear
                />
            </div>

            <div className="mb-4">
                <label className="block mb-2 font-semibold text-sm text-900">Empresa</label>
                <Dropdown
                    value={selectedEmpresaNombre}
                    onChange={(e) => setSelectedEmpresaNombre(e.value)}
                    options={[{ label: 'Todas las empresas', value: null }, ...empresas.map((emp) => ({ label: emp.nombre, value: emp.nombre }))]}
                    placeholder="Seleccionar empresa"
                    className="w-full"
                    showClear
                />
            </div>

            <Divider />

            <Button
                label="Limpiar Filtros"
                icon="pi pi-filter-slash"
                className="w-full p-button-outlined"
                onClick={limpiarFiltros}
                style={{
                    borderColor: primaryColor,
                    color: primaryColor,
                    backgroundColor: 'transparent',
                    transition: 'all 0.3s ease'
                }}
            />
        </div>
    );

    // ✅ NUEVO: Modal de producto
    const renderProductModal = () => {
        if (!selectedProduct) return null;

        const hasStock = productService.tieneStock(selectedProduct);
        const mensajeStock = productService.getMensajeStock(selectedProduct);

        return (
            <Dialog
                header={
                    <div className="flex align-items-center gap-3">
                        <i className="pi pi-eye text-primary text-xl"></i>
                        <span>Detalles del Producto</span>
                    </div>
                }
                visible={productModalVisible}
                onHide={() => setProductModalVisible(false)}
                style={{ width: '95vw', maxWidth: '600px' }}
                modal
                className="product-modal"
                breakpoints={{ '960px': '90vw', '641px': '95vw' }}
            >
                <div className="grid">
                    <div className="col-12 md:col-5">
                        <img src={selectedProduct.imagen || '/demo/images/product/product-placeholder.svg'} alt={selectedProduct.nombre} className="w-full border-round-lg shadow-2" style={{ maxHeight: '300px', objectFit: 'cover' }} />
                    </div>
                    <div className="col-12 md:col-7">
                        <h2 className="text-2xl font-bold mb-3 text-900">{selectedProduct.nombre}</h2>

                        <div className="flex align-items-center gap-2 mb-3">
                            <Badge value={selectedProduct.categoria.nombre} style={{ backgroundColor: `${primaryColor}20`, color: primaryColor }} />
                            <Badge value={selectedProduct.empresa.nombre} style={{ backgroundColor: primaryColor, color: 'white' }} />
                        </div>

                        <p className="text-700 line-height-3 mb-4">{selectedProduct.descripcion}</p>

                        <div
                            className="flex align-items-center p-3 border-round-md mb-4"
                            style={{
                                backgroundColor: hasStock ? `${primaryColor}08` : '#ef444408',
                                border: `1px solid ${hasStock ? primaryColor : '#ef4444'}15`
                            }}
                        >
                            <i className={`pi ${hasStock ? 'pi-check-circle' : 'pi-times-circle'} mr-2`} style={{ color: hasStock ? primaryColor : '#ef4444' }}></i>
                            <span className="font-medium" style={{ color: hasStock ? primaryColor : '#ef4444' }}>
                                {mensajeStock}
                            </span>
                        </div>

                        {!isAuthenticated || authService.canViewPrices() ? (
                            <div className="flex align-items-center justify-content-between">
                                <div className="text-3xl font-bold" style={{ color: primaryColor }}>
                                    {productService.formatearPrecio(selectedProduct.precio)}
                                </div>
                                <Button
                                    icon="pi pi-shopping-cart"
                                    label={authService.isEmployee() ? 'Gestionar' : isAuthenticated ? 'Agregar al Carrito' : 'Iniciar sesión para comprar'}
                                    disabled={isAuthenticated && !authService.isEmployee() && !hasStock}
                                    style={{
                                        backgroundColor: authService.isEmployee() || hasStock ? primaryColor : 'var(--surface-400)',
                                        borderColor: authService.isEmployee() || hasStock ? primaryColor : 'var(--surface-400)'
                                    }}
                                    onClick={() => {
                                        if (!isAuthenticated) {
                                            setProductModalVisible(false);
                                            handleAddToCart(selectedProduct);
                                        } else if (authService.isEmployee()) {
                                            router.push(`/products/${selectedProduct.id}/gestionar`);
                                        } else {
                                            handleAddToCart(selectedProduct);
                                            setProductModalVisible(false);
                                        }
                                    }}
                                />
                            </div>
                        ) : (
                            <div className="text-center">
                                <div className="text-600 mb-3">
                                    <i className="pi pi-lock mr-2" style={{ color: primaryColor }}></i>
                                    Inicia sesión para comprar
                                </div>
                                <Button
                                    label="Iniciar sesión para comprar"
                                    icon="pi pi-sign-in"
                                    className="w-full"
                                    style={{ backgroundColor: primaryColor, borderColor: primaryColor }}
                                    onClick={() => {
                                        setProductModalVisible(false);
                                        handleAddToCart(selectedProduct);
                                    }}
                                />
                            </div>
                        )}
                    </div>
                </div>
            </Dialog>
        );
    };

    return (
        <>
            <Toast ref={toast} />
            <ConfirmDialog />

            {!mounted ? (
                <div className="min-h-screen flex align-items-center justify-content-center" style={{ backgroundColor: 'var(--surface-ground)' }}>
                    <i className="pi pi-spin pi-spinner text-4xl" style={{ color: primaryColor }}></i>
                </div>
            ) : (
                <>
                    {/* ✅ CORREGIDO: Remover LandingNavbar duplicado */}

                    <div className="min-h-screen pt-4 pb-6" style={{ backgroundColor: 'var(--surface-ground)' }}>
                        <div className="container mx-auto px-4 max-w-7xl">
                            {/* Header */}
                            <div className="mb-6">
                                <div className="flex justify-content-between align-items-center flex-wrap gap-4">
                                    <div>
                                        <h1 className="text-4xl font-bold mb-2" style={{ color: 'var(--text-color)' }}>
                                            Todos los Productos
                                        </h1>
                                        <p className="text-lg" style={{ color: 'var(--text-color-secondary)' }}>
                                            {loading ? 'Cargando...' : `${productosFiltrados.length} productos disponibles`}
                                        </p>
                                    </div>

                                    <div className="flex align-items-center gap-2">
                                        <Button
                                            icon="pi pi-filter"
                                            label="Filtros"
                                            className="p-button-outlined lg:hidden"
                                            onClick={() => setSidebarVisible(true)}
                                            style={{
                                                borderColor: primaryColor,
                                                color: primaryColor
                                            }}
                                        />

                                        <Dropdown value={sortBy} onChange={(e) => setSortBy(e.value)} options={sortOptions} placeholder="Ordenar por" className="w-12rem" />
                                    </div>
                                </div>

                                {/* Filtros activos */}
                                {(selectedCategoriaNombre || selectedEmpresaNombre || searchTerm) && (
                                    <div className="flex align-items-center gap-2 mt-4 flex-wrap">
                                        <span className="text-sm font-semibold" style={{ color: 'var(--text-color)' }}>
                                            Filtros activos:
                                        </span>
                                        {searchTerm && <Chip label={`Búsqueda: "${searchTerm}"`} removable onRemove={() => setSearchTerm('')} style={{ backgroundColor: `${primaryColor}20`, color: primaryColor }} />}
                                        {selectedCategoriaNombre && (
                                            <Chip label={`Categoría: ${selectedCategoriaNombre}`} removable onRemove={() => setSelectedCategoriaNombre(null)} style={{ backgroundColor: `${primaryColor}20`, color: primaryColor }} />
                                        )}
                                        {selectedEmpresaNombre && <Chip label={`Empresa: ${selectedEmpresaNombre}`} removable onRemove={() => setSelectedEmpresaNombre(null)} style={{ backgroundColor: `${primaryColor}20`, color: primaryColor }} />}
                                    </div>
                                )}
                            </div>

                            {/* Layout principal */}
                            <div className="grid">
                                {/* Sidebar de filtros - Desktop */}
                                <div className="col-12 lg:col-3 hidden lg:block">
                                    <Card className="sticky top-4" style={{ backgroundColor: 'var(--surface-card)' }}>
                                        {renderFiltros()}
                                    </Card>
                                </div>

                                {/* Grid de productos */}
                                <div className="col-12 lg:col-9">
                                    {loading ? (
                                        <div className="grid">
                                            {Array.from({ length: 12 }).map((_, index) => (
                                                <div key={index} className="col-12 sm:col-6 lg:col-4 xl:col-3">
                                                    {renderSkeleton()}
                                                </div>
                                            ))}
                                        </div>
                                    ) : productosFiltrados.length > 0 ? (
                                        <div className="grid">
                                            {productosFiltrados.map((producto) => (
                                                <div key={producto.id} className="col-12 sm:col-6 lg:col-4 xl:col-3">
                                                    {renderProductCard(producto)}
                                                </div>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="text-center py-8">
                                            <Card className="max-w-md mx-auto" style={{ backgroundColor: 'var(--surface-card)' }}>
                                                <div className="p-6">
                                                    <i className="pi pi-search text-6xl mb-4" style={{ color: primaryColor }}></i>
                                                    <h3 className="text-xl font-bold mb-3" style={{ color: 'var(--text-color)' }}>
                                                        No se encontraron productos
                                                    </h3>
                                                    <p className="mb-4" style={{ color: 'var(--text-color-secondary)' }}>
                                                        No hay productos que coincidan con los filtros aplicados.
                                                    </p>
                                                    <Button
                                                        label="Limpiar filtros"
                                                        icon="pi pi-filter-slash"
                                                        className="p-button-outlined"
                                                        onClick={limpiarFiltros}
                                                        style={{
                                                            borderColor: primaryColor,
                                                            color: primaryColor,
                                                            backgroundColor: 'transparent',
                                                            transition: 'all 0.3s ease'
                                                        }}
                                                    />
                                                </div>
                                            </Card>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Sidebar móvil para filtros */}
                    <Sidebar
                        visible={sidebarVisible}
                        position="left"
                        onHide={() => setSidebarVisible(false)}
                        className="w-full sm:w-20rem"
                        pt={{
                            header: {
                                style: {
                                    backgroundColor: 'var(--surface-card)',
                                    borderBottom: '1px solid var(--surface-border)'
                                }
                            },
                            content: {
                                style: {
                                    backgroundColor: 'var(--surface-card)',
                                    padding: 0
                                }
                            }
                        }}
                    >
                        {renderFiltros()}
                    </Sidebar>

                    {/* ✅ NUEVO: Modal de producto */}
                    {renderProductModal()}

                    {/* Dialog para login */}
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
                            <h3 className="mb-3 font-bold" style={{ color: 'var(--text-color)' }}>
                                ¡Inicia Sesión!
                            </h3>
                            <p className="mb-4 line-height-3" style={{ color: 'var(--text-color-secondary)' }}>
                                Para agregar productos al carrito y comprar, necesitas iniciar sesión en tu cuenta.
                            </p>
                            {selectedProduct && (
                                <div
                                    className="p-3 border-round-xl mb-4"
                                    style={{
                                        backgroundColor: 'var(--surface-card)',
                                        border: `1px solid ${primaryColor}20`
                                    }}
                                >
                                    <small className="block mb-1" style={{ color: 'var(--text-color-secondary)' }}>
                                        Producto seleccionado:
                                    </small>
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

                    {/* Estilos CSS adicionales */}
                    <style jsx>{`
                        .product-card {
                            min-height: 420px;
                        }

                        .product-card .p-card-content {
                            padding: 0;
                        }

                        .product-modal .p-dialog-content {
                            padding: 1.5rem;
                        }

                        .product-modal .p-dialog-header {
                            background: linear-gradient(135deg, var(--primary-color) 0%, var(--pink-500) 100%);
                            color: white;
                        }

                        .product-modal .p-dialog-header .p-dialog-title {
                            color: white;
                        }

                        .product-modal .p-dialog-header-icon {
                            color: white;
                        }
                    `}</style>
                </>
            )}
        </>
    );
};

export default ProductosPage;
