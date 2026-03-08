# 알림 시스템 부하 테스트

## 환경

- 앱: `localhost:8080`
- Mock 발송 서버: `localhost:8090` (평균 응답 4초, SUCCESS/FAIL/ERROR 각 33%)
- 최대 VU: 200

## 테스트 구성

| 폴더 | 목적 | 핵심 변수 |
|------|------|----------|
| `test1_baseline` | 현재 상태 기준선 측정 | 단일 스레드 컨슈머 |
| `test2_multithread` | 처리량 향상 측정 | 스레드 1개 → 5개 |
| `test3_timeout` | 외부 서버 지연 대응 비교 | 타임아웃 없음 vs 5초 |

## 실행 순서

```
test1 → test2 → test3a → test3b
```

각 테스트 폴더 `README.md` 먼저 확인 후 실행

## 설치

```bash
brew install k6
```

## 결과 저장

```bash
mkdir -p k6/results

k6 run k6/test1_baseline/test.js              --out json=k6/results/test1.json
k6 run k6/test2_multithread/test.js           --out json=k6/results/test2.json
k6 run k6/test3_timeout/test3a_no_timeout.js  --out json=k6/results/test3a.json
k6 run k6/test3_timeout/test3b_with_timeout.js --out json=k6/results/test3b.json
```

## 관찰 방법

- **k6 터미널**: API TPS, latency, 에러율
- **앱 터미널**: `MAIN QUEUE SIZE` 로그로 큐 적체/소화 속도 확인
- 두 터미널 나란히 놓고 같이 보면 됨
