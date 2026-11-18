import authService from './authService';

export interface VisitasEstadisticas {
    total: number;
    hoy: number;
    fecha: string;
}

const PRODUCTOS_API_URL = (
    process.env.NEXT_PUBLIC_PRODUCTOS_API_URL || '/api/proxy/productos'
).replace(/\/+$/, '');

const VISITA_COOKIE = 'feriadigital_visit_day';

function yaRegistroVisita(): boolean {
    if (typeof document === 'undefined') return false;
    return document.cookie.split(';').some((cookie) => cookie.trim().startsWith(`${VISITA_COOKIE}=`));
}

function marcarVisitaRegistrada(): void {
    document.cookie = `${VISITA_COOKIE}=1; Max-Age=86400; Path=/; SameSite=Lax`;
}

class VisitasService {
    async registrarVisitaUnica(): Promise<boolean> {
        if (yaRegistroVisita()) return false;

        try {
            const response = await fetch(`${PRODUCTOS_API_URL}/visitas`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                signal: AbortSignal.timeout(5000)
            });

            if (!response.ok) return false;
            marcarVisitaRegistrada();
            return true;
        } catch {
            return false;
        }
    }

    async obtenerEstadisticas(): Promise<VisitasEstadisticas | null> {
        try {
            const response = await fetch(`${PRODUCTOS_API_URL}/visitas/estadisticas`, {
                method: 'GET',
                headers: authService.getAuthHeaders(),
                cache: 'no-store',
                signal: AbortSignal.timeout(8000)
            });

            if (!response.ok) return null;
            return await response.json();
        } catch {
            return null;
        }
    }
}

export default new VisitasService();
