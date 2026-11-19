<!-- 운영 이력: 추천 → 적용 여부 → 결과를 시간순 표로 보여 줍니다 -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHistoryStore } from '@/stores/useHistoryStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatHourMinute, formatKw, formatMonthDayTime } from '@/utils/format'

const historyStore = useHistoryStore()
const realtimeStore = useRealtimeStore()
const route = useRoute()

const REFRESH_INTERVAL_MS = 5000 // 새 추천이 생기면 표에 나타나도록 자동으로 다시 받는 주기

const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
// 처리 상태는 색만으로 구분하지 않도록 도형 + 글자로 표시
const STATUS_INFO = {
  RECOMMENDED: { shape: '○', label: '추천 중' },
  APPLIED: { shape: '●', label: '적용' },
  IGNORED: { shape: '✕', label: '넘김' },
  AUTO: { shape: '◇', label: '자동' },
}
// "목표 달성" 칸에 보이는 말 (UI 피드백: 더 짧고 바로 읽히는 말로)
const RESULT_INFO = {
  DEFENDED: { shape: '●', label: '달성', className: 'ok' },
  EXCEEDED: { shape: '■', label: '초과', className: 'crit' },
}

// ---- 조회 조건 ----
const preset = ref('today') // today(오늘) | week(최근 7일) | custom(직접 입력)
const fromDate = ref('') // 예: '2026-11-14'
const toDate = ref('')
const actionFilter = ref('ALL')
const statusFilter = ref('ALL')
const ruleFilter = ref('ALL') // 근거(규칙) 이름. 예: '피크 방어'
const keyword = ref('')

const openedId = ref(null) // 자세한 내용을 펼친 행의 id
let timer = null

// "오늘"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함 (시뮬레이터가 배속으로 돌 수 있음)
const serverDate = computed(() => realtimeStore.status?.recordedAt.slice(0, 10) ?? '')

// 백엔드가 한 번에 조회해 주는 최대 기간 (넘으면 400을 줌)
const MAX_DAYS = 62

// '2026-11-01' ~ '2026-11-18' → 18 (시작일과 종료일을 모두 셈)
function dayCount(from, to) {
  const toTime = (date) => {
    const [year, month, day] = date.split('-').map(Number)
    return new Date(year, month - 1, day).getTime()
  }
  return Math.round((toTime(to) - toTime(from)) / 86400000) + 1
}

const dateError = computed(() => {
  if (!fromDate.value || !toDate.value) return ''
  if (fromDate.value > toDate.value) return '시작일이 종료일보다 늦습니다.'
  // 요청을 보내기 전에 막음 (보내면 400이 남 — 이슈 #25)
  if (dayCount(fromDate.value, toDate.value) > MAX_DAYS) {
    return `기간은 최대 ${MAX_DAYS}일까지 조회할 수 있습니다.`
  }
  return ''
})

// '2026-11-14'에서 며칠 전 날짜 구하기
function daysBefore(date, days) {
  const [year, month, day] = date.split('-').map(Number)
  const result = new Date(year, month - 1, day - days)
  const pad = (n) => String(n).padStart(2, '0')
  return `${result.getFullYear()}-${pad(result.getMonth() + 1)}-${pad(result.getDate())}`
}

// 주소에 적힌 날짜가 '2026-11-14' 모양이고 실제로 있는 날짜인지 확인
function isValidDate(text) {
  if (typeof text !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(text)) return false
  const [year, month, day] = text.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  // '2026-02-30'처럼 없는 날짜는 다음 달로 넘어가므로, 다시 비교해서 걸러냄
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  toDate.value = serverDate.value
  fromDate.value = name === 'week' ? daysBefore(serverDate.value, 6) : serverDate.value
}

function load(showLoading = true) {
  if (!fromDate.value || !toDate.value || dateError.value) return
  historyStore.fetchDecisions(fromDate.value, toDate.value, showLoading)
}

function resetFilters() {
  actionFilter.value = 'ALL'
  statusFilter.value = 'ALL'
  ruleFilter.value = 'ALL'
  keyword.value = ''
  applyPreset('today')
}

// 근거 선택 상자에 넣을 목록: 받아 온 이력에 실제로 나온 근거만 모음 (중복 제거)
const ruleOptions = computed(() => [
  ...new Set(historyStore.decisions.map((item) => item.ruleLabel)),
])

