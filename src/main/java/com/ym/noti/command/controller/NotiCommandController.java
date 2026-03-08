package com.ym.noti.command.controller;

import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.dto.NotiCommandResponse;
import com.ym.noti.command.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification registration", description = "알림 등록 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotiCommandController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 발송 등록", description = "채널에 따라 즉시/예약 발송을 등록")
    @PostMapping("/regist")
    public ResponseEntity<NotiCommandResponse> register(@RequestBody NotiCommandRequest request) {
        // 발송 처리 로직 (예: 외부 API 호출 등)
        notificationService.register(request);

        return ResponseEntity.ok(new NotiCommandResponse("SUCCESS"));
    }

}
