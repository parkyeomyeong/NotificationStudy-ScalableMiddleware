package com.ym.noti.command.router.sender;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.SendResult;
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
    public SendResult send(NotificationRequest request) {
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
            String status = (String) response.getBody().get("status");
            if ("SUCCESS".equals(status)) {
                return SendResult.SUCCESS;
            } else {
                // Mock 서버가 200 OK를 주면서 논리적 FAILURE를 뱉을 때
                return SendResult.FAILURE;
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // HTTP 4xx, 5xx 에러 (Mock 서버가 500 ERROR를 뱉을 때)
            // e.getResponseBodyAsString() 을 통해 Mock 서버가 보낸 JSON 에러 바디를 읽을 수 있음
            return SendResult.ERROR;
        } catch (Exception e) {
            // 그 외 네트워크 단절 등
            return SendResult.ERROR;
        }
    }

    @Override
    public String getChannel() {
        return "EXTERNAL"; // 범용 채널 이름
    }
}
