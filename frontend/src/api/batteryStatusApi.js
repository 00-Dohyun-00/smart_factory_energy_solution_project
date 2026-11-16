// 전력·배터리 상태 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getLatestBatteryStatus = () => api('/battery-statuses/latest')
import {
  fakeGetLatestStatus,
  fakeGetRecentStatuses,
  fakeGetTodayStatuses,
  fakeGetZoneRecent,
} from '@/api/fakeBackend'

// 모니터링용 최신 상태 1건
export const getLatestBatteryStatus = () => fakeGetLatestStatus()

// 오늘의 15분 구간별 상태 목록 (금일 피크 차트용)
export const getTodayBatteryStatuses = () => fakeGetTodayStatuses()

// 최근 1시간, 1분 간격 상태 목록 (실시간 전력 추세용)
export const getRecentBatteryStatuses = () => fakeGetRecentStatuses()

// 구역 한 곳의 최근 1시간 전력과 오늘 요약 (설비 모니터링 화면의 상세용, zoneId 예: 'Z2')
export const getZoneRecent = (zoneId) => fakeGetZoneRecent(zoneId)
