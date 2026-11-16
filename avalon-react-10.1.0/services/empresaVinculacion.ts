// T11: lógica pura de la vinculación emprendedor-empresa (sin React ni fetch, para poder probarla).

export interface EmpresaBasica {
    id: number;
    nombre: string;
    activo: boolean;
}

export interface OpcionEmpresa {
    label: string;
    value: number;
    disabled: boolean;
}

/**
 * Empresas que el administrador puede elegir: solo las activas (el backend rechaza las inactivas con 409).
 * La empresa vinculada actualmente se muestra aunque esté inactiva, marcada y no seleccionable,
 * para que el administrador vea el estado real.
 */
export function opcionesEmpresasVinculables(empresas: EmpresaBasica[] | null | undefined, empresaActualId?: number | null): OpcionEmpresa[] {
    const lista = Array.isArray(empresas) ? empresas : [];
    return lista
        .filter((e) => e && Number.isFinite(e.id) && (e.activo || e.id === empresaActualId))
        .map((e) => ({
            label: e.activo ? `${e.nombre} (#${e.id})` : `${e.nombre} (#${e.id}) — inactiva`,
            value: e.id,
            disabled: !e.activo
        }))
        .sort((a, b) => a.label.localeCompare(b.label, 'es'));
}

export function nombreEmpresa(empresas: EmpresaBasica[] | null | undefined, empresaId?: number | null): string {
    if (empresaId == null) return 'Sin empresa vinculada';
    const empresa = (empresas || []).find((e) => e.id === empresaId);
    return empresa ? `${empresa.nombre} (#${empresa.id})` : `Empresa #${empresaId}`;
}

/** Mensaje para el administrador según el resultado del backend. */
export function mensajeResultadoVinculacion(resultado: string, requiereNuevoLogin: boolean): string {
    const base: Record<string, string> = {
        VINCULADA: 'Empresa vinculada al emprendedor.',
        REASIGNADA: 'Empresa reasignada al emprendedor.',
        DESVINCULADA: 'Empresa desvinculada del emprendedor.',
        SIN_CAMBIOS: 'No hubo cambios: la vinculación ya estaba así.'
    };
    const texto = base[resultado] ?? 'Operación realizada.';
    return requiereNuevoLogin
        ? `${texto} El emprendedor debe cerrar sesión y volver a ingresar para que se aplique.`
        : texto;
}

export function esEmprendedor(roles: string[] | null | undefined): boolean {
    return Array.isArray(roles) && roles.includes('ROLE_EMP');
}
