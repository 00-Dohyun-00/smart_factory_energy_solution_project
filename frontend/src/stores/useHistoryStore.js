import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getPeakAnalysis, getTrendAnalysis } from '@/api/analysisApi'
import { getBatteryDecisions } from '@/api/batteryDecisionApi'
import { getEquipmentPowers } from '@/api/equipmentPowerApi'
import { getEventsInPeriod } from '@/api/eventApi'

// 운영 이력 화면이 쓰는 데이터
export const useHistoryStore = defineStore('history', () => {
  const decisions = ref([]) // 추천 이력 목록 (최신 순)
  const loading = ref(false)
  const errorMessage = ref('')

  // 요청 번호. 기간을 빨리 바꾸면 요청이 여러 개 동시에 나가는데,
  // 늦게 온 이전 응답이 새 결과를 덮어쓰지 않게 가장 최근 요청의 응답만 반영함
  let latestDecisionsRequest = 0

  // 기간 안의 이력 조회 (from, to 예: '2026-11-14')
  // showLoading = false 로 부르면 "불러오는 중" 표시 없이 조용히 새로 받아 옴 (자동 갱신용)
  async function fetchDecisions(from, to, showLoading = true) {
    const requestId = ++latestDecisionsRequest
    if (showLoading) {
      loading.value = true
    }
    try {
      const result = await getBatteryDecisions(from, to)
      // 그 사이 새 요청이 나갔으면 이 응답은 버림
      if (requestId !== latestDecisionsRequest) return
      decisions.value = result
      errorMessage.value = ''
    } catch (error) {
      if (requestId !== latestDecisionsRequest) return
      // 실패해도 이미 받아 둔 목록은 지우지 않음
      errorMessage.value = error.message
    } finally {
      // "불러오는 중" 표시도 가장 최근 요청이 끝났을 때만 끔
      if (requestId === latestDecisionsRequest) {
        loading.value = false
      }
    }
  }

  // ---- 전력 이력 (운영 로그 > 전력 이력 탭) ----
  const powerEvents = ref([]) // 기간 안의 이벤트 (단계 변화, 추천, 적용·넘김, 구간 종료 ...) 오래된 순
  const powerExceeded = ref([]) // 기간 안에서 목표 피크를 넘은 15분 구간
  const powerTargetKw = ref(null) // 목표 피크 kW
  const powerPoints = ref([]) // 기간 안의 15분 점 (공장 소비, 한전 구매, 태양광, ESS, 잔량, 온도)
  const powerWeather = ref([]) // 날짜별 날씨 [{ date, text }]
  const powerDecisions = ref([]) // 기간 안의 추천 (추천 · 처리 · 결과 칸)
  const powerHourly = ref([]) // 시각별 요금 시간대 [{ hour, avgGridKw, period }]
  const powerLoading = ref(false)
  const powerError = ref('')
  let latestPowerRequest = 0

  // fromAt, toAt 예: '2026-11-19T13:00:00' (fromAt 이상 ~ toAt 미만)
  async function fetchPowerHistory(fromAt, toAt, showLoading = true) {
    const requestId = ++latestPowerRequest
    if (showLoading) powerLoading.value = true
    try {
      // 이벤트와 피크 분석(15분 초과 구간)을 같이 받음. 피크 분석은 날짜 단위라 앞뒤 날짜로 받고 시각으로 거름
      // toAt이 다음 날 0시면 그날은 빼야 함 (예: 하루 전체 = 11-19 00:00 ~ 11-20 00:00)
      const lastDay = new Date(new Date(toAt).getTime() - 1000)
      const pad = (n) => String(n).padStart(2, '0')
      const toDate = `${lastDay.getFullYear()}-${pad(lastDay.getMonth() + 1)}-${pad(lastDay.getDate())}`
      // 추이 분석(15분 간격) · 추천 이력도 같이 받아 표의 칸에 씀
      const [events, peak, trend, decisions] = await Promise.all([
        getEventsInPeriod(fromAt, toAt),
        getPeakAnalysis(fromAt.slice(0, 10), toDate),
        getTrendAnalysis(fromAt.slice(0, 10), toDate, 'QUARTER'),
        getBatteryDecisions(fromAt.slice(0, 10), toDate),
      ])
      if (requestId !== latestPowerRequest) return
      powerEvents.value = events
      powerExceeded.value = (peak.exceededWindows ?? []).filter(
        (item) => item.windowStart >= fromAt && item.windowStart < toAt,
      )
      powerTargetKw.value = peak.targetPeakKw
      powerPoints.value = (trend.points ?? []).filter(
        (item) => item.recordedAt >= fromAt && item.recordedAt < toAt,
      )
      powerWeather.value = trend.weather ?? []
      powerHourly.value = trend.hourly ?? []
      powerDecisions.value = decisions.filter(
        (item) => item.windowStart >= fromAt && item.windowStart < toAt,
      )
      powerError.value = ''
    } catch (error) {
      if (requestId !== latestPowerRequest) return
      powerError.value = error.message
    } finally {
      if (requestId === latestPowerRequest) powerLoading.value = false
    }
  }

  // 15분 구간별 · 설비별 전력 (조회 기간 전체)
  const equipmentPowers = ref([])
  const equipmentError = ref('')
  let latestEquipmentRequest = 0

  async function fetchEquipmentPowers(fromAt, toAt) {
    const requestId = ++latestEquipmentRequest
    try {
      const result = await getEquipmentPowers(fromAt, toAt)
      if (requestId !== latestEquipmentRequest) return
      equipmentPowers.value = result
      equipmentError.value = ''
    } catch (error) {
      if (requestId !== latestEquipmentRequest) return
      equipmentError.value = error.message
    }
  }

  return {
    decisions,
    powerPoints,
    powerWeather,
    powerHourly,
    powerDecisions,
    equipmentPowers,
    equipmentError,
    fetchEquipmentPowers,
    loading,
    errorMessage,
    fetchDecisions,
    powerEvents,
    powerExceeded,
    powerTargetKw,
    powerLoading,
    powerError,
    fetchPowerHistory,
  }
})
