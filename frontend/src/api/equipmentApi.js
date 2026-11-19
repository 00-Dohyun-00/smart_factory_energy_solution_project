// 설비 목록 API — 실제 백엔드
import { api } from '@/api/client'

// 설비 목록 (GET /api/equipment)
// 한 줄 모양: { equipmentId, zoneCode, name, equipmentRole, ratedKw }
// 지금 상태(site-powers/latest)의 설비 목록에는 구역 코드가 없어서, 설비가 어느 구역인지 알려고 씀
export const getEquipments = () => api('/equipment')
