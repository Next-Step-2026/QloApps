import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 20 }, // Ramp-up para 20 VUs
    { duration: '30s', target: 50 }, // Carga constante de 50 VUs
    { duration: '10s', target: 0 },  // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<15'], // 95% das avaliações de política devem responder em < 15ms
    http_req_failed: ['rate<0.01'],   // Menos de 1% de falhas
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://127.0.0.1:8105';

export default function () {
  const payload = JSON.stringify({
    policy: 'OVERBOOKING_LIMIT',
    facts: {
      total_capacity: 100,
      current_occupied: 98,
      requested_units: 2,
      max_overbooking_rate: 0.05,
    },
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-ID': `k6-${__VU}-${__ITER}`,
    },
  };

  const res = http.post(`${BASE_URL}/v1/policy-evaluations`, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'decision is present': (r) => JSON.parse(r.body).decision !== undefined,
  });

  sleep(0.05);
}
