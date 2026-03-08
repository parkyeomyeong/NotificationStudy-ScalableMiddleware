package com.ym.noti.command.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ym.noti.command.domain.NotificationRequest;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotiCommandRequest {
    private Long id;
    private String channel;
    private String receiver;
    private String title;
    private String content;
    private String notiType;
    @JsonFormat(pattern = "yyyyMMddHHmm")
    private LocalDateTime reservedAt;

    public NotiCommandRequest() {}
    public NotiCommandRequest(NotificationRequest notificationRequest) {
        this.id = notificationRequest.getId();
        this.channel = notificationRequest.getChannel();
        this.receiver = notificationRequest.getReceiver();
        this.title = notificationRequest.getTitle();
        this.content = notificationRequest.getContent();
        this.notiType = notificationRequest.getNotiType();
        this.reservedAt = notificationRequest.getReservedAt();
    }
}
