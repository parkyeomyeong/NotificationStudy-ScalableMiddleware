# Test 2 - 멀티 스레드 컨슈머

## 목적

mock 서버 부하 테스트로 파악한 최적 동시 요청 수(VU 200)를 기준으로 컨슈머 스레드 풀 크기를 결정하고,
Test 1과 동일한 15,000건 부하에서 처리량이 얼마나 개선되는지 수치로 증명

## 스레드 풀 크기 결정 근거

### Step 1. mock 서버 단위 성능 파악

mock 서버 부하 테스트 결과:

| 항목 | 값 |
|------|----|
| 최대 동시 처리 (VU) | **200** |
| 평균 응답시간 | **4초** |
| 스레드 1개당 처리량 | 1 / 4s = **0.25건/s** |
| 전체 합산 처리량 한도 | 200 × 0.25 = **50건/s** |

→ 총 스레드 수 상한 = **200** (초과 시 mock 서버 과부하)

### Step 2. 실패 큐 예상 유입량 계산

mock 서버 결과 분포: SUCCESS 33% / FAILURE 33% / ERROR 33%
FAILURE → 즉시 PERMANENT_FAILED, ERROR → 최대 3회 재시도

```
실패 큐 총 유입량 = N/3 + N/9 + N/27 = 13N/27 ≈ 0.48N

Main 처리량 : Failed 처리량 = N : 13N/27 = 27 : 13
```

15,000건 기준 실패 큐 예상 유입량 = 15,000 × 13/27 ≈ **7,222건**

### Step 3. 스레드 배분

총 200 스레드에서 Reserved 큐에 20을 고정 배분 → Main + Failed = 180
위에서 도출한 27:13 비율 적용:

| 큐 | 스레드 수 | 근거 |
|----|---------|------|
| Main | **120** | 180 × 27/40 ≈ 120 |
| Reserved | **20** | 고정 여유분 |
| Failed | **60** | 180 × 13/40 ≈ 60 |
| **합산** | **200** | mock 서버 VU 한도 |

### Step 4. 파이프라인 균형 검증 — 최적 배분임을 수치로 확인

각 큐의 처리속도 = 스레드 수 × 0.25건/s:

| 큐 | 처리속도 | 총 작업량 | 예상 소요시간 |
|----|---------|---------|------------|
| Main | 120 × 0.25 = **30건/s** | 15,000건 | **500초** |
| Failed | 60 × 0.25 = **15건/s** | 7,222건 | **481초** |

두 파이프라인의 완료 시점이 거의 동일 → **어느 쪽도 먼저 끝나서 대기 스레드가 생기지 않는 균형점**

대안 비교:

| 배분 | Main 처리속도 | Failed 처리속도 | Main 완료 | Failed 완료 | **전체 병목** |
|------|------------|--------------|---------|-----------|------------|
| **120 / 60** (채택) | 30건/s | 15건/s | 500초 | 481초 | **500초** |
| 100 / 80 | 25건/s | 20건/s | 600초 | 361초 | **600초** |

100/80은 Failed가 일찍 끝나 스레드가 유휴 상태가 되는 반면 Main이 늦어져 전체가 100초 느림.
두 파이프라인의 완료 시점을 일치시키는 것이 전체 소요시간 최소화 조건이고, **120/60이 이를 충족한다.**

### Step 5. 스케줄러 공급속도 검증

스케줄러 공급속도(B/D) ≥ 컨슈머 소비속도(T × 0.25) 를 만족해야 컨슈머가 굶지 않음:

| 큐 | 공급속도 (B/D) | 소비속도 (T/R) | 여유 |
|----|-------------|-------------|-----|
| Main | 100 / 1s = 100건/s | 30건/s | **3.3배** |
| Reserved | 100 / 5s = 20건/s | 5건/s | **4배** |
| Failed | 100 / 5s = 20건/s | 15건/s | **1.3배** |

→ 모든 큐에서 공급 > 소비. 스케줄러는 병목이 아님.

## 코드 변경

`NotificationQueueConsumer.java`:

