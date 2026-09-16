import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '3s', target: 20 },  // Rampa de subida rápida para 20 VUs
    { duration: '10s', target: 20 }, // Carga constante de 20 VUs
    { duration: '2s', target: 0 },   // Rampa de descida
  ],
  thresholds: {
    http_req_duration: ['p(95)<5', 'p(99)<10'], // Validação estrita do SLA da RFC-008 (< 5ms)
    http_req_failed: ['rate<0.001'],            // Taxa de falhas < 0.1%
  },
};

const CATALOG = [
  {
    id: "suite-master-01",
    title: "Suíte Master Vista Mar",
    capacity_adults: 2,
    amenities: ["vista_mar", "banheira", "ar_condicionado"],
    aliases: ["suite", "vista mar"]
  },
  {
    id: "standard-casal-02",
    title: "Quarto Standard Casal",
    capacity_adults: 2,
    amenities: ["ar_condicionado", "wifi"],
    aliases: ["standard", "casal"]
  },
  {
    id: "single-solteiro-03",
    title: "Quarto Single Individual",
    capacity_adults: 1,
    amenities: ["ventilador"],
    aliases: ["single", "solteiro"]
  }
];

const QUERIES = [
  "suite para 2 adultos com vista mar",
  "quarto casal com ar condicionado",
  "suite com banheira",
  "quarto standard para 2 pessoas",
  "single para 1 hospede"
];

export default function () {
  const url = __ENV.TARGET_URL || 'http://127.0.0.1:8108/v1/search/parse';
  const query = QUERIES[Math.floor(Math.random() * QUERIES.length)];

  const payload = JSON.stringify({
    query: query,
    catalog: CATALOG,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-ID': `k6-test-${__VU}-${__ITER}`,
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'total_matches >= 1': (r) => {
      try {
        const json = JSON.parse(r.body);
        return json.total_matches >= 1;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.01);
}
