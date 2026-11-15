// Pruebas del flujo Payphone sin token en el navegador (hallazgo C-02). Ejecutar con: npm test
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, relative, sep, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
    firmaCarrito,
    ventaPendienteReutilizable,
    ventaIdDesdeClientTxId,
    pedidoRegistrado
} from '../services/payphoneCheckout.ts';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');

describe('venta creada antes del pago (A-03)', () => {
    const carrito = [
        { producto: { id: 5 }, cantidad: 2 },
        { producto: { id: 3 }, cantidad: 1 }
    ];

    test('la firma del carrito no depende del orden y cambia con las cantidades', () => {
        assert.equal(firmaCarrito(carrito), firmaCarrito([...carrito].reverse()));
        assert.notEqual(firmaCarrito(carrito), firmaCarrito([{ producto: { id: 5 }, cantidad: 3 }, carrito[1]]));
        assert.equal(firmaCarrito(null), '');
    });

    test('se reutiliza la venta pendiente solo si el carrito es el mismo', () => {
        const firma = firmaCarrito(carrito);
        const pendiente = { ventaId: 77, clientTransactionId: 'FD-77-abcdef', firmaCarrito: firma };
        assert.equal(ventaPendienteReutilizable(pendiente, firma), 77);
        assert.equal(ventaPendienteReutilizable(pendiente, firmaCarrito([carrito[0]])), null);
        assert.equal(ventaPendienteReutilizable(null, firma), null);
        assert.equal(ventaPendienteReutilizable({ ...pendiente, ventaId: 0 }, firma), null);
        assert.equal(ventaPendienteReutilizable(pendiente, ''), null);
    });
});

describe('confirmación (falla cerrado)', () => {
    test('el ventaId se extrae solo de clientTransactionId propios', () => {
        assert.equal(ventaIdDesdeClientTxId('FD-77-abcdef123'), 77);
        for (const valor of [null, '', 'ORD-123', 'FD--abcdef', 'FD-0-abcdef', 'FD-12-abc', 'FD-12-a b c d', 'FD-1x-abcdef']) {
            assert.equal(ventaIdDesdeClientTxId(valor), null, String(valor));
        }
    });

    test('el pedido solo se da por registrado si Payphone aprobó y la venta lo aceptó', () => {
        assert.equal(pedidoRegistrado({ statusCode: 3, resultadoVenta: 'COMPLETADA' }), true);
        assert.equal(pedidoRegistrado({ statusCode: 3, resultadoVenta: 'YA_PROCESADA' }), true);
        assert.equal(pedidoRegistrado({ statusCode: 3, resultadoVenta: 'STOCK_PENDIENTE' }), true);
        assert.equal(pedidoRegistrado({ statusCode: 3, resultadoVenta: 'MONTO_NO_COINCIDE' }), false);
        assert.equal(pedidoRegistrado({ statusCode: 3, resultadoVenta: 'REQUIERE_REVISION' }), false);
        assert.equal(pedidoRegistrado({ statusCode: 3 }), false);
        assert.equal(pedidoRegistrado({ statusCode: 2, resultadoVenta: 'COMPLETADA' }), false);
        assert.equal(pedidoRegistrado(null), false);
    });
});

// ------------------------------------------------------------------ barrido del código fuente

const IGNORAR = new Set(['node_modules', '.next', 'tests', 'public', '.git']);
const EXTENSIONES = /\.(ts|tsx|js|jsx|mjs)$/;

const archivosFuente = (dir) => {
    const salida = [];
    for (const nombre of readdirSync(dir)) {
        if (IGNORAR.has(nombre)) continue;
        const ruta = join(dir, nombre);
        const info = statSync(ruta);
        if (info.isDirectory()) salida.push(...archivosFuente(ruta));
        else if (EXTENSIONES.test(nombre)) salida.push(ruta);
    }
    return salida;
};

const fuentes = archivosFuente(RAIZ).map((ruta) => ({
    ruta: relative(RAIZ, ruta).split(sep).join('/'),
    texto: readFileSync(ruta, 'utf8')
}));

// Única pantalla que ESCRIBE el token (formulario del emprendedor); nunca lo lee de una respuesta
const PANTALLA_CONFIGURACION = 'app/(full-page)/emprendedor/configuracion-pagos/page.tsx';

