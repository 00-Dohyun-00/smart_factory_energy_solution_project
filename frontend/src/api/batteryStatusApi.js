// 공장 전력·배터리 상태 API (명세 v0.2: site-powers, zones)
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getLatestBatteryStatus = () => api('/site-powers/latest')
//           export const getTodayBatteryStatuses = () => api('/site-powers/today')
//           export const getZoneRecent = (zoneName) => api(`/zones/${zoneName}`)
import { fakeGetLatestStatus, fakeGetTodayStatuses, fakeGetZoneRecent } from '@/api/fakeBackend'

// 지금 상태 1건 (명세 1번: GET /api/site-powers/latest)
export const getLatestBatteryStatus = () => fakeGetLatestStatus()

// 오늘 0시부터 지금까지 15분 간격 목록 (명세 5번: GET /api/site-powers/today)
export const getTodayBatteryStatuses = () => fakeGetTodayStatuses()

// 구역 한 곳의 최근 1시간 전력과 오늘 요약 (명세 7번: GET /api/zones/{zoneName}, zoneName 예: 'Z2')
export const getZoneRecent = (zoneName) => fakeGetZoneRecent(zoneName)
