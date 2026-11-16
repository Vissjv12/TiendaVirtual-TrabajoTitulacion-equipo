// services/checkoutEmpresa.ts
//
// Regla única para decidir a qué empresa pertenece el pago del checkout (hallazgo C-01).
// Módulo puro, sin imports: lo usan pagoService y las pruebas (`npm test`).
//
// Falla cerrado: si algún producto no tiene empresa identificable, si los ids de empresa
// del producto se contradicen o si el carrito mezcla varias empresas, NO se resuelve ninguna
// empresa y no debe consultarse ningún dato de pago (QR Deuna ni cuenta bancaria).

export type MotivoBloqueoCarrito = 'CARRITO_VACIO' | 'PRODUCTO_SIN_EMPRESA' | 'MULTIPLES_EMPRESAS';

export type ResolucionEmpresaCarrito =
    | { ok: true; empresaId: number; empresaNombre?: string }
    | { ok: false; codigo: MotivoBloqueoCarrito; motivo: string };

export const MENSAJES_BLOQUEO: Record<MotivoBloqueoCarrito, string> = {
    CARRITO_VACIO: 'El carrito está vacío',
    PRODUCTO_SIN_EMPRESA: 'No se pudo identificar al emprendedor de uno o más productos',
    MULTIPLES_EMPRESAS: 'Disponible solo cuando todos los productos son del mismo emprendedor'
};

const aIdValido = (valor: unknown): number | null => {
    if (valor === null || valor === undefined || valor === '') return null;
    const numero = Number(valor);
    return Number.isInteger(numero) && numero > 0 ? numero : null;
};

/**
 * empresaId de un producto, o null si no es identificable.
 * Solo se aceptan `empresa.id` y `empresaId` (nunca `emprendedorId` ni un id fijo).
 * Si ambos existen y no coinciden, el producto se considera ambiguo y devuelve null.
 */
export const obtenerEmpresaIdProducto = (producto: any): number | null => {
    const desdeEmpresa = aIdValido(producto?.empresa?.id);
    const desdeCampo = aIdValido(producto?.empresaId);
    const presenteEmpresa = producto?.empresa?.id !== undefined && producto?.empresa?.id !== null;
    const presenteCampo = producto?.empresaId !== undefined && producto?.empresaId !== null;

    // Un valor presente pero inválido (p. ej. "abc", 0) invalida el producto
    if ((presenteEmpresa && desdeEmpresa === null) || (presenteCampo && desdeCampo === null)) {
        return null;
    }
    if (desdeEmpresa !== null && desdeCampo !== null && desdeEmpresa !== desdeCampo) {
        return null;
    }
    return desdeEmpresa ?? desdeCampo;
};

/**
 * Resuelve la única empresa del carrito o indica por qué no puede resolverse.
 */
export const resolverEmpresaUnicaCarrito = (
    cartItems: Array<{ producto?: any }> | null | undefined
): ResolucionEmpresaCarrito => {
    const items = Array.isArray(cartItems) ? cartItems : [];
    if (items.length === 0) {
        return { ok: false, codigo: 'CARRITO_VACIO', motivo: MENSAJES_BLOQUEO.CARRITO_VACIO };
    }

    let empresaId: number | null = null;
    let empresaNombre: string | undefined;

    for (const item of items) {
        const id = obtenerEmpresaIdProducto(item?.producto);
        if (id === null) {
            return { ok: false, codigo: 'PRODUCTO_SIN_EMPRESA', motivo: MENSAJES_BLOQUEO.PRODUCTO_SIN_EMPRESA };
        }
        if (empresaId === null) {
            empresaId = id;
            empresaNombre = item?.producto?.empresa?.nombre;
        } else if (empresaId !== id) {
            return { ok: false, codigo: 'MULTIPLES_EMPRESAS', motivo: MENSAJES_BLOQUEO.MULTIPLES_EMPRESAS };
        }
    }

    return { ok: true, empresaId: empresaId as number, empresaNombre };
};

export interface DisponibilidadMetodo<T> {
    disponible: boolean;
    motivo?: string;
    empresaId?: number;
    datos?: T;
}

/**
 * Verifica un método de pago para el carrito. `consultar` solo se invoca si el carrito
 * resuelve a una única empresa; si devuelve null, el método no está configurado.
 */
export const verificarDisponibilidadMetodo = async <T>(
    cartItems: Array<{ producto?: any }> | null | undefined,
    consultar: (empresaId: number, empresaNombre?: string) => Promise<T | null>,
    motivoSinConfiguracion: string
): Promise<DisponibilidadMetodo<T>> => {
    const resolucion = resolverEmpresaUnicaCarrito(cartItems);
    if (!resolucion.ok) {
        return { disponible: false, motivo: resolucion.motivo };
    }
    const datos = await consultar(resolucion.empresaId, resolucion.empresaNombre);
    if (datos === null || datos === undefined) {
        return { disponible: false, motivo: motivoSinConfiguracion, empresaId: resolucion.empresaId };
    }
    return { disponible: true, empresaId: resolucion.empresaId, datos };
};

export const MOTIVO_DEUNA_SIN_CONFIGURAR = 'El emprendedor aún no ha configurado Deuna';
export const MOTIVO_TRANSFERENCIA_SIN_CONFIGURAR = 'El emprendedor aún no ha configurado sus datos bancarios';
