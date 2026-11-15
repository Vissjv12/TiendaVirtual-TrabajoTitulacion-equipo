// Pruebas de la regla de empresa única del checkout (hallazgo C-01).
// Ejecutar con: npm test   (usa node:test y la eliminación de tipos nativa de Node >= 22.18 / 23.6)
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
    obtenerEmpresaIdProducto,
    resolverEmpresaUnicaCarrito,
    verificarDisponibilidadMetodo,
    MENSAJES_BLOQUEO
} from '../services/checkoutEmpresa.ts';

const item = (producto) => ({ producto, cantidad: 1 });
const deEmpresa = (id, nombre = `Empresa ${id}`) => item({ id: id * 100, nombre: 'Producto', empresa: { id, nombre } });

/** Consulta simulada que registra cada llamada. */
const consultaEspia = (respuesta) => {
    const llamadas = [];
    const fn = async (empresaId, empresaNombre) => {
        llamadas.push({ empresaId, empresaNombre });
        return typeof respuesta === 'function' ? respuesta(empresaId) : respuesta;
    };
    return { fn, llamadas };
};

describe('obtenerEmpresaIdProducto', () => {
    test('usa empresa.id', () => {
        assert.equal(obtenerEmpresaIdProducto({ empresa: { id: 7 } }), 7);
    });

    test('usa empresaId cuando no hay objeto empresa', () => {
        assert.equal(obtenerEmpresaIdProducto({ empresaId: '8' }), 8);
    });

    test('ignora emprendedorId (no es un empresaId)', () => {
        assert.equal(obtenerEmpresaIdProducto({ emprendedorId: 5 }), null);
    });

    test('producto sin empresa devuelve null (no hay id por defecto)', () => {
        assert.equal(obtenerEmpresaIdProducto({}), null);
        assert.equal(obtenerEmpresaIdProducto(undefined), null);
        assert.equal(obtenerEmpresaIdProducto({ empresa: {} }), null);
    });

    test('ids inválidos devuelven null', () => {
        for (const id of [0, -3, 1.5, 'abc', '', NaN]) {
            assert.equal(obtenerEmpresaIdProducto({ empresa: { id } }), null, `id=${String(id)}`);
        }
    });

    test('empresa.id y empresaId contradictorios se consideran ambiguos', () => {
        assert.equal(obtenerEmpresaIdProducto({ empresa: { id: 1 }, empresaId: 2 }), null);
    });

    test('valor presente pero inválido invalida aunque el otro sea válido', () => {
        assert.equal(obtenerEmpresaIdProducto({ empresa: { id: 'x' }, empresaId: 3 }), null);
    });

    test('empresa.id y empresaId coincidentes son válidos', () => {
        assert.equal(obtenerEmpresaIdProducto({ empresa: { id: 4 }, empresaId: '4' }), 4);
    });
});

describe('resolverEmpresaUnicaCarrito', () => {
    test('carrito de una sola empresa resuelve su id y nombre', () => {
        const r = resolverEmpresaUnicaCarrito([deEmpresa(10, 'Tienda A'), deEmpresa(10, 'Tienda A')]);
        assert.deepEqual(r, { ok: true, empresaId: 10, empresaNombre: 'Tienda A' });
    });

    test('carrito vacío o nulo falla cerrado', () => {
        assert.equal(resolverEmpresaUnicaCarrito([]).codigo, 'CARRITO_VACIO');
        assert.equal(resolverEmpresaUnicaCarrito(null).codigo, 'CARRITO_VACIO');
    });

    test('producto válido + producto sin empresa.id falla cerrado', () => {
        const r = resolverEmpresaUnicaCarrito([deEmpresa(10), item({ id: 2, nombre: 'Sin empresa' })]);
        assert.equal(r.ok, false);
        assert.equal(r.codigo, 'PRODUCTO_SIN_EMPRESA');
    });

    test('el producto sin empresa también bloquea si va primero', () => {
        const r = resolverEmpresaUnicaCarrito([item({ id: 2 }), deEmpresa(10)]);
        assert.equal(r.codigo, 'PRODUCTO_SIN_EMPRESA');
    });

    test('producto con solo emprendedorId falla cerrado', () => {
        const r = resolverEmpresaUnicaCarrito([deEmpresa(10), item({ id: 3, emprendedorId: 10 })]);
        assert.equal(r.codigo, 'PRODUCTO_SIN_EMPRESA');
    });

    test('carrito con dos empresas falla cerrado', () => {
        const r = resolverEmpresaUnicaCarrito([deEmpresa(10), deEmpresa(20)]);
        assert.equal(r.ok, false);
        assert.equal(r.codigo, 'MULTIPLES_EMPRESAS');
        assert.equal(r.motivo, MENSAJES_BLOQUEO.MULTIPLES_EMPRESAS);
    });
});

describe('verificarDisponibilidadMetodo (Deuna y transferencia)', () => {
    test('carrito válido consulta una única vez a la empresa correcta', async () => {
        const espia = consultaEspia('/uploads/qr-deuna/qr_10.png');
        const r = await verificarDisponibilidadMetodo([deEmpresa(10, 'Tienda A'), deEmpresa(10, 'Tienda A')], espia.fn, 'sin config');
        assert.deepEqual(espia.llamadas, [{ empresaId: 10, empresaNombre: 'Tienda A' }]);
        assert.deepEqual(r, { disponible: true, empresaId: 10, datos: '/uploads/qr-deuna/qr_10.png' });
    });

    test('producto sin empresa.id: bloquea y NO consulta el backend', async () => {
        const espia = consultaEspia('no-deberia-usarse');
        const r = await verificarDisponibilidadMetodo([deEmpresa(10), item({ id: 2 })], espia.fn, 'sin config');
        assert.equal(r.disponible, false);
        assert.equal(r.motivo, MENSAJES_BLOQUEO.PRODUCTO_SIN_EMPRESA);
        assert.equal(espia.llamadas.length, 0);
    });

    test('carrito mezclado: bloquea y NO consulta ninguna empresa', async () => {
        const espia = consultaEspia({ banco: 'pichincha', numeroCuenta: '2200000010' });
        const r = await verificarDisponibilidadMetodo([deEmpresa(10), deEmpresa(20)], espia.fn, 'sin config');
        assert.equal(r.disponible, false);
        assert.equal(r.motivo, MENSAJES_BLOQUEO.MULTIPLES_EMPRESAS);
        assert.equal(espia.llamadas.length, 0);
    });

    test('carrito vacío: bloquea y NO consulta', async () => {
        const espia = consultaEspia('x');
        const r = await verificarDisponibilidadMetodo([], espia.fn, 'sin config');
        assert.equal(r.disponible, false);
        assert.equal(espia.llamadas.length, 0);
    });

    test('empresa sin configuración (404 -> null) queda no disponible con su motivo', async () => {
        const espia = consultaEspia(null);
        const r = await verificarDisponibilidadMetodo([deEmpresa(30)], espia.fn, 'El emprendedor aún no ha configurado Deuna');
        assert.deepEqual(r, { disponible: false, motivo: 'El emprendedor aún no ha configurado Deuna', empresaId: 30 });
        assert.equal(espia.llamadas.length, 1);
    });

    test('error de red se propaga (el checkout lo trata como no disponible)', async () => {
        const fallo = async () => {
            throw new Error('fallo de red');
        };
        await assert.rejects(verificarDisponibilidadMetodo([deEmpresa(10)], fallo, 'sin config'), /fallo de red/);
    });
});
