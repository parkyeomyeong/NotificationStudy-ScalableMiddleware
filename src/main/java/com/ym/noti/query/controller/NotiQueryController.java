package com.ym.noti.query.controller;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.query.data.NotificationQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.ym.noti.query.dto.NotificationHistoryResponse;
import java.time.LocalDateTime;

@Tag(name = "Notification", description = "알림 조회 API")
@RestController
@RequestMapping("/notifications")
public class NotiQueryController {
    private final NotificationQueryRepository notificationQueryRepository;

    @Autowired
    public NotiQueryController(NotificationQueryRepository notificationQueryRepository) {
        this.notificationQueryRepository = notificationQueryRepository;
    }

    @Operation(summary = "알림 성공목록 조회", description = "최대 N개월 안에 발송 성공한 알림")
    @GetMapping("/history")
    public Page<NotificationHistoryResponse> getHistory(
            @RequestParam String receiver,
            @RequestParam(defaultValue = "1") int month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime NMonthsAgo = LocalDateTime.now().minusMonths(month);

        Page<NotificationRequest> result = notificationQueryRepository
                .findByReceiverAndCreatedAtGreaterThanEqual(receiver, NMonthsAgo, pageable);
        return result.map(NotificationHistoryResponse::new);
    }

}
