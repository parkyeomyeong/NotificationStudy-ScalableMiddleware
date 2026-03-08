package com.ym.noti.command.controller;

import com.ym.noti.command.router.sender.NotiSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class NotiCommandControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private NotiSender notiSenderService; // 실제 Sender 대신에 가짜(mock) 주입

  @Test
  @DisplayName("[Unit Test] senderService.send(...)가 호출되면 무조건 true를 리턴하도록 설정 (성공 시뮬레이션)")
  void notiRequestForSuccess() throws Exception {
    given(notiSenderService.send(any())).willReturn(true);

    mockMvc.perform(post("/notifications/regist")
        .contentType(MediaType.APPLICATION_JSON) // 요청의 Content-Type은 JSON
        .accept(MediaType.APPLICATION_JSON) // 응답도 JSON으로 받겠다
        .content("""
                {
                  "channel": "EXTERNAL",
                  "receiver": "01012345678",
                  "title": "성공 테스트",
                  "contents": "내용"
                }
            """))
        .andExpect(status().isOk()); // 기대: HTTP 200 응답
  }

  @Test
  @DisplayName("[Unit Test] senderService.send(...)가 호출되면 무조건 false를 리턴하도록 설정 (실패 시뮬레이션)")
  void notiRequestForFailure() throws Exception {
    given(notiSenderService.send(any())).willReturn(false);

    mockMvc.perform(post("/notifications/regist")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content("""
                {
                  "channel": "EXTERNAL",
                  "receiver": "01012345678",
                  "title": "실패 테스트",
                  "contents": "내용"
                }
            """))
        .andExpect(status().isInternalServerError()); // 기대: HTTP 500 응답
  }
}
