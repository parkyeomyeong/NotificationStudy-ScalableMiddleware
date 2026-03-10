# Test 3 - 타임아웃 적용

## 목적

외부 서버 지연 상황에서 RestTemplate에 타임아웃을 설정하면 스레드 점유 시간이 줄어들어
전체 처리량이 향상될 것이라는 가설을 검증합니다.

## 타임아웃 값 결정 근거

```
mock 서버 평균 응답시간 4초 × 1.5배 = 6초
```

실제 관측값(평균 4초)보다 충분히 크게 잡아 정상 요청은 통과하고,
비정상적으로 느린 요청만 차단하는 의도로 설정했습니다.

## 코드 변경

`ExternalNotiConfig.java`:

```java
// Before (Test 2)
// 타임아웃 설정 없음

// After (Test 3)
private static final int TIMEOUT_MS = 6_000; // 6초

factory.setConnectTimeout(TIMEOUT_MS);
factory.setReadTimeout(TIMEOUT_MS);
```

## 테스트 환경

| 항목 | 값 |
|------|---|
| 채널 | TEAMS |
| 컨슈머 스레드 | Main 120 / Reserved 20 / Failed 60 |
| RestTemplate 타임아웃 | **6초** |
| Mock 서버 위치 | **내부 (로컬)** |
| Mock 서버 평균 응답시간(설정값) | 4초 |
| Mock 서버 결과 분포(설정값) | SUCCESS 33% / FAILURE 33% / ERROR 33% |
| 부하 패턴 | 100 VU, 고정 **15,000건** (shared-iterations) |

---

## 계층 2 측정 결과 - 컨슈머 처리 성능 (DB)

### 최종 처리 현황

| STATUS | COUNT |
|--------|-------|
| SUCCESS | **6,626건** |
| PERMANENT_FAILED | **8,374건** |

### Throughput

```
총 처리 건수  : 15,000건
소요 시간     : 666초 (약 11.1분)
Throughput    : 22.55건/s
```

---

## 계층 2 검증 - H2 쿼리

### 쿼리 1. 최종 상태 분포

```sql
SELECT status, COUNT(*) AS cnt
FROM notification_request
GROUP BY status;
```

```
STATUS              CNT
PERMANENT_FAILED    8374
SUCCESS             6626
```

### 쿼리 2. 재시도 횟수(tryCount)별 분포

```sql
SELECT try_count, status, COUNT(*) AS cnt
FROM notification_request
GROUP BY try_count, status
ORDER BY try_count, status;
```

```
TRY_COUNT    STATUS              CNT
1            PERMANENT_FAILED    3635
1            SUCCESS             3789
2            PERMANENT_FAILED    1901
2            SUCCESS             1864
3            PERMANENT_FAILED    2838
3            SUCCESS              973
```

Test 2와 비교:

| try_count 종료 건수 | Test 2 (타임아웃 없음) | Test 3 (6초 타임아웃) |
|--------------------|----------------------|---------------------|
| 1회 종료 | 10,069건 | **7,424건** |
| 2회 종료 | 3,301건 | **3,765건** |
| 3회 종료 | 1,630건 | **3,811건** |

3회까지 재시도하는 건이 Test 2 대비 **2.3배** 증가했습니다.
타임아웃으로 FAILED 처리된 건들이 failedQueue로 대거 유입된 것이 원인입니다.

### 쿼리 3. e2e 처리시간 분포 (p50/p95/p99)

```sql
SELECT
    try_count,
    status,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY
DATEDIFF('SECOND', created_at, last_tried_at)) AS p50_sec,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY
DATEDIFF('SECOND', created_at, last_tried_at)) AS p95_sec,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY
DATEDIFF('SECOND', created_at, last_tried_at)) AS p99_sec
FROM notification_request
WHERE last_tried_at IS NOT NULL
GROUP BY try_count, status
ORDER BY try_count, status;
```

