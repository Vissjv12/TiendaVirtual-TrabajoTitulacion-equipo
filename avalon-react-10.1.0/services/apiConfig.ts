/**
 * Configuración centralizada del API Gateway (Actividad A-13)
 * Unifica todas las llamadas HTTP del frontend hacia el punto único de entrada (puerto 8080),
 * eliminando el consumo directo de los puertos internos 8081, 8082, 8083 y 8084.
 */

const DEFAULT_GATEWAY_URL = 'http://localhost:8080';

function trimTrailingSlash(url: string): string {
    return url.replace(/\/+$/, '');
}

/**
 * URL base del API Gateway sin sufijo /api (ej. "http://localhost:8080").
 * Usada por servicios y componentes que ya incluyen "/api/..." en sus rutas:
 * - authService, usuariosService, cartService, clientesService, ventasService, inventarioService
 * - configuracion-pagos, PayphoneForm, DeunaForm, historial-compras
 */
export const GATEWAY_BASE_URL: string = trimTrailingSlash(
    process.env.NEXT_PUBLIC_GATEWAY_URL || DEFAULT_GATEWAY_URL
);

/**
 * URL base del API Gateway con sufijo /api (ej. "http://localhost:8080/api").
 * Usada por servicios cuyas rutas relativas empiezan en "/productos", "/categorias", etc.:
 * - categoriasEmpresasService, emprendedorService, productService (PRODUCTOS_URL),
 *   logisticaService, pagoService, stripeService
 */
export const GATEWAY_API_URL: string = `${GATEWAY_BASE_URL}/api`;

/**
 * URL base de inventarios en el API Gateway (ej. "http://localhost:8080/api/inventarios").
 * Usada por productService (INVENTARIO_URL).
 */
export const GATEWAY_INVENTARIOS_URL: string = `${GATEWAY_BASE_URL}/api/inventarios`;

/**
 * Normaliza URLs de recursos estáticos (ej. /uploads/qr-deuna/...) para que se carguen
 * siempre a través del API Gateway y nunca desde los puertos internos (8081-8084).
 */
export function resolveGatewayAssetUrl(assetUrl?: string | null): string {
    if (!assetUrl) return '';
    const normalized = assetUrl.replace(/^https?:\/\/(localhost|127\.0\.0\.1):808[1-4]/i, GATEWAY_BASE_URL);
    if (normalized.startsWith('/')) {
        return `${GATEWAY_BASE_URL}${normalized}`;
    }
    return normalized;
}
