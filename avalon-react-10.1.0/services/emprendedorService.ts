// services/emprendedorService.ts - Servicios para el panel de emprendedor

import authService from './authService';
import inventarioService from './inventarioService';

// ================ INTERFACES ACTUALIZADAS ================
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
    descripcion: string;
    activo?: boolean;
}

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
    direccion: string;
    telefono: string;
    email: string;
    activo?: boolean;
}

export interface InventarioInfo {
    id: number;
    cantidad: number;
    ubicacion: string;
}

export interface ProductoEmprendedor {
    id: number;
    nombre: string;
    descripcion: string;
    precio: number;
    imagen: string;
    activo: boolean;
    categoria: Categoria;
    empresa: Empresa;
    inventario?: InventarioInfo;
    fechaCreacion: string;
    fechaActualizacion: string;
}

export interface ProductoRequest {
    nombre: string;
    descripcion: string;
    precio: number;
    imagen: string;
    activo: boolean;
    categoriaId: number;
    empresaId: number;
    emprendedorId?: number;
}

export interface ApiResponse<T> {
    success: boolean;
    data?: T;
    message: string;
    status?: number;
}

// ================ SERVICIO PRINCIPAL ================
class EmprendedorService {
    private readonly PRODUCTOS_URL = process.env.NEXT_PUBLIC_PRODUCTOS_API_URL || 'http://localhost:8081/api';
    // ✅ CORREGIDO: no agregar /api extra; la env var ya incluye el path correcto del proxy
    private readonly INVENTARIO_URL = process.env.NEXT_PUBLIC_INVENTARIO_API_URL || 'http://localhost:8082/api';
    private readonly VENTAS_URL = process.env.NEXT_PUBLIC_VENTAS_API_URL || 'http://localhost:8083/api';

    /**
     * ✅ ACTUALIZAR STOCK EN SEGUNDO PLANO - VERSIÓN BATCH OPTIMIZADA
     */
    private async actualizarStockEnSegundoPlano(productos: ProductoEmprendedor[]) {
        try {
            console.log(`🔄 Actualizando stock para ${productos.length} productos usando método batch...`);

            // Obtener todos los IDs de productos
            const productosIds = productos.map((p) => p.id);

            // 🚀 USAR EL NUEVO MÉTODO BATCH DE INVENTARIO SERVICE
            const stockBatch = await inventarioService.obtenerStockBatch(productosIds);

            // Actualizar cada producto con su stock
            productos.forEach((producto) => {
                const stock = stockBatch[producto.id.toString()] || 0;

                if (producto.inventario) {
                    const stockAnterior = producto.inventario.cantidad;
                    producto.inventario.cantidad = stock;
                    producto.inventario.ubicacion = stock > 0 ? 'Disponible' : 'No disponible';

                    if (stockAnterior !== stock) {
                        console.log(`📦 Stock actualizado para ${producto.nombre}: ${stockAnterior} → ${stock}`);
                    }

                    // Emitir evento para actualizar UI
                    window.dispatchEvent(
                        new CustomEvent('stockUpdatedEmprendedor', {
                            detail: { productoId: producto.id, stock, producto }
                        })
                    );
                }
            });

            console.log('✅ Actualización batch completada exitosamente');
        } catch (error) {
            console.log('⚠️ Error actualizando stock batch:', error);

            // Si falla el batch, mantener el stock que viene del servidor
            productos.forEach((producto) => {
                if (producto.inventario && producto.inventario.cantidad === undefined) {
                    producto.inventario.cantidad = 0;
                    producto.inventario.ubicacion = 'No disponible';
                }
            });
        }
    }

