// 이벤트(단계 변화, 추천, 적용·넘김, 구간 종료 등) API — 실제 백엔드에 연결됨 (2026-11-19)
import { api } from '@/api/client'

// 최근 이벤트 목록, 최신 순 (명세 4번: GET /api/events?limit=. 명세 기본값은 6)
// 한 줄 모양: { id, occurredAt, level, message, eventType, recommendationId }
// eventType: STAGE_CHANGE(단계 변화) / RECOMMEND(추천) / HANDLE(적용·넘김) / INTERVAL_END(구간 종료) / COMM(통신) / SETTING(설정 변경)
export const getRecentEvents = (limit) => api(`/events?limit=${limit}`)
