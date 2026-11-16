import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8290';
const expectedProducts = Number(__ENV.EXPECTED_PRODUCTS || 39);

export default function () {
  const response = http.get(`${baseUrl}/api/productos/listado`, {
    tags: { endpoint: '/api/productos/listado' },
  });

  let productCount = -1;
  if (response.status === 200) {
    try {
      const payload = response.json();
      if (Array.isArray(payload)) productCount = payload.length;
    } catch (_) {
      productCount = -1;
    }
  }

  check(response, {
    'status 200': (res) => res.status === 200,
    'catalog product count matches fixture': () => productCount === expectedProducts,
  });
}
