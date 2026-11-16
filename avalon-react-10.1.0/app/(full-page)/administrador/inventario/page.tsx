'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { TabView, TabPanel } from 'primereact/tabview';
import { Toast } from 'primereact/toast';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import inventarioService from '../../../../services/inventarioService';
import InventarioTab from './components/InventarioTab';
import MovimientosTab from './components/MovimientosTab';

const InventarioPage: React.FC = () => {
    const router = useRouter();
    const toast = useRef<Toast>(null);
    const [activeIndex, setActiveIndex] = useState(0);

    return (
        <div className="inventario-page">
            <Toast ref={toast} />

            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center justify-content-between">
                    <div>
                        <h1 className="text-3xl font-bold text-900 mb-2">
                            <i className="pi pi-warehouse text-primary mr-3"></i>
                            Gestión de Inventario
                        </h1>
                        <p className="text-600 text-lg">
                            Controla el stock, movimientos y alertas de tus productos
                        </p>
                    </div>
                    
                    {/* Acciones rápidas */}
                    <div className="flex gap-2">
                        <Button
                            label="Nuevo Inventario"
                            icon="pi pi-plus"
                            className="p-button-success"
                            onClick={() => router.push('/administrador/inventario/crear')}
                        />
                        <Button
                            label="Ajuste Masivo"
                            icon="pi pi-cog"
                            className="p-button-warning"
                            onClick={() => router.push('/administrador/inventario/ajustar')}
                        />
                    </div>
                </div>
            </div>

            {/* Pestañas principales */}
            <Card>
                <TabView 
                    activeIndex={activeIndex} 
                    onTabChange={(e) => setActiveIndex(e.index)}
                    className="tabview-custom"
                >
                    <TabPanel 
                        header={
                            <div className="flex align-items-center gap-2">
                                <i className="pi pi-list"></i>
                                <span>Inventarios</span>
                            </div>
                        }
                    >
                        <InventarioTab toast={toast} />
                    </TabPanel>

                    <TabPanel 
                        header={
                            <div className="flex align-items-center gap-2">
                                <i className="pi pi-history"></i>
                                <span>Movimientos</span>
                            </div>
                        }
                    >
                        <MovimientosTab toast={toast} />
                    </TabPanel>
                </TabView>
            </Card>
        </div>
    );
};

export default InventarioPage;