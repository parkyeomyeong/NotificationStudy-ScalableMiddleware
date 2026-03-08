package com.ym.noti.command.data;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationRequest, Long> {
        List<NotificationRequest> findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus status);

        // 커서기반 페이징 쿼리
        List<NotificationRequest> findTop100ByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                        NotificationStatus status,
                        LocalDateTime createdAtCursor);

        // 3번 이하로 실패한 알람을 실패 순서대로 가져오기
        List<NotificationRequest> findTop100ByStatusOrderByLastTriedAtAsc(NotificationStatus status);

        // 예약 알람 가져오기 - 스케쥴러에서 지금 시간 기준으로 예약시간 이하인 알람들 모두 가져오기
        List<NotificationRequest> findTop100ByStatusAndReservedAtLessThanEqualOrderByReservedAtAsc(
                        NotificationStatus status,
                        LocalDateTime curTime);

        // 12개월 지난 데이터 삭제
        @Transactional
        @Modifying
        @Query("DELETE FROM NotificationRequest n WHERE n.createdAt < :threshold")
        void deleteOldNotifications(@Param("threshold") LocalDateTime threshold);
}