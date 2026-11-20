// 설비별 전력 API (전력 이력 화면용)
import { api } from '@/api/client'

// 15분 구간별 · 부하 설비별 평균 kW (GET /api/equipment-powers?from=&to=, 최대 24시간)
// from, to 예: '2026-11-19T14:15:00' (from 이상 ~ to 미만)
// 한 줄 모양: { windowStart, equipmentId, name, zoneCode, zoneName, avgKw, runningPercent } (구간 시간 순, 같은 구간은 많이 쓴 순)
export const getEquipmentPowers = (from, to) => api(`/equipment-powers?from=${from}&to=${to}`)
