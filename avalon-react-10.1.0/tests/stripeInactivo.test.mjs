// Stripe queda inactivo por decisión del proyecto: el checkout no puede iniciar pagos con Stripe.
// Los archivos StripeCheckoutForm.tsx y stripeService.ts se conservan para una evaluación futura.
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const CHECKOUTS = ['app/(main)/checkout/page.tsx', 'app/(landing)/checkout/page.tsx'];
const SELECTORES = [
    'app/(main)/checkout/components/PaymentMethodSelector.tsx',
    'app/(landing)/checkout/components/PaymentMethodSelector.tsx'
];

const leer = (rel) => readFileSync(join(RAIZ, rel), 'utf8');
const sinComentarios = (codigo) => codigo.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');

describe('Stripe inactivo en el checkout', () => {
    for (const rel of CHECKOUTS) {
        test(`${rel} no importa ni usa Stripe`, () => {
            const codigo = sinComentarios(leer(rel));
            assert.doesNotMatch(codigo, /stripeService|StripeCheckoutForm|crearPaymentIntent|PaymentIntent/);
            assert.doesNotMatch(codigo, /'TARJETA'/, 'el checkout manual no debe crear ventas con TARJETA (solo PayPhone)');
        });

        test(`${rel} rechaza métodos distintos de transferencia y Deuna antes de crear la venta`, () => {
            const codigo = leer(rel);
            const guardia = codigo.indexOf("selectedPaymentMethod !== 'transferencia' && selectedPaymentMethod !== 'deuna'");
            const crear = codigo.indexOf('cartService.processCheckout(');
            assert.ok(guardia > -1 && crear > -1 && guardia < crear);
        });
    }

    for (const rel of SELECTORES) {
        test(`${rel} no ofrece la opción de Stripe`, () => {
            assert.doesNotMatch(sinComentarios(leer(rel)), /value:\s*'tarjeta'/);
        });
    }
});
