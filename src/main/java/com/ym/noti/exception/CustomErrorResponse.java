package com.ym.noti.exception;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomErrorResponse {
    private int status;
    private String code;
    private String message;
    private LocalDateTime timestamp;
}
