// 이벤트(단계 변화, 추천, 적용·넘김, 구간 종료) API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getRecentEvents = (limit) => api(`/events?limit=${limit}`)
import { fakeGetRecentEvents } from '@/api/fakeBackend'

// 최근 이벤트 목록, 최신 순 (명세 4번: GET /api/events?limit=. 명세 기본값은 6, 화면은 10을 넘김)
export const getRecentEvents = (limit) => fakeGetRecentEvents(limit)
