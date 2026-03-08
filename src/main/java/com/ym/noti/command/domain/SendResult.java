package com.ym.noti.command.domain;

public enum SendResult {
    SUCCESS,
    FAILURE, // 논리적 실패 (차단 유저, 잘못된 형식 인자 사용 등) -> 재시도 안 함
    ERROR // 물리적 실패 (타임아웃, 서버 오류 등) -> 재시도 해야 함
}