```java
// Before
Executors.newSingleThreadExecutor().submit(() -> {

// After
var executor = Executors.newFixedThreadPool(THREAD_COUNT);
for (int i = 0; i < THREAD_COUNT; i++) {
    executor.submit(() -> { while (true) { ... } });
}
```

## 테스트 환경

| 항목 | 값 |
|------|---|
| 채널 | TEAMS |
| 컨슈머 스레드 | Main 120 / Reserved 20 / Failed 60 |
| RestTemplate 타임아웃 | **없음** |
| Mock 서버 위치 | **내부 (로컬)** |
| Mock 서버 평균 응답시간(설정값) | 4초 |
| Mock 서버 결과 분포(설정값) | SUCCESS 33% / FAILURE 33% / ERROR 33% |
| 부하 패턴 | 100 VU, 고정 **15,000건** (shared-iterations) |

---

## 계층 1 측정 결과 - API 수용 속도 (k6)

```
총 요청 수       : 15,000건
k6 실행 시간     : 15.4초
API TPS          : 973.70 req/s
에러율           : 0%
p50 latency      : 0.62ms
p95 latency      : 3.72ms
```

> **주의**: 이 TPS(973/s)는 서버의 최대 수용 능력이 아닙니다.
> k6 스크립트에 `sleep(0.1)`이 설정되어 있어 VU당 100ms 강제 대기가 발생합니다.
> 실제 응답시간은 0.62ms(p50)이므로 서버 자체 한계는 이보다 훨씬 높습니다.
> 이 테스트의 목적은 **고정 15,000건 부하 하에서 컨슈머 처리 성능을 비교**하는 것입니다.

---

## 계층 2 측정 결과 - 컨슈머 처리 성능 (DB)

### 최종 처리 현황

| STATUS | COUNT |
|--------|-------|
| SUCCESS | **7,252건** |
| PERMANENT_FAILED | **7,748건** |
| PENDING (미처리) | **0건** |

### Throughput

```
총 처리 건수  : 15,000건
소요 시간     : 528초 (약 8.8분)
Throughput    : 15,000 / 528 ≈ 28.4건/s
```

---

## 계층 2 검증 - H2 쿼리

컨슈머 처리 결과를 H2 콘솔에서 직접 SQL로 검증했다.

### 쿼리 1. 최종 상태 분포

```sql
SELECT status, COUNT(*) AS cnt
FROM notification_request
GROUP BY status;
```

```
STATUS              CNT
PERMANENT_FAILED    7748
SUCCESS             7252
```

PENDING 0건 확인. 이전 테스트에서 3건이 영구 누락됐던 커서 버그(createdAt → id 기반으로 수정)가 해결됐음을 확인.

### 쿼리 2. 재시도 횟수(tryCount)별 분포

```sql
SELECT try_count, status, COUNT(*) AS cnt
FROM notification_request
GROUP BY try_count, status
ORDER BY try_count, status;
```

```
TRY_COUNT    STATUS              CNT
1            PERMANENT_FAILED    5017
1            SUCCESS             5052
2            PERMANENT_FAILED    1642
2            SUCCESS             1659
3            PERMANENT_FAILED    1089
3            SUCCESS              541
```

이론값과 비교:

| 시도 횟수 | 이론 (33/33/33 기준) | 실측 | 오차 |
|---------|-------------------|------|------|
| 1회 종료 | 10,000건 | 10,069건 | +0.7% |
| 2회 종료 | 3,333건 | 3,301건 | -1.0% |
| 3회 종료 | 1,667건 | 1,630건 | -2.2% |

오차 2% 이내. mock 서버가 설정값 그대로 33/33/33으로 동작했음을 확인.

### 쿼리 3. 30초 구간별 처리 타임라인

```sql
SELECT
    FLOOR(DATEDIFF('SECOND',
        (SELECT MIN(created_at) FROM notification_request),
        last_tried_at) / 30) * 30 AS bucket_start_sec,
    COUNT(*) AS finished_in_bucket
FROM notification_request
WHERE last_tried_at IS NOT NULL
GROUP BY bucket_start_sec
ORDER BY bucket_start_sec;
```

