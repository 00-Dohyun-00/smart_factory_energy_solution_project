// 전력·배터리 상태 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getLatestBatteryStatus = () => api('/battery-statuses/latest')
import { fakeGetLatestStatus, fakeGetTodayStatuses } from '@/api/fakeBackend'

// 실시간 관제용 최신 상태 1건
export const getLatestBatteryStatus = () => fakeGetLatestStatus()

// 오늘 그래프용 상태 목록
export const getTodayBatteryStatuses = () => fakeGetTodayStatuses()
