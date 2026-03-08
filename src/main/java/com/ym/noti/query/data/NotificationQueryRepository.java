package com.ym.noti.query.data;

import com.ym.noti.command.domain.NotificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NotificationQueryRepository extends JpaRepository<NotificationRequest, Long> {
    // 수신자 기준으로 조회
    Page<NotificationRequest> findByReceiverAndCreatedAtGreaterThanEqual(String receiver, LocalDateTime after, Pageable pageable);
}
