package com.ym.noti.command.router.sender;

import com.ym.noti.command.domain.NotificationRequest;

public interface NotiSender {
    public Boolean send(NotificationRequest request);

    public String getChannel();
}
