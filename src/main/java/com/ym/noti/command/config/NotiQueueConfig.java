package com.ym.noti.command.config;

import com.ym.noti.command.dto.NotiCommandRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Configuration
public class NotiQueueConfig {
    @Bean(name="mainNotiQueue")
    public BlockingQueue<NotiCommandRequest> mainNotiQueue() {
        // 요청들을 밸런스있게 발송서버에 전달하기 위해선 큐가 넘치면 안되기 때문에 제한
        return new ArrayBlockingQueue<>(1000);
    }
    @Bean(name="reservedNotiQueue")
    public BlockingQueue<NotiCommandRequest> reservedNotiQueue() {
        // 요청들을 밸런스있게 발송서버에 전달하기 위해선 큐가 넘치면 안되기 때문에 제한
        return new ArrayBlockingQueue<>(1000);
    }
    @Bean(name="failedNotiQueue")
    public BlockingQueue<NotiCommandRequest> failedNotiQueue() {
        // 요청들을 밸런스있게 발송서버에 전달하기 위해선 큐가 넘치면 안되기 때문에 제한
        return new ArrayBlockingQueue<>(500);
    }
}
