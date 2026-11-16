'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { Toast } from 'primereact/toast';
import { InputNumber } from 'primereact/inputnumber';
import { InputText } from 'primereact/inputtext';
import { Dropdown } from 'primereact/dropdown';
import { Badge } from 'primereact/badge';
import { Image } from 'primereact/image';
import { Skeleton } from 'primereact/skeleton';
import { Toolbar } from 'primereact/toolbar';
import { InputTextarea } from 'primereact/inputtextarea';
import { Divider } from 'primereact/divider';
import { ProgressSpinner } from 'primereact/progressspinner';
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog';
import inventarioService, { Inventario, SalidaInventarioLote } from '../../../../../services/inventarioService';
import emprendedorService from '../../../../../services/emprendedorService';

interface AjusteItem extends Inventario {
    nuevaCantidad: number;
    seleccionado: boolean;
}

const AjustarInventarioPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);

    // Estados principales
    const [inventarios, setInventarios] = useState<AjusteItem[]>([]);
    const [inventariosFiltrados, setInventariosFiltrados] = useState<AjusteItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [procesando, setProcesando] = useState(false);
    const [searchTerm, setSearchTerm] = useState<string>('');

    // Estados para ajuste masivo
    const [tipoAjuste, setTipoAjuste] = useState<'INDIVIDUAL' | 'MASIVO'>('INDIVIDUAL');
    const [motivoGeneral, setMotivoGeneral] = useState('');
    const [ajusteMasivo, setAjusteMasivo] = useState({
        operacion: 'SUMA', // SUMA, RESTA, ESTABLECER
        valor: 0
    });

    // Opciones para operaciones masivas
    const operacionesDisponibles = [
        { label: 'Sumar cantidad', value: 'SUMA' },
        { label: 'Restar cantidad', value: 'RESTA' },
        { label: 'Establecer cantidad', value: 'ESTABLECER' }
    ];

    // Cargar datos iniciales
    useEffect(() => {
        cargarInventarios();
    }, []);

    // Filtrar inventarios cuando cambie el término de búsqueda
    useEffect(() => {
        aplicarFiltros();
    }, [inventarios, searchTerm]);

    const cargarInventarios = async () => {
        setLoading(true);
        try {
            console.log('🔄 Cargando inventarios para ajuste...');
            const [response, productosRes] = await Promise.all([
                inventarioService.obtenerInventarios(),
                emprendedorService.obtenerProductosEmprendedor()
            ]);

            if (response.success && response.data) {
                const idsPermitidos = new Set(
                    (productosRes.data || []).map(p => Number(p.id))
                );

                const inventariosAutorizados = response.data.filter(inv =>
                    idsPermitidos.has(Number(inv.producto?.id))
                );

                const inventariosConAjuste: AjusteItem[] = inventariosAutorizados.map(inv => ({
                    ...inv,
                    nuevaCantidad: inv.cantidad,
                    seleccionado: false
                }));
                
                setInventarios(inventariosConAjuste);
                console.log('✅ Inventarios autorizados cargados:', inventariosConAjuste.length);
            } else {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Error',
                    detail: response.message || 'Error cargando inventarios',
                    life: 3000
                });
            }
        } catch (error) {
            console.error('❌ Error cargando inventarios:', error);
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

    const aplicarFiltros = () => {
        if (!searchTerm.trim()) {
            setInventariosFiltrados(inventarios);
            return;
        }
        
        const filtered = inventarios.filter(inventario => 
            inventario.producto?.nombre?.toLowerCase().includes(searchTerm.toLowerCase()) ||
            inventario.ubicacion?.toLowerCase().includes(searchTerm.toLowerCase())
        );
        
        setInventariosFiltrados(filtered);
    };

    // Seleccionar/deseleccionar todos
    const toggleSelectAll = () => {
        const todosSeleccionados = inventariosFiltrados.every(inv => inv.seleccionado);
        const nuevosInventarios = inventarios.map(inv => {
            const enFiltro = inventariosFiltrados.find(filtrado => filtrado.id === inv.id);
            return enFiltro ? { ...inv, seleccionado: !todosSeleccionados } : inv;
        });
        setInventarios(nuevosInventarios);
    };

    // Seleccionar individual
    const toggleSelectItem = (id: number) => {
        const nuevosInventarios = inventarios.map(inv => 
            inv.id === id ? { ...inv, seleccionado: !inv.seleccionado } : inv
        );
        setInventarios(nuevosInventarios);
    };

    // Actualizar cantidad individual
    const actualizarCantidadIndividual = (id: number, nuevaCantidad: number) => {
        const nuevosInventarios = inventarios.map(inv => 
            inv.id === id ? { ...inv, nuevaCantidad: Math.max(0, nuevaCantidad) } : inv
        );
        setInventarios(nuevosInventarios);
    };

    // Aplicar ajuste masivo
    const aplicarAjusteMasivo = () => {
        const inventariosSeleccionados = inventarios.filter(inv => inv.seleccionado);
        
        if (inventariosSeleccionados.length === 0) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Advertencia',
                detail: 'Selecciona al menos un producto para aplicar ajuste masivo',
                life: 3000
            });
            return;
        }

        const nuevosInventarios = inventarios.map(inv => {
            if (!inv.seleccionado) return inv;
            
            let nuevaCantidad = inv.cantidad;
            
            switch (ajusteMasivo.operacion) {
                case 'SUMA':
                    nuevaCantidad = inv.cantidad + ajusteMasivo.valor;
                    break;
                case 'RESTA':
                    nuevaCantidad = Math.max(0, inv.cantidad - ajusteMasivo.valor);
                    break;
                case 'ESTABLECER':
                    nuevaCantidad = ajusteMasivo.valor;
                    break;
            }
            
            return { ...inv, nuevaCantidad: Math.max(0, nuevaCantidad) };
        });
        
        setInventarios(nuevosInventarios);
        
        toast.current?.show({
            severity: 'success',
            summary: 'Ajuste Aplicado',
            detail: `Ajuste masivo aplicado a ${inventariosSeleccionados.length} productos`,
            life: 3000
        });
    };

    // Confirmar y procesar ajustes
    const confirmarAjustes = () => {
        const productosConCambios = inventarios.filter(inv => inv.cantidad !== inv.nuevaCantidad);
        
        if (productosConCambios.length === 0) {
            toast.current?.show({
                severity: 'warn',
                summary: 'Sin Cambios',
                detail: 'No hay cambios para procesar',
                life: 3000
            });
            return;
        }

        confirmDialog({
            message: `¿Confirmas el ajuste de stock para ${productosConCambios.length} producto(s)?`,
            header: 'Confirmar Ajustes de Stock',
            icon: 'pi pi-exclamation-triangle',
            acceptClassName: 'p-button-success',
            acceptLabel: 'Sí, procesar ajustes',
            rejectLabel: 'Cancelar',
            accept: procesarAjustes
        });
    };

    // Procesar todos los ajustes
    const procesarAjustes = async () => {
        setProcesando(true);
        try {
            const productosConCambios = inventarios.filter(inv => inv.cantidad !== inv.nuevaCantidad);
            let exitosos = 0;
            let errores = 0;

            for (const inventario of productosConCambios) {
                try {
                    const response = await inventarioService.actualizarStock(
                        inventario.producto.id,
                        inventario.nuevaCantidad,
                        'AJUSTE',
                        motivoGeneral || 'Ajuste masivo de inventario'
                    );
                    
                    if (response.success) {
                        exitosos++;
                    } else {
                        errores++;
                    }
                } catch (error) {
                    errores++;
                }
            }

            if (exitosos > 0) {
                toast.current?.show({
                    severity: 'success',
                    summary: 'Ajustes Procesados',
                    detail: `${exitosos} ajuste(s) procesado(s) exitosamente`,
                    life: 5000
                });
            }

            if (errores > 0) {
                toast.current?.show({
                    severity: 'error',
                    summary: 'Errores en Procesamiento',
                    detail: `${errores} ajuste(s) fallaron`,
                    life: 5000
                });
            }

            // Recargar datos
            cargarInventarios();
            
        } catch (error) {
            console.error('❌ Error procesando ajustes:', error);
            toast.current?.show({
                severity: 'error',
                summary: 'Error',
                detail: 'Error procesando los ajustes',
                life: 3000
            });
        } finally {
            setProcesando(false);
        }
    };

    // Cancelar y volver
    const handleCancel = () => {
        router.push('/emprendedor/inventario');
    };

    // Templates para las columnas
    const checkboxTemplate = (rowData: AjusteItem) => {
        return (
            <input
                type="checkbox"
                checked={rowData.seleccionado}
                onChange={() => toggleSelectItem(rowData.id)}
                className="p-checkbox"
            />
        );
    };

    const imageBodyTemplate = (rowData: AjusteItem) => {
        return (
            <div className="flex align-items-center justify-content-center">
                <Image 
                    src={rowData.producto?.imagen || 'https://via.placeholder.com/50x50?text=P'} 
                    alt={rowData.producto?.nombre}
                    width="50" 
                    height="50"
                    className="border-round shadow-2"
                />
            </div>
        );
    };

    const stockActualTemplate = (rowData: AjusteItem) => {
        const cantidad = rowData.cantidad || 0;
        let severity: "success" | "warning" | "danger" = 'success';
        
        if (cantidad === 0) severity = 'danger';
        else if (cantidad <= 5) severity = 'warning';
        
        return (
            <Badge value={cantidad.toString()} severity={severity} />
        );
    };

    const nuevaCantidadTemplate = (rowData: AjusteItem) => {
        return (
            <InputNumber
                value={rowData.nuevaCantidad}
                onValueChange={(e) => actualizarCantidadIndividual(rowData.id, e.value || 0)}
                min={0}
                className="w-full"
                size={1}
                showButtons
                buttonLayout="horizontal"
                style={{ maxWidth: '120px' }}
            />
        );
    };

    const cambioTemplate = (rowData: AjusteItem) => {
        const cambio = rowData.nuevaCantidad - rowData.cantidad;
        if (cambio === 0) return <span className="text-600">Sin cambio</span>;
        
        return (
            <span className={`font-bold ${cambio > 0 ? 'text-green-600' : 'text-red-600'}`}>
                {cambio > 0 ? '+' : ''}{cambio}
            </span>
        );
    };

    // Toolbar
    const leftToolbarTemplate = () => {
        const seleccionados = inventarios.filter(inv => inv.seleccionado).length;
        const conCambios = inventarios.filter(inv => inv.cantidad !== inv.nuevaCantidad).length;
        
        return (
            <div className="flex align-items-center gap-2">
                <Button 
                    label={inventariosFiltrados.every(inv => inv.seleccionado) ? 'Deseleccionar Todo' : 'Seleccionar Todo'}
                    icon={inventariosFiltrados.every(inv => inv.seleccionado) ? 'pi pi-times' : 'pi pi-check'}
                    className="p-button-outlined"
                    onClick={toggleSelectAll}
                />
                <Badge value={seleccionados} severity="info" className="mr-2" />
                <span className="text-600">seleccionados</span>
                {conCambios > 0 && (
                    <>
                        <Divider layout="vertical" />
                        <Badge value={conCambios} severity="warning" className="mr-2" />
                        <span className="text-600">con cambios</span>
                    </>
                )}
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
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Buscar productos..." 
                        className="w-15rem"
                    />
                </span>
            </div>
        );
    };

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
        <div className="ajustar-inventario">
            <Toast ref={toast} />
            <ConfirmDialog />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center gap-2 mb-2">
                    <Button
                        icon="pi pi-arrow-left"
                        className="p-button-text p-button-rounded"
                        onClick={handleCancel}
                        tooltip="Volver al inventario"
                    />
                    <h1 className="text-3xl font-bold text-900 m-0">
                        <i className="pi pi-cog text-primary mr-3"></i>
                        Ajuste Masivo de Stock
                    </h1>
                </div>
                <p className="text-600 text-lg ml-6">
                    Modifica las cantidades de múltiples productos de forma simultánea
                </p>
            </div>

            {/* Panel de control de ajuste masivo */}
            <Card className="mb-4">
                <h3 className="text-xl font-bold mb-3">
                    <i className="pi pi-sliders-h text-primary mr-2"></i>
                    Ajuste Masivo
                </h3>
                
                <div className="grid">
                    <div className="col-12 md:col-3">
                        <label className="block text-900 font-medium mb-2">Operación</label>
                        <Dropdown
                            value={ajusteMasivo.operacion}
                            options={operacionesDisponibles}
                            onChange={(e) => setAjusteMasivo(prev => ({ ...prev, operacion: e.value }))}
                            className="w-full"
                        />
                    </div>
                    
                    <div className="col-12 md:col-2">
                        <label className="block text-900 font-medium mb-2">Valor</label>
                        <InputNumber
                            value={ajusteMasivo.valor}
                            onValueChange={(e) => setAjusteMasivo(prev => ({ ...prev, valor: e.value || 0 }))}
                            min={0}
                            className="w-full"
                        />
                    </div>
                    
                    <div className="col-12 md:col-5">
                        <label className="block text-900 font-medium mb-2">Motivo General</label>
                        <InputText
                            value={motivoGeneral}
                            onChange={(e) => setMotivoGeneral(e.target.value)}
                            placeholder="Razón del ajuste..."
                            className="w-full"
                        />
                    </div>
                    
                    <div className="col-12 md:col-2">
                        <label className="block text-900 font-medium mb-2">&nbsp;</label>
                        <Button
                            label="Aplicar"
                            icon="pi pi-check"
                            className="p-button-warning w-full"
                            onClick={aplicarAjusteMasivo}
                        />
                    </div>
                </div>
            </Card>

            {/* Tabla de inventarios */}
            <Card>
                <Toolbar 
                    className="mb-4" 
                    left={leftToolbarTemplate} 
                    right={rightToolbarTemplate}
                />

                {loading ? (
                    renderSkeleton()
                ) : (
                    <DataTable 
                        value={inventariosFiltrados}
                        paginator
                        rows={15}
                        rowsPerPageOptions={[10, 15, 25, 50]}
                        emptyMessage="No se encontraron productos"
                        responsiveLayout="scroll"
                        className="p-datatable-sm"
                        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
                        currentPageReportTemplate="Mostrando {first} a {last} de {totalRecords} productos"
                    >
                        <Column 
                            body={checkboxTemplate}
                            header="Sel."
                            style={{ width: '60px' }}
                        />
                        <Column 
                            field="producto.imagen" 
                            header="Imagen" 
                            body={imageBodyTemplate} 
                            style={{ width: '80px' }}
                        />
                        <Column 
                            field="producto.nombre" 
                            header="Producto" 
                            sortable 
                            className="font-bold"
                        />
                        <Column 
                            field="ubicacion" 
                            header="Ubicación" 
                            sortable
                        />
                        <Column 
                            field="cantidad" 
                            header="Stock Actual" 
                            body={stockActualTemplate}
                            sortable
                            style={{ width: '120px' }}
                        />
                        <Column 
                            field="nuevaCantidad" 
                            header="Nueva Cantidad" 
                            body={nuevaCantidadTemplate}
                            style={{ width: '150px' }}
                        />
                        <Column 
                            header="Cambio" 
                            body={cambioTemplate}
                            style={{ width: '100px' }}
                        />
                    </DataTable>
                )}

                {/* Botones de acción */}
                <div className="flex justify-content-end gap-2 mt-4">
                    <Button
                        label="Cancelar"
                        icon="pi pi-times"
                        className="p-button-outlined"
                        onClick={handleCancel}
                        disabled={procesando}
                    />
                    <Button
                        label={procesando ? 'Procesando...' : 'Procesar Ajustes'}
                        icon={procesando ? undefined : 'pi pi-save'}
                        className="p-button-success"
                        onClick={confirmarAjustes}
                        disabled={procesando || inventarios.every(inv => inv.cantidad === inv.nuevaCantidad)}
                    >
                        {procesando && <ProgressSpinner style={{ width: '20px', height: '20px' }} className="mr-2" />}
                    </Button>
                </div>
            </Card>
        </div>
    );
};

export default AjustarInventarioPage;