// ESS 추천(충전/방전/대기) API (명세 v0.2: recommendations) — 실제 백엔드에 연결됨
// (추천 이력 2026-11-17, 지금 추천·적용/넘기기 2026-11-19)
import { api } from '@/api/client'

// 지금 추천 1건 (명세 2번: GET /api/recommendations/latest)
// 추천이 없으면 백엔드가 204(본문 없음)를 주고, api()가 null을 돌려줌
export const getLatestBatteryDecision = () => api('/recommendations/latest')

// 기간 안의 추천 이력, 최신 순 (명세 8번: GET /api/recommendations?from=&to=, 최대 62일)
export const getBatteryDecisions = (from, to) => api(`/recommendations?from=${from}&to=${to}`)

// 추천 적용 / 넘기기 (명세 3번: PATCH /api/recommendations/{id}, status: 'APPLIED' 또는 'IGNORED')
// 공용 DB에 바로 기록되고, 적용하면 시뮬레이터에 ESS 명령이 내려감. 바뀐 추천 1건이 돌아옴
export const updateBatteryDecision = (id, status) =>
  api(`/recommendations/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) })