```
TRY_COUNT    STATUS              P50_SEC    P95_SEC    P99_SEC
1            PERMANENT_FAILED    221        413        431
1            SUCCESS             222        412        430
2            PERMANENT_FAILED    307        562        584
2            SUCCESS             327        571        589
3            PERMANENT_FAILED    430        640        647
3            SUCCESS             437        635        644
```

### 쿼리 4. 30초 구간별 처리 타임라인

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
0                   673
30                  780
60                  796
90                  804
120                 874
150                 762
180                 809
210                 835
240                 759
270                 791
300                 815
330                 786
360                 872
390                 884
420                 771
450                 356   ← 여기서 처리량 급감 (절반 이하)
480                 370
510                 354
540                 376
570                 392
600                 501
630                 577
660                  63
```

**450초 시점에 처리량이 800 → 360으로 절반 이하로 꺾입니다.**
이 지점이 mainQueue 소진 시점이며, 이후 failedQueue 60 스레드만 남은 상태로 처리됩니다.

mainQueue 소진 시점은 두 가지 방법으로 유추할 수 있습니다.

**방법 1. 처리량 비율로 역산**

처리량이 ~800 → ~360으로 꺾인 비율이 Main(120) : Failed(60) = 2 : 1과 정확히 일치합니다.
Main 스레드가 사라졌을 때 나오는 숫자입니다.

**방법 2. try_count=1 마지막 완료 시각으로 직접 확인**

```sql
SELECT id, created_at, last_tried_at,
       DATEDIFF('MILLISECOND', created_at, last_tried_at) / 1000.0 AS duration_sec
FROM notification_request
WHERE try_count = 1
  AND last_tried_at IS NOT NULL
ORDER BY last_tried_at DESC
LIMIT 1;
```

```
ID       CREATED_AT                       LAST_TRIED_AT                    DURATION_SEC
14970    2026-03-10 14:59:34.322446    2026-03-10 15:06:51.161926    436.839
```

전체 시작 시각(14:59:19)과 비교하면 **15:06:51 - 14:59:19 = 약 452초**.
try_count=1, 즉 mainQueue에서 처음 처리된 마지막 건이 452초에 완료됐음을 직접 확인했습니다.

600~630초 구간에서 501~577건으로 소폭 회복되는 것은
try_count=3 재시도들이 이 구간에 몰려 PERMANENT_FAILED로 일괄 마무리되기 때문입니다.

### 쿼리 5. 마지막 완료 항목 (꼬리 분석)

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
PERMANENT_FAILED    3            666
PERMANENT_FAILED    3            665
PERMANENT_FAILED    3            664
...
SUCCESS             3            663
...
```

마지막 완료 항목이 전부 try_count=3입니다. failedQueue 병목으로 인한 재시도 꼬리가 666초까지 이어졌습니다.

---

## Test 2 대비 결과 비교

| 지표 | Test 2 (타임아웃 없음) | Test 3 (6초 타임아웃) | 변화 |
|------|----------------------|---------------------|------|
| 총 소요시간 | 528초 | **666초** | **+138초 (+26%)** |
| Throughput | 28.4건/s | **22.55건/s** | **-21%** |
| 최종 성공 건수 | 7,252건 (48.3%) | **6,626건 (44.2%)** | **-626건** |
| try_count=3 건수 | 1,630건 | **3,811건** | **+2.3배** |

가설("타임아웃 → 스레드 점유 시간 단축 → 처리량 향상")과 **정반대의 결과**가 나왔습니다.

---

## 원인 분석

### 타임아웃이 역효과를 낸 메커니즘

```
타임아웃 없을 때:
  요청 → mock 서버 응답 (SUCCESS / FAILURE / ERROR)
  failedQueue 유입 ≈ ERROR 33% = 약 5,000건

타임아웃 설정 후:
  mock 서버 응답시간 일부가 6초 초과
  → SocketTimeoutException
  → catch(Exception) → FAILED 처리
  → failedQueue 유입 급증 (5,000건 + 타임아웃 건)
```

