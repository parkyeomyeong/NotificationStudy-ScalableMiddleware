package com.ym.noti.query.data;

import com.ym.noti.command.domain.NotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationQueryRepositoryTest {

    @Autowired
    NotificationQueryRepository repo;

    @Test
    @DisplayName("[Unit] 실제로 12개월 이내 데이터만 페이징 해서 가져오는지 가져오는지 확인")
    void find_recent_notifications_within_12_months() {
        String receiver = "user@test.com";
        LocalDateTime now = LocalDateTime.now();

        NotificationRequest first = new NotificationRequest();
        first.setReceiver(receiver);
        first.setTitle("Test" + 1);
        first.setContent("Test" + 1);
        first.setCreatedAt(now.minusMonths(1));

        NotificationRequest old = new NotificationRequest();
        old.setReceiver(receiver);
        old.setTitle("Test" + 11);
        old.setContent("Test" + 11);
        old.setCreatedAt(now.minusMonths(4));

        List<NotificationRequest> saveList = new ArrayList<>();
        saveList.add(first);
        for (int i = 2; i < 11; i++) {
            NotificationRequest saveData = new NotificationRequest();
            saveData.setReceiver(receiver);
            saveData.setTitle("Test" + i);
            saveData.setContent("Test" + i);
            saveData.setCreatedAt(now.minusMonths(1));
            saveList.add(saveData);
        }
        saveList.add(old);
        repo.saveAll(saveList);

        Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<NotificationRequest> result = repo.findByReceiverAndCreatedAtGreaterThanEqual(
                receiver, now.minusMonths(12), pageable).getContent();
        assertThat(result).doesNotContain(first);
        assertThat(result).doesNotContain(old);
    }
}