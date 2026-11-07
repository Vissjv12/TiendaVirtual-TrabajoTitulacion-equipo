import authService from './authService';

const API_BASE_URL = process.env.NEXT_PUBLIC_INVENTARIO_API_URL || 'http://localhost:8082';

// =====================================
// INTERFACES PARA INVENTARIOS
// =====================================
export interface ProductoDto {
    id: number;
    nombre: string;
    descripcion: string;
    precio: number;
    imagen: string;
    activo: boolean;
}

export interface Inventario {
    id: number;
    producto: ProductoDto;
    cantidad: number;
    ubicacion: string;
    activo: boolean;
    fechaCreacion: string;
    fechaActualizacion: string;
}

export interface InventarioRequest {
    productoId: number;
    cantidad: number;
    ubicacion?: string;
}

// =====================================
// INTERFACES PARA MOVIMIENTOS
// =====================================
export interface MovimientoInventario {
    id: number;
    inventarioId: number;
    tipoMovimiento: string;
    cantidad: number;
    motivo: string;
    fechaMovimiento: string;
    usuarioId: number;
}

export interface MovimientoRequest {
    inventarioId: number;
    tipoMovimiento: string;
    cantidad: number;
    motivo?: string;
    usuarioId?: number;
}

// =====================================
// INTERFACES PARA SALIDAS EN LOTE
// =====================================
export interface SalidaInventarioItem {
    productoId: number;
    cantidad: number;
}

export interface SalidaInventarioLote {
    items: SalidaInventarioItem[];
    motivo?: string;
}

// =====================================
// INTERFACE GENÉRICA PARA RESPUESTAS
// =====================================
export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data?: T;
}

class InventarioService {
    // =====================================
    // MÉTODOS AUXILIARES
    // =====================================

    private async makeRequest<T>(url: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
        try {
            const response = await fetch(`${API_BASE_URL}${url}`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders(),
                    ...options.headers
                },
                ...options
            });

            if (!response.ok) {
                const errorData = await response.text();
                return {
                    success: false,
                    message: `Error ${response.status}: ${errorData || 'Error desconocido'}`
                };
            }

            // Si es DELETE y no hay contenido, retornar éxito
            if (response.status === 204) {
                return {
                    success: true,
                    message: 'Operación exitosa'
                };
            }

