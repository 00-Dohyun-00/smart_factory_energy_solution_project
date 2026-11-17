import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getDemandIntervals, getPeakAnalysis, getTrendAnalysis } from '@/api/analysisApi'

// 분석 화면이 쓰는 데이터
export const useAnalysisStore = defineStore('analysis', () => {
  const trend = ref(null) // 추이 분석 결과 (아직 못 받았으면 null)
  const loading = ref(false)
  const errorMessage = ref('')

  // 요청 번호. 기간을 빨리 바꾸면 요청이 여러 개 동시에 나가는데,
  // 늦게 온 이전 응답이 새 결과를 덮어쓰지 않게 가장 최근 요청의 응답만 반영함
  let latestTrendRequest = 0

  // 추이 분석 조회 (from, to 예: '2026-11-10' / interval: 'QUARTER' | 'HOUR' | 'DAY')
  // showLoading = false 로 부르면 "불러오는 중" 표시 없이 조용히 새로 받아 옴 (자동 갱신용)
  async function fetchTrend(from, to, interval, showLoading = true) {
    const requestId = ++latestTrendRequest
    if (showLoading) {
      loading.value = true
    }
    try {
      const result = await getTrendAnalysis(from, to, interval)
      // 그 사이 새 요청이 나갔으면 이 응답은 버림
      if (requestId !== latestTrendRequest) return
      trend.value = result
      errorMessage.value = ''
    } catch (error) {
      if (requestId !== latestTrendRequest) return
      // 실패해도 이미 받아 둔 결과는 지우지 않음
      errorMessage.value = error.message
    } finally {
      // "불러오는 중" 표시도 가장 최근 요청이 끝났을 때만 끔
      if (requestId === latestTrendRequest) {
        loading.value = false
      }
    }
  }

  // ---- 피크 분석 ----
  const peak = ref(null) // 피크 분석 결과 (아직 못 받았으면 null)
  const peakLoading = ref(false)
  const peakError = ref('')
  let latestPeakRequest = 0 // 가장 최근 요청의 응답만 반영하기 위한 번호 (위 latestTrendRequest 와 같은 이유)

  // 피크 분석 조회 (from, to 예: '2026-11-01')
  async function fetchPeak(from, to, showLoading = true) {
    const requestId = ++latestPeakRequest
    if (showLoading) {
      peakLoading.value = true
    }
    try {
      const result = await getPeakAnalysis(from, to)
      if (requestId !== latestPeakRequest) return
      peak.value = result
      peakError.value = ''
    } catch (error) {
      if (requestId !== latestPeakRequest) return
      // 실패해도 이미 받아 둔 결과는 지우지 않음
      peakError.value = error.message
    } finally {
      if (requestId === latestPeakRequest) {
        peakLoading.value = false
      }
    }
  }

  // ---- 피크가 높았던 구간 순위 (명세 16번: demand-intervals) ----
  const peakWindows = ref([]) // [{ windowStart, windowEnd, peakKw, noEssPeakKw, reducedKw, action, status }]
  const peakWindowsError = ref('')
  let latestPeakWindowsRequest = 0 // 가장 최근 요청의 응답만 반영하기 위한 번호

  async function fetchPeakWindows(from, to, limit) {
    const requestId = ++latestPeakWindowsRequest
    try {
      const result = await getDemandIntervals(from, to, limit)
      if (requestId !== latestPeakWindowsRequest) return
      peakWindows.value = result
      peakWindowsError.value = ''
    } catch (error) {
      if (requestId !== latestPeakWindowsRequest) return
      // 실패해도 이미 받아 둔 순위는 지우지 않음
      peakWindowsError.value = error.message
    }
  }

  return {
    peakWindows,
    peakWindowsError,
    fetchPeakWindows,
    trend,
    loading,
    errorMessage,
    fetchTrend,
    peak,
    peakLoading,
    peakError,
    fetchPeak,
  }
})
