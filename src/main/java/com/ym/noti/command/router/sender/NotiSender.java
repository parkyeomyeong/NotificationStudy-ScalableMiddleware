package com.ym.noti.command.router.sender;

import com.ym.noti.command.dto.NotiCommandRequest;

public interface NotiSender {
    public Boolean send(NotiCommandRequest request);
    public String getChannel();
}
