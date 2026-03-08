package com.ym.noti.query.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationHistoryResponse {
    private Long id;
    private String receiver;
    private String channel;
    private String title;
    private String content;
    private String notiType;
    @JsonFormat(pattern = "yyyyMMddHHmm")
    private LocalDateTime reservedAt;
    private NotificationStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public NotificationHistoryResponse(NotificationRequest entity) {
        this.id = entity.getId();
        this.receiver = entity.getReceiver();
        this.channel = entity.getChannel();
        this.title = entity.getTitle();
        this.content = entity.getContent();
        this.notiType = entity.getNotiType();
        this.reservedAt = entity.getReservedAt();
        this.status = entity.getStatus();
        this.createdAt = entity.getCreatedAt();
    }
}
