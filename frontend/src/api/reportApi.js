// 성적표 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getReport = (month) => api(`/reports?month=${month}`)
import { fakeGetReport } from '@/api/fakeBackend'

// 한 달 성적표 (month 예: '2026-11')
export const getReport = (month) => fakeGetReport(month)