describe('el navegador no maneja el token de Payphone', () => {
    test('se encontraron fuentes para revisar', () => {
        assert.ok(fuentes.length > 50, `solo ${fuentes.length} archivos`);
        assert.ok(fuentes.some((f) => f.ruta === PANTALLA_CONFIGURACION));
    });

    test('ningún archivo usa NEXT_PUBLIC_PAYPHONE_*', () => {
        const culpables = fuentes.filter((f) => /NEXT_PUBLIC_PAYPHONE/i.test(f.texto)).map((f) => f.ruta);
        assert.deepEqual(culpables, []);
    });

    test('ningún archivo .env del frontend define variables PAYPHONE públicas', () => {
        for (const nombre of ['.env', '.env.local', '.env.local.example', '.env.production', '.env.development']) {
            const ruta = join(RAIZ, nombre);
            if (!existsSync(ruta)) continue;
            assert.doesNotMatch(readFileSync(ruta, 'utf8'), /^\s*NEXT_PUBLIC_PAYPHONE/im, nombre);
        }
    });

    test('payphoneToken solo aparece en el formulario de configuración del emprendedor', () => {
        const culpables = fuentes
            .filter((f) => f.ruta !== PANTALLA_CONFIGURACION && /payphoneToken/.test(f.texto))
            .map((f) => f.ruta);
        assert.deepEqual(culpables, []);
    });

    test('el formulario de configuración no lee el token de ninguna respuesta', () => {
        const texto = fuentes.find((f) => f.ruta === PANTALLA_CONFIGURACION).texto;
        assert.doesNotMatch(texto, /\.payphoneToken\b/);
    });

    test('no se carga el SDK de botón de Payphone que exige el token en el cliente', () => {
        const culpables = fuentes
            .filter((f) => /payphone\.Button\s*\(|api\/button\/js/.test(f.texto))
            .map((f) => f.ruta);
        assert.deepEqual(culpables, []);
    });

    test('el navegador no envía montos al preparar el cobro (A-03)', () => {
        const servicio = fuentes.find((f) => f.ruta === 'services/payphoneService.ts').texto;
        const preparar = servicio.slice(servicio.indexOf('async preparar('), servicio.indexOf('async confirmarPago('));
        assert.match(preparar, /ventas\/\$\{ventaId\}\/preparar/);
        assert.doesNotMatch(preparar, /body\s*:/);
        assert.doesNotMatch(preparar, /amount|total|monto/i);
    });

    test('la confirmación no crea ni completa la venta desde el navegador (A-04)', () => {
        const pagina = fuentes.find((f) => f.ruta === 'app/(main)/checkout/payphone-confirmacion/page.tsx').texto;
        assert.doesNotMatch(pagina, /processCheckout|completarVenta/);
    });

    test('no se registran tokens ni configuraciones de Payphone en consola', () => {
        const patron = /console\.\w+\([^)]*(payphoneToken|cleanToken|configData|Token dinámico|Body recibido)/i;
        const culpables = fuentes.filter((f) => patron.test(f.texto)).map((f) => f.ruta);
        assert.deepEqual(culpables, []);
    });
});

describe('A-02: el emprendedor no edita RUC ni estado de su empresa', () => {
    const PAGINA_EMP = 'app/(full-page)/emprendedor/empresas/[id]/editar/page.tsx';

    test('la pantalla del emprendedor usa solo el endpoint de contacto', () => {
        const texto = fuentes.find((f) => f.ruta === PAGINA_EMP).texto;
        assert.match(texto, /actualizarDatosContactoEmpresa\(/);
        assert.doesNotMatch(texto, /categoriasEmpresasService\.actualizarEmpresa\(/);
        assert.doesNotMatch(texto, /handleInputChange\('activo'/);
        assert.doesNotMatch(texto, /handleInputChange\('ruc'/);
    });

    test('el servicio de contacto no envía ruc ni activo', () => {
        const servicio = fuentes.find((f) => f.ruta === 'services/categoriasEmpresasService.ts').texto;
        const inicio = servicio.indexOf('async actualizarDatosContactoEmpresa(');
        const metodo = servicio.slice(inicio, servicio.indexOf('async eliminarEmpresa(', inicio));
        assert.match(metodo, /\/contacto/);
        assert.doesNotMatch(metodo, /ruc|activo/);
    });
});
