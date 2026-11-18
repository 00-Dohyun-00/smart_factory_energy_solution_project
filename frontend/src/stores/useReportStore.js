import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getReport } from '@/api/reportApi'

// 성적표 화면이 쓰는 데이터
export const useReportStore = defineStore('report', () => {
  const report = ref(null) // 한 달 성적표 (아직 못 받았으면 null)
  const loading = ref(false)
  const errorMessage = ref('')
  // 작년 같은 달의 전기요금 합계(원). 전기요금 비교용이며, 기록이 없거나 못 받았으면 null
  const lastYearTotal = ref(null)

  // 요청 번호. 달을 빨리 넘기면 요청이 여러 개 동시에 나가는데,
  // 늦게 온 이전 달의 응답이 새 결과를 덮어쓰지 않게 가장 최근 요청의 응답만 반영함
  let latestReportRequest = 0
  let latestLastYearRequest = 0 // 작년 같은 달 요청도 따로 번호를 매김

  // 한 달 성적표 조회 (month 예: '2026-11')
  async function fetchReport(month) {
    const requestId = ++latestReportRequest
    loading.value = true
    errorMessage.value = ''
    // 작년 같은 달도 같은 API로 함께 받음 (주소·필드 그대로, 달만 1년 전)
    fetchLastYearTotal(month)
    try {
      const result = await getReport(month)
      // 그 사이 다른 달을 다시 요청했으면 이 응답은 버림
      if (requestId !== latestReportRequest) return
      report.value = result
    } catch (error) {
      if (requestId !== latestReportRequest) return
      errorMessage.value = error.message
    } finally {
      // "불러오는 중" 표시도 가장 최근 요청이 끝났을 때만 끔
      if (requestId === latestReportRequest) {
        loading.value = false
      }
    }
  }

  // 작년 같은 달 요금. 실패해도 성적표는 그대로 보여야 하므로 오류를 따로 띄우지 않고 비워 둠
  async function fetchLastYearTotal(month) {
    const requestId = ++latestLastYearRequest
    lastYearTotal.value = null
    const [year, monthNumber] = month.split('-')
    try {
      const lastYear = await getReport(`${Number(year) - 1}-${monthNumber}`)
      if (requestId !== latestLastYearRequest) return
      lastYearTotal.value = lastYear.hasData ? lastYear.bill.total : null
    } catch {
      if (requestId !== latestLastYearRequest) return
      lastYearTotal.value = null
    }
  }

  return { report, loading, errorMessage, lastYearTotal, fetchReport }
})
