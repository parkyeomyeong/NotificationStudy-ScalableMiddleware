// ====================================================
// 공통 유틸 - 모든 테스트에서 공유
// ====================================================

export const BASE_URL = 'http://localhost:8080';

let counter = 0;

// 알림 등록 요청 바디 생성
export function makePayload() {
  counter++;
  return JSON.stringify({
    channel: 'teams',
    receiver: `user_${counter}`,
    title: `부하테스트 알림 ${counter}`,
    content: `테스트 내용 ${counter}`,
  });
}

export const JSON_HEADERS = { 'Content-Type': 'application/json' };