```
BUCKET_START_SEC    FINISHED_IN_BUCKET
0                   715
30                  921
60                  869
90                  909
120                 910
150                 897
180                 892
210                 889
240                 896
270                 884
300                 890
330                 917
360                 913
390                 850
420                 879
450                 911
480                 789
510                 69
```

0~30초 구간(715건)은 서버 시작 직후 스케줄러 첫 실행 전까지 큐가 비어있는 구간이라 낮음.

30~480초 구간은 매 30초마다 880~921건으로 균일 → **건당 최종 완료 속도 ≈ 30건/s**.
(Main 큐 30/s + Failed 큐 15/s = 총 45/s가 mock 서버에 나가지만,
재시도는 동일 건의 재처리이므로 "건 완료" 카운트는 늘어나지 않음. 이 버킷은 건 완료 기준이므로 이론상 30건/s과 일치!)<br>
510~540초 구간(69건)은 후반부 재시도 항목들의 꼬리. 아래 쿼리 4에서 이 구간 항목이 try_count=2~3임을 확인.

### 쿼리 4. 마지막으로 완료된 항목 (꼬리 분석)

```sql
SELECT status, try_count,
       DATEDIFF('SECOND',
           (SELECT MIN(created_at) FROM notification_request),
           last_tried_at) AS sec_from_start
FROM notification_request
WHERE last_tried_at IS NOT NULL
ORDER BY last_tried_at DESC
LIMIT 20;
```

```
STATUS              TRY_COUNT    SEC_FROM_START
SUCCESS             3            528
PERMANENT_FAILED    3            527
PERMANENT_FAILED    2            527
PERMANENT_FAILED    3            526
PERMANENT_FAILED    3            525
...
```

마지막 완료 항목은 모두 try_count=2~3. Main 큐 말미(t≈490~500초)에 ERROR가 난 항목들이
Failed 큐 재시도를 2회(각 최대 5초 대기 + 4초 처리) 거치면서 528초까지 이어짐.

---

## 최대 소요시간 이론값 대비 실측값 분석

이론 최대 소요시간: **~527초** / 실측: **528초** → **오차 1초 이내**

```
이론 최대 소요시간 산출 (최악의 경우 - 마지막 항목이 t=500초에 첫 시도 시작):

  Main 큐 마지막 처리  :       4초  (t=500 → t=504)
  Failed 스케줄러 대기 :       5초  (t=504 → t=509)
  Failed 재시도 1회    :       4초  (t=509 → t=513)
  Failed 스케줄러 대기 :       5초  (t=513 → t=518)
  Failed 재시도 2회    :       4초  (t=518 → t=522)
  Failed 스케줄러 대기 :       5초  (t=522 → t=527)
  ─────────────────────────────────────────
  이론 최대            : 500 + 27 = 527초
  실측                 :           528초  (오차 1초)
```



## Test 1 대비 개선율

| 지표 | Test 1 (1스레드) | Test 2 (풀 200) | 개선율 |
|------|----------------|----------------|--------|
| 총 등록 건수 | 15,000건 | 15,000건 | - |
| 처리 완료 시간 | ~15.3시간 (추산) | **8.8분** | - |
| Throughput | 0.27건/s | **28.4건/s** | **약 105배** |
| 미처리(PENDING) | - | **0건** | - |

---

**성공율 비교:**

| 항목 | 이론값 | 실측값 |
|------|--------|--------|
| 최종 성공율 | 13/27 = **48.1%** | 7,252/15,000 = **48.3%** |
| tryCount 분포 오차 | - | **2% 이내** |

이론값과 실측값이 거의 완벽하게 일치.<br>
mock 서버 VU 한도(200)와 응답시간(4초)이라는 제약 하에서<br>
재시도 비율과 파이프라인 균형을 수학적으로 모델링하여<br>
처리 시간이 최소가 되는 스레드 배분을 계산했고,<br>
실제 테스트를 통해 이론값과 거의 일치하는 결과가 나오는 것을 검증했습니다.

---

## 다음 단계

타임아웃을 설정하면 외부 서버 장애 상황에서도 스레드가 블로킹되지 않고
빠르게 실패 처리 → 재시도 사이클을 돌 수 있는지 검증한다. → Test 3
