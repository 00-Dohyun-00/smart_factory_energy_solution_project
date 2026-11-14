// ESS 추천(충전/방전/대기) API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) api(`/battery-decisions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) })
import { fakeGetLatestDecision, fakeUpdateDecision } from '@/api/fakeBackend'

// 최신 추천 1건
export const getLatestBatteryDecision = () => fakeGetLatestDecision()

// 추천 적용 / 무시 (status: 'APPLIED' 또는 'IGNORED')
export const updateBatteryDecision = (id, status) => fakeUpdateDecision(id, status)
