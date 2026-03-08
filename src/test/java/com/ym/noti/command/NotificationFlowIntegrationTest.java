package com.ym.noti.command;

import com.ym.noti.command.data.NotificationRepository;
import com.ym.noti.command.domain.NotificationRequest;
import com.ym.noti.command.domain.NotificationStatus;
import com.ym.noti.command.dto.NotiCommandRequest;
import com.ym.noti.command.router.NotiSenderRouter;
import com.ym.noti.command.service.NotificationQueueFillerScheduler;
import com.ym.noti.command.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create")
class NotificationFlowIntegrationTest {

    @Autowired
    NotificationService service;
    @Autowired
    NotificationRepository repo;
    @Autowired
    NotiSenderRouter router;
    @Autowired
    NotificationQueueFillerScheduler scheduler;

    @Qualifier("mainNotiQueue")
    @Autowired
    BlockingQueue<Long> queue;

    @Test
    @DisplayName("[번외] 발솔서버는 알람처리를 단일 쓰레드로만 처리하는지 병렬로 하는지 확인1")
    void isNotiServiceOneThread1() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            NotificationRequest request = new NotificationRequest();
            request.setChannel("EXTERNAL");
            request.setReceiver("Test user");
            request.setTitle("Test title");
            request.setContent("Test content");
            futures.add(executor.submit(() -> {
                long start = System.currentTimeMillis();
                router.getNotiSender(request.getChannel()).send(request);
                return start;
            }));
        }

        for (Future<Long> future : futures) {
            System.out.println("응답 시간: " + future.get() + "ms");
        }
    }

    @Test
    @DisplayName("[번외] 실제 서버 로직과 동일하게 동시에 3알람을 보내고 발송서버가 동시에 받고 처리하는지 확인")
    void isNotiServiceOneThread2() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3); // 테스트가 끝나기전에 JVM종료되서 기다리게 하기 위함

        NotificationRequest request1 = new NotificationRequest();
        NotificationRequest request2 = new NotificationRequest();
        NotificationRequest request3 = new NotificationRequest();
        request1.setChannel("EXTERNAL");
        request2.setChannel("EXTERNAL");
        request3.setChannel("EXTERNAL");
        request1.setReceiver("Test user1");
        request2.setReceiver("Test user2");
        request3.setReceiver("Test user3");
        request1.setTitle("Test title1");
        request2.setTitle("Test title2");
        request3.setTitle("Test title3");
        request1.setContent("Test content1");
        request2.setContent("Test content2");
        request3.setContent("Test content3");
        request1.setTryCount(0);
        request2.setTryCount(0);
        request3.setTryCount(0);
        request1.setStatus(NotificationStatus.PENDING);
        request2.setStatus(NotificationStatus.PENDING);
        request3.setStatus(NotificationStatus.FAILED);

        List<NotificationRequest> requests = new ArrayList<>();
        requests.add(request1);
        requests.add(request2);
        requests.add(request3);

        for (int i = 0; i < 3; i++) {
            final int index = i;
            Executors.newSingleThreadExecutor().submit(() -> {
                NotificationRequest noti = requests.get(index);

                try {
                    System.out.println("보냈나?");
                    boolean result = router.getNotiSender(noti.getChannel()).send(noti);
                    noti.setStatus(result ? NotificationStatus.SUCCESS : NotificationStatus.FAILED);
                    System.out.println(index + "번째 쓰레드 완료 : " + noti.getStatus());
                } catch (Exception e) {
                    System.out.println("실패여?" + e.getMessage());
                    noti.setStatus(NotificationStatus.FAILED);
                }
                noti.setLastTriedAt(LocalDateTime.now());
                noti.setTryCount(noti.getTryCount() + 1);
                // repo.save(noti);
            });
        }
        latch.await(); // 모든 작업이 끝날 때까지 대기
    }

    @Test
    @DisplayName("[통합] DB 저장")
    void notificationShouldGoToPending() {
        // given: 알림 요청 생성
        NotiCommandRequest dto = new NotiCommandRequest();
        dto.setChannel("EXTERNAL");
        dto.setContent("test content");
        dto.setReceiver("test receiver");
        dto.setReservedAt(LocalDateTime.now());

        // when: 등록 API 호출
        service.register(dto);

        // then: 저장된 상태가 PENDING인지 확인
        List<NotificationRequest> all = repo.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("[통합] 디비 저장 후 큐에 잘 들어가는지 확인 - 이거 할때는 컨슈머에 PostContruct 잠시 주석하고 해야함")
    void schedulerShouldQueuePendingTasks() {
        // given: PENDING 상태 알림 저장
        NotiCommandRequest dto = new NotiCommandRequest();
        dto.setChannel("EXTERNAL");
        dto.setContent("test content");
        dto.setReceiver("test receiver");
        dto.setReservedAt(LocalDateTime.now());

        service.register(dto);

        // when: 스케줄러 실행
        scheduler.refillMainQueueFromDb();

        // System.out.println("📦 scheduler 큐: " +
        // System.identityHashCode(scheduler.getQueue()));
        // System.out.println("📦 test 주입 큐: " + System.identityHashCode(this.queue));

        // then: 큐에 데이터가 들어갔는지 확인
        assertThat(queue.size()).isGreaterThan(0);
    }
}
