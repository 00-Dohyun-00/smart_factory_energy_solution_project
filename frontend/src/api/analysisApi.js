// 분석 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) api(`/analyses/trend?from=${from}&to=${to}&interval=${interval}`)
//           api(`/analyses/peak?from=${from}&to=${to}`)
import { fakeGetPeakAnalysis, fakeGetTrendAnalysis } from '@/api/fakeBackend'

// 기간별 추이 분석 (from, to 예: '2026-11-10' / interval: 'QUARTER' | 'HOUR' | 'DAY')
export const getTrendAnalysis = (from, to, interval) => fakeGetTrendAnalysis(from, to, interval)

// 기간별 피크 분석 (from, to 예: '2026-11-01')
export const getPeakAnalysis = (from, to) => fakeGetPeakAnalysis(from, to)
