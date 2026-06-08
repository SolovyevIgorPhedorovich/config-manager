// Нагрузочный сценарий: сквозное сканирование сети /24 (полный стенд на ~250
// устройств). Меряем время полного цикла "запуск скана → completed".
//
//   k6 run -e API=http://localhost:8080 k6-scan.js
//
// Параметры через -e:
//   API, ADMIN_USER, ADMIN_PASS, SUBNET, MASK, COMMUNITY, SSH_USER, SSH_PASS, VUS, ITERATIONS
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const API       = __ENV.API        || 'http://localhost:8080';
const USER      = __ENV.ADMIN_USER || 'admin';
const PASS      = __ENV.ADMIN_PASS || 'admin123';
const SUBNET    = __ENV.SUBNET     || '172.100.100.0';
const MASK      = __ENV.MASK       || '24';
const COMMUNITY = __ENV.COMMUNITY  || 'public';
const SSH_USER  = __ENV.SSH_USER   || 'netadmin';
const SSH_PASS  = __ENV.SSH_PASS   || 'netadmin123';

const scanDuration = new Trend('scan_duration_ms', true);
const devicesFound = new Trend('scan_devices_found');
const scanSuccess  = new Rate('scan_success');

export const options = {
  scenarios: {
    full_scan: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 2),
      iterations: Number(__ENV.ITERATIONS || 6),
      maxDuration: '20m',
    },
  },
  thresholds: {
    scan_success: ['rate>0.95'],
    scan_duration_ms: ['p(95)<180000'], // полный /24 должен укладываться в 3 мин (p95)
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

  const qs = `ipaddr=${SUBNET}&mask=${MASK}&community=${COMMUNITY}&snmpv=v2c&scanMode=all` +
             `&sshUsername=${SSH_USER}&sshPassword=${SSH_PASS}`;
  const start = http.get(`${API}/api/v1/devices/scan?${qs}`, auth);
  if (!check(start, { 'scan started': (r) => r.status === 200 && r.json('taskId') })) {
    scanSuccess.add(false);
    return;
  }
  const taskId = start.json('taskId');
  const t0 = Date.now();

  let status = 'running';
  for (let i = 0; i < 240; i++) {           // до ~12 минут (240 * 3s)
    const r = http.get(`${API}/api/v1/devices/scan/status?taskId=${taskId}`, auth);
    status = r.json('status');
    if (status === 'completed') {
      const elapsed = Date.now() - t0;
      scanDuration.add(elapsed);
      devicesFound.add(r.json('count'));
      scanSuccess.add(true);
      return;
    }
    sleep(3);
  }
  scanSuccess.add(false); // не завершился вовремя
}
