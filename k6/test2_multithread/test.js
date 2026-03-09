// ====================================================
// TEST 2 - 멀티 스레드 컨슈머: 처리량 향상 측정
// ====================================================
// 목적: 컨슈머 스레드 수를 늘렸을 때 처리 TPS가 몇 배 향상되는지 증명
// 실행: k6 run k6/test2_multithread/test.js
//
// [코드 변경 사항 - 실행 전 반드시 적용]
// NotificationQueueConsumer.java 의 각 startXxxConsumerThread() 메서드에서
// Executors.newSingleThreadExecutor() → Executors.newFixedThreadPool(5) 로 변경
// ====================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, makePayload, JSON_HEADERS } from '../shared/utils.js';

// ── 커스텀 메트릭 ──────────────────────────────────
const successRate = new Rate('api_success_rate');
const apiLatency  = new Trend('api_latency_ms', true);

// ── 테스트 옵션: Test1과 완전히 동일한 조건 ─────────
// 비교 실험은 반드시 동일 조건에서 해야 의미 있음
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

// ── 메인 루프: Test1과 동일 ────────────────────────
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
