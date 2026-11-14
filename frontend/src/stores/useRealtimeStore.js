import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLatestBatteryStatus, getTodayBatteryStatuses } from '@/api/batteryStatusApi'
import { getLatestBatteryDecision, updateBatteryDecision } from '@/api/batteryDecisionApi'

// 실시간 관제 화면과 헤더가 함께 쓰는 데이터
export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref(null) // 최신 상태 1건 (아직 못 받았으면 null)
  const decision = ref(null) // 최신 추천 1건
  const todayStatuses = ref([]) // 오늘 그래프용 목록
  const todayLoading = ref(true) // 그래프를 불러오는 중 (처음에는 아직 받기 전이라 true)
  const todayError = ref('') // 그래프를 불러오지 못했을 때의 메시지

  const connected = ref(true) // 서버 연결 표시등
  const failCount = ref(0)
  const errorMessage = ref('')

  // 최신 상태와 추천 조회 (폴링으로 반복 호출)
  async function fetchLatest() {
    try {
      const [latestStatus, latestDecision] = await Promise.all([
        getLatestBatteryStatus(),
        getLatestBatteryDecision(),
      ])
      status.value = latestStatus
      decision.value = latestDecision
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

  // 추천 적용 / 무시
  async function changeDecisionStatus(newStatus) {
    decision.value = await updateBatteryDecision(decision.value.id, newStatus)
    await fetchLatest()
  }

  return {
    status,
    decision,
    todayStatuses,
    todayLoading,
    todayError,
    connected,
    errorMessage,
    fetchLatest,
    fetchToday,
    changeDecisionStatus,
  }
})
