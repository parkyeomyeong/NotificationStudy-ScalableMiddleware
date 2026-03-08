package com.ym.noti.command.service;

import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.router.sender.NotiSender;
import com.ym.noti.command.router.NotiSenderRouter;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest
public class NotiSenderServiceTest {

    @Mock
    private NotiSender mockSender;

    @MockBean
    private NotiSenderRouter router;

    @Autowired
    private NotiSenderService_de senderService;

    @Test
    @DisplayName("[Unit Test] 정상 채널일때 성공")
    void test1() {
        NotiCommandRequest req = new NotiCommandRequest();
        req.setChannel("EXTERNAL");

        // Router에서 sender 꺼내면 mockSender 리턴하게 설정
        given(router.getNotiSender("EXTERNAL")).willReturn(mockSender);
        given(mockSender.send(req)).willReturn(true);

        boolean result = senderService.send(req);

        assertTrue(result);
    }

    @Test
    @DisplayName("[Unit Test] 없는 채널일때 에러")
    void test2() {
        NotiCommandRequest req = new NotiCommandRequest();
        req.setChannel("INVALID");
        req.setReceiver("TEST");
        req.setTitle("TEST");
        req.setContent("TEST");

        // 2. Router에서 "INVALID" 채널 요청 시 예외 던지도록 설정
//        given(router.getNotiSender("INVALID"))
//                .willThrow(new IllegalArgumentException("지원하지 않는 채널입니다."));

        // 3. 실제 서비스 호출 시 예외가 발생해야 함
        assertThrows(IllegalArgumentException.class, () -> {
            senderService.send(req);
        });

    }

}
