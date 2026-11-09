import authService from './authService';
import { GATEWAY_BASE_URL } from './apiConfig';

// =====================================
// INTERFACES PARA USUARIOS
// =====================================

export interface Usuario {
    id: number;
    username: string;
    email: string;
    nombre: string;
    apellido: string;
    activo: boolean;
    roles: string[]; 
}

export interface CrearUsuarioRequest {
    username: string;
    password: string;
    email: string;
    nombre: string;
    apellido: string;
    roles: string[]; 
}

export interface ActualizarUsuarioRequest {
    username?: string;
    email?: string;
    nombre?: string;
    apellido?: string;
    password?: string; 
    roles?: string[];
}

export interface EstadisticasUsuarios {
    totalUsuarios: number;
    usuariosActivos: number;
    usuariosInactivos: number;
    administradores: number;
    emprendedores: number;
    clientes: number;
}

// =====================================
// INTERFACE GENÉRICA PARA RESPUESTAS
// =====================================
export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data?: T;
}

// =====================================
// ROLES DISPONIBLES PARA ADMINISTRADOR - ✅ CORREGIDO
// =====================================
export const ROLES_DISPONIBLES = [
    { value: 'ROLE_ADMIN', label: 'Administrador', description: 'Acceso completo al sistema', color: 'danger' },
    { value: 'ROLE_EMP', label: 'Emprendedor', description: 'Gestión de productos y ventas', color: 'warning' },
    { value: 'ROLE_USER', label: 'Cliente', description: 'Usuario cliente del sistema', color: 'info' }
];

export const ESTADOS_USUARIO = [
    { value: true, label: 'Activo', severity: 'success' as const },
    { value: false, label: 'Inactivo', severity: 'danger' as const }
];

class UsuariosService {
    private readonly API_BASE_URL = GATEWAY_BASE_URL;

    // =====================================
    // MÉTODOS AUXILIARES
    // =====================================
    
    private async makeRequest<T>(url: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
        try {
            const authHeaders = authService.getAuthHeaders();
            
            const response = await fetch(`${this.API_BASE_URL}${url}`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authHeaders,
                    ...options.headers,
                },
                ...options,
            });

