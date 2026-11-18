// 성적표 API — 실제 백엔드에 연결됨 (2026-11-18)
import { api } from '@/api/client'

// 한 달 성적표 (명세 9번: GET /api/reports?month=, month 예: '2026-11'. 기록 없는 달은 hasData: false)
export const getReport = (month) => api(`/reports?month=${month}`)
