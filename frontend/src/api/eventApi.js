// 이벤트(경고, 단계 변화, 적용 기록) API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getRecentEvents = (limit) => api(`/events?limit=${limit}`)
import { fakeGetRecentEvents } from '@/api/fakeBackend'

// 최근 이벤트 목록 (최신 순)
export const getRecentEvents = (limit) => fakeGetRecentEvents(limit)