    // ================ CATEGORÍAS ================
    async obtenerCategorias(): Promise<ApiResponse<Categoria[]>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/categorias`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                const categorias = await response.json();
                return {
                    success: true,
                    data: categorias,
                    message: 'Categorías obtenidas exitosamente'
                };
            } else {
                return {
                    success: false,
                    message: `Error ${response.status}: ${response.statusText}`
                };
            }
        } catch (error: any) {
            console.error('Error obteniendo categorías:', error);
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async obtenerCategoriaPorId(id: number): Promise<ApiResponse<Categoria>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/categorias/${id}`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                const categoria = await response.json();
                return {
                    success: true,
                    data: categoria,
                    message: 'Categoría obtenida exitosamente'
                };
            } else {
                return {
                    success: false,
                    message: `Error ${response.status}: ${response.statusText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async crearCategoria(categoria: CategoriaRequest): Promise<ApiResponse<Categoria>> {
        try {
            const categoriaConDefecto = {
                ...categoria,
                activo: categoria.activo !== undefined ? categoria.activo : true
            };

            const response = await fetch(`${this.PRODUCTOS_URL}/categorias`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(categoriaConDefecto)
            });

            if (response.ok) {
                const nuevaCategoria = await response.json();
                return {
                    success: true,
                    data: nuevaCategoria,
                    message: 'Categoría creada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error creando categoría: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async actualizarCategoria(id: number, categoria: CategoriaRequest): Promise<ApiResponse<Categoria>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/categorias/${id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(categoria)
            });

            if (response.ok) {
                const categoriaActualizada = await response.json();
                return {
                    success: true,
                    data: categoriaActualizada,
                    message: 'Categoría actualizada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error actualizando categoría: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async eliminarCategoria(id: number): Promise<ApiResponse<boolean>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/categorias/${id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                return {
                    success: true,
                    data: true,
                    message: 'Categoría eliminada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error eliminando categoría: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    // ================ EMPRESAS ================
    async obtenerEmpresas(): Promise<ApiResponse<Empresa[]>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/empresas`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                const empresas = await response.json();
                const isAdmin = authService.isAdmin();
                const userEmpresaId = authService.getEmpresaId();
                const empresasFiltradas = !isAdmin && userEmpresaId
                    ? empresas.filter((e: Empresa) => Number(e.id) === Number(userEmpresaId))
                    : empresas;
                return {
                    success: true,
                    status: response.status,
                    data: empresasFiltradas,
                    message: 'Empresas obtenidas exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(response, `Error ${response.status}: ${response.statusText}`);
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async obtenerEmpresaPorId(id: number): Promise<ApiResponse<Empresa>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/empresas/${id}`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                const empresa = await response.json();
                return {
                    success: true,
                    data: empresa,
                    message: 'Empresa obtenida exitosamente'
                };
            } else {
                return {
                    success: false,
                    message: `Error ${response.status}: ${response.statusText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async crearEmpresa(empresa: EmpresaRequest): Promise<ApiResponse<Empresa>> {
        try {
            const empresaConDefecto = {
                ...empresa,
                activo: empresa.activo !== undefined ? empresa.activo : true
            };

            const response = await fetch(`${this.PRODUCTOS_URL}/empresas`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(empresaConDefecto)
            });

            if (response.ok) {
                const nuevaEmpresa = await response.json();
                return {
                    success: true,
                    data: nuevaEmpresa,
                    message: 'Empresa creada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error creando empresa: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async actualizarEmpresa(id: number, empresa: EmpresaRequest): Promise<ApiResponse<Empresa>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/empresas/${id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(empresa)
            });

            if (response.ok) {
                const empresaActualizada = await response.json();
                return {
                    success: true,
                    data: empresaActualizada,
                    message: 'Empresa actualizada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error actualizando empresa: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async eliminarEmpresa(id: number): Promise<ApiResponse<boolean>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/empresas/${id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                return {
                    success: true,
                    data: true,
                    message: 'Empresa eliminada exitosamente'
                };
            } else {
                const errorText = await response.text();
                return {
                    success: false,
                    message: `Error eliminando empresa: ${errorText}`
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    // ================ PRODUCTOS ================
    private mapProductoDtoToEmprendedor(producto: any): ProductoEmprendedor {
        return {
            id: producto.id,
            nombre: producto.nombre,
            descripcion: producto.descripcion,
            precio: producto.precio,
            imagen: producto.imagen || '/demo/images/product/product-placeholder.svg',
            activo: producto.activo !== undefined ? producto.activo : true,
            categoria: {
                id: producto.categoria?.id || 0,
                nombre: producto.categoria?.nombre || producto.categoriaNombre || 'Sin categoría',
                descripcion: producto.categoria?.descripcion || '',
                activo: producto.categoria?.activo !== undefined ? producto.categoria.activo : true,
                fechaCreacion: producto.categoria?.fechaCreacion || '',
                fechaActualizacion: producto.categoria?.fechaActualizacion || ''
            },
            empresa: {
                id: producto.empresa?.id || producto.empresaId || 0,
                nombre: producto.empresa?.nombre || producto.empresaNombre || 'Sin empresa',
                ruc: producto.empresa?.ruc || '',
                direccion: producto.empresa?.direccion || '',
                telefono: producto.empresa?.telefono || '',
                email: producto.empresa?.email || '',
                activo: producto.empresa?.activo !== undefined ? producto.empresa.activo : true,
                fechaCreacion: producto.empresa?.fechaCreacion || '',
                fechaActualizacion: producto.empresa?.fechaActualizacion || ''
            },
            inventario: producto.inventario
                ? {
                      id: producto.inventario.id || 0,
                      cantidad: producto.inventario.cantidad || 0,
                      ubicacion: producto.inventario.ubicacion || 'Sin ubicación'
                  }
                : {
                      id: 0,
                      cantidad: producto.inventarioCantidad || 0,
                      ubicacion: (producto.inventarioCantidad || 0) > 0 ? 'Disponible' : 'Sin stock'
                  },
            fechaCreacion: producto.fechaCreacion || '',
            fechaActualizacion: producto.fechaActualizacion || ''
        };
    }

    async obtenerProductosEmprendedor(): Promise<ApiResponse<ProductoEmprendedor[]>> {
        try {
            console.log('🚀 Obteniendo productos para emprendedor...');

            const isAdmin = authService.isAdmin();
            const userEmpresaId = authService.getEmpresaId();

            // 🔒 Si es ROLE_EMP, solo tiene permiso sobre su propia empresa
            if (!isAdmin) {
                if (!userEmpresaId) {
                    return {
                        success: false,
                        status: 403,
                        data: [],
                        message: 'Tu cuenta de emprendedor no tiene una empresa asociada en la sesión. Inicia sesión nuevamente.'
                    };
                }

                const responseEmpresa = await fetch(`${this.PRODUCTOS_URL}/productos/empresa/${userEmpresaId}`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                        ...authService.getAuthHeaders()
                    },
                    signal: AbortSignal.timeout(5000)
                });

                if (responseEmpresa.ok) {
                    const productos = await responseEmpresa.json();
                    const productosEmprendedor: ProductoEmprendedor[] = productos
                        .map((p: any) => this.mapProductoDtoToEmprendedor(p))
                        .filter((p: ProductoEmprendedor) => !p.empresa?.id || Number(p.empresa.id) === Number(userEmpresaId));

                    // Asegurar que empresa.id tenga el valor correcto
                    productosEmprendedor.forEach((p) => {
                        if (!p.empresa.id) {
                            p.empresa.id = userEmpresaId;
                        }
                    });

                    if (productosEmprendedor.length > 0) {
                        this.actualizarStockEnSegundoPlano(productosEmprendedor);
                    }

                    return {
                        success: true,
                        status: responseEmpresa.status,
                        data: productosEmprendedor,
                        message: 'Productos de tu empresa obtenidos exitosamente'
                    };
                } else {
                    const errorMsg = await authService.extractErrorMessage(
                        responseEmpresa,
                        `Error ${responseEmpresa.status}: ${responseEmpresa.statusText}`
                    );
                    return {
                        success: false,
                        status: responseEmpresa.status,
                        message: errorMsg
                    };
                }
            }

            // ✅ Si es ROLE_ADMIN: cargar todos los productos con información completa de empresa
            const response = await fetch(`${this.PRODUCTOS_URL}/productos`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                signal: AbortSignal.timeout(5000)
            });

            if (response.ok) {
                const productos = await response.json();
                console.log(`✅ Endpoint básico funcionó: ${productos.length} productos`);

                const productosEmprendedor: ProductoEmprendedor[] = productos.map((producto: any) =>
                    this.mapProductoDtoToEmprendedor(producto)
                );

                if (productosEmprendedor.length > 0) {
                    console.log(`🔄 Iniciando actualización batch de stock (admin)...`);
                    this.actualizarStockEnSegundoPlano(productosEmprendedor);
                }

                return {
                    success: true,
                    status: response.status,
                    data: productosEmprendedor,
                    message: 'Productos obtenidos exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(
                    response,
                    `Error ${response.status}: ${response.statusText}`
                );
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            console.error('❌ Error obteniendo productos emprendedor:', error);
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async obtenerProductoPorId(id: number): Promise<ApiResponse<ProductoEmprendedor>> {
        try {
            const response = await fetch(`${this.PRODUCTOS_URL}/productos/${id}`, {
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                const producto = await response.json();
                const productoMapeado = this.mapProductoDtoToEmprendedor(producto);

                // 🔒 Validar alcance autorizado en frontend para ROLE_EMP
                if (!authService.isAdmin() && productoMapeado.empresa?.id && !authService.canManageEmpresa(productoMapeado.empresa.id)) {
                    return {
                        success: false,
                        status: 403,
                        message: 'Acceso denegado (403): El usuario no puede operar sobre este producto porque pertenece a otra empresa.'
                    };
                }

                return {
                    success: true,
                    status: response.status,
                    data: productoMapeado,
                    message: 'Producto obtenido exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(
                    response,
                    `Error ${response.status}: ${response.statusText}`
                );
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async crearProducto(producto: ProductoRequest): Promise<ApiResponse<ProductoEmprendedor>> {
        try {
            if (!authService.isAuthenticated()) {
                return {
                    success: false,
                    status: 401,
                    message: 'No autenticado (401). Por favor inicia sesión.'
                };
            }

            const userInfo = authService.getUserInfo();
            const userEmpresaId = authService.getEmpresaId();

            // 🔒 Para ROLE_EMP, validar alcance de empresa antes de enviar
            if (!authService.isAdmin()) {
                if (!userEmpresaId || Number(producto.empresaId) !== Number(userEmpresaId)) {
                    return {
                        success: false,
                        status: 403,
                        message: 'Acceso denegado (403): El usuario no puede operar sobre esta empresa.'
                    };
                }
            }

            const emprendedorId = producto.emprendedorId ?? userInfo?.id ?? userEmpresaId ?? producto.empresaId;
            const payload = { ...producto, emprendedorId };

            const response = await fetch(`${this.PRODUCTOS_URL}/productos`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(payload)
            });

            if (response.ok) {
                const nuevoProducto = await response.json();
                return {
                    success: true,
                    status: response.status,
                    data: nuevoProducto,
                    message: 'Producto creado exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(response, 'Error creando producto');
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async actualizarProducto(id: number, producto: ProductoRequest): Promise<ApiResponse<ProductoEmprendedor>> {
        try {
            if (!authService.isAuthenticated()) {
                return {
                    success: false,
                    status: 401,
                    message: 'No autenticado (401). Por favor inicia sesión.'
                };
            }

            const userInfo = authService.getUserInfo();
            const userEmpresaId = authService.getEmpresaId();

            // 🔒 Para ROLE_EMP, validar alcance de empresa antes de enviar
            if (!authService.isAdmin()) {
                if (!userEmpresaId || Number(producto.empresaId) !== Number(userEmpresaId)) {
                    return {
                        success: false,
                        status: 403,
                        message: 'Acceso denegado (403): El usuario no puede operar sobre esta empresa.'
                    };
                }
            }

            const emprendedorId = producto.emprendedorId ?? userInfo?.id ?? userEmpresaId ?? producto.empresaId;
            const payload = { ...producto, emprendedorId };

            const response = await fetch(`${this.PRODUCTOS_URL}/productos/${id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                },
                body: JSON.stringify(payload)
            });

            if (response.ok) {
                const productoActualizado = await response.json();
                return {
                    success: true,
                    status: response.status,
                    data: productoActualizado,
                    message: 'Producto actualizado exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(response, 'Error actualizando producto');
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    async eliminarProducto(id: number): Promise<ApiResponse<boolean>> {
        try {
            if (!authService.isAuthenticated()) {
                return {
                    success: false,
                    status: 401,
                    message: 'No autenticado (401). Por favor inicia sesión.'
                };
            }

            const response = await fetch(`${this.PRODUCTOS_URL}/productos/${id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                    ...authService.getAuthHeaders()
                }
            });

            if (response.ok) {
                return {
                    success: true,
                    status: response.status,
                    data: true,
                    message: 'Producto eliminado exitosamente'
                };
            } else {
                const errorMsg = await authService.extractErrorMessage(response, 'Error eliminando producto');
                return {
                    success: false,
                    status: response.status,
                    message: errorMsg
                };
            }
        } catch (error: any) {
            return {
                success: false,
                message: `Error de conexión: ${error.message}`
            };
        }
    }

    // ================ UTILIDADES ================
    formatearPrecio(precio: number): string {
        return new Intl.NumberFormat('es-EC', {
            style: 'currency',
            currency: 'USD'
        }).format(precio);
    }

    formatearFecha(fecha: string): string {
        return new Date(fecha).toLocaleDateString('es-EC', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    obtenerEstadoColor(estado: string | boolean): string {
        if (typeof estado === 'boolean') {
            return estado ? 'success' : 'danger';
        }

        switch (estado.toLowerCase()) {
            case 'completada':
            case 'activo':
            case 'true':
                return 'success';
            case 'pendiente':
                return 'warning';
            case 'cancelada':
            case 'inactivo':
            case 'false':
                return 'danger';
            default:
                return 'info';
        }
    }

    validarRUC(ruc: string): boolean {
        // Validación básica de RUC ecuatoriano (13 dígitos)
        const rucPattern = /^[0-9]{13}$/;
        return rucPattern.test(ruc);
    }

    validarEmail(email: string): boolean {
        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailPattern.test(email);
    }
}

export default new EmprendedorService();
