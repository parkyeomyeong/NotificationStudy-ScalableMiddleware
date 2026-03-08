package com.ym.noti.command.service;

import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import com.ym.noti.command.dto.NotiCommandRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.Notification;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NotificationQueueFillerScheduler {

    private final BlockingQueue<Long> mainQueue;
    private final BlockingQueue<Long> reservedQueue;
    private final BlockingQueue<Long> failedQueue;
    private final NotificationRepository repo;

    @Autowired
    public NotificationQueueFillerScheduler(
            @Qualifier("mainNotiQueue") BlockingQueue<Long> mainQueue,
            @Qualifier("reservedNotiQueue") BlockingQueue<Long> reservedQueue,
            @Qualifier("failedNotiQueue") BlockingQueue<Long> failedQueue,
            NotificationRepository repo) {
        this.mainQueue = mainQueue;
        this.reservedQueue = reservedQueue;
        this.failedQueue = failedQueue;
        this.repo = repo;
    }

    private LocalDateTime cursor = LocalDateTime.now().minusYears(10); // 초기 커서 값
    private final AtomicBoolean isMainSchRunning = new AtomicBoolean(false);
    private final AtomicBoolean isReservedSchRunning = new AtomicBoolean(false);
    private final AtomicBoolean isFailedSchRunning = new AtomicBoolean(false);

    // public BlockingQueue<NotiCommandRequest> getQueue(){return this.queue;} // 같은
    // 큐 쓰는지 확인위해 테스트하려고 잠시 만듬

    // [1. 일반 알람발송] DB -> Queue로 넣는 스케쥴러
    // fixedRate로 1초마다 실행하면 작업이 밀리면 계속 대기하는 작업이 많아져서 fixedDelay로 지정(끝나면 1초뒤실행)
    @Scheduled(fixedDelay = 1_000)
    public void refillMainQueueFromDb() {
        // 앞에서 이미 실행중 이라면 실행 안함 (기본적인 스케쥴링 처리는 단일 쓰래드라 중복 처리 걱정 안해도 되지만 쓰레드 풀 늘릴 예정이라 이
        // 검증 작업 추가)
        if (!isMainSchRunning.compareAndSet(false, true))
            return;

        try {
            // System.out.println("현재 main queeu 실행 중인 스레드: " +
            // Thread.currentThread().getName());

            // queue에 남은 자리가 있을떄만 큐에 넣기
            if (mainQueue.remainingCapacity() > 0) {
                // 대기상태의 메시지를 들어온 순서대로 100개 가져오기
                List<NotificationRequest> candidates = repo.findTop100ByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                        NotificationStatus.PENDING,
                        cursor);
                // 벌크 처리를 하면 데이터 정확성과 장애 대비가 어려워 건 by 건으로 데이터 update
                for (NotificationRequest notiEntity : candidates) {
                    if (mainQueue.offer(notiEntity.getId())) {
                        cursor = notiEntity.getCreatedAt(); // 커서 업데이트 (일단 큐에 들어가면 해당시간으로 커서 변경!!!!!!!!!!)
                        notiEntity.setStatus(NotificationStatus.QUEUED);
                        repo.save(notiEntity);
                    } else
                        break; // 큐가 가득 차면 중단
                }
            }
        } finally {
            isMainSchRunning.set(false); // 반드시 실행 종료 후 플래그 해제
        }
    }

    // [2. 예약 알람 발송] DB -> Queue로 넣는 스케쥴러
    @Scheduled(fixedDelay = 5_000)
    public void refillReservedQueue() {
        // 앞에서 이미 실행중 이라면 실행 안함
        if (!isReservedSchRunning.compareAndSet(false, true))
            return;

        try {
            // queue에 남은 자리가 있을떄만 큐에 넣기
            if (reservedQueue.remainingCapacity() > 0) {
                // 예약 된 알람 가져오기
                List<NotificationRequest> candidates = repo
                        .findTop100ByStatusAndReservedAtLessThanEqualOrderByReservedAtAsc(NotificationStatus.RESERVED,
                                LocalDateTime.now());

                for (NotificationRequest notiEntity : candidates) {
                    if (reservedQueue.offer(notiEntity.getId())) {
                        notiEntity.setStatus(NotificationStatus.QUEUED);
                        repo.save(notiEntity);
                    } else
                        break;
                }
            }
        } finally {
            isReservedSchRunning.set(false); // 반드시 실행 종료 후 플래그 해제
        }
    }

    // [3. 실패 알람 발송] DB -> Queue로 넣는 스케쥴러
    @Scheduled(fixedDelay = 5_000)
    public void refillFailedQueue() {
        // 앞에서 이미 실행중 이라면 실행 안함
        if (!isFailedSchRunning.compareAndSet(false, true))
            return;

        try {
            // queue에 남은 자리가 있을떄만 큐에 넣기
            if (failedQueue.remainingCapacity() > 0) {
                // 실패한 상태만 (영구실패도 안가져옴) 100개 마지막 시도 시간순서대로 가져옴
                List<NotificationRequest> candidates = repo
                        .findTop100ByStatusOrderByLastTriedAtAsc(NotificationStatus.FAILED);

                for (NotificationRequest notiEntity : candidates) {
                    // 최근 1분 이내에 이미 시도한 알림은 스킵
                    // if (noti.getLastTriedAt() != null &&
                    // noti.getLastTriedAt().isAfter(LocalDateTime.now().minusSeconds(60)))
                    // continue;

                    if (failedQueue.offer(notiEntity.getId())) {
                        notiEntity.setStatus(NotificationStatus.QUEUED);
                        repo.save(notiEntity);
                    } else
                        break;
                }
            }
        } finally {
            isFailedSchRunning.set(false); // 반드시 실행 종료 후 플래그 해제
        }
    }
}
