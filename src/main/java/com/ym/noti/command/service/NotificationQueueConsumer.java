package com.ym.noti.command.service;

import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
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
    private final BlockingQueue<NotiCommandRequest> mainQueue;
    private final BlockingQueue<NotiCommandRequest> reservedQueue;
    private final BlockingQueue<NotiCommandRequest> failedQueue;
    private final NotificationRepository repo;
    private final NotiSenderRouter router;

    @Autowired
    public NotificationQueueConsumer(
            @Qualifier("mainNotiQueue") BlockingQueue<NotiCommandRequest> mainQueue,
            @Qualifier("reservedNotiQueue") BlockingQueue<NotiCommandRequest> reservedQueue,
            @Qualifier("failedNotiQueue") BlockingQueue<NotiCommandRequest> failedQueue,
            NotificationRepository repo,
            NotiSenderRouter router
    ){
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
                NotiCommandRequest NotiTask = mainQueue.take(); //메시지 들어올 떄 까지 대기

                NotificationRequest noti = repo.findById(NotiTask.getId()).orElse(null);
                if (noti == null) continue;

                try {
                    log.warn("MAIN QUEUE SIZE: {}", mainQueue.size());
                    boolean result = router.getNotiSender(NotiTask.getChannel()).send(NotiTask);
                    noti.setStatus(result ? NotificationStatus.SUCCESS : NotificationStatus.FAILED);
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
                NotiCommandRequest NotiTask = reservedQueue.take(); //메시지 들어올 떄 까지 대기
                NotificationRequest noti = repo.findById(NotiTask.getId()).orElse(null);
                if (noti == null) continue;

                try {
                    log.warn("RESERVED QUEUE SIZE: {}", reservedQueue.size());
                    boolean result = router.getNotiSender(NotiTask.getChannel()).send(NotiTask);
                    noti.setStatus(result ? NotificationStatus.SUCCESS : NotificationStatus.FAILED);
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
                NotiCommandRequest NotiTask = failedQueue.take(); //메시지 들어올 떄 까지 대기
                NotificationRequest noti = repo.findById(NotiTask.getId()).orElse(null);
                if (noti == null) continue;

                try {
                    log.warn("FAIL QUEUE SIZE: {}", failedQueue.size());
                    boolean result = router.getNotiSender(NotiTask.getChannel()).send(NotiTask);
                    if (result) {
                        noti.setStatus(NotificationStatus.SUCCESS);
                    } else {
                        // 3번 시도했는데 모두 실패면 영구 실패로 저장
                        noti.setStatus(noti.getTryCount()+1 >= 3 ? NotificationStatus.PERMANENT_FAILED : NotificationStatus.FAILED);
                    }
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
