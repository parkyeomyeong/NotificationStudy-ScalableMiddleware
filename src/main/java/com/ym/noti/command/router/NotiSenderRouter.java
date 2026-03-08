package com.ym.noti.command.router;

import com.ym.noti.command.router.sender.NotiSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotiSenderRouter {
    private final Map<String, NotiSender> notiSenderMap = new ConcurrentHashMap<>(); // 멀티쓰레드 환경에서 안전하게 쓰이는 HashMap

    //NotiSender를 구현한 Component 빈을 찾아서 알아서 주입
    @Autowired
    public NotiSenderRouter(List<NotiSender> notiSenderList) {
        for (NotiSender notiSender : notiSenderList) {
            notiSenderMap.put(notiSender.getChannel(), notiSender);
        }
    }
    //현재 허용되는 채널인지
    public Boolean hasChannel(String channel) {
        return notiSenderMap.containsKey(channel.toUpperCase());
    }
    //발송서버로 알람 전송 요청보내기위한 센더 반환
    public NotiSender getNotiSender(String channel) {
        NotiSender sender = notiSenderMap.get(channel.toUpperCase());
        if (sender == null) {
            throw new IllegalArgumentException("지원하지 않는 채널입니다: " + channel);
        }
        return sender;
    }
}
