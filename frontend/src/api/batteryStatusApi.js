// 공장 전력·배터리 상태 API (명세 v0.2: site-powers, zones) — 실제 백엔드에 연결됨 (2026-11-19)
import { api } from '@/api/client'

// 지금 상태 1건 (명세 1번: GET /api/site-powers/latest)
// 전력·배터리·15분 피크·요금 시간대·설비별 전력이 함께 옴. recordedAt이 화면의 "지금" 기준 시각
export const getLatestBatteryStatus = () => api('/site-powers/latest')

// 오늘 0시부터 지금까지 15분 간격 목록 (명세 5번: GET /api/site-powers/today)
export const getTodayBatteryStatuses = () => api('/site-powers/today')

// 구역 한 곳의 최근 1시간 전력과 오늘 요약 (명세 7번: GET /api/zones/{zoneCode}, zoneCode 예: 'Z2', 유틸리티는 'Z6')
export const getZoneRecent = (zoneCode) => api(`/zones/${zoneCode}`)
