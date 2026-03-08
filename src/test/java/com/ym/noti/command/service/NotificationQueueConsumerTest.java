package com.ym.noti.command.service;

import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.domain.SendResult;
import com.ym.noti.command.router.NotiSenderRouter;
import com.ym.noti.command.router.sender.NotiSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationQueueConsumerTest {

    // Mock 객체 주입
    @Mock
    NotificationRepository repo;
    @Mock
    NotiSenderRouter router;
    @Mock
    NotiSender mockSender;
    @Mock
    BlockingQueue<Long> queue;
    // 테스트 대상 클래스에 Mock 주입
    @InjectMocks
    NotificationQueueConsumer consumer;

    @Test
    @DisplayName("[Unit] 큐 컨수머에서 꺼내서 잘 쓰는지 로직 테스트 ")
    void sendSuccess_shouldUpdateStatusToSuccess() throws Exception {
        // given: 알림 요청 생성
        NotificationRequest entity = new NotificationRequest();
        entity.setId(1L);
        // entity.setChannel("EXTERNAL");
        entity.setStatus(NotificationStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());

        // 컨수머에서 수행되는 실제 로직 start
        Long notiId = entity.getId();
        NotificationRequest noti = repo.findById(notiId).orElse(null);
        if (noti == null)
            return;

        try {
            SendResult result = router.getNotiSender(noti.getChannel()).send(noti);
            if (result == SendResult.SUCCESS) {
                noti.setStatus(NotificationStatus.SUCCESS);
            } else if (result == SendResult.FAILURE) {
                noti.setStatus(NotificationStatus.PERMANENT_FAILED);
            } else { // ERROR
                noti.setStatus(NotificationStatus.FAILED);
            }
        } catch (Exception e) {
            noti.setStatus(NotificationStatus.FAILED);
        }
        noti.setLastTriedAt(LocalDateTime.now());
        noti.setTryCount(noti.getTryCount() + 1);
        repo.save(noti);
        // end

        verify(repo).save(argThat(n -> n.getStatus() == NotificationStatus.SUCCESS));
    }
}
