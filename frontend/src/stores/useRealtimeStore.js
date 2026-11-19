import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getLatestBatteryStatus,
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

  const zoneDetail = ref(null) // 설비 모니터링 화면에서 고른 구역의 상세
  const zoneError = ref('') // 구역 상세를 받지 못했을 때의 메시지

  const connected = ref(true) // 서버 연결 표시등
  const failCount = ref(0)
  const errorMessage = ref('')

  // 요청 번호와 "요청 중" 표시.
  // 화면 갱신 주기가 짧으면(예: 1초) 앞 요청이 끝나기 전에 다음 요청이 나가 서버에 요청이 쌓이므로,
  // 앞 요청이 끝날 때까지 다음 폴링은 건너뜀. 늦게 온 옛 응답이 새 값을 덮어쓰지 않게 번호도 확인함
  let latestRequest = 0
  let requesting = false

  // 최신 상태·추천·이벤트 조회 (폴링으로 반복 호출)
  // force = true: 요청 중이어도 새로 받음 (적용/넘기기 직후처럼 바로 최신 값이 필요할 때)
  async function fetchLatest(force = false) {
    if (requesting && !force) return
    const requestId = ++latestRequest
    requesting = true
    try {
      const [latestStatus, latestDecision, latestEvents] = await Promise.all([
        getLatestBatteryStatus(),
        getLatestBatteryDecision(),
        getRecentEvents(30), // 이벤트 로그 필터용으로 30건 (화면에 보이는 건 칸 높이만큼)
      ])
      if (requestId !== latestRequest) return // 그 사이 새 요청이 나갔으면 이 응답은 버림
      status.value = latestStatus
      decision.value = latestDecision
      events.value = latestEvents
      failCount.value = 0
      connected.value = true
      errorMessage.value = ''
    } catch (error) {
      if (requestId !== latestRequest) return
      // 실패해도 마지막으로 받은 값은 지우지 않음 (화면에 계속 보여 주기 위해)
      failCount.value += 1
      errorMessage.value = error.message
      if (failCount.value >= 3) {
        connected.value = false
      }
    } finally {
      if (requestId === latestRequest) requesting = false
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

  // 실패하면 이전 값은 그대로 두고 메시지만 남김 (화면에서 "다시 시도"를 보여 주기 위해)
  // zoneCode 예: 'Z2' (유틸리티는 백엔드 코드 그대로 'Z6')
  async function fetchZoneDetail(zoneCode) {
    zoneError.value = ''
    try {
      zoneDetail.value = await getZoneRecent(zoneCode)
    } catch (error) {
      zoneError.value = error.message
    }
  }

  // 추천 적용 / 넘기기 (실제 백엔드: 공용 DB에 기록되고, 적용하면 시뮬레이터에 ESS 명령이 내려감)
  async function changeDecisionStatus(newStatus) {
    decision.value = await updateBatteryDecision(decision.value.id, newStatus)
    await fetchLatest(true)
  }

  return {
    status,
    decision,
    events,
    todayStatuses,
    todayLoading,
    todayError,
    zoneDetail,
    zoneError,
    connected,
    errorMessage,
    fetchLatest,
    fetchToday,
    fetchZoneDetail,
    changeDecisionStatus,
  }
})
