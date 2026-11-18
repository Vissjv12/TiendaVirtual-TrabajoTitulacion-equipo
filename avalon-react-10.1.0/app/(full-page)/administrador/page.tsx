'use client';

import React, { useState, useEffect, useContext } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Badge } from 'primereact/badge';
import { Chart } from 'primereact/chart';
import { DataTable } from 'primereact/datatable';
import { Column } from 'primereact/column';
import { LayoutContext } from '../../../layout/context/layoutcontext';
import authService from '../../../services/authService';
import visitasService, { VisitasEstadisticas } from '../../../services/visitasService';

const EmprendedorDashboard: React.FC = () => {
    const router = useRouter();
    const { layoutConfig } = useContext(LayoutContext);
    const [mounted, setMounted] = useState(false);
    const [visitas, setVisitas] = useState<VisitasEstadisticas | null>(null);

    const userInfo = authService.getUserInfo();

    useEffect(() => {
        setMounted(true);
        void visitasService.obtenerEstadisticas().then(setVisitas);
    }, []);

    // Datos mock para las estadísticas
    const stats = [
        {
            title: 'Total Ventas',
            value: '156',
            icon: 'pi pi-shopping-cart',
            color: '#3B82F6',
            bgColor: '#EFF6FF'
        },
        {
            title: 'Productos Activos',
            value: '24',
            icon: 'pi pi-box',
            color: '#10B981',
            bgColor: '#ECFDF5'
        },
        {
            title: 'Ventas Hoy',
            value: '8',
            icon: 'pi pi-calendar',
            color: '#8B5CF6',
            bgColor: '#F3E8FF'
        },
        {
            title: 'Ingresos del Mes',
            value: '$15,480.00',
            icon: 'pi pi-dollar',
            color: '#F59E0B',
            bgColor: '#FFFBEB'
        }
    ];

    // Datos para el gráfico
    const chartData = {
        labels: ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio'],
        datasets: [
            {
                label: 'Ventas ($)',
                data: [1200, 1900, 800, 1500, 2000, 2400],
                fill: true,
                backgroundColor: 'rgba(139, 92, 246, 0.1)',
                borderColor: '#8B5CF6',
                tension: 0.4
            }
        ]
    };

    const chartOptions = {
        maintainAspectRatio: false,
        aspectRatio: 0.8,
        plugins: {
            legend: {
                display: false
            }
        },
        scales: {
            x: {
                grid: {
                    display: false
                }
            },
            y: {
                grid: {
                    color: '#f1f5f9'
                }
            }
        }
    };

    // Ventas recientes mock
    const ventasRecientes = [
        {
            id: 1,
            producto: 'iPhone 15 Pro',
            cliente: 'Juan Pérez',
            cantidad: 2,
            total: 2400.00,
            fecha: '2024-07-20',
            estado: 'Completada'
        },
        {
            id: 2,
            producto: 'MacBook Air',
            cliente: 'María García',
            cantidad: 1,
            total: 1200.00,
            fecha: '2024-07-19',
            estado: 'Pendiente'
        },
        {
            id: 3,
            producto: 'AirPods Pro',
            cliente: 'Carlos López',
            cantidad: 3,
            total: 750.00,
            fecha: '2024-07-18',
            estado: 'Completada'
        }
    ];

    const estadoBodyTemplate = (rowData: any) => {
        return (
            <Badge 
                value={rowData.estado} 
                severity={rowData.estado === 'Completada' ? 'success' : 'warning'}
            />
        );
    };

    const totalBodyTemplate = (rowData: any) => {
        return <span className="font-bold">${rowData.total.toFixed(2)}</span>;
    };

    if (!mounted) {
        return (
            <div className="min-h-screen flex align-items-center justify-content-center">
                <i className="pi pi-spin pi-spinner text-4xl" style={{ color: 'var(--primary-color)' }}></i>
            </div>
        );
    }

    return (
        <div className="emprendedor-dashboard">
            {/* Header */}
            <div className="mb-4">
                <div className="flex align-items-center justify-content-between flex-wrap gap-4">
                    <div>
                        <h1 className="text-3xl font-bold text-900 mb-2">
                            ¡Bienvenido, Administrador!
                        </h1>
                        <p className="text-600 text-lg m-0">
                            Gestiona tu negocio desde tu panel de administrador
                        </p>
                    </div>
                    <div className="text-right">
                        <div className="text-600 text-sm">Fecha actual</div>
                        <div className="text-900 font-semibold">
                            {new Date().toLocaleDateString('es-ES', {
                                weekday: 'long',
                                year: 'numeric',
                                month: 'long',
                                day: 'numeric'
                            })}
                        </div>
                    </div>
                </div>
            </div>

            {/* Tarjetas de estadísticas */}
            <div className="grid mb-4">
                {stats.map((stat, index) => (
                    <div key={index} className="col-12 sm:col-6 lg:col-3">
                        <Card className="shadow-2 border-round-lg overflow-hidden h-full">
                            <div 
                                className="p-4"
                                style={{
                                    background: `linear-gradient(135deg, ${stat.color} 0%, ${stat.color}90 100%)`,
                                    color: 'white'
                                }}
                            >
                                <div className="flex align-items-center justify-content-between">
                                    <div>
                                        <div className="text-sm opacity-90 mb-1">{stat.title}</div>
                                        <div className="text-2xl font-bold">{stat.value}</div>
                                    </div>
                                    <div 
                                        className="w-3rem h-3rem border-circle flex align-items-center justify-content-center"
                                        style={{
                                            backgroundColor: 'rgba(255,255,255,0.2)'
                                        }}
                                    >
                                        <i className={`${stat.icon} text-xl`}></i>
                                    </div>
                                </div>
                            </div>
                        </Card>
                    </div>
                ))}
                {visitas && (
                    <div className="col-12 sm:col-6 lg:col-3">
                        <Card className="shadow-2 border-round-lg overflow-hidden h-full">
                            <div
                                className="p-4"
                                style={{
                                    background: 'linear-gradient(135deg, #0EA5E9 0%, #0284C7 100%)',
                                    color: 'white'
                                }}
                            >
                                <div className="flex align-items-center justify-content-between">
                                    <div>
                                        <div className="text-sm opacity-90 mb-1">Visitas del sitio</div>
                                        <div className="text-2xl font-bold">{visitas.total.toLocaleString('es-EC')}</div>
                                        <div className="text-xs opacity-90 mt-1">
                                            Hoy: {visitas.hoy.toLocaleString('es-EC')}
                                        </div>
                                    </div>
                                    <div className="w-3rem h-3rem border-circle flex align-items-center justify-content-center" style={{ backgroundColor: 'rgba(255,255,255,0.2)' }}>
                                        <i className="pi pi-eye text-xl"></i>
                                    </div>
                                </div>
                            </div>
                        </Card>
                    </div>
                )}
            </div>

            {/* Contenido principal */}
            <div className="grid">
                {/* Gráfico de tendencias - EXPANDIDO */}
                <div className="col-12">
                    <Card title="Tendencia de Ventas" className="shadow-2 h-full">
                        <Chart 
                            type="line" 
                            data={chartData} 
                            options={chartOptions} 
                            style={{ height: '400px' }}
                        />
                    </Card>
                </div>

                {/* Ventas recientes */}
                <div className="col-12">
                    <Card title="Ventas Recientes" className="shadow-2">
                        <DataTable 
                            value={ventasRecientes}
                            paginator={false}
                            responsiveLayout="scroll"
                            className="p-datatable-sm"
                        >
                            <Column field="id" header="ID" style={{ width: '80px' }} />
                            <Column field="producto" header="Producto" />
                            <Column field="cliente" header="Cliente" />
                            <Column field="cantidad" header="Cantidad" style={{ width: '100px' }} />
                            <Column field="total" header="Total" body={totalBodyTemplate} style={{ width: '120px' }} />
                            <Column field="fecha" header="Fecha" style={{ width: '120px' }} />
                            <Column field="estado" header="Estado" body={estadoBodyTemplate} style={{ width: '120px' }} />
                        </DataTable>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default EmprendedorDashboard;
