// 분석 API — 실제 백엔드에 연결됨 (2026-11-18)
import { api } from '@/api/client'

// 기간별 추이 분석 (명세 10번: GET /api/analyses/trend, interval: 'QUARTER' | 'HOUR' | 'DAY')
export const getTrendAnalysis = (from, to, interval) =>
  api(`/analyses/trend?from=${from}&to=${to}&interval=${interval}`)

// 기간별 피크 분석: 요약·일별 최대 피크·히트맵·피크가 높았던 구간(topWindows)
// 처음 명세에서는 "피크가 높았던 구간"을 따로(16번 demand-intervals) 받기로 했으나,
// 백엔드가 이 응답 안의 topWindows로 함께 주기로 만들어서 요청 한 번으로 받음
export const getPeakAnalysis = (from, to) => api(`/analyses/peak?from=${from}&to=${to}`)
