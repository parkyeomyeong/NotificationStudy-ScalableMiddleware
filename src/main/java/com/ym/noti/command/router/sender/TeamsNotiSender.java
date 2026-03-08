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
public class TeamsNotiSender implements NotiSender {

    private final RestTemplate restTemplate;

    @Autowired
    public TeamsNotiSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public SendResult send(NotificationRequest request) {
        String url = ExternalNotiConfig.BASE_URL + "api/v1/notification/teams";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("toTeamsUserId", request.getReceiver());
        body.put("title", request.getTitle());
        body.put("content", request.getContent());

        HttpEntity<Map<String, String>> requestBody = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestBody, Map.class);
            String status = (String) response.getBody().get("status");
            if ("SUCCESS".equals(status)) {
                return SendResult.SUCCESS;
            } else {
                return SendResult.FAILURE;
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            return SendResult.ERROR;
        } catch (Exception e) {
            return SendResult.ERROR;
        }
    }

    @Override
    public String getChannel() {
        return "TEAMS";
    }
}
