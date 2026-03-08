package com.ym.noti.command.router.sender;

import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.router.ExternalNotiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class ExternalNotiSender implements NotiSender {

    private final RestTemplate restTemplate;

    @Autowired
    public ExternalNotiSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Boolean send(NotiCommandRequest request) {
        // Mock API 로 보낼 주소
        String url = ExternalNotiConfig.BASE_URL + "/mock/send";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("receiver", request.getReceiver());
        body.put("title", request.getTitle());
        body.put("contents", request.getContent());

        HttpEntity<Map<String, String>> requestBody = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestBody, Map.class);
            return "SUCCESS".equals(response.getBody().get("resultCode"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getChannel() {
        return "EXTERNAL"; // 범용 채널 이름
    }
}
