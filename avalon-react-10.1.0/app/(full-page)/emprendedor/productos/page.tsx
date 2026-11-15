'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { DataTable, DataTablePageEvent, DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { Dialog } from 'primereact/dialog';
import { InputText } from 'primereact/inputtext';
import { Badge } from 'primereact/badge';
import { Image } from 'primereact/image';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import emprendedorService, { ProductoEmprendedor, Categoria, Empresa } from '../../../../services/emprendedorService';
import authService from '../../../../services/authService';
import { toApiSortParam } from '../../../../services/apiConfig';

const ProductosPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados principales
    const [productos, setProductos] = useState<ProductoEmprendedor[]>([]);
    const [categorias, setCategorias] = useState<Categoria[]>([]);
    const [empresas, setEmpresas] = useState<Empresa[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [debouncedSearch, setDebouncedSearch] = useState<string>('');
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('id');
    const [sortOrder, setSortOrder] = useState<1 | -1>(-1);

    // Estados para el modal de detalles
    const [selectedProducto, setSelectedProducto] = useState<ProductoEmprendedor | null>(null);
    const [productDetailVisible, setProductDetailVisible] = useState(false);

    // ✅ FUNCIÓN PARA FORMATEAR FECHAS
    const formatearFecha = (fecha: string): string => {
        try {
            if (!fecha) return 'Fecha no disponible';
            const date = new Date(fecha);
            if (isNaN(date.getTime())) return 'Fecha inválida';

            return date.toLocaleDateString('es-ES', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (error) {
            console.error('Error formateando fecha:', error);
            return 'Fecha inválida';
        }
    };

    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm.trim());
            setFirst(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    // Cargar categorías, empresas y escuchar eventos de stock
    useEffect(() => {
        Promise.all([emprendedorService.obtenerCategorias(), emprendedorService.obtenerEmpresas()])
            .then(([categoriasRes, empresasRes]) => {
                if (categoriasRes.success && categoriasRes.data) setCategorias(categoriasRes.data);
                if (empresasRes.success && empresasRes.data) setEmpresas(empresasRes.data);
            })
            .catch(() => {});

        const handleStockUpdate = (event: CustomEvent) => {
            const { productoId, stock } = event.detail;
            setProductos((prevProductos) =>
                prevProductos.map((producto) =>
                    producto.id === productoId
                        ? {
                              ...producto,
                              inventario: {
                                  ...producto.inventario,
                                  id: producto.inventario?.id || 0,
                                  cantidad: stock,
                                  ubicacion: stock > 0 ? 'Disponible' : 'No disponible'
                              }
                          }
                        : producto
                )
            );
        };

        window.addEventListener('stockUpdatedEmprendedor', handleStockUpdate as EventListener);
        return () => {
            window.removeEventListener('stockUpdatedEmprendedor', handleStockUpdate as EventListener);
        };
    }, []);

    useEffect(() => {
        cargarDatos();
    }, [first, rows, debouncedSearch, sortField, sortOrder]);

    const cargarDatos = async () => {
        setLoading(true);
        try {
            const page = Math.floor(first / rows);
            const productosRes = await emprendedorService.obtenerProductosEmprendedorPaginado({
                page,
                size: rows,
                sort: toApiSortParam(sortField, sortOrder, 'id,desc'),
                q: debouncedSearch || undefined
            });

            if (productosRes.success && productosRes.data) {
                const productosNormalizados = (productosRes.data.content || []).map((producto) => ({
                    ...producto,
                    nombre: producto.nombre || '',
                    descripcion: producto.descripcion || '',
                    categoria: {
                        ...producto.categoria,
                        nombre: producto.categoria?.nombre || 'Sin categoría'
                    },
                    empresa: {
                        ...producto.empresa,
                        nombre: producto.empresa?.nombre || 'Sin empresa'
                    },
                    inventario: producto.inventario || { id: 0, cantidad: 0, ubicacion: 'Sin stock' }
                }));

                setProductos(productosNormalizados);
                setTotalRecords(productosRes.data.totalElements ?? 0);
            } else {
                if (productosRes.status === 401) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'Sesión expirada (401)',
                        detail: productosRes.message || 'Por favor inicia sesión nuevamente.',
                        life: 3000
                    });
                    setTimeout(() => router.push('/auth/login2'), 1500);
                    return;
                }
                toast.current?.show({
                    severity: productosRes.status === 403 ? 'warn' : 'error',
                    summary: productosRes.status === 403 ? 'Acceso restringido (403)' : 'Error',
                    detail: productosRes.message || 'Error cargando productos',
                    life: 4000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando datos:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error de conexión con el servidor',
                life: 3000
            });
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchTerm(e.target.value);
    };

    const onPage = (event: DataTablePageEvent) => {
        setFirst(event.first);
        setRows(event.rows);
    };

    const onSort = (event: DataTableSortEvent) => {
        setSortField(event.sortField || 'id');
        setSortOrder((event.sortOrder || -1) as 1 | -1);
        setFirst(0);
    };

    // Confirmar eliminación
    const confirmarEliminacion = (producto: ProductoEmprendedor) => {
        confirmDialog({
            message: `¿Estás seguro de que deseas eliminar el producto "${producto.nombre}"?`,
            header: 'Confirmar Eliminación',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-danger',
            acceptLabel: 'Sí, eliminar',
            rejectLabel: 'Cancelar',
            accept: () => eliminarProducto(producto.id)
        });
    };

    // Eliminar producto
    const eliminarProducto = async (id: number) => {
        try {
            const response = await emprendedorService.eliminarProducto(id);

            if (response.success) {
                cargarDatos();
                toast.current?.show({
                    severity: 'success',
                    summary: 'Éxito',
                    detail: response.message,
                    life: 3000
                });
            } else {
                if (response.status === 401) {
                    toast.current?.show({
                        severity: 'warn',
                        summary: 'No autenticado (401)',
                        detail: response.message || 'Tu sesión ha expirado. Inicia sesión nuevamente.',
                        life: 3500
                    });
                    setTimeout(() => router.push('/auth/login2'), 1500);
                    return;
                }
                toast.current?.show({
                    severity: response.status === 403 ? 'warn' : 'error',
                    summary: response.status === 403 ? 'Acceso denegado (403)' : 'Error',
                    detail: response.message,
                    life: 4000
                });
            }
        } catch (error) {
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error eliminando producto',
                life: 3000
            });
        }
    };

    // Ver detalles del producto
    const verDetalles = (producto: ProductoEmprendedor) => {
        setSelectedProducto(producto);
        setProductDetailVisible(true);
    };

    // Templates para las columnas
    const imageBodyTemplate = (rowData: ProductoEmprendedor) => {
        return (
            <div className="flex align-items-center justify-content-center">
                <Image src={rowData.imagen || '/demo/images/product/product-placeholder.svg'} alt={rowData.nombre} width="60" height="60" className="border-round-lg shadow-2" preview />
            </div>
        );
    };

    const priceBodyTemplate = (rowData: ProductoEmprendedor) => {
        return <span className="font-bold text-primary">{emprendedorService.formatearPrecio(rowData.precio)}</span>;
    };

    const statusBodyTemplate = (rowData: ProductoEmprendedor) => {
        return <Badge value={rowData.activo ? 'Activo' : 'Inactivo'} severity={rowData.activo ? 'success' : 'danger'} />;
    };

    const stockBodyTemplate = (rowData: ProductoEmprendedor) => {
        const cantidad = rowData.inventario?.cantidad || 0;
        let severity: 'success' | 'warning' | 'danger' = 'success';

        if (cantidad === 0) severity = 'danger';
        else if (cantidad <= 5) severity = 'warning';

        return (
            <div className="flex align-items-center gap-2">
                <Badge value={cantidad.toString()} severity={severity} />
                <span className="text-sm text-600">unidades</span>
            </div>
        );
    };

    const actionBodyTemplate = (rowData: ProductoEmprendedor) => {
        const canManage = authService.canManageEmpresa(rowData.empresa?.id);
        return (
            <div className="flex gap-2">
                <Button icon="pi pi-eye" className="p-button-rounded p-button-info p-button-sm" onClick={() => verDetalles(rowData)} tooltip="Ver detalles" />
                {canManage && (
                    <>
                        <Button icon="pi pi-pencil" className="p-button-rounded p-button-success p-button-sm" onClick={() => router.push(`/emprendedor/productos/${rowData.id}/editar`)} tooltip="Editar" />
                        <Button icon="pi pi-trash" className="p-button-rounded p-button-danger p-button-sm" onClick={() => confirmarEliminacion(rowData)} tooltip="Eliminar" />
                    </>
                )}
            </div>
        );
    };

    // Toolbar del listado
    const leftToolbarTemplate = () => {
        const canCreate = authService.isAdmin() || !!authService.getEmpresaId();
        return (
            <div className="flex align-items-center gap-2">
                {canCreate && (
                    <Button label="Nuevo Producto" icon="pi pi-plus" className="p-button-success" onClick={() => router.push('/emprendedor/productos/crear')} />
                )}
                <Button label="Actualizar" icon="pi pi-refresh" className="p-button-outlined" onClick={cargarDatos} />
            </div>
        );
    };

    const rightToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <span className="p-input-icon-left">
                    <i className="pi pi-search" />
                    <InputText value={searchTerm} onChange={handleSearch} placeholder="Buscar productos..." className="w-20rem" />
                </span>
            </div>
        );
    };

    // Renderizar esqueleto mientras carga
    const renderSkeleton = () => (
        <div className="grid">
            {Array.from({ length: 8 }).map((_, index) => (
                <div key={index} className="col-12">
                    <Skeleton width="100%" height="4rem" className="mb-2" />
                </div>
            ))}
        </div>
    );

    return (
        <div className="emprendedor-productos">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="mb-4">
                <h1 className="text-3xl font-bold text-900 mb-2">
                    <i className="pi pi-box text-primary mr-3"></i>
                    Gestión de Productos
                </h1>
                <p className="text-600 text-lg">Administra el catálogo de productos de tu negocio</p>
            </div>

            {/* Contenido principal */}
            <Card>
                <Toolbar className="mb-4" left={leftToolbarTemplate} right={rightToolbarTemplate} />

                {loading ? (
                    renderSkeleton()
                ) : (
                    <DataTable
                        value={productos}
                        lazy
                        paginator
                        first={first}
                        rows={rows}
                        totalRecords={totalRecords}
                        onPage={onPage}
                        onSort={onSort}
                        sortField={sortField}
                        sortOrder={sortOrder}
                        rowsPerPageOptions={[5, 10, 25, 50]}
                        emptyMessage="No se encontraron productos"
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} productos"
                    >
                        <Column field="imagen" header="Imagen" body={imageBodyTemplate} style={{ width: '100px' }} sortable={false} />
                        <Column field="nombre" header="Nombre" sortable className="font-bold" />
                        <Column field="categoria.nombre" header="Categoría" />
                        <Column field="empresa.nombre" header="Empresa" />
                        <Column field="precio" header="Precio" body={priceBodyTemplate} sortable />
                        <Column field="inventario.cantidad" header="Stock" body={stockBodyTemplate} />
                        <Column field="activo" header="Estado" body={statusBodyTemplate} sortable />
                        <Column body={actionBodyTemplate} header="Acciones" style={{ width: '150px' }} sortable={false} />
                    </DataTable>
                )}
            </Card>

            {/* Modal de detalles del producto */}
            <Dialog header="Detalles del Producto" visible={productDetailVisible} style={{ width: '600px' }} modal onHide={() => setProductDetailVisible(false)}>
                {selectedProducto && (
                    <div className="grid">
                        <div className="col-12 md:col-4">
                            <Image src={selectedProducto.imagen || '/demo/images/product/product-placeholder.svg'} alt={selectedProducto.nombre} width="100%" className="border-round-lg shadow-2" preview />
                        </div>
                        <div className="col-12 md:col-8">
                            <h3 className="text-2xl font-bold text-900 mb-3">{selectedProducto.nombre}</h3>

                            <div className="mb-3">
                                <strong>Descripción:</strong>
                                <p className="mt-1 text-600">{selectedProducto.descripcion}</p>
                            </div>

                            <div className="grid">
                                <div className="col-6">
                                    <strong>Precio:</strong>
                                    <div className="text-primary font-bold text-xl">{emprendedorService.formatearPrecio(selectedProducto.precio)}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Stock:</strong>
                                    <div className="font-bold">{selectedProducto.inventario?.cantidad || 0} unidades</div>
                                </div>
                                <div className="col-6">
                                    <strong>Categoría:</strong>
                                    <div>{selectedProducto.categoria.nombre}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Empresa:</strong>
                                    <div>{selectedProducto.empresa.nombre}</div>
                                </div>
                                <div className="col-6">
                                    <strong>Estado:</strong>
                                    <div>
                                        <Badge value={selectedProducto.activo ? 'Activo' : 'Inactivo'} severity={selectedProducto.activo ? 'success' : 'danger'} />
                                    </div>
                                </div>
                                <div className="col-6">
                                    <strong>Creado:</strong>
                                    <div className="text-600">{formatearFecha(selectedProducto.fechaCreacion)}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </Dialog>
        </div>
    );
};

export default ProductosPage;
