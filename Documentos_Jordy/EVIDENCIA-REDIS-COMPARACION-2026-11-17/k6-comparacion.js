import http from 'k6/http';
import { check } from 'k6';

const target = __ENV.TARGET_URL;
const targetIp = __ENV.TARGET_IP;
const rate = Number(__ENV.LOAD_RPS || 10);
const duration = __ENV.DURATION || '5m';

if (!target) throw new Error('Falta TARGET_URL.');
if (!Number.isFinite(rate) || rate <= 0) throw new Error('LOAD_RPS debe ser positivo.');

export const options = {
  hosts: targetIp ? { 'tienda.gadmsigchos.gob.ec': targetIp } : undefined,
  scenarios: {
    catalogo: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(20, rate * 2),
      maxVUs: Math.max(50, rate * 5),
      gracefulStop: '15s',
      tags: { endpoint: 'listado-completo', cache_mode: __ENV.CACHE_MODE || 'unspecified' },
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
    checks: [{ threshold: 'rate>0.99', abortOnFail: true, delayAbortEval: '10s' }],
    dropped_iterations: [{ threshold: 'count==0' }],
  },
};

export default function () {
  const response = http.get(target, { timeout: '8s', redirects: 0 });
  let bodyIsArray = false;
  try {
    bodyIsArray = Array.isArray(response.json());
  } catch (_) {
    bodyIsArray = false;
  }
  check(response, {
    'HTTP 200': (r) => r.status === 200,
    'JSON es una lista': () => bodyIsArray,
  });
}
