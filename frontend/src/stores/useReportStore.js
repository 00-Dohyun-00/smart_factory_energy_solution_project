import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getReport } from '@/api/reportApi'

// 성적표 화면이 쓰는 데이터
export const useReportStore = defineStore('report', () => {
  const report = ref(null) // 한 달 성적표 (아직 못 받았으면 null)
  const loading = ref(false)
  const errorMessage = ref('')

  // 한 달 성적표 조회 (month 예: '2026-11')
  async function fetchReport(month) {
    loading.value = true
    errorMessage.value = ''
    try {
      report.value = await getReport(month)
    } catch (error) {
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  return { report, loading, errorMessage, fetchReport }
})
