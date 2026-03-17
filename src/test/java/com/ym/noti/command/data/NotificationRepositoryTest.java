package com.ym.noti.command.data;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    NotificationRepository repo;

    @Test
    @DisplayName("[Unit]스케쥴러로 12개월 이상 지난거 삭제되는 로직 잘 되는지 확인")
    void shouldDeleteNotificationsOlderThan12Months() {
        LocalDateTime now = LocalDateTime.now();

        NotificationRequest old = new NotificationRequest();
        old.setCreatedAt(now.minusMonths(13));

        NotificationRequest recent = new NotificationRequest();
        recent.setCreatedAt(now.minusMonths(2));

        repo.saveAll(List.of(old, recent));
        // when
        repo.deleteOldNotifications(now.minusMonths(3));

        // then
        List<NotificationRequest> remaining = repo.findAll();
        assertThat(remaining).contains(recent);
        assertThat(remaining).doesNotContain(old);
    }

    // [DEPRECATED] 커서 기반(ID) 폴링으로 리팩토링 후 삭제된 메서드. 테스트 비활성화.
    // @Test
    // void findTop100ByStatusAndCreatedAtAfterOrderByCreatedAtAsc() { ... }

    @Test
    void findTop100ByStatusShouldReturnOrderedList() {
        // given: createdAt이 다른 알림 3개 저장
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 3; i++) {
            NotificationRequest n = new NotificationRequest();
            n.setStatus(NotificationStatus.PENDING);
            n.setCreatedAt(now.minusMinutes(i));
            n.setChannel("TEST");
            n.setContent("Test Content");
            n.setReceiver("Test Receiver");
            repo.save(n);
        }

        // when: 정렬 쿼리 실행
        List<NotificationRequest> result = repo.findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);
        // then: createdAt 순으로 정렬됐는지 확인
        assertThat(result).isSortedAccordingTo(Comparator.comparing(NotificationRequest::getCreatedAt));
    }
}