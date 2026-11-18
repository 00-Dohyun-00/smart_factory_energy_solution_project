import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getDemandIntervals, getPeakAnalysis, getTrendAnalysis } from '@/api/analysisApi'

// 분석 화면이 쓰는 데이터
export const useAnalysisStore = defineStore('analysis', () => {
  const trend = ref(null) // 추이 분석 결과 (아직 못 받았으면 null)
  const loading = ref(false)
  const errorMessage = ref('')

  // 추이 분석 조회 (from, to 예: '2026-11-10' / interval: 'QUARTER' | 'HOUR' | 'DAY')
  // showLoading = false 로 부르면 "불러오는 중" 표시 없이 조용히 새로 받아 옴 (자동 갱신용)
  async function fetchTrend(from, to, interval, showLoading = true) {
    if (showLoading) {
      loading.value = true
    }
    try {
      trend.value = await getTrendAnalysis(from, to, interval)
      errorMessage.value = ''
    } catch (error) {
      // 실패해도 이미 받아 둔 결과는 지우지 않음
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  // ---- 피크 분석 ----
  const peak = ref(null) // 피크 분석 결과 (아직 못 받았으면 null)
  const peakLoading = ref(false)
  const peakError = ref('')

  // 피크 분석 조회 (from, to 예: '2026-11-01')
  async function fetchPeak(from, to, showLoading = true) {
    if (showLoading) {
      peakLoading.value = true
    }
    try {
      peak.value = await getPeakAnalysis(from, to)
      peakError.value = ''
    } catch (error) {
      // 실패해도 이미 받아 둔 결과는 지우지 않음
      peakError.value = error.message
    } finally {
      peakLoading.value = false
    }
  }

  // ---- 피크가 높았던 구간 순위 (명세 16번: demand-intervals) ----
  const peakWindows = ref([]) // [{ windowStart, windowEnd, peakKw, noEssPeakKw, reducedKw, action, status }]
  const peakWindowsError = ref('')

  async function fetchPeakWindows(from, to, limit) {
    try {
      peakWindows.value = await getDemandIntervals(from, to, limit)
      peakWindowsError.value = ''
    } catch (error) {
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
