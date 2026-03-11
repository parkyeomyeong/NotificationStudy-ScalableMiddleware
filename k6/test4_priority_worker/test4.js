// ====================================================
// TEST 4 - 우선순위 워커 풀: 동적 스레드 배분 검증
// ====================================================
// 목적: 고정 스레드 배분(Main 120 / Reserved 20 / Failed 60) 대신
//       200 스레드 공유 + PriorityBlockingQueue로
//       실패 큐 폭증 시에도 메인 큐가 starvation 없이 처리되는지 검증
// 실행: k6 run k6/test4_priority_worker/test4.js
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
