package com.ym.noti.command.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 실험 2: 블로킹 I/O에서 쓰레드 수 증가 시 리스크 측정
 *
 * 측정 지표:
 *   1. 힙 메모리 사용량       → MemoryMXBean (JMX 공식 API)
 *   2. 라이브 쓰레드 수       → ThreadMXBean (JMX 공식 API)
 *   3. 쓰레드 생성 소요 시간   → System.currentTimeMillis()
 *   4. 컨텍스트 스위칭 지연    → sleep(10ms) 후 실제 깨어나는 데 걸린 시간 (간접 측정)
 *   5. 스택 메모리 추정치      → 쓰레드수 × 1MB (JVM 기본 -Xss=1m)
 *
 * 목적:
 *   "쓰레드 수를 단순히 늘리면 메모리가 선형 증가하고 컨텍스트 스위칭 지연이 커진다"
 *   → Non-blocking I/O 전환의 근거 데이터 확보
 */
class ThreadScalingMemoryTest {

    private static final int[] THREAD_COUNTS = {200, 500, 1000, 2000};

    @Test
    @DisplayName("[실험2] 쓰레드 수 증가에 따른 메모리 + 컨텍스트 스위칭 리스크 측정")
    void measureMemoryAndLatencyByThreadCount() throws Exception {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        System.out.println("\n" + "=".repeat(105));
        System.out.println("              쓰레드 수 증가 시 리스크 측정 결과");
        System.out.println("              (블로킹 I/O 방식에서 쓰레드 단순 증가의 한계)");
        System.out.println("=".repeat(105));
        System.out.printf("%-10s | %-13s | %-13s | %-13s | %-12s | %-12s | %-12s%n",
                "쓰레드 수",
                "Heap(MB)",
                "추정 스택(MB)",
                "추정 합계(MB)",
                "Live 쓰레드",
                "생성시간(ms)",
                "WakeUp 지연");
        System.out.println("-".repeat(105));

        for (int threadCount : THREAD_COUNTS) {
            measureSingleStep(threadCount, memoryBean, threadBean);
        }

        System.out.println("=".repeat(105));
        System.out.println();
        System.out.println("[지표 설명]");
        System.out.println("  Heap(MB)       : MemoryMXBean.getHeapMemoryUsage().getUsed() (JMX 공식 API)");
        System.out.println("  추정 스택(MB)   : 쓰레드 수 × 1MB (JVM 기본 -Xss=1m, 직접 측정 불가하여 공식문서 기반 추정)");
        System.out.println("  추정 합계(MB)   : Heap + 추정 스택");
        System.out.println("  WakeUp 지연    : 쓰레드가 sleep(10ms) 후 실제 깨어나는 데 걸린 평균 시간");
        System.out.println("                   (쓰레드가 많을수록 OS 컨텍스트 스위칭 부하로 지연 증가)");
        System.out.println();
        System.out.println("[결론 가이드]");
        System.out.println("  - 쓰레드 수 2배 → 메모리 ~2배 : 선형 증가 = 단순 스케일링의 한계");
        System.out.println("  - WakeUp 지연 증가 → 쓰레드가 많을수록 유효 TPS가 오히려 하락 가능");
        System.out.println("  - OOM 발생 → 그 자체가 '쓰레드 단순 증가는 불가능'의 직접 증명");
        System.out.println("  → Non-blocking I/O (Reactor, WebFlux) 전환 필요성의 근거 데이터\n");
    }

    /**
     * 한 단계(특정 쓰레드 수)에 대해:
     * 1. 쓰레드 풀 생성 → 전부 활성화
     * 2. 메모리 측정
     * 3. 컨텍스트 스위칭 지연 측정 (sleep 후 wake-up latency)
     * 4. 정리
     */
    private void measureSingleStep(int threadCount, MemoryMXBean memoryBean, ThreadMXBean threadBean)
            throws Exception {

        // 1. GC + baseline
        System.gc();
        Thread.sleep(500);

        CountDownLatch allReady   = new CountDownLatch(threadCount);  // 모든 쓰레드 시작 확인
        CountDownLatch startGate  = new CountDownLatch(1);           // 동시에 sleep 시작 신호
        CountDownLatch allDone    = new CountDownLatch(threadCount);  // sleep 완료 대기
        AtomicLong totalWakeupNs  = new AtomicLong(0);               // wake-up 지연 합산

        ExecutorService pool = null;

        try {
            long createStart = System.currentTimeMillis();
            pool = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    allReady.countDown();
                    try {
                        // ── Phase 1: 모든 쓰레드가 준비될 때까지 대기 ──
                        startGate.await();

                        // ── Phase 2: 컨텍스트 스위칭 지연 측정 ──
                        long beforeSleep = System.nanoTime();
                        Thread.sleep(10); // 정확히 10ms만 자겠다는 의도
                        long actualSleepNs = System.nanoTime() - beforeSleep;
                        long overheadNs = actualSleepNs - 10_000_000L; // 10ms(나노초) 초과분 = 지연
                        totalWakeupNs.addAndGet(Math.max(0, overheadNs));

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allDone.countDown();
                    }
                });
            }

            // 모든 쓰레드 시작 대기
            boolean ready = allReady.await(60, TimeUnit.SECONDS);
            long createTime = System.currentTimeMillis() - createStart;

            if (!ready) {
                System.out.printf("%-10d | ⚠ 쓰레드 생성 실패 (60초 타임아웃)%n", threadCount);
                return;
            }

            // ── 메모리 측정 (모든 쓰레드가 살아있는 상태) ──
            System.gc();
            Thread.sleep(200);
            long heapUsed    = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            int  liveThreads = threadBean.getThreadCount();
            long stackEstMB  = threadCount;           // 쓰레드당 ~1MB 스택
            long totalEstMB  = heapUsed + stackEstMB;

            // ── 컨텍스트 스위칭 테스트 시작 ──
            startGate.countDown(); // 모든 쓰레드 동시에 sleep 시작
            allDone.await(30, TimeUnit.SECONDS);

            // 평균 wake-up 지연
            double avgWakeupMs = (totalWakeupNs.get() / (double) threadCount) / 1_000_000.0;

            System.out.printf("%-10d | %-13d | %-13d | %-13d | %-12d | %-12d | %.2fms%n",
                    threadCount,
                    heapUsed,
                    stackEstMB,
                    totalEstMB,
                    liveThreads,
                    createTime,
                    avgWakeupMs);

        } catch (OutOfMemoryError e) {
            System.out.printf("%-10d | ❌ OutOfMemoryError: %s%n", threadCount, e.getMessage());
            System.out.println("           → 이 자체가 '쓰레드 단순 증가의 한계'를 증명하는 데이터!");
        } finally {
            if (pool != null) {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
            // 확실한 정리
            System.gc();
            Thread.sleep(500);
        }
    }
}
