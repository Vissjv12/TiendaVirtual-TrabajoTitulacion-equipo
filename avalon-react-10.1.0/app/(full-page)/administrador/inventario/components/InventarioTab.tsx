'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { DataTable, DataTablePageEvent, DataTableSortEvent } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Badge } from 'primereact/badge';
import { Image } from 'primereact/image';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { confirmDialog } from 'primereact/confirmdialog';
import { Toast } from 'primereact/toast';
import emprendedorService, { ProductoEmprendedor } from '../../../../../services/emprendedorService';
import { toApiSortParam } from '../../../../../services/apiConfig';

interface InventarioTabProps {
    toast: React.RefObject<Toast>;
}

const InventarioTab: React.FC<InventarioTabProps> = ({ toast }) => {
    const router = useRouter();
    
    const [productos, setProductos] = useState<ProductoEmprendedor[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [debouncedSearch, setDebouncedSearch] = useState<string>('');
    const [first, setFirst] = useState<number>(0);
    const [rows, setRows] = useState<number>(10);
    const [totalRecords, setTotalRecords] = useState<number>(0);
    const [sortField, setSortField] = useState<string>('id');
    const [sortOrder, setSortOrder] = useState<1 | -1>(-1);

    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm.trim());
            setFirst(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    useEffect(() => {
        const handleStockUpdate = (event: CustomEvent) => {
            const { productoId, stock } = event.detail;
            setProductos(prevProductos =>
                prevProductos.map(producto =>
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
                const productosNormalizados = (productosRes.data.content || []).map(producto => ({
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
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: productosRes.message || 'Error cargando productos',
                    life: 3000
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

    // ✅ EXACTAMENTE LOS MISMOS TEMPLATES QUE PRODUCTOS
    const imageBodyTemplate = (rowData: ProductoEmprendedor) => {
        return (
            <div className="flex align-items-center justify-content-center">
                <Image 
                    src={rowData.imagen || 'https://via.placeholder.com/60x60?text=Producto'} 
                    alt={rowData.nombre}
                    width="60" 
                    height="60"
                    className="border-round-lg shadow-2"
                    preview
                />
            </div>
        );
    };

    // ✅ EXACTAMENTE EL MISMO stockBodyTemplate QUE PRODUCTOS
    const stockBodyTemplate = (rowData: ProductoEmprendedor) => {
        const cantidad = rowData.inventario?.cantidad || 0;
        let severity: "success" | "warning" | "danger" = 'success';
        
        if (cantidad === 0) severity = 'danger';
        else if (cantidad <= 5) severity = 'warning';
        
        return (
            <div className="flex align-items-center gap-2">
                <Badge value={cantidad.toString()} severity={severity} />
                <span className="text-sm text-600">unidades</span>
            </div>
        );
    };

    const ubicacionBodyTemplate = (rowData: ProductoEmprendedor) => {
        const ubicacion = rowData.inventario?.ubicacion || 'Sin ubicación';
        const cantidad = rowData.inventario?.cantidad || 0;
        
        return (
            <span className={`text-sm ${cantidad > 0 ? 'text-600' : 'text-red-500'}`}>
                {ubicacion}
            </span>
        );
    };

    const actionBodyTemplate = (rowData: ProductoEmprendedor) => {
        return (
            <div className="flex gap-2">
                <Button 
                    icon="pi pi-pencil" 
                    className="p-button-rounded p-button-warning p-button-sm" 
                    onClick={() => {
                        console.log('🔄 Navegando a editar inventario producto:', rowData.id);
                        router.push(`/emprendedor/inventario/${rowData.id}/editar`);
                    }}
                    tooltip="Editar inventario"
                />
                <Button 
                    icon="pi pi-plus" 
                    className="p-button-rounded p-button-success p-button-sm" 
                    onClick={() => {
                        console.log('🔄 Navegando a ajuste rápido producto:', rowData.id);
                        router.push(`/emprendedor/inventario/ajustar?producto=${rowData.id}`);
                    }}
                    tooltip="Ajuste rápido"
                />
            </div>
        );
    };

    // ✅ EXACTAMENTE LOS MISMOS TOOLBARS QUE PRODUCTOS
    const leftToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <Button 
                    label="Nuevo Inventario" 
                    icon="pi pi-plus" 
                    className="p-button-success"
                    onClick={() => router.push('/emprendedor/inventario/crear')}
                />
                <Button 
                    label="Actualizar" 
                    icon="pi pi-refresh" 
                    className="p-button-outlined"
                    onClick={cargarDatos}
                />
                <Button 
                    label="Ajuste Masivo" 
                    icon="pi pi-cog" 
                    className="p-button-warning"
                    onClick={() => router.push('/emprendedor/inventario/ajustar')}
                />
            </div>
        );
    };

    const rightToolbarTemplate = () => {
        return (
            <div className="flex align-items-center gap-2">
                <span className="p-input-icon-left">
                    <i className="pi pi-search" />
                    <InputText 
                        value={searchTerm} 
                        onChange={handleSearch}
                        placeholder="Buscar en inventario..." 
                        className="w-20rem"
                    />
                </span>
            </div>
        );
    };

    // ✅ EXACTAMENTE EL MISMO SKELETON QUE PRODUCTOS
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
        <div className="inventario-tab">
            {/* ✅ ESTADÍSTICAS RÁPIDAS */}
            <div className="grid mb-4">
                <div className="col-12 md:col-3">
                    <div className="bg-blue-50 border-round p-3 text-center">
                        <div className="text-blue-600 font-bold text-xl">
                            {totalRecords}
                        </div>
                        <div className="text-600 text-sm">Total Productos</div>
                    </div>
                </div>
                <div className="col-12 md:col-3">
                    <div className="bg-green-50 border-round p-3 text-center">
                        <div className="text-green-600 font-bold text-xl">
                            {productos.filter(p => {
                                const stock = p.inventario?.cantidad || 0;
                                return stock > 5;
                            }).length}
                        </div>
                        <div className="text-600 text-sm">Stock Suficiente (pág.)</div>
                    </div>
                </div>
                <div className="col-12 md:col-3">
                    <div className="bg-orange-50 border-round p-3 text-center">
                        <div className="text-orange-600 font-bold text-xl">
                            {productos.filter(p => {
                                const stock = p.inventario?.cantidad || 0;
                                return stock > 0 && stock <= 5;
                            }).length}
                        </div>
                        <div className="text-600 text-sm">Stock Bajo (pág.)</div>
                    </div>
                </div>
                <div className="col-12 md:col-3">
                    <div className="bg-red-50 border-round p-3 text-center">
                        <div className="text-red-600 font-bold text-xl">
                            {productos.filter(p => {
                                const stock = p.inventario?.cantidad || 0;
                                return stock === 0;
                            }).length}
                        </div>
                        <div className="text-600 text-sm">Sin Stock (pág.)</div>
                    </div>
                </div>
            </div>

            {/* ✅ TOOLBAR */}
            <Toolbar 
                className="mb-4" 
                left={leftToolbarTemplate} 
                right={rightToolbarTemplate}
            />

            {/* ✅ TABLA DE INVENTARIO */}
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
                    emptyMessage="No se encontraron productos en inventario"
                    responsiveLayout="scroll"
                    className="p-datatable-sm"
                    paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                    currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} productos"
                >
                    <Column 
                        field="imagen" 
                        header="Imagen" 
                        body={imageBodyTemplate} 
                        style={{ width: '100px' }}
                        sortable={false}
                    />
                    <Column 
                        field="nombre" 
                        header="Producto" 
                        sortable 
                        className="font-bold"
                    />
                    <Column 
                        field="categoria.nombre" 
                        header="Categoría" 
                    />
                    <Column 
                        field="inventario.cantidad" 
                        header="Stock Actual" 
                        body={stockBodyTemplate}
                    />
                    <Column 
                        field="inventario.ubicacion" 
                        header="Ubicación/Estado" 
                        body={ubicacionBodyTemplate}
                    />
                    <Column 
                        body={actionBodyTemplate} 
                        header="Acciones"
                        style={{ width: '120px' }}
                        sortable={false}
                    />
                </DataTable>
            )}
        </div>
    );
};

export default InventarioTab;