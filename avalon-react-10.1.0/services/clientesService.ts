// services/clientesService.ts
import { GATEWAY_BASE_URL } from './apiConfig';

const API_BASE_URL = GATEWAY_BASE_URL;

// =====================================
// INTERFACES PARA CLIENTES
// =====================================

export interface ClienteRequest {
    nombre: string;
    apellido: string;
    email: string;
    telefono: string;
    direccion: string;
    documento?: string; // Campo de cédula agregado
}

export interface ClienteResponse {
    id: number;
    nombre: string;
    apellido: string;
    email: string;
    telefono: string;
    direccion: string;
    documento?: string;
    activo: boolean;
    fechaCreacion: string;
    fechaActualizacion: string;
}

// =====================================
// INTERFACE GENÉRICA PARA RESPUESTAS
// =====================================
export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data?: T;
}

class ClientesService {
    
    // =====================================
    // MÉTODOS AUXILIARES
    // =====================================
    
    private async makeRequest<T>(url: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
        try {
            const token = localStorage.getItem('token');
            
            const response = await fetch(`${API_BASE_URL}${url}`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': token ? `Bearer ${token}` : '',
                    ...options.headers,
                },
                ...options,
            });

            if (!response.ok) {
                const errorData = await response.text();
                return {
                    success: false,
                    message: `Error ${response.status}: ${errorData || 'Error desconocido'}`,
                };
            }

            // Si es DELETE y no hay contenido, retornar éxito
            if (response.status === 204) {
                return {
                    success: true,
                    message: 'Operación exitosa',
                };
            }

            const data = await response.json();
            return {
                success: true,
                message: 'Operación exitosa',
                data,
            };
        } catch (error) {
            console.error('Error en la petición:', error);
            return {
                success: false,
                message: 'Error de conexión con el servidor',
            };
        }
    }

    // =====================================
    // MÉTODOS PARA CLIENTES
    // =====================================
    
    /**
     * Crear un nuevo cliente
     */
    async crearCliente(cliente: ClienteRequest): Promise<ApiResponse<ClienteResponse>> {
        console.log('🔄 Creando cliente:', cliente);
        return this.makeRequest<ClienteResponse>('/api/clientes', {
            method: 'POST',
            body: JSON.stringify(cliente),
        });
    }

    /**
     * Actualizar un cliente existente
     */
    async actualizarCliente(id: number, cliente: ClienteRequest): Promise<ApiResponse<ClienteResponse>> {
        return this.makeRequest<ClienteResponse>(`/api/clientes/${id}`, {
            method: 'PUT',
            body: JSON.stringify(cliente),
        });
    }

    /**
     * Obtener un cliente por ID
     */
    async obtenerClientePorId(id: number): Promise<ApiResponse<ClienteResponse>> {
        return this.makeRequest<ClienteResponse>(`/api/clientes/${id}`);
    }

    /**
     * Obtener un cliente por email
     */
    async obtenerClientePorEmail(email: string): Promise<ApiResponse<ClienteResponse>> {
        console.log('🔍 Buscando cliente por email:', email);
        return this.makeRequest<ClienteResponse>(`/api/clientes/email/${encodeURIComponent(email)}`);
    }

    /**
     * Obtener un cliente por documento (cédula)
     */
    async obtenerClientePorDocumento(documento: string): Promise<ApiResponse<ClienteResponse>> {
        console.log('🔍 Buscando cliente por documento:', documento);
        return this.makeRequest<ClienteResponse>(`/api/clientes/documento/${documento}`);
    }

    /**
     * Listar todos los clientes
     */
    async listarClientes(): Promise<ApiResponse<ClienteResponse[]>> {
        return this.makeRequest<ClienteResponse[]>('/api/clientes');
    }

    /**
     * Eliminar un cliente (desactivación lógica)
     */
    async eliminarCliente(id: number): Promise<ApiResponse<void>> {
        return this.makeRequest<void>(`/api/clientes/${id}`, {
            method: 'DELETE',
        });
    }

    /**
     * Guardar datos del cliente en localStorage para el checkout
     */
    guardarDatosClienteLocal(cliente: ClienteRequest): void {
        if (typeof window !== 'undefined') {
            localStorage.setItem('checkoutCliente', JSON.stringify(cliente));
            console.log('💾 Datos del cliente guardados localmente');
        }
    }

    /**
     * Obtener datos del cliente de localStorage
     */
    obtenerDatosClienteLocal(): ClienteRequest | null {
        if (typeof window === 'undefined') return null;
        
        try {
            const data = localStorage.getItem('checkoutCliente');
            return data ? JSON.parse(data) : null;
        } catch (error) {
            console.error('Error obteniendo datos del cliente:', error);
            return null;
        }
    }

    /**
     * Limpiar datos del cliente de localStorage
     */
    limpiarDatosClienteLocal(): void {
        if (typeof window !== 'undefined') {
            localStorage.removeItem('checkoutCliente');
            console.log('🧹 Datos del cliente limpiados del localStorage');
        }
    }

    /**
     * Buscar o crear cliente - método auxiliar para checkout
     */
    async buscarOCrearCliente(datosCliente: ClienteRequest): Promise<ApiResponse<ClienteResponse>> {
        try {
            console.log('🔄 Buscando o creando cliente...');
            
            // Primero intentar buscar por email
            let clienteExistente = await this.obtenerClientePorEmail(datosCliente.email);
            
            if (clienteExistente.success && clienteExistente.data) {
                console.log('✅ Cliente encontrado por email:', clienteExistente.data.id);
                return clienteExistente;
            }
            
            // Si tiene documento, buscar por documento
            if (datosCliente.documento) {
                clienteExistente = await this.obtenerClientePorDocumento(datosCliente.documento);
                
                if (clienteExistente.success && clienteExistente.data) {
                    console.log('✅ Cliente encontrado por documento:', clienteExistente.data.id);
                    return clienteExistente;
                }
            }
            
            // Si no existe, crear nuevo cliente
            console.log('📝 Cliente no encontrado, creando nuevo...');
            const nuevoCliente = await this.crearCliente(datosCliente);
            
            if (nuevoCliente.success && nuevoCliente.data) {
                console.log('✅ Nuevo cliente creado:', nuevoCliente.data.id);
                return nuevoCliente;
            } else {
                throw new Error(nuevoCliente.message || 'Error creando cliente');
            }
            
        } catch (error: any) {
            console.error('❌ Error en buscarOCrearCliente:', error);
            return {
                success: false,
                message: error.message || 'Error procesando datos del cliente'
            };
        }
    }
}

// Exportar una instancia única del servicio
const clientesService = new ClientesService();
export default clientesService;