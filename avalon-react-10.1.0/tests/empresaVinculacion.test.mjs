// T11: vinculación emprendedor-empresa desde la administración. Ejecutar con: npm test
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
    opcionesEmpresasVinculables,
    nombreEmpresa,
    mensajeResultadoVinculacion,
    esEmprendedor
} from '../services/empresaVinculacion.ts';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const leer = (rel) => readFileSync(join(RAIZ, rel), 'utf8');

const EMPRESAS = [
    { id: 2, nombre: 'Zeta', activo: true },
    { id: 1, nombre: 'Alfa', activo: true },
    { id: 3, nombre: 'Cerrada', activo: false }
];

describe('opciones de empresas vinculables', () => {
    test('solo empresas activas, ordenadas por nombre', () => {
        const opciones = opcionesEmpresasVinculables(EMPRESAS, null);
        assert.deepEqual(opciones.map((o) => o.value), [1, 2]);
        assert.ok(opciones.every((o) => !o.disabled));
    });

    test('la empresa actual inactiva se muestra pero no se puede elegir', () => {
        const opciones = opcionesEmpresasVinculables(EMPRESAS, 3);
        const actual = opciones.find((o) => o.value === 3);
        assert.ok(actual);
        assert.equal(actual.disabled, true);
        assert.match(actual.label, /inactiva/);
    });

    test('entradas vacías o inválidas no rompen la pantalla', () => {
        assert.deepEqual(opcionesEmpresasVinculables(null, null), []);
        assert.deepEqual(opcionesEmpresasVinculables([{ id: NaN, nombre: 'x', activo: true }], null), []);
    });
});

describe('textos para el administrador', () => {
    test('nombre de la empresa vinculada o aviso de que no tiene', () => {
        assert.equal(nombreEmpresa(EMPRESAS, 1), 'Alfa (#1)');
        assert.equal(nombreEmpresa(EMPRESAS, null), 'Sin empresa vinculada');
        assert.equal(nombreEmpresa(EMPRESAS, 99), 'Empresa #99');
    });

    test('avisa que hace falta un nuevo inicio de sesión cuando cambia la empresa', () => {
        assert.match(mensajeResultadoVinculacion('VINCULADA', true), /volver a ingresar/);
        assert.doesNotMatch(mensajeResultadoVinculacion('SIN_CAMBIOS', false), /volver a ingresar/);
    });

    test('solo ROLE_EMP puede vincularse', () => {
        assert.equal(esEmprendedor(['ROLE_EMP']), true);
        assert.equal(esEmprendedor(['ROLE_USER']), false);
        assert.equal(esEmprendedor(undefined), false);
    });
});

describe('integración con el backend', () => {
    test('el servicio usa PUT para vincular y DELETE para desvincular (nunca empresaId null)', () => {
        const codigo = leer('services/usuariosService.ts');
        assert.match(codigo, /\/api\/usuarios\/\$\{usuarioId\}\/empresa`,\s*\{\s*method: 'PUT'/);
        assert.match(codigo, /\/api\/usuarios\/\$\{usuarioId\}\/empresa`,\s*\{\s*method: 'DELETE'/);
    });

    test('la pantalla de edición muestra la tarjeta con los roles GUARDADOS del usuario', () => {
        const pagina = leer('app/(full-page)/administrador/usuarios/[id]/editar/page.tsx');
        assert.match(pagina, /<EmpresaVinculadaCard[\s\S]*rolesGuardados=\{usuario\.roles\}/);
    });
});
