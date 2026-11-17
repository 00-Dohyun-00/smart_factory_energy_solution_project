<!--
  환경 설정 > 기준값
  묶음 세 개(피크 / ESS / 운영)를 나란히 두고 묶음마다 저장합니다. 아래에는 변경 기록.

  - 바꿨지만 아직 저장하지 않은 값은 왼쪽 굵은 줄과 "저장 전 값 → 바꾼 값"으로 표시합니다.
  - 값이 올바른지는 백엔드도 확인하지만, 빈칸이나 범위를 벗어난 값은 화면에서 먼저 알려 줍니다.
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatInteger, formatKw, formatOneDecimal } from '@/utils/format'

const settingsStore = useSettingsStore()
const realtimeStore = useRealtimeStore()

const MODES = [
  { value: 'MANUAL', label: '운영자 승인 모드', note: '추천을 사람이 보고 적용' },
  { value: 'AUTO', label: '자동 모드 (시뮬레이션)', note: '추천을 바로 반영한 것으로 표시' },
]
const MODE_LABELS = { MANUAL: '운영자 승인 모드', AUTO: '자동 모드 (시뮬레이션)' }

// 화면에 놓을 항목들. key는 백엔드 설정 이름과 같아야 함
// min, max: 화면에서 먼저 걸러 주는 범위 / decimal: 소수 1자리까지 쓰는 값
// (2026-11-17, UI 피드백) 요금 단가 4개는 요금 기준표에서, 충·방전 효율은 외부에서 받아오는 값이라
// 여기서 고치지 않도록 화면에서 뺐음
const GROUPS = [
  {
    key: 'peak',
    title: '피크',
    note: '피크 단계를 나누는 기준',
    fields: [
      { key: 'targetPeakKw', label: '목표 피크', unit: 'kW', min: 100, max: 100000 },
      { key: 'warnPercent', label: '주의선', unit: '%', min: 50, max: 100 },
    ],
  },
  {
    key: 'ess',
    title: 'ESS',
    note: '배터리를 안전하게 쓰는 범위',
    fields: [
      { key: 'capacityKwh', label: '배터리 용량', unit: 'kWh', min: 1, max: 100000 },
      { key: 'maxPowerKw', label: '최대 충·방전 출력', unit: 'kW', min: 1, max: 100000 },
      { key: 'socLowPercent', label: '잔량 하한', unit: '%', min: 0.1, max: 99 },
      { key: 'socHighPercent', label: '잔량 상한', unit: '%', min: 1, max: 100 },
      { key: 'roomTempLimitC', label: '배터리실 온도 제한', unit: '℃', min: 1, max: 80 },
    ],
  },
  {
    key: 'operation',
    title: '운영',
    note: '추천을 반영하는 방식',
    fields: [
      { key: 'operationMode', label: '운영 모드', mode: true },
      { key: 'pollSeconds', label: '화면 갱신 주기', unit: '초', min: 1, max: 60 },
      { key: 'decisionValidMinutes', label: '추천 유효 시간', unit: '분', min: 1, max: 60 },
    ],
  },
]
const ALL_FIELDS = GROUPS.flatMap((group) => group.fields.map((field) => ({ ...field, group })))

// 화면에서는 뺐지만 예전 변경 기록에는 남아 있을 수 있는 항목.
// 기록이 'pricePeakWon' 같은 영문 이름으로 보이지 않게 이름과 단위만 둠 (고치는 칸은 만들지 않음)
const RETIRED_FIELDS = [
  { key: 'priceLightWon', label: '경부하 단가', unit: '원/kWh', group: { title: '요금' } },
  { key: 'priceMidWon', label: '중간부하 단가', unit: '원/kWh', group: { title: '요금' } },
  { key: 'pricePeakWon', label: '최대부하 단가', unit: '원/kWh', group: { title: '요금' } },
  { key: 'basePriceWon', label: '기본요금 단가', unit: '원/kW', group: { title: '요금' } },
  {
    key: 'efficiencyPercent',
    label: '충·방전 효율',
    unit: '%',
    decimal: true,
    group: { title: 'ESS' },
  },
]

