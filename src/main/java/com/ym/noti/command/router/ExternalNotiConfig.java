package com.ym.noti.command.router;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ExternalNotiConfig {

    public static final String BASE_URL = "http://localhost:8090/";

    //Singleton으로 RestTemplate 관리 - 추후 원리 좀더 파악 필요
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