### failedQueue 스레드 병목이 핵심

failedQueue 처리 스레드는 60개로 고정되어 있습니다.
타임아웃으로 유입량이 늘어도 처리 속도는 그대로이므로 큐가 적체됩니다.

타임라인 데이터가 이것을 보여줍니다:
- 0~420초: ~800건/30초 → Main(120) + Failed(60) = 180 스레드 가동
- 450~570초: ~360건/30초 → Main 큐 소진, Failed(60) 스레드만 처리

**Main 큐가 끝난 순간 처리량이 정확히 절반으로 꺾입니다.**
Main 120 스레드가 비워지면서 Failed 60 스레드만 남는 것이 수치로 확인됩니다.

### 성공률 하락 원인

타임아웃이 없었다면 이론 성공률은 48.1% = 약 7,215건입니다.
실측은 6,626건이므로 **589건이 타임아웃의 피해**를 입었습니다.

타임아웃으로 FAILED 처리된 건들은 failedQueue에서 최대 3회 재시도하지만,
재시도에서도 동일하게 타임아웃이 발생할 수 있고,
3회 소진 시 PERMANENT_FAILED로 확정됩니다.
결국 원래라면 SUCCESS로 끝날 수 있었던 건들이 성공 기회를 잃게 됩니다.

### 스레드 배분 재조정으로 해결이 가능한가

타임아웃 도입 후 failedQueue 유입 비율이 늘었으니,
Failed 스레드를 60 → 80~100으로 올리면 이번 병목은 해소할 수 있다고 생각했습니다.

그러나 이것은 **근본 해결이 아닙니다.**

- 타임아웃 값이 바뀔 때마다 failedQueue 유입 비율이 달라집니다.
- 유입 비율이 달라질 때마다 스레드 배분을 다시 계산해야 합니다.
- 이 루프는 끝이 없다고 판단했습니다.

스레드 배분 비율은 사전에 고정값으로 결정되지만,
실제 큐 유입량은 런타임에 변합니다.
**고정된 배분으로는 변하는 부하에 근본적으로 대응할 수 없습니다.**

---

## 결론: Blocking I/O + Fixed Thread Pool 모델의 한계

이 테스트까지 세 단계를 거치며 하나의 결론에 도달했습니다.

| 단계 | 접근 | 결과 |
|------|------|------|
| Test 1 | 단일 스레드 | Throughput 0.27건/s |
| Test 2 | 스레드 풀 200개 + 비율 최적화 | Throughput 28.4건/s (105배) |
| Test 3 | 타임아웃 도입 | Throughput 22.55건/s (오히려 하락) |

### 근본 원인

스레드 하나가 mock 서버 응답을 기다리는 4초 동안 OS 스레드를 점유합니다.
스레드 수 조정, 비율 최적화, 타임아웃 설정 모두 이 구조 위에서의 튜닝일 뿐이며,
직접 실험을 통해 어느 방향으로 조정해도 성능이 개선되지 않는 한계를 확인했습니다.

`ThreadPoolTaskExecutor`의 동적 스레드 수 조절이나 `newWorkStealingPool()`처럼
스레드를 유연하게 배분하는 방법도 존재하지만, 이 역시 Blocking I/O 구조 위에서 동작하므로
한 번 보낸 요청은 응답이 올 때까지 스레드를 점유한다는 사실 자체는 달라지지 않습니다.

---

## 다음 단계

스레드 튜닝의 한계를 직접 확인했으므로, 다음 단계는 구조 자체를 바꾸는 것입니다.

**Virtual Threads (Java 21)**: IO 대기 중 OS 스레드를 반환하고 다른 작업에 재사용합니다.
코드 변경이 거의 없는 상태에서 스레드 수 제약을 사실상 제거할 수 있습니다.

**Non-blocking I/O (WebClient)**: IO 대기 시간 자체를 스레드 점유에서 분리합니다.
스레드 수 튜닝이라는 개념 자체가 사라집니다.
