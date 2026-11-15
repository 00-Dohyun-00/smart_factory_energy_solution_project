import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getBatteryDecisions } from '@/api/batteryDecisionApi'

// 운영 이력 화면이 쓰는 데이터
export const useHistoryStore = defineStore('history', () => {
  const decisions = ref([]) // 추천 이력 목록 (최신 순)
  const loading = ref(false)
  const errorMessage = ref('')

  // 기간 안의 이력 조회 (from, to 예: '2026-11-14')
  // showLoading = false 로 부르면 "불러오는 중" 표시 없이 조용히 새로 받아 옴 (자동 갱신용)
  async function fetchDecisions(from, to, showLoading = true) {
    if (showLoading) {
      loading.value = true
    }
    try {
      decisions.value = await getBatteryDecisions(from, to)
      errorMessage.value = ''
    } catch (error) {
      // 실패해도 이미 받아 둔 목록은 지우지 않음
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  return { decisions, loading, errorMessage, fetchDecisions }
})