// 기간이 바뀌면 다시 조회
watch([fromDate, toDate], () => {
  openedId.value = null
  ruleFilter.value = 'ALL' // 새 기간에는 고른 근거가 없을 수 있으므로 처음으로 돌림
  load()
})

// 성적표의 "추천 이력 보기"처럼 주소에 기간이 있으면(/logs?from=2026-11-01&to=2026-11-17) 그 기간으로 시작.
// 위의 감시(watch)가 기간이 바뀐 것을 보고 바로 조회함. 기간이 올바르지 않으면 무시하고 아래처럼 "오늘"로 시작
const queryFrom = route.query.from
const queryTo = route.query.to
if (
  isValidDate(queryFrom) &&
  isValidDate(queryTo) &&
  queryFrom <= queryTo &&
  dayCount(queryFrom, queryTo) <= MAX_DAYS
) {
  preset.value = 'custom'
  fromDate.value = queryFrom
  toDate.value = queryTo
}

// 서버 날짜를 처음 알게 되면 "오늘"로 조회 시작 (주소에 기간이 있었으면 이미 날짜가 있어 건너뜀)
watch(
  serverDate,
  (date) => {
    if (date && !fromDate.value) applyPreset('today')
  },
  { immediate: true },
)

onMounted(() => {
  // 조회 기간에 오늘이 들어 있을 때만 자동으로 다시 받음 (지난 기간은 바뀌지 않으므로)
  timer = setInterval(() => {
    if (toDate.value >= serverDate.value) load(false)
  }, REFRESH_INTERVAL_MS)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})

// 처리자 표시: 명세는 자동일 때 "자동 모드"를 보냄 → 실제 제어가 아니라는 뜻으로 "(시뮬레이션)"을 붙임
function handlerText(appliedBy) {
  if (!appliedBy) return '—'
  return appliedBy === '자동 모드' ? '자동 모드 (시뮬레이션)' : appliedBy
}

// ---- 표에 보여 줄 목록 (행동, 상태, 검색어로 거름) ----
const filtered = computed(() => {
  const word = keyword.value.trim()
  return historyStore.decisions.filter((item) => {
    if (actionFilter.value !== 'ALL' && item.action !== actionFilter.value) return false
    if (statusFilter.value !== 'ALL' && item.status !== statusFilter.value) return false
    if (ruleFilter.value !== 'ALL' && item.ruleLabel !== ruleFilter.value) return false
    if (word) {
      const text = `${item.reason} ${item.ruleLabel} ${handlerText(item.appliedBy)}`
      if (!text.includes(word)) return false
    }
    return true
  })
})

// 위쪽 요약 숫자 (표시용 개수 세기)
const counts = computed(() => {
  const count = (status) => filtered.value.filter((item) => item.status === status).length
  return [
    { label: '전체', value: filtered.value.length },
    { label: '적용', value: count('APPLIED') },
    { label: '자동', value: count('AUTO') },
    { label: '넘김', value: count('IGNORED') },
    { label: '추천 중', value: count('RECOMMENDED') },
  ]
})

