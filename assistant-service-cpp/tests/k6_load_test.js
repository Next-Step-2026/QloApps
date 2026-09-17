import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2s', target: 10 },  // Ramp-up para 10 VUs
    { duration: '5s', target: 20 },  // Carga estavel de 20 VUs
    { duration: '2s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    // SLA da RFC-001: Latência P95 < 20ms no serviço C++
    http_req_duration: ['p(95)<20'],
    // Taxa de falha máxima permitida < 1%
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://127.0.0.1:8101';

const QUERIES = [
  'tem quarto deluxe para depois de amanha para 2 pessoas?',
  'preciso de um quarto standard para amanha',
  'qual a politica de cancelamento para reservas de feriado?',
  'qual o horario de check-in tardio e regras de no-show?',
  'consultar status da minha reserva RES-9941',
  'qual o cardapio do restaurante para hoje?',
];

export default function () {
  const query = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const correlationId = `k6-test-${__VU}-${__ITER}`;

  const payload = JSON.stringify({
    query: query,
    reference_date: '2026-08-27',
    locale: 'pt-BR',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-ID': correlationId,
    },
  };

  const res = http.post(`${BASE_URL}/v1/assist/interpret`, payload, params);

  check(res, {
    'status code e 200': (r) => r.status === 200,
    'retorna correlation_id': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.correlation_id === correlationId;
      } catch (e) {
        return false;
      }
    },
    'retorna intent valida': (r) => {
      try {
        const body = JSON.parse(r.body);
        return ['AVAILABILITY_QUERY', 'POLICY_QUERY', 'RESERVATION_LOOKUP', 'UNKNOWN'].includes(body.intent);
      } catch (e) {
        return false;
      }
    },
    'tempo de resposta < 20ms': (r) => r.timings.duration < 20,
  });

  sleep(0.05); // 50ms de pausa entre iteracoes
}
