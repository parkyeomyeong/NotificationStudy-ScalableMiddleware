package com.ym.noti.command.service;

import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import com.ym.noti.command.domain.SendResult;
import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.router.NotiSenderRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class NotificationQueueConsumer {
    private final BlockingQueue<Long> mainQueue;
    private final BlockingQueue<Long> reservedQueue;
    private final BlockingQueue<Long> failedQueue;
    private final NotificationRepository repo;
    private final NotiSenderRouter router;

    @Autowired
    public NotificationQueueConsumer(
            @Qualifier("mainNotiQueue") BlockingQueue<Long> mainQueue,
            @Qualifier("reservedNotiQueue") BlockingQueue<Long> reservedQueue,
            @Qualifier("failedNotiQueue") BlockingQueue<Long> failedQueue,
            NotificationRepository repo,
            NotiSenderRouter router) {
        this.mainQueue = mainQueue;
        this.reservedQueue = reservedQueue;
        this.failedQueue = failedQueue;
        this.repo = repo;
        this.router = router;
    }

    // ==========================================================================================
    // 기존 고정 스레드 할당 방식 (주석 처리)
    // ==========================================================================================
    /*
     * // mock 서버 최대 동시 처리 200 기준으로 스레드 배분
     * // 총 유입량 비율: Main(N) : Failed(13N/27) = 27:13
     * // Main 120 / Reserved 20 / Failed 60 = 200
     * private static final int MAIN_THREAD_COUNT = 120;
     * private static final int RESERVED_THREAD_COUNT = 20;
     * private static final int FAILED_THREAD_COUNT = 60;
     * 
     * @PostConstruct
     * public void startMainQueueConsumerThread() {
     * var executor = Executors.newFixedThreadPool(MAIN_THREAD_COUNT);
     * for (int i = 0; i < MAIN_THREAD_COUNT; i++) {
     * executor.submit(() -> {
     * while (true) {
     * Long notiId = mainQueue.take();
     * NotificationRequest noti = repo.findById(notiId).orElse(null);
     * if (noti == null) continue;
     * try {
     * log.warn("MAIN QUEUE SIZE: {}", mainQueue.size());
     * SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
     * handleSendResult(noti, result, false);
     * } catch (Exception e) {
     * noti.setStatus(NotificationStatus.FAILED);
     * log.error("MAIN QUEUE ERROR", e);
     * }
     * noti.setLastTriedAt(LocalDateTime.now());
     * noti.setTryCount(noti.getTryCount() + 1);
     * repo.save(noti);
     * }
     * });
     * }
     * }
     * 
     * @PostConstruct
     * public void startReservedQueueConsumerThread() {
     * var executor = Executors.newFixedThreadPool(RESERVED_THREAD_COUNT);
     * for (int i = 0; i < RESERVED_THREAD_COUNT; i++) {
     * executor.submit(() -> {
     * while (true) {
     * Long notiId = reservedQueue.take();
     * NotificationRequest noti = repo.findById(notiId).orElse(null);
     * if (noti == null) continue;
     * try {
     * log.warn("RESERVED QUEUE SIZE: {}", reservedQueue.size());
     * SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
     * handleSendResult(noti, result, false);
     * } catch (Exception e) {
     * noti.setStatus(NotificationStatus.FAILED);
     * log.error("RESERVED QUEUE ERROR", e);
     * }
     * noti.setLastTriedAt(LocalDateTime.now());
     * noti.setTryCount(noti.getTryCount() + 1);
     * repo.save(noti);
     * }
     * });
     * }
     * }
     * 
     * @PostConstruct
     * public void startFailedQueueConsumerThread() {
     * var executor = Executors.newFixedThreadPool(FAILED_THREAD_COUNT);
     * for (int i = 0; i < FAILED_THREAD_COUNT; i++) {
     * executor.submit(() -> {
     * while (true) {
     * Long notiId = failedQueue.take();
     * NotificationRequest noti = repo.findById(notiId).orElse(null);
     * if (noti == null) continue;
     * try {
     * log.warn("FAIL QUEUE SIZE: {}", failedQueue.size());
     * SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
     * handleSendResult(noti, result, true);
     * } catch (Exception e) {
     * noti.setStatus(NotificationStatus.FAILED);
     * log.error("FAIL QUEUE ERROR", e);
     * }
     * noti.setTryCount(noti.getTryCount() + 1);
     * noti.setLastTriedAt(LocalDateTime.now());
     * repo.save(noti);
     * }
     * });
     * }
     * }
     */

    // ==========================================================================================
    // 4단계: 이벤트 기반(Blocking) 우선순위 워커 풀 패턴
    // ==========================================================================================

    // Mock 서버의 최대 동시 처리량(200)에 맞춘 워커 스레드
    private static final int MAX_WORKER_THREADS = 200;

    // 워커 200개 + 우선순위 대기열(버퍼) 100개 확보 (총 300개의 토큰)
    // 버퍼(100)를 두는 이유: 버퍼 공간 안에서 '메인(1순위)' 알림이 '실패(3순위)' 알림을 역전할 수 있게 만들기 위함!
    // Backpressure 기능: 300개가 꽉 차면 디스패처가 강제로 대기하게 되어, 원본 큐 메시지를 다 읽어버려 발생하는 OOM 방지.
    private final java.util.concurrent.Semaphore backpressureTokens = new java.util.concurrent.Semaphore(
            MAX_WORKER_THREADS + 100);

    // 실제 API 통신을 담당할 우선순위 기반 워커 풀
    private java.util.concurrent.ExecutorService workerPool;

    @PostConstruct
    public void startEnterpriseConsumers() {
        // 1. 일반 큐가 아닌 PriorityBlockingQueue를 내부 작업 대기열로 사용하는 워커 풀 생성
        this.workerPool = new java.util.concurrent.ThreadPoolExecutor(
                MAX_WORKER_THREADS, MAX_WORKER_THREADS,
                0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.PriorityBlockingQueue<Runnable>());

        // 2. CPU를 전혀 쓰지 않는(take 블로킹) 매니저(디스패처) 스레드 3개만 별도로 기동
        startPriorityDispatcher(mainQueue, 1, false, "Main-Dispatcher");
        startPriorityDispatcher(reservedQueue, 2, false, "Reserved-Dispatcher");
        startPriorityDispatcher(failedQueue, 3, true, "Failed-Dispatcher");
    }

    private void startPriorityDispatcher(BlockingQueue<Long> queue, int priority, boolean isFailedQueue, String name) {
        Thread dispatcher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1. (가장 중요) poll(100ms)이라는 꼼수 대신, 알림이 올 때까지 이벤트 기반 완벽 대기 (CPU 0%)
                    Long notiId = queue.take();

                    // 2. 워커 풀 + 버퍼가 300개를 초과했으면 자리가 날 때까지 여기서 스스로 대기
                    backpressureTokens.acquire();

                    // 3. 자리가 나면 우선순위가 적힌 작업표(PrioritizedTask)를 풀장(workerPool)에 던져넣음
                    workerPool.execute(new PrioritizedTask(priority, notiId, isFailedQueue));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        dispatcher.setName(name);
        dispatcher.start();
    }

    // 우선순위 큐(PriorityBlockingQueue)에서 자동으로 정렬될 수 있도록 Comparable을 구현한 작업 단위
    private class PrioritizedTask implements Runnable, Comparable<PrioritizedTask> {
        private final int priority;
        private final Long notiId;
        private final boolean isFailedQueue;

        public PrioritizedTask(int priority, Long notiId, boolean isFailedQueue) {
            this.priority = priority;
            this.notiId = notiId;
            this.isFailedQueue = isFailedQueue;
        }

        @Override
        public int compareTo(PrioritizedTask other) {
            // priority 숫자가 작을수록(1:메인) 우선순위가 높기 때문에 오름차순 정렬
            return Integer.compare(this.priority, other.priority);
        }

        @Override
        public void run() {
            try {
                processNotification(notiId, isFailedQueue);
            } finally {
                // 작업이 끝나면 반드시 토큰을 1개 반납하여, 디스패처가 다음 메시지를 가져오도록!
                backpressureTokens.release();
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 5000)
    public void logWorkerStats() {
        if (workerPool == null)
            return;
        java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) workerPool;
        log.info("[WORKER STATS] active={}/{} | pendingTasks={} | tokens={}/300 | queues=[M:{} R:{} F:{}]",
                tpe.getActiveCount(), MAX_WORKER_THREADS,
                tpe.getQueue().size(),
                backpressureTokens.availablePermits(),
                mainQueue.size(), reservedQueue.size(), failedQueue.size());
    }

    private void processNotification(Long notiId, boolean isFailedQueue) {
        NotificationRequest noti = repo.findById(notiId).orElse(null);
        if (noti == null)
            return;

        try {
            SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
            handleSendResult(noti, result, isFailedQueue);
        } catch (Exception e) {
            noti.setStatus(NotificationStatus.FAILED);
            log.error("WORKER PROCESS ERROR", e);
        }

        noti.setLastTriedAt(LocalDateTime.now());
        noti.setTryCount(noti.getTryCount() + 1);
        repo.save(noti);
    }

    private void handleSendResult(NotificationRequest noti, SendResult result, boolean isFailedQueue) {
        if (result == SendResult.SUCCESS) {
            noti.setStatus(NotificationStatus.SUCCESS);
        } else if (result == SendResult.FAILURE) {
            noti.setStatus(NotificationStatus.PERMANENT_FAILED);
        } else { // ERROR
            if (isFailedQueue) {
                noti.setStatus(
                        noti.getTryCount() + 1 >= 3 ? NotificationStatus.PERMANENT_FAILED : NotificationStatus.FAILED);
            } else {
                noti.setStatus(NotificationStatus.FAILED);
            }
        }
    }
}
