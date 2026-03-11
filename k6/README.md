# 알림 시스템 부하 테스트

## 환경

- 앱: `localhost:8080`
- Mock 발송 서버: `localhost:8081` (평균 응답 4초, SUCCESS/FAILURE/ERROR 각 33%)
- 최대 VU: 200

## 테스트 구성

| 폴더 | 목적 | 핵심 변수 | 상세 |
|------|------|----------|------|
| `test1_baseline` | 단일 스레드 컨슈머 기준선 측정 | 단일 스레드 컨슈머 | [README](test1_baseline/README.md) |
| `test2_multithread` | 스레드 풀 확장 후 처리량 측정 | 고정 스레드 풀 Main 120 / Reserved 20 / Failed 60 | [README](test2_multithread/README.md) |
| `test3_timeout` | 외부 서버 지연 시 타임아웃 효과 비교 | 타임아웃 없음 → 6초 설정 | [README](test3_timeout/README.md) |
| `test4_priority_worker` | 공유 우선순위 워커 풀 전환 효과 측정 | 고정 배분 → 공유 200 스레드 + PriorityBlockingQueue | [README](test4_priority_worker/README.md) |

## 실행 순서

```
test1 → test2 → test3 → test4
```

각 테스트 폴더 `README.md` 먼저 확인 후 실행

## 실행 방법 (Windows)

```bash
# test1
k6.exe run k6/test1_baseline/test.js

# test2
k6.exe run k6/test2_multithread/test.js

# test3
k6.exe run k6/test3_timeout/test3.js

# test4
k6.exe run k6/test4_priority_worker/test4.js
```

## 관찰 방법

- **k6 터미널**: API TPS, latency, 에러율
- **앱 터미널**: `[WORKER STATS]` 로그로 워커 활성 수, 배압 토큰, 큐 적체 확인

```
[WORKER STATS] active=187/200 | pendingTasks=43 | tokens=270/300 | queues=[M:12 R:0 F:134]
  - active     : 현재 mock API 호출 중인 워커 수
  - pendingTasks: 워커 풀 내부 PriorityBlockingQueue 대기 중인 작업 수
  - tokens     : 남은 배압 토큰 (0에 가까울수록 포화 상태)
  - queues     : 원본 3개 큐 크기 (M=메인, R=예약, F=실패)
```

## 전체 결과 요약

| 테스트 | 컨슈머 구조 | 타임아웃 | 소요시간 | Throughput | 이론 대비 실효율 |
|--------|-----------|---------|---------|-----------|----------------|
| Test 1 | 단일 스레드 | 없음 | ~15.3시간 (추산) | 0.27건/s | - |
| Test 2 | 고정 풀 120/20/60 | 없음 | 528초 | 28.4건/s | ~81% |
| Test 3 | 고정 풀 120/20/60 | 6초 | 666초 | 22.55건/s | ~79% |
| Test 4 | **공유 우선순위 풀 200** | 6초 | **549초** | **27.3건/s** | **~99%** |

> **이론 대비 실효율**: 총 mock API 호출 수 ÷ mock 서버 이론 처리량(50 TPS)으로 산출한 최적 시간 대비 실측 시간 비율