            if (!response.ok) {
                let errorMessage = `Error ${response.status}`;
                try {
                    const errorData = await response.text();
                    errorMessage = errorData || errorMessage;
                } catch {
                    // Si no se puede leer el error, usar el mensaje por defecto
                }
                return {
                    success: false,
                    message: errorMessage,
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
    // MÉTODOS PARA GESTIÓN DE USUARIOS (ADMIN)
    // =====================================
    
    async obtenerUsuarios(): Promise<ApiResponse<Usuario[]>> {
        return this.makeRequest<Usuario[]>('/api/usuarios');
    }

    async obtenerUsuarioPorId(id: number): Promise<ApiResponse<Usuario>> {
        return this.makeRequest<Usuario>(`/api/usuarios/${id}`);
    }

    async crearUsuario(usuario: CrearUsuarioRequest): Promise<ApiResponse<Usuario>> {
        console.log('🔄 Creando usuario:', usuario);
        return this.makeRequest<Usuario>('/api/usuarios', {
            method: 'POST',
            body: JSON.stringify(usuario),
        });
    }

    async actualizarUsuario(id: number, usuario: ActualizarUsuarioRequest): Promise<ApiResponse<Usuario>> {
        return this.makeRequest<Usuario>(`/api/usuarios/${id}`, {
            method: 'PUT',
            body: JSON.stringify(usuario),
        });
    }

    async cambiarEstadoUsuario(id: number, activo: boolean): Promise<ApiResponse<Usuario>> {
        return this.makeRequest<Usuario>(`/api/usuarios/${id}/estado`, {
            method: 'PATCH',
            body: JSON.stringify({ activo }),
        });
    }

    async eliminarUsuario(id: number): Promise<ApiResponse<void>> {
        return this.makeRequest<void>(`/api/usuarios/${id}`, {
            method: 'DELETE',
        });
    }

    async buscarUsuarios(termino: string): Promise<ApiResponse<Usuario[]>> {
        const params = new URLSearchParams({ q: termino });
        return this.makeRequest<Usuario[]>(`/api/usuarios/buscar?${params}`);
    }

    async obtenerUsuariosPorRol(rol: string): Promise<ApiResponse<Usuario[]>> {
        return this.makeRequest<Usuario[]>(`/api/usuarios/rol/${rol}`);
    }

    async obtenerEstadisticas(): Promise<ApiResponse<EstadisticasUsuarios>> {
        return this.makeRequest<EstadisticasUsuarios>('/api/usuarios/estadisticas');
    }

    // =====================================
    // MÉTODOS DE VALIDACIÓN
    // =====================================
    
    async validarUsername(username: string): Promise<ApiResponse<boolean>> {
        return this.makeRequest<boolean>(`/api/usuarios/validar/username/${username}`);
    }

    async validarEmail(email: string): Promise<ApiResponse<boolean>> {
        return this.makeRequest<boolean>(`/api/usuarios/validar/email/${email}`);
    }

    // =====================================
    // UTILIDADES - ✅ CORREGIDO
    // =====================================
    
    formatearRoles(roles: string[]): string {
        return roles.map(rol => {
            const rolInfo = ROLES_DISPONIBLES.find(r => r.value === rol);
            return rolInfo ? rolInfo.label : rol;
        }).join(', ');
    }

    obtenerInfoRol(rol: string) {
        return ROLES_DISPONIBLES.find(r => r.value === rol) || {
            value: rol,
            label: rol,
            description: 'Rol desconocido',
            color: 'secondary'
        };
    }

    obtenerColorRol(rol: string): string {
        const rolInfo = this.obtenerInfoRol(rol);
        return rolInfo.color;
    }

    validarPassword(password: string): { valida: boolean; mensaje: string } {
        if (password.length < 6) {
            return { valida: false, mensaje: 'La contraseña debe tener al menos 6 caracteres' };
        }
        return { valida: true, mensaje: 'Contraseña válida' };
    }

    validarFormatoEmail(email: string): boolean {
        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailPattern.test(email);
    }

    validarFormatoUsername(username: string): { valido: boolean; mensaje: string } {
        if (username.length < 4) {
            return { valido: false, mensaje: 'El username debe tener al menos 4 caracteres' };
        }
        if (username.length > 20) {
            return { valido: false, mensaje: 'El username no puede tener más de 20 caracteres' };
        }
        if (!/^[a-zA-Z0-9_]+$/.test(username)) {
            return { valido: false, mensaje: 'El username solo puede contener letras, números y guiones bajos' };
        }
        return { valido: true, mensaje: 'Username válido' };
    }

    // ✅ CORREGIDO: Usar ROLE_EMP
    obtenerRolPrincipal(usuario: Usuario): string {
        if (usuario.roles.includes('ROLE_ADMIN')) return 'ROLE_ADMIN';
        if (usuario.roles.includes('ROLE_EMP')) return 'ROLE_EMP';
        if (usuario.roles.includes('ROLE_USER')) return 'ROLE_USER';
        return usuario.roles[0] || 'Sin rol';
    }

    validarRoles(roles: string[]): { validos: boolean; mensaje: string } {
        if (!roles || roles.length === 0) {
            return { validos: false, mensaje: 'Debe seleccionar al menos un rol' };
        }

        const rolesValidos = ROLES_DISPONIBLES.map(r => r.value);
        const rolesInvalidos = roles.filter(rol => !rolesValidos.includes(rol));

        if (rolesInvalidos.length > 0) {
            return { validos: false, mensaje: `Roles inválidos: ${rolesInvalidos.join(', ')}` };
        }

        return { validos: true, mensaje: 'Roles válidos' };
    }
}

const usuariosService = new UsuariosService();
export default usuariosService;