import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getBatteryDecisions } from '@/api/batteryDecisionApi'

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

  return { decisions, loading, errorMessage, fetchDecisions }
})