function toggle(id) {
  openedId.value = openedId.value === id ? null : id
}
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="crumb">
        <span class="crumb-parent">운영 로그</span>
        <span class="crumb-parent" aria-hidden="true">›</span>
        <h1 class="crumb-title">추천 이력</h1>
      </div>
      <ul class="counts">
        <li v-for="item in counts" :key="item.label">
          {{ item.label }} <strong class="num">{{ item.value }}</strong>
        </li>
      </ul>
      <button class="reset head-reset" @click="resetFilters">조건 초기화</button>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button
            :class="{ active: preset === 'today' }"
            :aria-pressed="preset === 'today'"
            @click="applyPreset('today')"
          >
            오늘
          </button>
          <button
            :class="{ active: preset === 'week' }"
            :aria-pressed="preset === 'week'"
            @click="applyPreset('week')"
          >
            최근 7일
          </button>
        </div>
        <input
          v-model="fromDate"
          type="date"
          class="input num"
          aria-label="시작일"
          @change="preset = 'custom'"
        />
        <span aria-hidden="true">~</span>
        <input
          v-model="toDate"
          type="date"
          class="input num"
          aria-label="종료일"
          @change="preset = 'custom'"
        />
      </div>

      <label class="field">
        <span class="field-label">추천</span>
        <select v-model="actionFilter" class="input">
          <option value="ALL">전체</option>
          <option value="DISCHARGE">방전</option>
          <option value="CHARGE">충전</option>
          <option value="IDLE">대기</option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">처리</span>
        <select v-model="statusFilter" class="input">
          <option value="ALL">전체</option>
          <option value="RECOMMENDED">추천 중</option>
          <option value="APPLIED">적용</option>
          <option value="AUTO">자동</option>
          <option value="IGNORED">넘김</option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">규칙</span>
        <select v-model="ruleFilter" class="input">
          <option value="ALL">전체</option>
          <option v-for="rule in ruleOptions" :key="rule" :value="rule">{{ rule }}</option>
        </select>
      </label>

      <label class="field search">
        <span class="field-label">검색</span>
        <input
          v-model="keyword"
          type="search"
          class="input"
          placeholder="규칙, 이유 문장, 처리자"
        />
      </label>

      <p v-if="dateError" class="date-error" role="alert">{{ dateError }}</p>
    </section>

    <!-- 이력 표 -->
    <section class="card table-card">
      <p v-if="historyStore.errorMessage" class="notice" role="alert">
        <!-- 실패해도 이전 목록은 지우지 않으므로, 아래 표가 지금 기간의 것이 아니라는 걸 알려 줌 (이슈 #25) -->
        {{
          historyStore.decisions.length
            ? '새 이력을 불러오지 못해 이전에 받은 이력을 보여 줍니다.'
            : '이력을 불러오지 못했습니다.'
        }}
        ({{ historyStore.errorMessage }})
        <button class="retry" @click="load()">다시 시도</button>
      </p>

      <p v-if="historyStore.loading" class="empty">불러오는 중입니다.</p>

      <!-- "오늘"은 서버 시각으로 정하므로, 서버 시각을 받기 전에는 조회하지 않음 ("이력 없음"으로 잘못 보이지 않게) -->
      <p v-else-if="!fromDate" class="empty">
        서버에서 현재 시각을 받으면 오늘 이력을 자동으로 조회합니다.
      </p>

      <p v-else-if="!filtered.length && !historyStore.errorMessage" class="empty">
        조건에 맞는 이력이 없습니다. 기간이나 조건을 바꿔 보세요.
      </p>

      <div v-else-if="filtered.length" class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th class="col-toggle"><span class="sr-only">자세히 보기</span></th>
              <!--
                칼럼 이름은 UI 피드백(2026-11-17)으로 더 바로 읽히는 말로 바꿈.
                예전 이름: 15분 구간 → 대상 시간, 근거 → 규칙, 예상 평균 → 예상 피크,
                처리자 · 시각 → 처리자 / 처리 시각(두 칸), 결과 피크 → 실제 피크, 결과 → 목표 달성
              -->
              <th>추천 시각</th>
              <th>대상 시간</th>
              <th>추천</th>
              <th>규칙</th>
              <th class="right">예상 피크</th>
              <th>처리</th>
              <th>처리자</th>
              <th>처리 시각</th>
              <th class="right">실제 피크</th>
              <th>목표 달성</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in filtered" :key="item.id">
              <tr class="row" :class="{ opened: openedId === item.id }" @click="toggle(item.id)">
                <td class="col-toggle">
                  <button
                    class="toggle"
                    :aria-expanded="openedId === item.id"
                    :aria-label="`${formatMonthDayTime(item.decidedAt)} 추천 자세히 보기`"
                    @click.stop="toggle(item.id)"
                  >
                    {{ openedId === item.id ? '▾' : '▸' }}
                  </button>
                </td>
                <td class="num">{{ formatMonthDayTime(item.decidedAt) }}</td>
                <td class="num">
                  {{ formatHourMinute(item.windowStart) }}~{{ formatHourMinute(item.windowEnd) }}
                </td>
                <td>
                  <strong>{{ ACTION_LABELS[item.action] }}</strong>
                  <span v-if="item.action !== 'IDLE'" class="power num">
                    {{ formatKw(item.powerKw) }} kW</span
                  >
                </td>
                <td>{{ item.ruleLabel }}</td>
                <td class="right num">{{ formatKw(item.predictedKw) }} kW</td>
                <td class="status" :class="{ strong: item.status === 'APPLIED' }">
                  <span aria-hidden="true">{{ STATUS_INFO[item.status].shape }}</span>
                  {{ STATUS_INFO[item.status].label }}
                </td>
                <td>{{ handlerText(item.appliedBy) }}</td>
                <td class="num">
                  {{ item.appliedAt ? formatHourMinute(item.appliedAt) : '—' }}
                </td>
                <td class="right num">
                  <template v-if="item.resultPeakKw !== null">
                    {{ formatKw(item.resultPeakKw) }} kW
                  </template>
                  <template v-else>—</template>
                </td>
                <td>
                  <span
                    v-if="item.resultStatus"
                    class="result"
                    :class="RESULT_INFO[item.resultStatus].className"
                  >
                    <span aria-hidden="true">{{ RESULT_INFO[item.resultStatus].shape }}</span>
                    {{ RESULT_INFO[item.resultStatus].label }}
                  </span>
                  <span v-else class="sub">구간 진행 중</span>
                </td>
              </tr>

              <!-- 펼친 행: 추천 이유와 판단에 쓴 값 -->
              <tr v-if="openedId === item.id" class="detail-row">
                <td></td>
                <td colspan="10">
                  <!-- 통합 화면의 ESS 추천 카드와 같은 이름(이유 / 판단 근거)으로 나눠 보여 줌 -->
                  <h4 class="detail-title">이유</h4>
                  <p class="reason">{{ item.reason }}</p>
                  <!-- 판단 근거(usedValues)는 명세에서 보류 중이라 없을 수 있음 → 없으면 제목째 숨김 -->
                  <h4 v-if="item.usedValues?.length" class="detail-title">판단 근거</h4>
                  <dl v-if="item.usedValues?.length" class="used">
                    <div v-for="used in item.usedValues" :key="used.label" class="used-item">
                      <dt>{{ used.label }}</dt>
                      <dd class="num">
                        {{ typeof used.value === 'number' ? formatKw(used.value) : used.value }}
                        {{ used.unit }}
                      </dd>
                    </div>
                  </dl>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 560px;
  padding: 20px;
}

