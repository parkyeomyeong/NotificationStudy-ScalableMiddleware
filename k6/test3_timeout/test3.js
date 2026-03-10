// ====================================================
// TEST 3 - 타임아웃 적용: 외부 서버 장애 대응 검증
// ====================================================
// 목적: RestTemplate에 2초 타임아웃을 설정하면
//       mock 서버 응답(4초)이 모두 timeout → ERROR 처리되며
//       스레드가 4초씩 블로킹되지 않고 빠르게 실패 처리 → 재시도 사이클을 도는지 검증
// 실행: k6 run k6/test3_timeout/test.js
// ====================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, makePayload, JSON_HEADERS } from '../shared/utils.js';

const successRate = new Rate('api_success_rate');
const apiLatency  = new Trend('api_latency_ms', true);

export const options = {
  scenarios: {
    fixed_load: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 15000,
      maxDuration: '3m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    api_success_rate: ['rate>0.5'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/notifications/regist`,
    makePayload(),
    { headers: JSON_HEADERS }
  );

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'resultCode SUCCESS': (r) => {
      try { return JSON.parse(r.body).resultCode === 'SUCCESS'; }
      catch { return false; }
    },
  });

  successRate.add(ok);
  apiLatency.add(res.timings.duration);

  sleep(0.1);
}
