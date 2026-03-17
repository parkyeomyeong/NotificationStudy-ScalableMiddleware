package com.ym.noti.command.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

// 세마포어 내부를 들여다보기 위한 커스텀 클래스
class DebuggableSemaphore extends Semaphore {
    public DebuggableSemaphore(int permits, boolean fair) {
        super(permits, fair);
    }

    // 내부 대기열 스레드 목록을 리스트로 반환 (순서대로)
    public List<String> getWaitingThreadNames() {
        List<String> names = new ArrayList<>();
        // getQueuedThreads()는 뒤에서부터(최신순) 주므로 뒤집어서 반환
        Collection<Thread> threads = super.getQueuedThreads();
        for (Thread t : threads) {
            names.add(t.getName());
        }
        Collections.reverse(names);
        return names;
    }
}

class QueueFairnessTest {

    static final int SEMAPHORE_PERMITS = 90;
    static final int ITEMS_PER_QUEUE = 2000;
    static final int OBSERVE_COUNT = 100; // 측정할 순수 경합 건수
    static final long CONSUMER_DELAY_MS = 0; // 토큰 반납 전 대기(안쓰는게 정확할듯)

    @Test
    @DisplayName("[테스트1-1] Non-fair: 세마포어 0 이후 새치기로 랜덤으로 큐에 들어가는지 테스트")
    void nonFair() throws Exception {
        run(false, "Non-fair");
    }

    @Test
    @DisplayName("[테스트1-2] Fair: 세마포어 0 이후 ex.M→R→F 균등하게 로테이션 되는지 테스트")
    void fair() throws Exception {
        run(true, "Fair");
    }

    private void run(boolean fair, String label) throws Exception {
        DebuggableSemaphore sem = new DebuggableSemaphore(SEMAPHORE_PERMITS, fair);

        BlockingQueue<Long> mainQ = new LinkedBlockingQueue<>();
        BlockingQueue<Long> resQ = new LinkedBlockingQueue<>();
        BlockingQueue<Long> failQ = new LinkedBlockingQueue<>();
        // 1. Main, reserved, fail 큐에 많은 양의 데이터를 채움
        for (long i = 1; i <= ITEMS_PER_QUEUE; i++) {
            mainQ.put(i);
            resQ.put(i + 10000);
            failQ.put(i + 20000);
        }

        // 컨슈머가 사용할 PBQ 역할: 각 큐의디스패처가 acquire() 후 여기에 넣음
        BlockingQueue<String> pbq = new LinkedBlockingQueue<>();
        List<String> contentionLog = new CopyOnWriteArrayList<>();
        CountDownLatch testFinished = new CountDownLatch(1); // 테스트 종료 신호

        AtomicBoolean isSaturated = new AtomicBoolean(false); // 세마포어 0 도달 여부
        AtomicBoolean recordingStarted = new AtomicBoolean(false); // 드레인 완료 여부

        // 1. 디스패처 시작: 프로덕션과 동일한 구조로
        Thread d1 = startDispatcher(mainQ, "M", sem, pbq);
        Thread d2 = startDispatcher(resQ, "R", sem, pbq);
        Thread d3 = startDispatcher(failQ, "F", sem, pbq);

        // 2. 소비자: 드레인 로직 포함
        Thread consumer = new Thread(() -> {
            int drainCount = 0;
            int targetDrainCount = 0; // 세마포어 소진 시점에 동적으로 캡처할 드레인 목표치

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String dispatched = pbq.take();

                    // 세마포어가 0이 되는 '정확한 찰나'에 큐에 남은 과거의 유산 개수를 캡처
                    if (!isSaturated.get() && sem.availablePermits() == 0) {
                        isSaturated.set(true);
                        // 고정값(90)이 아닌, 현재 pbq에 들어있는 실제 개수를 기준값으로 설정!
                        targetDrainCount = pbq.size();
                        System.out.println("\n[" + label + "] 세마포어 소진! 초기 버퍼(" + targetDrainCount + "개) 드레인 시작...");
                    }

                    // 드레인 이후 세마포어 반납 직전 대기열 확인
                    if (recordingStarted.get()) {
                        List<String> waiters = sem.getWaitingThreadNames();
                        System.out.println("현재 Release 직전 대기열: " + waiters);
                    }
                    sem.release();

                    // 드레인 진행 구간
                    if (isSaturated.get() && !recordingStarted.get()) {
                        drainCount++;

                        // 사용자님 지적대로, 고정값이 아닌 동적 캡처 사이즈(targetDrainCount)를 사용
                        if (drainCount >= targetDrainCount) {
                            recordingStarted.set(true);
                            System.out.println("[" + label + "] 드레인 완료 (" + drainCount + "개 비움). 지금부터 " + OBSERVE_COUNT
                                    + "건 측정 시작!");
                        }
                    }
                    // 실제 측정 구간
                    else if (recordingStarted.get()) {
                        contentionLog.add(dispatched);

                        // System.out.printf("[%s] 경합[%3d] %s-Dispatcher 획득 (대기열: %d명) [Queue size]
                        // %d%n",
                        // label, contentionLog.size(), dispatched, sem.getQueueLength(), pbq.size());

                        if (contentionLog.size() >= OBSERVE_COUNT) {
                            testFinished.countDown(); // 목표치 달성 시 즉시 종료
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        // 3. 종료 대기 및 결과 출력
        boolean completed = testFinished.await(30, TimeUnit.SECONDS);

        // 스레드 중지
        d1.interrupt();
        d2.interrupt();
        d3.interrupt();
        consumer.interrupt();

        assertTrue(completed, label + ": 측정 목표치 미달성 (타임아웃)");
        printResults(label, contentionLog);
    }

    // 프로덕션 startPriorityDispatcher와 동일 구조
    // take() → acquire() → pbq.put() (프로덕션에서는 workerPool.execute(PrioritizedTask))
    private Thread startDispatcher(BlockingQueue<Long> queue, String label,
            Semaphore sem, BlockingQueue<String> pbq) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queue.take();
                    sem.acquire();
                    pbq.put(label);
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
        System.out.println("  [" + mode + "] 경합 구간 (세마포어 소진 후 " + OBSERVE_COUNT + "건)");
        System.out.println("=".repeat(70));

        System.out.print("  순서: ");
        for (int i = 0; i < log.size(); i++) {
            if (i > 0 && i % 10 == 0)
                System.out.print("\n        ");
            System.out.printf("[%d]%s ", i + 1, log.get(i));
        }
        System.out.println();

        int maxRun = calcMaxRunLength(log);
        Map<String, Long> counts = log.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        System.out.println("\n  최대 연속 동일 큐: " + maxRun);
        System.out.println("  큐별 건수: " + counts);
        System.out.println("  → Fair이면 maxRun=1 (M,R,F 순환) / Non-fair이면 높음 (독점)");
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
