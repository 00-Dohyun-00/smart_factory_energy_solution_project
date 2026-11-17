import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getEnergySettingHistory, getEnergySettings, updateEnergySetting } from '@/api/settingsApi'

// 환경 설정(기준값). 화면 갱신 주기와 운영 모드는 모든 화면이 쓰므로 공통 틀(MainLayout)에서도 읽음
export const useSettingsStore = defineStore('settings', () => {
  const items = ref([]) // 기준값 목록: [{ key, value, updatedAt, updatedBy }]
  const history = ref([]) // 변경 기록 (최신 순)
  const loading = ref(true) // 처음에는 아직 받기 전이라 true
  const errorMessage = ref('')

  // key로 값을 바로 꺼낼 수 있게 한 모양. 예: values.targetPeakKw → 1800
  const values = computed(() => {
    const result = {}
    for (const item of items.value) result[item.key] = item.value
    return result
  })

  async function fetchSettings() {
    try {
      items.value = await getEnergySettings()
      errorMessage.value = ''
    } catch (error) {
      // 실패해도 이미 받아 둔 값은 지우지 않음
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  // 실패하면 이전 기록을 그대로 둠 (연결 상태는 공통 틀이 알려 줌)
  async function fetchHistory() {
    try {
      history.value = await getEnergySettingHistory()
    } catch {
      // 위와 같음
    }
  }

  // 바꾼 값들을 하나씩 저장. changes 예: [{ key: 'targetPeakKw', value: 1750 }]
  // 중간에 실패하면 그 앞까지는 저장된 상태이므로, 끝나면 항상 최신 값을 다시 받음
  async function saveSettings(changes) {
    try {
      for (const change of changes) {
        await updateEnergySetting(change.key, change.value)
      }
    } finally {
      await fetchSettings()
      await fetchHistory()
    }
  }

  return {
    items,
    history,
    loading,
    errorMessage,
    values,
    fetchSettings,
    fetchHistory,
    saveSettings,
  }
})
