import authService from './authService';
const API_BASE_URL = process.env.NEXT_PUBLIC_PRODUCTOS_API_URL || 'http://localhost:8081/api';

// =====================================
// INTERFACES PARA CATEGORÍAS
// =====================================
export interface Categoria {
    id: number;
    nombre: string;
    descripcion: string;
    activo: boolean;
    fechaCreacion: string;
    fechaActualizacion: string;
}

export interface CategoriaRequest {
    nombre: string;
    descripcion?: string;
    activo?: boolean;
}

// =====================================
// INTERFACES PARA EMPRESAS
// =====================================
export interface Empresa {
    id: number;
    nombre: string;
    ruc: string;
    direccion: string;
    telefono: string;
    email: string;
    activo: boolean;
    fechaCreacion: string;
    fechaActualizacion: string;
}

export interface EmpresaRequest {
    nombre: string;
    ruc: string;
    direccion?: string;
    telefono?: string;
    email?: string;
    activo?: boolean;
}

// =====================================
// INTERFACE GENÉRICA PARA RESPUESTAS
// =====================================
export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data?: T;
    status?: number;
}

class CategoriasEmpresasService {
    
    // =====================================
    // MÉTODOS AUXILIARES
    // =====================================
    
    private async makeRequest<T>(url: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
        try {
            const response = await fetch(`${API_BASE_URL}${url}`, {
                headers: {
                    ...authService.getAuthHeaders(),
                    ...options.headers,
                },
                ...options,
            });

            if (!response.ok) {
                const errorMsg = await authService.extractErrorMessage(response, `Error ${response.status}: Error desconocido`);
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg,
                };
            }

            // Si es DELETE y no hay contenido, retornar éxito
            if (response.status === 204) {
                return {
                    success: true,
                    status: response.status,
                    message: 'Operación exitosa',
                };
            }

            const data = await response.json();
            return {
                success: true,
                status: response.status,
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

    // Formatear fecha para mostrar
    formatearFecha(fecha: string): string {
        try {
            const date = new Date(fecha);
            return date.toLocaleDateString('es-ES', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return 'Fecha inválida';
        }
    }

    // =====================================
    // MÉTODOS PARA CATEGORÍAS
    // =====================================
    
    async obtenerCategorias(): Promise<ApiResponse<Categoria[]>> {
        return this.makeRequest<Categoria[]>('/categorias');
    }

    async obtenerCategoriaPorId(id: number): Promise<ApiResponse<Categoria>> {
        return this.makeRequest<Categoria>(`/categorias/${id}`);
    }

    async crearCategoria(categoria: CategoriaRequest): Promise<ApiResponse<Categoria>> {
        return this.makeRequest<Categoria>('/categorias', {
            method: 'POST',
            body: JSON.stringify({
                ...categoria,
                activo: categoria.activo ?? true
            }),
        });
    }

    async actualizarCategoria(id: number, categoria: CategoriaRequest): Promise<ApiResponse<Categoria>> {
        return this.makeRequest<Categoria>(`/categorias/${id}`, {
            method: 'PUT',
            body: JSON.stringify(categoria),
        });
    }

    async eliminarCategoria(id: number): Promise<ApiResponse<void>> {
        return this.makeRequest<void>(`/categorias/${id}`, {
            method: 'DELETE',
        });
    }

    // =====================================
    // MÉTODOS PARA EMPRESAS
    // =====================================
    
    async obtenerEmpresas(): Promise<ApiResponse<Empresa[]>> {
        const res = await this.makeRequest<Empresa[]>('/empresas');
        if (res.success && res.data && !authService.isAdmin() && authService.hasRole('ROLE_EMP')) {
            const userEmpresaId = authService.getEmpresaId();
            if (userEmpresaId) {
                res.data = res.data.filter((e) => Number(e.id) === Number(userEmpresaId));
            } else {
                res.data = [];
            }
        }
        return res;
    }

    async obtenerEmpresaPorId(id: number): Promise<ApiResponse<Empresa>> {
        if (!authService.isAdmin() && !authService.canManageEmpresa(id)) {
            return {
                success: false,
                status: 403,
                message: 'Acceso denegado (403): No tienes permisos para consultar o editar otra empresa.'
            };
        }
        return this.makeRequest<Empresa>(`/empresas/${id}`);
    }

    async crearEmpresa(empresa: EmpresaRequest): Promise<ApiResponse<Empresa>> {
        if (!authService.isAdmin()) {
            return {
                success: false,
                status: 403,
                message: 'Acceso denegado (403): Solo un administrador puede registrar nuevas empresas.'
            };
        }
        return this.makeRequest<Empresa>('/empresas', {
            method: 'POST',
            body: JSON.stringify({
                ...empresa,
                activo: empresa.activo ?? true
            }),
        });
    }

    async actualizarEmpresa(id: number, empresa: EmpresaRequest): Promise<ApiResponse<Empresa>> {
        if (!authService.isAdmin() && !authService.canManageEmpresa(id)) {
            return {
                success: false,
                status: 403,
                message: 'Acceso denegado (403): No tienes permisos para modificar los datos de otra empresa.'
            };
        }
        return this.makeRequest<Empresa>(`/empresas/${id}`, {
            method: 'PUT',
            body: JSON.stringify(empresa),
        });
    }

    async eliminarEmpresa(id: number): Promise<ApiResponse<void>> {
        if (!authService.isAdmin()) {
            return {
                success: false,
                status: 403,
                message: 'Acceso denegado (403): Solo un administrador puede eliminar empresas.'
            };
        }
        return this.makeRequest<void>(`/empresas/${id}`, {
            method: 'DELETE',
        });
    }
}

// Exportar una instancia única del servicio
const categoriasEmpresasService = new CategoriasEmpresasService();
export default categoriasEmpresasService;
