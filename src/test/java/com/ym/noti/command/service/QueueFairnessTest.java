package com.ym.noti.command.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

// 세마포어 내부를 들여다보기 위한 커스텀 클래스
class DebuggableSemaphore extends Semaphore {
    public DebuggableSemaphore(int permits, boolean fair) {
        super(permits, fair);
    }
}

class QueueFairnessTest {

    static final int SEMAPHORE_PERMITS = 90;
    static final int ITEMS_PER_QUEUE = 2000;
    static final int OBSERVE_COUNT = 100;

    @Test
    @DisplayName("[테스트1-1] Non-fair: 세마포어 0 이후 새치기 독점 현상 확인")
    void nonFair() throws Exception {
        run(false, "Non-fair", false);
    }

    @Test
    @DisplayName("[테스트1-2] Fair: 세마포어 0 이후 M,R,F 교차 진입 보장 확인")
    void fair() throws Exception {
        run(true, "Fair", false);
    }

    @Test
    @DisplayName("[테스트1-3] Burst-Fair: N개의 알람이 동시 종료되어 대량의 세마포어가 풀릴 때의 분배 확인")
    void burstFair() throws Exception {
        // Burst 시나리오: 50개의 스레드가 동시에 작업을 끝내고 50개의 permit을 한 번에 반납함
        run(true, "Burst-Fair", true);
    }

    private void run(boolean fair, String label, boolean isBurstTest) throws Exception {
        // Burst 테스트는 시작부터 세마포어가 0개여서 디스패처들이 AQS 큐에 완벽히 정렬되도록 함
        int initialPermits = isBurstTest ? 0 : SEMAPHORE_PERMITS;
        DebuggableSemaphore sem = new DebuggableSemaphore(initialPermits, fair);

        BlockingQueue<Long> mainQ = new LinkedBlockingQueue<>();
        BlockingQueue<Long> resQ = new LinkedBlockingQueue<>();
        BlockingQueue<Long> failQ = new LinkedBlockingQueue<>();

        for (long i = 1; i <= ITEMS_PER_QUEUE; i++) {
            mainQ.put(i);
            resQ.put(i + 10000);
            failQ.put(i + 20000);
        }

        // 스레드 풀 병목에 의한 Exception을 막기 위해 넉넉한 Cached 풀 사용
        ExecutorService workerPool = Executors.newCachedThreadPool();

        List<String> contentionLog = new CopyOnWriteArrayList<>();
        CountDownLatch testFinished = new CountDownLatch(1);

        // ★ 핵심: 초기 90번의 acquire()가 모두 끝날 때까지 workerPool의 작업 처리를 일시 정지
        // 이렇게 해야 세마포어가 0이 되어 디스패처 3개가 완벽하게 AQS 큐에 줄을 서게 됩니다.
        CountDownLatch initialAcquireBarrier = new CountDownLatch(isBurstTest ? 0 : SEMAPHORE_PERMITS);

        // 디스패처 시작
        Thread d1 = startDispatcher(mainQ, "M", sem, workerPool, contentionLog, testFinished, initialAcquireBarrier,
                isBurstTest);
        Thread d2 = startDispatcher(resQ, "R", sem, workerPool, contentionLog, testFinished, initialAcquireBarrier,
                isBurstTest);
        Thread d3 = startDispatcher(failQ, "F", sem, workerPool, contentionLog, testFinished, initialAcquireBarrier,
                isBurstTest);

        if (isBurstTest) {
            // 디스패처 3개가 모두 확실하게 AQS 세마포어 대기열에 진입하도록 잠시 대기
            Thread.sleep(500);
            System.out.println("\n  >>> 워커 50개가 동시에 완료되어 세마포어 50개가 동시 반납됨! (Burst Release) <<<");
            sem.release(50); // 50개 동시 반납!
        }

        boolean completed = testFinished.await(10, TimeUnit.SECONDS);

        d1.interrupt();
        d2.interrupt();
        d3.interrupt();
        workerPool.shutdownNow();

        assertTrue(completed, label + ": 측정 목표치 미달성 (타임아웃)");
        printResults(label, contentionLog);
    }

    private Thread startDispatcher(BlockingQueue<Long> queue, String label,
            Semaphore sem, ExecutorService workerPool,
            List<String> contentionLog, CountDownLatch testFinished,
            CountDownLatch initialAcquireBarrier, boolean isBurstTest) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queue.take();
                    sem.acquire();

                    if (!isBurstTest) {
                        initialAcquireBarrier.countDown();
                    }

                    // 풀 종료 중엔 추가 제출 안함
                    if (workerPool.isShutdown()) {
                        sem.release();
                        break;
                    }

                    try {
                        workerPool.execute(() -> {
                            try {
                                if (!isBurstTest) {
                                    initialAcquireBarrier.await();
                                }

                                if (contentionLog.size() < OBSERVE_COUNT) {
                                    contentionLog.add(label);
                                    if (contentionLog.size() == OBSERVE_COUNT) {
                                        testFinished.countDown();
                                    }
                                }

                                if (!isBurstTest) {
                                    Thread.sleep(10);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                sem.release(); // Burst든 아니든 무조건 반납해야 100건 측정이 계속됨
                            }
                        });
                    } catch (RejectedExecutionException ree) {
                        // 풀 셧다운 후에 들어온 작업은 무시하고 세마포어 반납
                        sem.release();
                        break;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, label + "-Dispatcher");
        t.start();
        return t;
    }

    private void printResults(String mode, List<String> log) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  [" + mode + "] 워커 처리 순서 기록 (총 " + OBSERVE_COUNT + "건)");
        System.out.println("=".repeat(70));

        System.out.print("  순서: ");
        for (int i = 0; i < log.size(); i++) {
            if (i > 0 && i % 10 == 0)
                System.out.print("\n        ");
            System.out.printf("[%d]%s ", i + 1, log.get(i));
        }
        System.out.println();

        int maxRun = calcMaxRunLength(log);
        Map<String, Long> counts = log.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        System.out.println("\n  최대 연속 동일 큐(Max Run): " + maxRun);
        System.out.println("  큐별 처리 건수: " + counts);
        System.out.println("  → Fair 설정일 경우: 특정 큐의 독점 없이 M,R,F가 고르게 교대 (maxRun 낮음)");
        System.out.println("  → Non-fair 설정일 경우: 특정 큐가 연속으로 독점하는 현상 발생 (maxRun 높음)");
        System.out.println("=".repeat(70));
    }

    private int calcMaxRunLength(List<String> log) {
        if (log.isEmpty())
            return 0;
        int max = 1, cur = 1;
        for (int i = 1; i < log.size(); i++) {
            cur = log.get(i).equals(log.get(i - 1)) ? cur + 1 : 1;
            max = Math.max(max, cur);
        }
        return max;
    }
}