// ---- 저장된 값과 고치는 중인 값 ----
const saved = computed(() => settingsStore.values) // 저장된 값 (key → 값)
const draft = ref({}) // 화면에서 고치는 중인 값 (key → 값)
const savingGroup = ref('') // 저장 요청 중인 묶음
const savedGroup = ref('') // 방금 저장을 마친 묶음 ("저장했습니다" 표시용)
const saveErrors = ref({}) // 묶음별 저장 실패 메시지

// 묶음의 값을 저장된 값으로 되돌림 (처음 받았을 때, 저장한 뒤, [되돌리기])
function resetGroup(group) {
  for (const field of group.fields) draft.value[field.key] = saved.value[field.key]
}

onMounted(() => {
  settingsStore.fetchSettings()
  settingsStore.fetchHistory()
})

// 값을 처음 받으면 고치는 칸에 채워 넣음
watch(
  () => settingsStore.items.length,
  (count) => {
    if (count && !Object.keys(draft.value).length) GROUPS.forEach(resetGroup)
  },
  { immediate: true },
)

const isChanged = (field) => draft.value[field.key] !== saved.value[field.key]
const changedFields = (group) => group.fields.filter(isChanged)

// ---- 저장하지 않은 값이 있을 때 화면을 떠나지 못하게 한 번 물어봄 ----
// 바꾼 값은 이 화면 안에만 있어서, 묻지 않으면 다른 메뉴를 누르는 순간 말없이 사라짐
const unsavedCount = computed(() => {
  if (!Object.keys(draft.value).length) return 0 // 아직 값을 받기 전
  return GROUPS.reduce((sum, group) => sum + changedFields(group).length, 0)
})

// 앱 안에서 다른 화면으로 갈 때 (왼쪽 메뉴, 링크)
onBeforeRouteLeave(() => {
  if (!unsavedCount.value) return true
  return window.confirm(
    `저장하지 않은 값이 ${unsavedCount.value}건 있습니다. 저장하지 않고 다른 화면으로 갈까요?`,
  )
})

// 새로 고침, 탭 닫기, 주소 직접 입력 (브라우저가 정한 문구로 물어봄)
function warnBeforeUnload(event) {
  if (!unsavedCount.value) return
  event.preventDefault()
  event.returnValue = '' // 예전 브라우저도 묻게 하려면 이 값이 필요함
}

onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload))

// 값이 잘못됐으면 그 이유, 괜찮으면 ''
function errorOf(field) {
  if (field.mode) return ''
  const value = draft.value[field.key]
  if (typeof value !== 'number' || !Number.isFinite(value)) return '숫자를 넣어 주세요.'
  if (value < field.min || value > field.max) {
    return `${formatInteger(field.min)} ~ ${formatInteger(field.max)} 사이로 넣어 주세요.`
  }
  if (field.key === 'socLowPercent' && value >= draft.value.socHighPercent) {
    return '잔량 하한은 잔량 상한보다 작아야 합니다.'
  }
  return ''
}
const hasError = (group) => group.fields.some((field) => errorOf(field))

async function saveGroup(group) {
  const changes = changedFields(group).map((field) => ({
    key: field.key,
    value: draft.value[field.key],
  }))
  savingGroup.value = group.key
  savedGroup.value = ''
  saveErrors.value[group.key] = ''
  try {
    await settingsStore.saveSettings(changes)
    savedGroup.value = group.key
  } catch (error) {
    saveErrors.value[group.key] = error.message
  } finally {
    savingGroup.value = ''
  }
}

function saveLabel(group) {
  if (savingGroup.value === group.key) return '저장하는 중…'
  const count = changedFields(group).length
  return count ? `변경 ${count}건 저장` : '바뀐 값 없음'
}

