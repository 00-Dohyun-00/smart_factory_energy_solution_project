// ESS 추천(충전/방전/대기) API (명세 v0.2: recommendations)
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getLatestBatteryDecision = () => api('/recommendations/latest')
//           export const getBatteryDecisions = (from, to) => api(`/recommendations?from=${from}&to=${to}`)
//           export const updateBatteryDecision = (id, status) =>
//             api(`/recommendations/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) })
import { fakeGetDecisions, fakeGetLatestDecision, fakeUpdateDecision } from '@/api/fakeBackend'

// 지금 추천 1건. 추천이 없으면 null (명세 2번: GET /api/recommendations/latest, 없으면 204)
export const getLatestBatteryDecision = () => fakeGetLatestDecision()

// 기간 안의 추천 이력, 최신 순 (명세 8번: GET /api/recommendations?from=&to=, 최대 62일)
export const getBatteryDecisions = (from, to) => fakeGetDecisions(from, to)

// 추천 적용 / 넘기기 (명세 3번: PATCH /api/recommendations/{id}, status: 'APPLIED' 또는 'IGNORED')
export const updateBatteryDecision = (id, status) => fakeUpdateDecision(id, status)
