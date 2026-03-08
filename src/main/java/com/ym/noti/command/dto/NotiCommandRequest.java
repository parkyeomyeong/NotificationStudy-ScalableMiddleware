package com.ym.noti.command.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotiCommandRequest {
    private String channel;
    private String receiver;
    private String title;
    private String content;
    private String notiType;
    @JsonFormat(pattern = "yyyyMMddHHmm")
    private LocalDateTime reservedAt;

    public NotiCommandRequest() {
    }
}
