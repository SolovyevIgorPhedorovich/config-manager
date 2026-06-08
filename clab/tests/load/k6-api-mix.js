// Нагрузочный сценарий: смешанная нагрузка на REST API (без тяжёлого скана).
// Имитирует работу многих операторов: чтение списка устройств, аудит, опрос
// статусов. Параллельно (опционально) держим фоновый скан.
//
//   k6 run -e API=http://localhost:8080 k6-api-mix.js
//
// Профиль ramp-up: 0→50→100 VU. Меняется через -e VUS_MAX / DURATION.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const API  = __ENV.API        || 'http://localhost:8080';
const USER = __ENV.ADMIN_USER || 'admin';
const PASS = __ENV.ADMIN_PASS || 'admin123';

const errorRate = new Rate('api_errors');

export const options = {
  scenarios: {
    api_mix: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '30s', target: Number(__ENV.VUS_MID || 50) },
        { duration: __ENV.HOLD || '2m',  target: Number(__ENV.VUS_MAX || 100) },
        { duration: '30s',                target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.02'],
    http_req_duration: ['p(95)<800'],
    api_errors:        ['rate<0.02'],
  },
};

export function setup() {
  const res = http.post(`${API}/api/v1/auth/login`,
    JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  return { token: res.json('token') };
}

export default function (data) {
  const auth = { headers: { Authorization: `Bearer ${data.token}` } };

  group('devices', () => {
    const r = http.get(`${API}/api/v1/devices`, auth);
    errorRate.add(r.status !== 200);
    check(r, { 'devices 200': (x) => x.status === 200 });
  });

  group('health', () => {
    const r = http.get(`${API}/api/actuator/health`);
    check(r, { 'health up': (x) => x.status === 200 });
  });

  group('audit-events', () => {
    const r = http.get(`${API}/api/v1/event/logs?page=0&size=20`, auth);
    errorRate.add(r.status >= 500);
    check(r, { 'events ok': (x) => x.status === 200 });
  });

  sleep(Math.random() * 1.5);
}