.head {
  display: flex;
  align-items: center;
  gap: 24px;
}
.head-reset {
  margin-left: auto;
}
.title {
  font-size: 20px;
  font-weight: 700;
}
.counts {
  display: flex;
  gap: 18px;
  color: var(--ink-2);
}
.counts strong {
  margin-left: 2px;
  font-weight: 700;
  color: var(--ink);
}

/* 조회 조건 */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  flex: none;
}
.field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.field-label {
  font-weight: 700;
  color: var(--ink-2);
}
.search {
  flex: 1;
  min-width: 180px;
}
.search .input {
  flex: 1;
}
.input {
  height: 34px;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink);
}
/* 고르는 상자는 가장 긴 항목만큼 넓어지므로, 조건 줄이 두 줄로 넘어가지 않게 폭을 묶어 둠 */
select.input {
  max-width: 132px;
}
.reset {
  height: 34px;
  padding: 0 14px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink-2);
}
.reset:hover {
  background: var(--bg);
}
.date-error {
  width: 100%;
  color: var(--crit);
}

/* 표 */
.table-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-height: 0;
  padding: 0;
}
.notice {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 16px 0;
  color: var(--crit);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--mute);
}
.table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
}
.table th {
  position: sticky;
  top: 0;
  padding: 10px 12px;
  background: var(--bg);
}
.table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}
.table .right {
  text-align: right;
}
.col-toggle {
  width: 40px;
}
.row {
  cursor: pointer;
}
.row:hover,
.row.opened {
  background: var(--bg);
}
.toggle {
  width: 24px;
  height: 24px;
  background: none;
  border: 0;
  color: var(--ink-2);
}
.power {
  margin-left: 4px;
}
.status {
  color: var(--ink-2);
}
.status.strong {
  font-weight: 700;
  color: var(--ink);
}
.sub {
  margin-left: 4px;
  color: var(--mute);
}
.result {
  font-weight: 700;
}
.result.ok {
  color: var(--ok);
}
.result.crit {
  color: var(--crit);
}

.detail-row td {
  padding-top: 12px;
  padding-bottom: 14px;
  background: var(--bg);
  white-space: normal;
}
.detail-title {
  margin-bottom: 2px;
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-2);
}
.detail-title + .used {
  margin-top: 0;
}
.reason {
  margin-bottom: 10px;
  color: var(--ink);
}
.used {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 28px;
  margin-top: 10px;
}
.used-item dt {
  font-size: 12px;
  color: var(--mute);
}
.used-item dd {
  font-weight: 700;
}

/* 화면에는 안 보이고 화면 낭독기만 읽는 글자 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
