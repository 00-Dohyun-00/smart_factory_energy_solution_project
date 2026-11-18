// 분석 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getTrendAnalysis = (from, to, interval) =>
//             api(`/analyses/trend?from=${from}&to=${to}&interval=${interval}`)
//           export const getPeakAnalysis = (from, to) => api(`/analyses/peak?from=${from}&to=${to}`)
//           export const getDemandIntervals = (from, to, limit) =>
//             api(`/demand-intervals?from=${from}&to=${to}&limit=${limit}`)
import {
  fakeGetDemandIntervals,
  fakeGetPeakAnalysis,
  fakeGetTrendAnalysis,
} from '@/api/fakeBackend'

// 기간별 추이 분석 (명세 10번: GET /api/analyses/trend, interval: 'QUARTER' | 'HOUR' | 'DAY')
export const getTrendAnalysis = (from, to, interval) => fakeGetTrendAnalysis(from, to, interval)

// 기간별 피크 분석: 요약·일별 최대 피크·히트맵
// 명세 v0.2에 아직 없음 → 백엔드에 추가 요청 (docs/api-spec-diff.md 2-9)
export const getPeakAnalysis = (from, to) => fakeGetPeakAnalysis(from, to)

// 피크가 높았던 15분 구간 순위 (명세 16번: GET /api/demand-intervals?from=&to=&limit=)
export const getDemandIntervals = (from, to, limit) => fakeGetDemandIntervals(from, to, limit)
