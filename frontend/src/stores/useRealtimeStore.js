import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getLatestBatteryStatus,
  getRecentBatteryStatuses,
  getTodayBatteryStatuses,
  getZoneRecent,
} from '@/api/batteryStatusApi'
import { getLatestBatteryDecision, updateBatteryDecision } from '@/api/batteryDecisionApi'
import { getRecentEvents } from '@/api/eventApi'

// 모니터링 화면들과 왼쪽 메뉴가 함께 쓰는 실시간 데이터
export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref(null) // 최신 상태 1건 (아직 못 받았으면 null)
  const decision = ref(null) // 최신 추천 1건
  const events = ref([]) // 최근 이벤트 (최신 순)

  const todayStatuses = ref([]) // 오늘의 15분 구간별 목록 (금일 피크 차트용)
  const todayLoading = ref(true) // 처음에는 아직 받기 전이라 true
  const todayError = ref('')

  const recentStatuses = ref([]) // 최근 1시간 목록 (실시간 전력 추세용)
  const zoneDetail = ref(null) // 설비 모니터링 화면에서 고른 구역의 상세
  const zoneError = ref('') // 구역 상세를 받지 못했을 때의 메시지

  const connected = ref(true) // 서버 연결 표시등
  const failCount = ref(0)
  const errorMessage = ref('')

  // 최신 상태·추천·이벤트 조회 (폴링으로 반복 호출)
  async function fetchLatest() {
    try {
      const [latestStatus, latestDecision, latestEvents] = await Promise.all([
        getLatestBatteryStatus(),
        getLatestBatteryDecision(),
        getRecentEvents(10),
      ])
      status.value = latestStatus
      decision.value = latestDecision
      events.value = latestEvents
      failCount.value = 0
      connected.value = true
      errorMessage.value = ''
    } catch (error) {
      // 실패해도 마지막으로 받은 값은 지우지 않음 (화면에 계속 보여 주기 위해)
      failCount.value += 1
      errorMessage.value = error.message
      if (failCount.value >= 3) {
        connected.value = false
      }
    }
  }

  async function fetchToday() {
    todayLoading.value = true
    todayError.value = ''
    try {
      todayStatuses.value = await getTodayBatteryStatuses()
    } catch (error) {
      todayError.value = error.message
    } finally {
      todayLoading.value = false
    }
  }

  // 실패하면 이전 그래프를 그대로 둠 (다음 갱신 때 다시 시도)
  async function fetchRecent() {
    try {
      recentStatuses.value = await getRecentBatteryStatuses()
    } catch {
      // 연결 상태는 fetchLatest가 표시하므로 여기서는 따로 알리지 않음
    }
  }

  // 실패하면 이전 값은 그대로 두고 메시지만 남김 (화면에서 "다시 시도"를 보여 주기 위해)
  async function fetchZoneDetail(zoneId) {
    zoneError.value = ''
    try {
      zoneDetail.value = await getZoneRecent(zoneId)
    } catch (error) {
      zoneError.value = error.message
    }
  }

  // 추천 적용 / 넘기기
  async function changeDecisionStatus(newStatus) {
    decision.value = await updateBatteryDecision(decision.value.id, newStatus)
    await fetchLatest()
  }

  return {
    status,
    decision,
    events,
    todayStatuses,
    todayLoading,
    todayError,
    recentStatuses,
    zoneDetail,
    zoneError,
    connected,
    errorMessage,
    fetchLatest,
    fetchToday,
    fetchRecent,
    fetchZoneDetail,
    changeDecisionStatus,
  }
})
