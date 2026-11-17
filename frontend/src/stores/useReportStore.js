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

  // 한 달 성적표 조회 (month 예: '2026-11')
  async function fetchReport(month) {
    loading.value = true
    errorMessage.value = ''
    // 작년 같은 달도 같은 API로 함께 받음 (주소·필드 그대로, 달만 1년 전)
    fetchLastYearTotal(month)
    try {
      report.value = await getReport(month)
    } catch (error) {
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  // 작년 같은 달 요금. 실패해도 성적표는 그대로 보여야 하므로 오류를 따로 띄우지 않고 비워 둠
  async function fetchLastYearTotal(month) {
    lastYearTotal.value = null
    const [year, monthNumber] = month.split('-')
    try {
      const lastYear = await getReport(`${Number(year) - 1}-${monthNumber}`)
      lastYearTotal.value = lastYear.hasData ? lastYear.bill.total : null
    } catch {
      lastYearTotal.value = null
    }
  }

  return { report, loading, errorMessage, lastYearTotal, fetchReport }
})
