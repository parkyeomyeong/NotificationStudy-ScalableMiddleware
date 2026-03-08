package com.ym.noti.command.dto;

public class NotiCommandResponse {
    private String resultCode;

    public NotiCommandResponse() {
        this.resultCode = "FAIL";
    }

    public NotiCommandResponse(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultCode() {
        return resultCode;
    }
}
