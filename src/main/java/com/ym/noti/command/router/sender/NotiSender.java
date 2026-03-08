package com.ym.noti.command.router.sender;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.SendResult;

public interface NotiSender {
    public SendResult send(NotificationRequest request);

    public String getChannel();
}