            const data = await response.json();
            return {
                success: true,
                message: 'Operación exitosa',
                data
            };
        } catch (error) {
            console.error('Error en la petición:', error);
            return {
                success: false,
                message: 'Error de conexión con el servidor'
            };
        }
    }

    // Formatear fecha para mostrar
    formatearFecha(fecha: string): string {
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
    }

    // Formatear tipo de movimiento
    formatearTipoMovimiento(tipo: string): string {
        const tipos: Record<string, string> = {
            ENTRADA: 'Entrada',
            SALIDA: 'Salida',
            AJUSTE: 'Ajuste',
            DEVOLUCION: 'Devolución',
            TRANSFERENCIA: 'Transferencia'
        };
        return tipos[tipo] || tipo;
    }

    // =====================================
    // MÉTODOS PARA INVENTARIOS
    // =====================================

    async obtenerInventarios(): Promise<ApiResponse<Inventario[]>> {
        return this.makeRequest<Inventario[]>('/api/inventarios');
    }

    async obtenerInventarioPorId(id: number): Promise<ApiResponse<Inventario>> {
        return this.makeRequest<Inventario>(`/api/inventarios/${id}`);
    }

    async obtenerInventarioPorProducto(productoId: number): Promise<ApiResponse<Inventario>> {
        return this.makeRequest<Inventario>(`/api/inventarios/producto/${productoId}`);
    }

    async crearInventario(inventario: InventarioRequest): Promise<ApiResponse<Inventario>> {
        return this.makeRequest<Inventario>('/api/inventarios', {
            method: 'POST',
            body: JSON.stringify(inventario)
        });
    }

    async crearInventarioParaProducto(productoId: number, stock: number): Promise<ApiResponse<Inventario>> {
        return this.makeRequest<Inventario>(`/api/inventarios/producto/${productoId}?stock=${stock}`, {
            method: 'POST'
        });
    }

    async actualizarInventario(id: number, inventario: InventarioRequest): Promise<ApiResponse<Inventario>> {
        return this.makeRequest<Inventario>(`/api/inventarios/${id}`, {
            method: 'PUT',
            body: JSON.stringify(inventario)
        });
    }

    async actualizarStock(productoId: number, cantidad: number, tipoMovimiento: string = 'AJUSTE', motivo?: string): Promise<ApiResponse<Inventario>> {
        const params = new URLSearchParams({
            cantidad: cantidad.toString(),
            tipoMovimiento,
            ...(motivo && { motivo })
        });

        const response = await this.makeRequest<Inventario>(`/api/inventarios/producto/${productoId}/stock?${params}`, {
            method: 'PUT'
        });

        if (!response.success && response.message.includes('Inventario no encontrado')) {
            return this.crearInventario({ productoId, cantidad });
        }

        return response;
    }

    async eliminarInventario(id: number): Promise<ApiResponse<void>> {
        return this.makeRequest<void>(`/api/inventarios/${id}`, {
            method: 'DELETE'
        });
    }

    async procesarSalidaLote(salida: SalidaInventarioLote): Promise<ApiResponse<Inventario[]>> {
        return this.makeRequest<Inventario[]>('/api/inventarios/salida-lote', {
            method: 'POST',
            body: JSON.stringify(salida)
        });
    }

    /**
     * Obtener stock de un producto específico
     * Endpoint simple que devuelve solo el número
     */
    async obtenerStockProducto(productoId: number): Promise<number> {
        try {
            const response = await this.makeRequest<number>(`/api/inventarios/cantidad/producto/${productoId}`);
            return response.success && response.data !== undefined ? response.data : 0;
        } catch (error) {
            console.error('Error obteniendo stock:', error);
            return 0;
        }
    }

    /**
     * 🚀 NUEVO: Obtener stock de múltiples productos en una sola petición
     * Usa el endpoint batch optimizado
     */
    async obtenerStockBatch(productosIds: number[]): Promise<Record<string, number>> {
        try {
            console.log(`🔄 Obteniendo stock batch para ${productosIds.length} productos...`);

            const response = await this.makeRequest<Record<string, number>>('/api/inventarios/cantidad/batch', {
                method: 'POST',
                body: JSON.stringify(productosIds)
            });

            if (response.success && response.data) {
                console.log('✅ Stock batch obtenido exitosamente');
                return response.data;
            } else {
                console.log('⚠️ Error en respuesta batch, devolviendo objeto vacío');
                return {};
            }
        } catch (error) {
            console.error('❌ Error obteniendo stock batch:', error);
            // Devolver vacío: un fallo de consulta no significa stock 0.
            // Los consumidores conservan la cantidad que ya tenían.
            return {};
        }
    }

    // =====================================
    // MÉTODOS PARA MOVIMIENTOS
    // =====================================

    async obtenerMovimientos(): Promise<ApiResponse<MovimientoInventario[]>> {
        return this.makeRequest<MovimientoInventario[]>('/api/movimientos');
    }

    async obtenerMovimientoPorId(id: number): Promise<ApiResponse<MovimientoInventario>> {
        return this.makeRequest<MovimientoInventario>(`/api/movimientos/${id}`);
    }

    async obtenerMovimientosPorInventario(inventarioId: number): Promise<ApiResponse<MovimientoInventario[]>> {
        return this.makeRequest<MovimientoInventario[]>(`/api/movimientos/inventario/${inventarioId}`);
    }

    async registrarMovimiento(movimiento: MovimientoRequest): Promise<ApiResponse<MovimientoInventario>> {
        return this.makeRequest<MovimientoInventario>('/api/movimientos', {
            method: 'POST',
            body: JSON.stringify(movimiento)
        });
    }
}

// Exportar una instancia única del servicio
const inventarioService = new InventarioService();
export default inventarioService;
