import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getPeakAnalysis, getTrendAnalysis } from '@/api/analysisApi'

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
      // 1시간 간격 결과는 사용 패턴 카드가 쓰는 데이터와 똑같으므로 같이 넣어 둠
      // (예전에는 같은 요청을 두 번 보냈음 — 이슈 #25). 기간이 같으면 다시 넣지 않음:
      // 자동 갱신 때마다 바꾸면 카드에서 좌우로 옮겨 보던 위치가 처음으로 돌아감
      if (interval === 'HOUR' && (pattern.value?.from !== from || pattern.value?.to !== to)) {
        latestPatternRequest++ // 이전 기간의 패턴 요청이 늦게 와도 덮어쓰지 않게
        pattern.value = result
        patternError.value = ''
        patternLoading.value = false
      }
    } catch (error) {
      if (requestId !== latestTrendRequest) return
      // 실패해도 이미 받아 둔 결과는 지우지 않음
      errorMessage.value = error.message
      if (interval === 'HOUR' && showLoading) patternError.value = error.message
    } finally {
      // "불러오는 중" 표시도 가장 최근 요청이 끝났을 때만 끔
      if (requestId === latestTrendRequest) {
        loading.value = false
      }
    }
  }

  // ---- 시간대별 사용 패턴용 데이터 (추이 분석 화면 오른쪽 카드) ----
  // 위 그래프의 간격(15분/1시간/1일)과 상관없이 항상 1시간 간격으로 받아 두고,
  // 화면이 1시간 · 4시간 · 1일로 묶어서 그림
  const pattern = ref(null)
  const patternLoading = ref(false)
  const patternError = ref('')
  let latestPatternRequest = 0 // 가장 최근 요청의 응답만 반영하기 위한 번호

  async function fetchPattern(from, to) {
    const requestId = ++latestPatternRequest
    patternLoading.value = true
    try {
      const result = await getTrendAnalysis(from, to, 'HOUR')
      if (requestId !== latestPatternRequest) return
      pattern.value = result
      patternError.value = ''
    } catch (error) {
      if (requestId !== latestPatternRequest) return
      patternError.value = error.message
    } finally {
      if (requestId === latestPatternRequest) {
        patternLoading.value = false
      }
    }
  }

  // ---- 피크 분석 ----
  const peak = ref(null) // 피크 분석 결과 (아직 못 받았으면 null)
  // 피크가 높았던 구간 순위. 피크 분석 응답의 topWindows를 그대로 꺼내 둔 것
  // [{ windowStart, windowEnd, peakKw, status, note }] (추천이 없었던 구간은 status가 null)
  const peakWindows = ref([])
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
      // 기록이 없는 기간이면 백엔드가 topWindows를 null로 줌 → 빈 목록으로 바꿔 "기록 없음"으로 보이게 함
      peakWindows.value = result.topWindows ?? []
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

  // ---- 목표 피크를 넘은 15분 구간 (추이 그래프의 점 ●) ----
  // 피크 분석 API의 exceededWindows를 씀. 추이 그래프 선은 기간이 길면 1시간 평균이라 짧은 피크가 묻히므로
  // 15분 기준 초과 구간을 따로 받아 점으로 찍음. 피크 분석 화면의 peak 값과 섞이지 않게 따로 둠
  // [{ windowStart, windowEnd, peakKw }]
  const exceededWindows = ref([])
  let latestExceededRequest = 0

  async function fetchExceeded(from, to) {
    const requestId = ++latestExceededRequest
    try {
      const result = await getPeakAnalysis(from, to)
      if (requestId !== latestExceededRequest) return
      exceededWindows.value = result.exceededWindows ?? []
    } catch {
      // 점은 보조 표시라 실패하면 점만 안 보이게 함 (그래프 오류 안내는 추이 조회 쪽에서 함)
      if (requestId !== latestExceededRequest) return
      exceededWindows.value = []
    }
  }

  return {
    exceededWindows,
    fetchExceeded,
    pattern,
    patternLoading,
    patternError,
    fetchPattern,
    peakWindows,
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
