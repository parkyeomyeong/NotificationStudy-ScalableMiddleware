package com.ym.noti.command.service;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.router.NotiSenderRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotiSenderRouter router;
    private final NotificationRepository repo;

    @Transactional // 하나의 단위에 insert, update가 있기 때문에 같은 영속성 공유를 위해 어노테이션
    public void register(NotiCommandRequest request) {
        // 인자값 유효성 검사
        if (!router.hasChannel(request.getChannel())) {
            throw new IllegalArgumentException("지원하지 않는 채널입니다: " + request.getChannel());
        }
        // 받은 요청은 일단 DB로 저장
        NotificationRequest noti = new NotificationRequest(request);

        if (request.getReservedAt() == null) {
            noti.setStatus(NotificationStatus.PENDING);
        } else {
            noti.setStatus(NotificationStatus.RESERVED);
        }

        noti.setCreatedAt(LocalDateTime.now());
        repo.save(noti); // DB에 먼저 저장 (id 없으면 Insert)
    }

}
