package com.ym.noti.command.domain;

import com.ym.noti.command.dto.NotiCommandRequest;
import jakarta.persistence.*;
import lombok.Setter;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "notification_request", indexes = {
        @Index(name = "idx_status_createdAt", columnList = "status, createdAt"), // 조회 + 정렬을 위해 복합 인덱스로 성능 올리기
        @Index(name = "idx_status_reservedAt", columnList = "status, reservedAt"),
        @Index(name = "idx_receiver_createdAt", columnList = "receiver, createdAt") // 조회 API 최적화

})
public class NotificationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String receiver; // 수신자
    private String channel; // 발송 채널
    private String title; // 제목
    private String content; // 알림 내용
    private String notiType; // 알람 타입 (일반, 예약)

    private LocalDateTime reservedAt; // 예약 발송 시각 (예약 아닐 경우 null)

    @Enumerated(EnumType.STRING)
    private NotificationStatus status; // 상태 (PENDING, QUEUED, SUCCESS, FAILED)

    private LocalDateTime createdAt; // 생성 시각
    private LocalDateTime lastTriedAt; // 마지막 시도 시각

    @Column(nullable = false)
    private int tryCount = 0; // 시도 횟수

    public NotificationRequest() {
    }

    public NotificationRequest(NotiCommandRequest dto) {
        this.channel = dto.getChannel();
        this.receiver = dto.getReceiver();
        this.title = dto.getTitle();
        this.content = dto.getContent();
        this.notiType = dto.getNotiType();
        this.reservedAt = dto.getReservedAt(); // 예약 알림이면 여기에 시간 들어감
        // status, createdAt 등은 서비스에서 따로 설정
    }
}
