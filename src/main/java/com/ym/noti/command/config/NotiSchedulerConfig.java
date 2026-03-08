package com.ym.noti.command.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class NotiSchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // 스케줄러 수 + 여유분
        scheduler.setThreadNamePrefix("noti-scheduler-"); //queue채우는 스케쥴러 쓰레드 확인하기
        scheduler.initialize();
        return scheduler;
    }
}
