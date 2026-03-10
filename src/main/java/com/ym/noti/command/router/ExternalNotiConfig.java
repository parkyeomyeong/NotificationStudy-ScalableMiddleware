package com.ym.noti.command.router;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ExternalNotiConfig {

    public static final String BASE_URL = "http://localhost:8081/";
    // public static final String BASE_URL =
    // "https://port-0-notificationstudy-mockapiserver-mm8l71fu10b9179d.sel3.cloudtype.app/";

    private static final int TIMEOUT_MS = 6_000; // 6초 (mock 평균 응답 4초의 1.5배, Failed 큐 병목 하한 3초 초과)

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
