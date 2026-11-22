import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getSettingHistories, getSettings, updateSettings } from '@/api/settingsApi'

// 설비별 전력 임계치 기본값 (kW) = 각 설비의 정격 전력 (실시간 상태의 equipments[].ratedKw와 같은 값)
// DB(setting 표)에 equipment.{설비 id}.max_kw 행이 없어서 프론트에 둔 값. 서버에 값이 생기면 서버 값이 우선임
// 정격으로 정한 이유: 설비 사양이라 "정격을 넘으면 초과"로 설명하기 쉽고, 평소 부하는 정격의 80~87%라
// 늘 "▲ 초과"가 뜨지 않음 (2026-11-21~22 하루 동안 15분 평균 최고값도 모두 정격 아래)
// (예전 값은 11-19 한 시점 전력의 2배였는데, 지금 부하보다 낮은 설비가 있어 사출 구역에 늘 "▲ 초과"가 떴음, 이슈 #35-2)
// 바꾸려면 이 표의 숫자를 고침 (환경 설정 화면에서는 서버에 행이 없어 저장할 수 없음)
const DEFAULT_EQUIPMENT_THRESHOLDS = {
  'equipment.HEX-01.max_kw': 120, // CNC 가공기 1호
  'equipment.HEX-02.max_kw': 120, // CNC 가공기 2호
  'equipment.HEX-03.max_kw': 100, // 프레스
  'equipment.BRZ-01.max_kw': 600, // 브레이징로
  'equipment.BRZ-02.max_kw': 180, // 탈지·건조기
  'equipment.INJ-01.max_kw': 220, // 사출기 1호
  'equipment.INJ-02.max_kw': 220, // 사출기 2호
  'equipment.INJ-03.max_kw': 220, // 사출기 3호
  'equipment.MOD-01.max_kw': 40, // 조립 라인 1
  'equipment.MOD-02.max_kw': 40, // 조립 라인 2
  'equipment.INS-01.max_kw': 120, // 리크 테스터
  'equipment.INS-02.max_kw': 80, // 비전 검사기
  'equipment.UTL-01.max_kw': 200, // 공기압축기
  'equipment.UTL-02.max_kw': 150, // 냉각탑
  'equipment.UTL-03.max_kw': 120, // 공조기
}

// 환경 설정(기준값). 화면 갱신 주기와 운영 모드는 모든 화면이 쓰므로 공통 틀(MainLayout)에서도 읽음
// 키 이름은 명세 v0.2 그대로 씀 (예: 'peak.target_kw'). 이름을 바꾸는 변환 코드는 두지 않음
export const useSettingsStore = defineStore('settings', () => {
  const items = ref([]) // 기준값 목록: [{ key, group, label, value: '1800', unit, updatedAt, updatedBy }]
  const history = ref([]) // 변경 기록 (최신 순): [{ id, changedAt, key, label, oldValue, newValue, changedBy }]
  const historyError = ref('') // 변경 기록을 받지 못했을 때의 메시지 ("기록 없음"과 구분하기 위해)
  const loading = ref(true) // 처음에는 아직 받기 전이라 true
  const errorMessage = ref('')
  let historyRange = null // 마지막으로 조회한 변경 기록 기간 (저장한 뒤 같은 기간으로 다시 받기 위해)

  // 문자열 값을 화면에서 쓰기 좋은 값으로 바꿈 (명세: "DB가 VARCHAR라 문자열로 보냄. 숫자 변환은 프론트에서")
  // 'true' / 'false' → true / false, 숫자 글자 → 숫자, 그 밖에는 글자 그대로
  function parseValue(value) {
    if (value === 'true') return true
    if (value === 'false') return false
    const number = Number(value)
    return value !== '' && Number.isFinite(number) ? number : value
  }

  // key로 값을 바로 꺼낼 수 있게 한 모양. 예: values['peak.target_kw'] → 1800
  // 설비 임계치는 프론트 기본값(DEFAULT_EQUIPMENT_THRESHOLDS)을 먼저 넣어 두고,
  // 서버가 준 값이 있으면 그 값으로 덮어씀
  const values = computed(() => {
    const result = { ...DEFAULT_EQUIPMENT_THRESHOLDS }
    for (const item of items.value) result[item.key] = parseValue(item.value)
    return result
  })

  async function fetchSettings() {
    try {
      items.value = await getSettings()
      errorMessage.value = ''
    } catch (error) {
      // 실패해도 이미 받아 둔 값은 지우지 않음
      errorMessage.value = error.message
    } finally {
      loading.value = false
    }
  }

  // 변경 기록 조회 (from, to 예: '2026-10-18', '2026-11-17'). 실패해도 이전 기록은 그대로 둠
  async function fetchHistory(from, to) {
    historyRange = { from, to }
    try {
      history.value = await getSettingHistories(from, to)
      historyError.value = ''
    } catch (error) {
      historyError.value = error.message
    }
  }

  // 바꾼 값들을 한 번에 저장 (명세 12번: PUT /api/settings)
  // changes 예: [{ key: 'peak.target_kw', value: 1750 }] → 보낼 때는 값을 문자열로 바꿈
  async function saveSettings(changes) {
    const body = changes.map((change) => ({ key: change.key, value: String(change.value) }))
    items.value = await updateSettings(body) // 저장된 전체 목록이 돌아옴
    if (historyRange) await fetchHistory(historyRange.from, historyRange.to)
  }

  return {
    items,
    history,
    historyError,
    loading,
    errorMessage,
    values,
    fetchSettings,
    fetchHistory,
    saveSettings,
  }
})
