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

    // mock 서버 최대 동시 처리 200 기준으로 스레드 배분
    // 총 유입량 비율: Main(N) : Failed(13N/27) = 27:13
    // Main 120 / Reserved 20 / Failed 60 = 200
    private static final int MAIN_THREAD_COUNT     = 120;
    private static final int RESERVED_THREAD_COUNT =  20;
    private static final int FAILED_THREAD_COUNT   =  60;

    @PostConstruct
    public void startMainQueueConsumerThread() {
        var executor = Executors.newFixedThreadPool(MAIN_THREAD_COUNT);
        for (int i = 0; i < MAIN_THREAD_COUNT; i++) {
            executor.submit(() -> {
                while (true) {
                    Long notiId = mainQueue.take();
                    NotificationRequest noti = repo.findById(notiId).orElse(null);
                    if (noti == null) continue;
                    try {
                        log.warn("MAIN QUEUE SIZE: {}", mainQueue.size());
                        SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
                        handleSendResult(noti, result, false);
                    } catch (Exception e) {
                        noti.setStatus(NotificationStatus.FAILED);
                        log.error("MAIN QUEUE ERROR", e);
                    }
                    noti.setLastTriedAt(LocalDateTime.now());
                    noti.setTryCount(noti.getTryCount() + 1);
                    repo.save(noti);
                }
            });
        }
    }

    @PostConstruct
    public void startReservedQueueConsumerThread() {
        var executor = Executors.newFixedThreadPool(RESERVED_THREAD_COUNT);
        for (int i = 0; i < RESERVED_THREAD_COUNT; i++) {
            executor.submit(() -> {
                while (true) {
                    Long notiId = reservedQueue.take();
                    NotificationRequest noti = repo.findById(notiId).orElse(null);
                    if (noti == null) continue;
                    try {
                        log.warn("RESERVED QUEUE SIZE: {}", reservedQueue.size());
                        SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
                        handleSendResult(noti, result, false);
                    } catch (Exception e) {
                        noti.setStatus(NotificationStatus.FAILED);
                        log.error("RESERVED QUEUE ERROR", e);
                    }
                    noti.setLastTriedAt(LocalDateTime.now());
                    noti.setTryCount(noti.getTryCount() + 1);
                    repo.save(noti);
                }
            });
        }
    }

    @PostConstruct
    public void startFailedQueueConsumerThread() {
        var executor = Executors.newFixedThreadPool(FAILED_THREAD_COUNT);
        for (int i = 0; i < FAILED_THREAD_COUNT; i++) {
            executor.submit(() -> {
                while (true) {
                    Long notiId = failedQueue.take();
                    NotificationRequest noti = repo.findById(notiId).orElse(null);
                    if (noti == null) continue;
                    try {
                        log.warn("FAIL QUEUE SIZE: {}", failedQueue.size());
                        SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
                        handleSendResult(noti, result, true);
                    } catch (Exception e) {
                        noti.setStatus(NotificationStatus.FAILED);
                        log.error("FAIL QUEUE ERROR", e);
                    }
                    noti.setTryCount(noti.getTryCount() + 1);
                    noti.setLastTriedAt(LocalDateTime.now());
                    repo.save(noti);
                }
            });
        }
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
