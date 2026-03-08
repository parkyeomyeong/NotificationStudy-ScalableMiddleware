package com.ym.noti.query.controller;

import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.query.data.NotificationQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotiQueryController.class)
@AutoConfigureMockMvc
class NotiQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationQueryRepository repo;

    @Test
    @DisplayName("[Integration] 고객별 최근 12개월 알림내역 페이징 조회")
    void testHistoryAPI() throws Exception {
        String receiver = "user@test.com";
        LocalDateTime now = LocalDateTime.now();

        NotificationRequest data1 = new NotificationRequest();
        NotificationRequest data2 = new NotificationRequest();

        data1.setChannel("EXTERNAL");
        data2.setChannel("EXTERNAL");
        data1.setReceiver(receiver);
        data2.setReceiver(receiver);
        data1.setTitle("Test1");
        data2.setTitle("Test2");
        data1.setContent("Test1");
        data2.setContent("Test2");
        data1.setCreatedAt(now.minusDays(1));
        data2.setCreatedAt(now.minusMonths(13));

        List<NotificationRequest> list = List.of(data1, data2);
        Page<NotificationRequest> page = new PageImpl<>(list);

        given(repo.findByReceiverAndCreatedAtGreaterThanEqual(eq(receiver), any(), any())).willReturn(page);

        mockMvc.perform(get("/notifications/history")
                .param("receiver", receiver)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].createdAt").value(
                        containsString(now.minusDays(1).toLocalDate().toString())));
    }
}