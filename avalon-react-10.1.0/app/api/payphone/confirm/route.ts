import { NextResponse } from 'next/server';

// Función auxiliar para hacer fetch con reintentos
async function fetchWithRetry(url: string, options: RequestInit, retries = 3, timeoutMs = 15000): Promise<Response> {
    for (let attempt = 1; attempt <= retries; attempt++) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

        try {
            console.log(`[Payphone Confirm] Intento ${attempt}/${retries} → ${url}`);
            const response = await fetch(url, { ...options, signal: controller.signal });
            clearTimeout(timeoutId);
            return response;
        } catch (err: any) {
            clearTimeout(timeoutId);
            const isLastAttempt = attempt === retries;
            const isTimeout = err?.name === 'AbortError' || err?.cause?.code === 'UND_ERR_CONNECT_TIMEOUT';

            console.warn(`[Payphone Confirm] Intento ${attempt} fallido:`, err?.cause?.code || err?.message);

            if (isLastAttempt) throw err;

            // Esperar antes de reintentar (backoff)
            const delay = attempt * 2000; // 2s, 4s, 6s...
            console.log(`[Payphone Confirm] Reintentando en ${delay}ms...`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
    throw new Error('Todos los reintentos agotados');
}

export async function POST(request: Request) {
    try {
        const body = await request.json();
        const { id, clientTxId, emprendedorId } = body;

        console.log('[Payphone Confirm] Iniciando proceso de confirmación');
        console.log('[Payphone Confirm] Body recibido:', JSON.stringify(body));

        if (!id || !clientTxId) {
            console.warn('[Payphone Confirm] Faltan parámetros:', { id, clientTxId });
            return NextResponse.json({ error: 'Faltan parámetros requeridos (id o clientTxId)' }, { status: 400 });
        }

        // Intentar obtener el token de la base de datos si tenemos el emprendedorId
        let token = process.env.PAYPHONE_TOKEN || process.env.NEXT_PUBLIC_PAYPHONE_TOKEN;
        console.log('[Payphone Confirm] Token inicial (env):', token ? 'Configurado' : 'No configurado');
        
        // Validar que emprendedorId sea un valor útil y no sea el string "null" o "undefined"
        const validEmprendedorId = emprendedorId && emprendedorId !== 'null' && emprendedorId !== 'undefined';

        if (validEmprendedorId) {
            try {
                // Consumir a través del API Gateway (Actividad A-13), usando 127.0.0.1 por defecto para evitar resolución IPv6
                const gatewayBase = (process.env.GATEWAY_INTERNAL_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
                const configUrl = `${gatewayBase}/api/emprendedor/configuracion-pagos/payphone/${emprendedorId}`;
                console.log(`[Payphone Confirm] Buscando token en DB vía Gateway: ${configUrl}`);
                
                const configRes = await fetch(configUrl, { 
                    cache: 'no-store',
                    headers: { 'Accept': 'application/json' }
                });
                
                if (configRes.ok) {
                    const configData = await configRes.json();
                    console.log('[Payphone Confirm] Respuesta de DB recibida:', JSON.stringify(configData));
                    if (configData.payphoneToken) {
                        token = configData.payphoneToken;
                        console.log(`[Payphone Confirm] Token dinámico obtenido correctamente para emprendedor ${emprendedorId}`);
                    } else {
                        console.warn(`[Payphone Confirm] El emprendedor ${emprendedorId} no tiene payphoneToken en su configuración de DB.`);
                    }
                } else {
                    const errorText = await configRes.text().catch(() => 'No body');
                    console.warn(`[Payphone Confirm] Error al obtener config de DB (Status ${configRes.status}):`, errorText);
                }
            } catch (dbError: any) {
                console.error('[Payphone Confirm] Excepción conectando con el microservicio de configuración:', dbError.message);
            }
        } else {
            console.log('[Payphone Confirm] No se proporcionó un emprendedorId válido para búsqueda en DB. Valor recibido:', emprendedorId);
        }

        if (!token || token === 'AQUI_PON_TU_TOKEN_DE_PAYPHONE') {
            const errorMsg = 'Token de Payphone no configurado';
            const errorDetails = `No se encontró un token válido en la base de datos para emprendedorId: ${emprendedorId} (Válido: ${validEmprendedorId}). Tampoco hay un token por defecto en .env.local`;
            console.error('[Payphone Confirm] ERROR FINAL:', errorMsg, '| Details:', errorDetails);
            
            return NextResponse.json({ 
                error: errorMsg,
                details: errorDetails,
                receivedId: emprendedorId,
                isValid: validEmprendedorId
            }, { status: 500 });
        }

        // Limpiar el token por si acaso tiene espacios o saltos de línea
        const cleanToken = token.trim();
        console.log(`[Payphone Confirm] Usando token (primeros 10 caracteres): ${cleanToken.substring(0, 10)}...`);

        const payphoneBody = {
            id: Number(id), // Documentación Cajita: "Número entero que representa el identificador"
            clientTxId: String(clientTxId) // Documentación Cajita: Usa "clientTxId"
        };

        // Construir el referer completo (algunos servidores lo validan así)
        const fullUrl = new URL(request.url);
        const referer = fullUrl.toString();
        const origin = fullUrl.origin;

        console.log('[Payphone Confirm] Enviando a Payphone (PaymentBox API):', payphoneBody);

        let response: Response;
        try {
            response = await fetch("https://paymentbox.payphonetodoesposible.com/api/confirm", {


                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json, text/plain, */*",
                    "Authorization": `Bearer ${cleanToken}`,
                    "Referer": referer,
                    "Origin": origin,
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                },
                body: JSON.stringify(payphoneBody)
            });
        } catch (networkError: any) {


            console.error('[Payphone Confirm] Error de red:', networkError.message);
            return NextResponse.json({
                error: 'Error de red al contactar Payphone',
                details: networkError.message
            }, { status: 502 });
        }

        // Leer siempre como texto para evitar errores de parseo
        const responseText = await response.text();
        console.log('[Payphone Confirm] HTTP Status:', response.status);
        console.log('[Payphone Confirm] Body raw:', responseText);

        if (!response.ok) {
            console.error('[Payphone Confirm] Error desde Payphone:', response.status, responseText);
            return NextResponse.json({
                error: 'Error al comunicarse con Payphone',
                details: responseText,
                httpStatus: response.status
            }, { status: response.status });
        }

        // Si Payphone responde 200 con body vacío (común con pagos por App Payphone)
        if (!responseText || responseText.trim() === '') {
            console.warn('[Payphone Confirm] Body vacío con HTTP 200 → Pago aprobado por App Payphone');
            return NextResponse.json({
                statusCode: 3,
                transactionStatus: 'Approved',
                message: 'Pago confirmado correctamente'
            });
        }

        // Intentar parsear como JSON
        try {
            const data = JSON.parse(responseText);
            console.log('[Payphone Confirm] Respuesta JSON:', data);
            return NextResponse.json(data);
        } catch {
            // HTTP 200 pero respuesta no es JSON válido — tratar como éxito
            console.warn('[Payphone Confirm] Respuesta no es JSON pero fue HTTP 200:', responseText);
            return NextResponse.json({
                statusCode: 3,
                transactionStatus: 'Approved',
                message: responseText
            });
        }

    } catch (error) {
        console.error('[Payphone Confirm] Error interno:', error);
        return NextResponse.json({ error: 'Error interno del servidor' }, { status: 500 });
    }
}
