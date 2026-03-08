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

    // 메인 큐에서 메시지를 보내는 컨수머 : Bean으로 등록 하고 바로 실행되는 메소드 하나의 쓰레드 점유
    @PostConstruct
    public void startMainQueueConsumerThread() {
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                Long notiId = mainQueue.take(); // 메시지 들어올 떄 까지 대기

                NotificationRequest noti = repo.findById(notiId).orElse(null);
                if (noti == null)
                    continue;

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

    // 예약 큐에서 메시지를 보내는 컨수머 : Bean으로 등록 하고 바로 실행되는 메소드 하나의 쓰레드 점유(예약시간대로 )
    @PostConstruct
    public void startReservedQueueConsumerThread() {
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                Long notiId = reservedQueue.take(); // 메시지 들어올 떄 까지 대기
                NotificationRequest noti = repo.findById(notiId).orElse(null);
                if (noti == null)
                    continue;

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

    // 실패 큐에서 메시지를 보내는 컨수머 : Bean으로 등록 하고 바로 실행되는 메소드 하나의 쓰레드 점유
    @PostConstruct
    public void startFailedQueueConsumerThread() {
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                Long notiId = failedQueue.take(); // 메시지 들어올 떄 까지 대기
                NotificationRequest noti = repo.findById(notiId).orElse(null);
                if (noti == null)
                    continue;

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
