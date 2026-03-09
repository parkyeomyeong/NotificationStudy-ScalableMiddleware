// ====================================================
// TEST 1 - Baseline: 단일 스레드 컨슈머 기준선 측정
// ====================================================
// 목적: 아무것도 바꾸지 않은 현재 상태에서 API TPS + 컨슈머 처리량 기록
// 실행: k6 run k6/test1_baseline/test.js
// ====================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { BASE_URL, makePayload, JSON_HEADERS } from '../shared/utils.js';

// ── 커스텀 메트릭 ──────────────────────────────────
const successRate = new Rate('api_success_rate');   // API 등록 성공률
const apiLatency  = new Trend('api_latency_ms', true);

// ── 테스트 옵션 ────────────────────────────────────
// 총 15,000건 고정 전송 - Test 2/3과 동일한 부하로 처리량 비교
// Test 2 (200 스레드) 기준 5분 안에 소화 가능한 양
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

// ── 메인 루프: API 등록 TPS 측정 ──────────────────
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