// ---- 표시용 글자 ----
// 값 + 단위 (예: '1,800 kW', '89.5 %', '자동 모드 (시뮬레이션)')
function valueText(field, value) {
  if (field.mode) return MODE_LABELS[value] ?? '—'
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  const number = field.decimal ? formatOneDecimal(value) : formatInteger(value)
  return `${number} ${field.unit}`
}

// 항목 아래의 도움말
function helpOf(field) {
  if (field.key === 'targetPeakKw') return '15분 평균이 이 값을 넘으면 경고'
  if (field.key === 'warnPercent') {
    // 주의가 시작되는 kW = 목표 피크 × 주의선 % (고치는 중인 값으로 미리 보여 주는 표시용 계산)
    const warnKw = (draft.value.targetPeakKw * draft.value.warnPercent) / 100
    return Number.isFinite(warnKw)
      ? `목표의 이 비율부터 주의. 지금 값으로는 ${formatKw(Math.round(warnKw))} kW부터`
      : '목표의 이 비율부터 주의'
  }
  if (field.key === 'maxPowerKw') return '추천 전력은 이 값을 넘지 않음'
  if (field.key === 'socLowPercent') return '이 아래로는 방전하지 않음'
  if (field.key === 'socHighPercent') return '이 위로는 충전하지 않음'
  if (field.key === 'roomTempLimitC') {
    const now = realtimeStore.status
      ? `, 지금 ${formatOneDecimal(realtimeStore.status.batteryTempC)} ℃`
      : ''
    return `넘으면 충·방전을 멈추고 대기${now}`
  }
  if (field.key === 'pollSeconds') return '화면이 서버에서 새 값을 받아 오는 간격'
  if (field.key === 'decisionValidMinutes') return '구간이 끝나면 추천도 끝남 (확인 필요)'
  return ''
}

// '2026-11-14T16:20:00' → '2026-11-14 16:20'
const dateTimeText = (dateTime) => (dateTime ? dateTime.slice(0, 16).replace('T', ' ') : '—')

// 묶음에서 가장 최근에 저장한 시각과 사람
function lastSaved(group) {
  const keys = group.fields.map((field) => field.key)
  const items = settingsStore.items.filter((item) => keys.includes(item.key) && item.updatedAt)
  if (!items.length) return null
  return items.reduce((latest, item) => (item.updatedAt > latest.updatedAt ? item : latest))
}

