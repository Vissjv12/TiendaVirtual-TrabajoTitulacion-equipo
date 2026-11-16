import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8090';
const endpoints = [
  '/health',
  '/api/productos/listado',
  '/api/productos/1',
  '/api/categorias',
  '/api/empresas',
  '/api/inventarios/producto/1',
];

export default function () {
  const path = endpoints[Math.floor(Math.random() * endpoints.length)];
  const response = http.get(`${baseUrl}${path}`, {
    tags: { endpoint: path },
  });

  check(response, {
    'status 200': (res) => res.status === 200,
  });
}
