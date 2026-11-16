import { NextResponse } from 'next/server';

/**
 * Receptor de la "URL de Notificación" registrada en Payphone Developer (M-06).
 *
 * No confirma pagos ni maneja tokens (C-02): reenvía la notificación al backend
 * (POST /api/emprendedor/configuracion-pagos/payphone/notificacion), que vuelve a consultar
 * a Payphone con el token del comercio y finaliza la venta de forma idempotente. Así el pedido
 * se registra aunque el comprador cierre el navegador antes de volver a la tienda.
 *
 * Se conserva esta URL para no cambiar la configuración del portal de Payphone.
 * No se registra el cuerpo recibido para no escribir datos de pago en los logs.
 */
const MAX_BYTES = 16 * 1024;

const backendBase = (): string =>
    (process.env.GATEWAY_INTERNAL_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');

export async function POST(request: Request) {
    const cuerpo = await request.text();
    if (!cuerpo || cuerpo.length > MAX_BYTES) {
        return NextResponse.json({ recibido: false }, { status: 400 });
    }

    try {
        const respuesta = await fetch(`${backendBase()}/api/emprendedor/configuracion-pagos/payphone/notificacion`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: cuerpo,
            cache: 'no-store',
            signal: AbortSignal.timeout(20000)
        });
        // 200 procesada/ignorada · 400 inválida · 503 reintentable (Payphone vuelve a notificar)
        const estado = respuesta.ok ? 200 : respuesta.status === 400 ? 400 : 503;
        return NextResponse.json({ recibido: respuesta.ok }, { status: estado });
    } catch {
        console.warn('[Payphone notificación] backend no disponible; se solicitará reintento');
        return NextResponse.json({ recibido: false }, { status: 503 });
    }
}
