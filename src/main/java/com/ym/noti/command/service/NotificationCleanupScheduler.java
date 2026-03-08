package com.ym.noti.command.service;

import com.ym.noti.command.data.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationRepository repo;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정 12개월 지난 알람내역 삭제
    public void cleanUpOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(12);
        repo.deleteOldNotifications(threshold);
    }
}