// 변경 기록의 한 줄을 화면용으로 (항목 이름과 단위는 위의 항목 목록에서 찾음)
const historyRows = computed(() =>
  settingsStore.history.map((item) => {
    const field = [...ALL_FIELDS, ...RETIRED_FIELDS].find((one) => one.key === item.key)
    return {
      id: item.id,
      changedAt: dateTimeText(item.changedAt),
      group: field?.group.title ?? '—',
      label: field?.label ?? item.key,
      before: field ? valueText(field, item.before) : item.before,
      after: field ? valueText(field, item.after) : item.after,
      changedBy: item.changedBy,
    }
  }),
)
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">환경 설정</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">기준값</h1>
      <p class="crumb-note">
        바꾼 값은 <strong>저장</strong>을 눌러야 반영됩니다. 저장하지 않은 값은 왼쪽에 굵은 줄로
        표시됩니다.
      </p>
    </div>

    <p
      v-if="settingsStore.errorMessage && !settingsStore.items.length"
      class="state error"
      role="alert"
    >
      기준값을 불러오지 못했습니다. ({{ settingsStore.errorMessage }})
      <button class="button" @click="settingsStore.fetchSettings()">다시 시도</button>
    </p>
    <p v-else-if="settingsStore.loading || !settingsStore.items.length" class="state">
      불러오는 중입니다.
    </p>

    <template v-else>
      <div class="groups">
        <section v-for="group in GROUPS" :key="group.key" class="card group">
          <div class="card-head">
            <h2 class="card-title">{{ group.title }}</h2>
            <span class="note">{{ group.note }}</span>
          </div>

          <div
            v-for="field in group.fields"
            :key="field.key"
            class="item"
            :class="{ changed: isChanged(field), invalid: errorOf(field), wide: field.mode }"
          >
            <!-- 운영 모드: 둘 중 하나를 고름 -->
            <template v-if="field.mode">
              <span class="item-label">{{ field.label }}</span>
              <div class="modes" role="radiogroup" :aria-label="field.label">
                <button
                  v-for="mode in MODES"
                  :key="mode.value"
                  class="mode"
                  :class="{ on: draft[field.key] === mode.value }"
                  role="radio"
                  :aria-checked="draft[field.key] === mode.value"
                  @click="draft[field.key] = mode.value"
                >
                  <strong>
                    <span v-if="draft[field.key] === mode.value" aria-hidden="true">●</span>
                    {{ mode.label }}
                  </strong>
                  <span>{{ mode.note }}</span>
                </button>
              </div>
              <p v-if="isChanged(field)" class="item-note">
                저장 전 값 <s>{{ valueText(field, saved[field.key]) }}</s> → 바꾼 값
                {{ valueText(field, draft[field.key]) }} (아직 저장하지 않음)
              </p>
              <p v-else class="item-help">
                자동 모드는 실제 설비를 제어하지 않습니다. 켜면 추천이 바로 반영된 것으로 기록되고
                화면에 "자동 모드 (시뮬레이션)"으로 표시됩니다.
              </p>
            </template>

            <!-- 숫자 항목 -->
            <template v-else>
              <label class="item-label" :for="`setting-${field.key}`">{{ field.label }}</label>
              <input
                :id="`setting-${field.key}`"
                v-model.number="draft[field.key]"
                type="number"
                class="box num"
                :min="field.min"
                :max="field.max"
                :step="field.decimal ? 0.1 : 1"
              />
              <span class="unit">{{ field.unit }}</span>
              <p v-if="errorOf(field)" class="item-error" role="alert">{{ errorOf(field) }}</p>
              <p v-else-if="isChanged(field)" class="item-note num">
                저장 전 값 <s>{{ valueText(field, saved[field.key]) }}</s> → 바꾼 값
                {{ valueText(field, draft[field.key]) }} (아직 저장하지 않음)
              </p>
              <p v-else-if="helpOf(field)" class="item-help num">{{ helpOf(field) }}</p>
            </template>
          </div>

          <!-- 묶음 아래: 마지막 저장, 되돌리기, 저장 -->
          <div class="group-foot">
            <p v-if="saveErrors[group.key]" class="foot-text error" role="alert">
              저장하지 못했습니다. ({{ saveErrors[group.key] }})
            </p>
            <p
              v-else-if="savedGroup === group.key && !changedFields(group).length"
              class="foot-text done"
            >
              저장했습니다.
            </p>
            <p v-else-if="lastSaved(group)" class="foot-text num">
              마지막 저장 {{ dateTimeText(lastSaved(group).updatedAt) }} ·
              {{ lastSaved(group).updatedBy }}
            </p>
            <button
              class="button"
              :disabled="!changedFields(group).length || savingGroup === group.key"
              @click="resetGroup(group)"
            >
              되돌리기
            </button>
            <button
              class="button dark"
              :disabled="
                !changedFields(group).length || hasError(group) || savingGroup === group.key
              "
              @click="saveGroup(group)"
            >
              {{ saveLabel(group) }}
            </button>
          </div>
        </section>
      </div>

      <!-- 변경 기록 -->
      <section class="card history">
        <div class="card-head">
          <h2 class="card-title">변경 기록</h2>
          <span class="note">저장할 때마다 저장 전 값과 저장 후 값이 남습니다</span>
        </div>
        <p v-if="!historyRows.length" class="empty">변경 기록이 없습니다.</p>
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>변경 시각</th>
                <th>묶음</th>
                <th>항목</th>
                <th>저장 전</th>
                <th>저장 후</th>
                <th>변경자</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in historyRows" :key="row.id">
                <td class="num">{{ row.changedAt }}</td>
                <td>{{ row.group }}</td>
                <td>
                  <strong>{{ row.label }}</strong>
                </td>
                <td class="num">
                  <s>{{ row.before }}</s>
                </td>
                <td class="num">
                  <strong>{{ row.after }}</strong>
                </td>
                <td>{{ row.changedBy }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 20px;
}
.crumb-note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.crumb-note strong {
  color: var(--ink);
}
.state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-height: 200px;
  color: var(--mute);
}
.state.error {
  color: var(--crit);
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}

/* 묶음 세 개 */
.groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.group {
  display: flex;
  flex-direction: column;
}
.group .card-head {
  margin-bottom: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}

/* 항목 한 줄: 이름 · 입력 칸 · 단위, 그 아래에 설명 */
.item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 56px;
  align-items: center;
  gap: 2px 10px;
  padding: 8px 0 8px 12px;
  border-left: 4px solid transparent;
}
.item-label {
  font-weight: 700;
}
.box {
  height: 36px;
  width: 100%;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
  text-align: right;
}
.unit {
  font-size: 13px;
  color: var(--ink-2);
}
.item-help,
.item-note,
.item-error {
  grid-column: 1 / -1;
  font-size: 12px;
}
.item-help {
  color: var(--mute);
}
/* 바꿨지만 아직 저장하지 않은 값 */
.item.changed {
  background: var(--bg);
  border-left-color: var(--ink);
}
.item.changed .box {
  border: 2px solid var(--ink);
}
.item-note {
  font-weight: 700;
  color: var(--ink);
}
.item-note s {
  font-weight: 400;
  color: var(--mute);
}
/* 잘못된 값 */
.item.invalid {
  border-left-color: var(--crit);
}
.item.invalid .box {
  border: 2px solid var(--crit);
}
.item-error {
  font-weight: 700;
  color: var(--crit);
}

/* 운영 모드 */
.item.wide {
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
}
.modes {
  display: flex;
  border: 1px solid var(--line-strong);
}
.mode {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  padding: 10px 14px;
  background: var(--card);
  border: 0;
  color: var(--ink-2);
  text-align: left;
}
.mode + .mode {
  border-left: 1px solid var(--line-strong);
}
.mode strong {
  font-size: 15px;
}
.mode span {
  font-size: 12px;
}
.mode:hover {
  background: var(--bg);
}
.mode.on {
  background: var(--ink);
  color: var(--card);
}

/* 화면이 좁으면 운영 모드 두 개를 위아래로 놓아 글자가 잘리지 않게 함 */
@media (max-width: 1600px) {
  .modes {
    flex-direction: column;
  }
  .mode + .mode {
    border-top: 1px solid var(--line-strong);
    border-left: 0;
  }
}

/* 묶음 아래 */
.group-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.foot-text {
  margin-right: auto;
  font-size: 12px;
  color: var(--mute);
}
.foot-text.done {
  font-weight: 700;
  color: var(--ok);
}
.foot-text.error {
  font-weight: 700;
  color: var(--crit);
}
.button {
  flex: none;
  height: 36px;
  margin-left: auto;
  padding: 0 16px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink);
}
.button + .button {
  margin-left: 0;
}
.button.dark {
  background: var(--ink);
  border-color: var(--ink);
  font-weight: 700;
  color: var(--card);
}
.button:hover:not(:disabled) {
  border-color: var(--ink);
}
.button.dark:hover:not(:disabled) {
  background: var(--ink-2);
}
.button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* 변경 기록 */
.history {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}
.empty {
  color: var(--mute);
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th {
  padding: 6px 12px 6px 0;
  border-bottom: 1px solid var(--ink);
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
  text-align: left;
}
.table td {
  padding: 9px 12px 9px 0;
  border-bottom: 1px solid var(--line);
}
.table s {
  color: var(--mute);
}
</style>
