<!--
  운영 로그 > 전력 이력
  15분 구간마다 한 줄씩, 그 구간의 전력 상황을 모두 표의 칸으로 보여 주는 로그 화면. [엑셀 받기]로 지금 표를 그대로 파일로 받음
  - 공장 소비 · 한전 구매 · 태양광 · ESS · 잔량 · 온도: 추이 분석 API (15분 간격). 날씨 · 요금 시간대도 같은 응답에서 씀
  - 피크 초과: 피크 분석 API의 exceededWindows
  - 피크 단계 · 이벤트: 이벤트 API (event_log 테이블)
  - 추천 · 처리 · 결과: 추천 이력 API (그 15분 구간을 대상으로 한 추천)
  - 구역별 · 설비별 전력: 설비별 전력 API (power_minute 테이블)
  표의 칸은 아래 columns 목록 하나로 정하고, 화면 표와 CSV가 같은 목록을 씀
  추이 분석 그래프의 빨간 점(●)을 누르면 /logs/power?at=2026-11-19T14:15:00 으로 들어옴
-->
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { useHistoryStore } from '@/stores/useHistoryStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatHourMinute, formatKw, formatOneDecimal } from '@/utils/format'

const historyStore = useHistoryStore()
const realtimeStore = useRealtimeStore()
const route = useRoute()
const router = useRouter()

// 표에 보일 줄 고르기
const ROW_FILTERS = [
  { value: 'ALL', label: '전체' },
  { value: 'EXCEEDED', label: '피크 초과' },
  { value: 'EVENT', label: '이벤트 구간' },
]
// 단계는 색만으로 구분하지 않도록 도형 + 글자로 표시 (위쪽 상태 표시줄과 같은 표시)
const LEVEL_INFO = {
  CRITICAL: { shape: '■', label: '경고', className: 'crit' },
  WARNING: { shape: '▲', label: '주의', className: 'warn' },
  NORMAL: { shape: '●', label: '정상', className: 'ok' },
}
const PERIOD_LABELS = { PEAK: '최대부하', MID: '중간부하', LIGHT: '경부하' }
// 이벤트 종류 이름 (내용 칸 앞에 붙임)
const EVENT_LABELS = {
  STAGE_CHANGE: '단계',
  INTERVAL_END: '구간 종료',
  RECOMMEND: '추천',
  HANDLE: '처리',
  SETTING: '설정',
  COMM: '통신',
}
const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
const STATUS_LABELS = { RECOMMENDED: '추천 중', APPLIED: '적용', IGNORED: '넘김', AUTO: '자동' }
const RESULT_LABELS = { DEFENDED: '달성', EXCEEDED: '초과' }

// ---- 조회 조건 ----
const preset = ref('today') // today(오늘) | week(최근 7일) | custom(직접 입력)
const fromDate = ref('') // 예: '2026-11-14'
const toDate = ref('')
// 추이 그래프의 빨간 점을 눌러 들어오면 그 15분 구간의 시작 (예: '2026-11-19T14:15:00'). 표에서 강조함
const focusAt = ref('')
// 기본은 "이벤트 구간" (기록이 없는 시간까지 다 보이면 표가 너무 길어짐)
const rowFilter = ref('EVENT')

// 한 번에 조회할 수 있는 최대 기간 (15분 구간이 하루 96줄이라 7일이면 672줄. 백엔드 이벤트 · 설비별 전력 API도 최대 7일)
const MAX_DAYS = 7

// "오늘"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함 (시뮬레이터가 배속으로 돌 수 있음)
const serverDate = computed(() => realtimeStore.status?.recordedAt.slice(0, 10) ?? '')

const pad2 = (number) => String(number).padStart(2, '0')
// Date → '2026-11-19T14:30:00' (백엔드 시각과 같은 모양)
function toDateTime(value) {
  const day = `${value.getFullYear()}-${pad2(value.getMonth() + 1)}-${pad2(value.getDate())}`
  return `${day}T${pad2(value.getHours())}:${pad2(value.getMinutes())}:00`
}
const addMinutes = (dateTime, minutes) =>
  toDateTime(new Date(new Date(dateTime).getTime() + minutes * 60000))

// '2026-11-01' ~ '2026-11-07' → 7 (시작일과 종료일을 모두 셈)
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
  // 요청을 보내기 전에 막음 (보내면 400이 남)
  if (dayCount(fromDate.value, toDate.value) > MAX_DAYS) {
    return `기간은 최대 ${MAX_DAYS}일까지 조회할 수 있습니다.`
  }
  return ''
})

// '2026-11-14'에서 며칠 전 날짜 구하기
function daysBefore(date, days) {
  const [year, month, day] = date.split('-').map(Number)
  const result = new Date(year, month - 1, day - days)
  return `${result.getFullYear()}-${pad2(result.getMonth() + 1)}-${pad2(result.getDate())}`
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  toDate.value = serverDate.value
  fromDate.value = name === 'week' ? daysBefore(serverDate.value, 6) : serverDate.value
}

// 조회할 기간 [시작일 0시, 종료일 다음 날 0시)
const period = computed(() => {
  if (!fromDate.value || !toDate.value || dateError.value) return null
  return { from: `${fromDate.value}T00:00:00`, to: addMinutes(`${toDate.value}T00:00:00`, 24 * 60) }
})

function load(showLoading = true) {
  if (!period.value) return
  historyStore.fetchPowerHistory(period.value.from, period.value.to, showLoading)
  historyStore.fetchEquipmentPowers(period.value.from, period.value.to)
}

// 기간이 바뀌면 다시 조회
watch(
  () => (period.value ? `${period.value.from}~${period.value.to}` : ''),
  () => load(),
)

// 주소에 시각이 있으면(?at=2026-11-19T14:15:00) 그날 하루를 조회하고 그 구간을 강조. 없으면 서버 날짜의 "오늘"
function applyQuery() {
  const at = route.query.at
  if (typeof at === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(at)) {
    const minute = Number(at.slice(14, 16))
    focusAt.value = `${at.slice(0, 13)}:${pad2(Math.floor(minute / 15) * 15)}:00`
    preset.value = 'custom'
    fromDate.value = at.slice(0, 10)
    toDate.value = at.slice(0, 10)
  }
}
applyQuery()
// 이 화면에 있는 채로 다른 점을 눌러 들어와도 반영
watch(() => route.query.at, applyQuery)

// 서버 날짜를 처음 알게 되면 "오늘"로 조회 시작 (주소에 시각이 있었으면 이미 날짜가 있어 건너뜀)
watch(
  serverDate,
  (value) => {
    if (value && !fromDate.value) applyPreset('today')
  },
  { immediate: true },
)

// 기간을 직접 바꾸면 강조하던 구간과 주소의 시각을 지움
function clearFocus() {
  focusAt.value = ''
  if (route.query.at) router.replace({ query: {} })
}

// ---- 표의 줄: 15분 구간 하나 = 한 줄 ----
// 백엔드 시각을 '2026-11-19T14:15' 모양 키로 맞춤 (초 표기가 달라도 같은 구간으로 보게)
const keyOf = (dateTime) => dateTime.slice(0, 16)

const allRows = computed(() => {
  if (!period.value) return []
  const target = historyStore.powerTargetKw

  // 구간 키 → 값 찾기 표
  const pointByKey = {}
  for (const item of historyStore.powerPoints) pointByKey[keyOf(item.recordedAt)] = item
  const exceededKeys = new Set(historyStore.powerExceeded.map((item) => keyOf(item.windowStart)))
  const periodByHour = {}
  for (const item of historyStore.powerHourly) periodByHour[item.hour] = item.period
  const equipmentByKey = {}
  for (const item of historyStore.equipmentPowers) {
    const key = keyOf(item.windowStart)
    if (!equipmentByKey[key]) equipmentByKey[key] = []
    equipmentByKey[key].push(item) // 백엔드가 많이 쓴 순으로 줌
  }

  // 그 15분 구간을 대상으로 한 추천 (여러 개면 가장 나중에 나온 것)
  const decisionByKey = {}
  for (const item of historyStore.powerDecisions) {
    const key = keyOf(item.windowStart)
    if (!decisionByKey[key] || decisionByKey[key].decidedAt < item.decidedAt)
      decisionByKey[key] = item
  }
  const weatherByDate = {}
  for (const item of historyStore.powerWeather) weatherByDate[item.date] = item.text

  const rows = []
  let stage = null // 그 구간이 끝날 때의 피크 단계 (그때까지 나온 마지막 단계 변화)
  const events = historyStore.powerEvents // 오래된 순
  let eventIndex = 0
  for (let start = period.value.from; start < period.value.to; start = addMinutes(start, 15)) {
    const end = addMinutes(start, 15)
    const key = keyOf(start)
    // 이 구간 안의 이벤트 모으기
    const inWindow = []
    while (eventIndex < events.length && events[eventIndex].occurredAt < end) {
      const event = events[eventIndex]
      if (event.eventType === 'STAGE_CHANGE') stage = event.level
      if (event.occurredAt >= start) inWindow.push(event)
      eventIndex++
    }
    const point = pointByKey[key] ?? {}
    const grid = point.gridKw ?? null
    rows.push({
      start,
      end,
      exceeded: exceededKeys.has(key),
      loadKw: point.loadKw ?? null,
      gridKw: grid,
      targetPercent: grid != null && target ? Math.round((grid / target) * 100) : null,
      solarKw: point.solarKw ?? null,
      essKw: point.essKw ?? null,
      socPercent: point.socPercent ?? null,
      roomTempC: point.roomTempC ?? null,
      outsideTempC: point.outsideTempC ?? null,
      period: PERIOD_LABELS[periodByHour[Number(start.slice(11, 13))]] ?? null,
      stage,
      weather: weatherByDate[start.slice(0, 10)] ?? null,
      equipments: equipmentByKey[key] ?? [],
      decision: decisionByKey[key] ?? null,
      events: inWindow,
      hasRecord: point.gridKw != null || point.loadKw != null,
    })
  }
  return rows
})

const rows = computed(() =>
  allRows.value.filter((row) => {
    if (isFocus(row)) return true // 추이 그래프 점에서 고른 구간은 조건과 상관없이 항상 보임
    if (rowFilter.value === 'EXCEEDED') return row.exceeded
    if (rowFilter.value === 'EVENT') return row.events.length > 0
    return true
  }),
)

const isFocus = (row) => row.start === focusAt.value

// 조건 초기화: 오늘 · 이벤트 구간
function resetFilters() {
  rowFilter.value = 'EVENT'
  clearFocus()
  applyPreset('today')
}

// ---- 표의 칸 (화면 표와 CSV가 같이 씀) ----
// 칸 하나 = { label: 머리글, right: 숫자면 오른쪽 정렬, text(row): 칸에 적을 글자, className(row): 색 }
const kw = (value) => (value == null ? '' : formatKw(value))
const kwh = (value) => (value == null ? '' : formatOneDecimal(value / 4)) // 15분 평균 kW × 0.25시간
const one = (value) => (value == null ? '' : formatOneDecimal(value))
const essState = (value) => {
  if (value == null) return ''
  if (value > 0.5) return '방전'
  if (value < -0.5) return '충전'
  return '대기'
}
const eventCount = (row, type) => row.events.filter((item) => item.eventType === type).length

// 기간에 나온 구역 · 설비 목록 (구역별 칸, 설비별 칸을 만들 때 씀)
const zones = computed(() => {
  const result = {}
  for (const item of historyStore.equipmentPowers) {
    result[item.zoneCode] = item.zoneName ?? item.zoneCode
  }
  return Object.keys(result)
    .sort()
    .map((code) => ({ code, name: result[code] }))
})
const equipmentList = computed(() => {
  const result = {}
  for (const item of historyStore.equipmentPowers) {
    result[item.equipmentId] = item
  }
  return Object.values(result).sort((a, b) =>
    `${a.zoneCode}${a.equipmentId}`.localeCompare(`${b.zoneCode}${b.equipmentId}`),
  )
})
const zoneKw = (row, code) => {
  const list = row.equipments.filter((item) => item.zoneCode === code)
  return list.length ? list.reduce((sum, item) => sum + item.avgKw, 0) : null
}
const equipmentKw = (row, id) => row.equipments.find((item) => item.equipmentId === id)?.avgKw

const columns = computed(() => [
  {
    label: '15분 구간',
    text: (row) =>
      `${row.start.slice(5, 10)} ${formatHourMinute(row.start)}~${formatHourMinute(row.end)}`,
  },
  {
    label: '피크 초과',
    text: (row) => (row.exceeded ? '■ 초과' : ''),
    className: (row) => (row.exceeded ? 'crit' : ''),
  },
  {
    label: '한전 구매 kW',
    right: true,
    text: (row) => kw(row.gridKw),
    className: (row) => (row.exceeded ? 'crit' : ''),
  },
  { label: '한전 구매 kWh', right: true, text: (row) => kwh(row.gridKw) },
  {
    label: '목표 대비 %',
    right: true,
    text: (row) => (row.targetPercent == null ? '' : row.targetPercent),
    className: (row) => (row.exceeded ? 'crit' : row.targetPercent >= 90 ? 'warn' : ''),
  },
  { label: '공장 소비 kW', right: true, text: (row) => kw(row.loadKw) },
  { label: '공장 소비 kWh', right: true, text: (row) => kwh(row.loadKw) },
  { label: '태양광 kW', right: true, text: (row) => kw(row.solarKw) },
  { label: '태양광 kWh', right: true, text: (row) => kwh(row.solarKw) },
  { label: 'ESS 상태', text: (row) => essState(row.essKw) },
  {
    label: 'ESS kW (+방전/−충전)',
    right: true,
    text: (row) => (row.essKw == null ? '' : formatKw(row.essKw)),
  },
  {
    label: '충전 kWh',
    right: true,
    text: (row) => (row.essKw != null && row.essKw < 0 ? kwh(-row.essKw) : ''),
  },
  {
    label: '방전 kWh',
    right: true,
    text: (row) => (row.essKw != null && row.essKw > 0 ? kwh(row.essKw) : ''),
  },
  { label: '배터리 잔량 %', right: true, text: (row) => one(row.socPercent) },
  { label: '배터리실 온도 °C', right: true, text: (row) => one(row.roomTempC) },
  { label: '바깥 기온 °C', right: true, text: (row) => one(row.outsideTempC) },
  { label: '날씨', text: (row) => row.weather ?? '' },
  {
    label: '요금 시간대',
    text: (row) => row.period ?? '',
    className: (row) => (row.period === '최대부하' ? 'warn' : ''),
  },
  {
    label: '피크 단계',
    text: (row) =>
      LEVEL_INFO[row.stage] ? `${LEVEL_INFO[row.stage].shape} ${LEVEL_INFO[row.stage].label}` : '',
    className: (row) => LEVEL_INFO[row.stage]?.className ?? '',
  },
  {
    label: '추천',
    text: (row) => {
      const item = row.decision
      if (!item) return ''
      const power =
        item.action === 'IDLE' || item.powerKw == null ? '' : ` ${formatKw(item.powerKw)} kW`
      return `${ACTION_LABELS[item.action] ?? item.action}${power}`
    },
  },
  { label: '추천 규칙', text: (row) => row.decision?.ruleLabel ?? '' },
  { label: '예상 피크 kW', right: true, text: (row) => kw(row.decision?.predictedKw ?? null) },
  {
    label: '처리',
    text: (row) => STATUS_LABELS[row.decision?.status] ?? row.decision?.status ?? '',
  },
  { label: '처리자', text: (row) => row.decision?.appliedBy ?? '' },
  {
    label: '처리 시각',
    text: (row) => (row.decision?.appliedAt ? formatHourMinute(row.decision.appliedAt) : ''),
  },
  { label: '실제 피크 kW', right: true, text: (row) => kw(row.decision?.resultPeakKw ?? null) },
  {
    label: '목표 달성',
    text: (row) => RESULT_LABELS[row.decision?.resultStatus] ?? '',
    className: (row) => (row.decision?.resultStatus === 'EXCEEDED' ? 'crit' : ''),
  },
  { label: '단계 변화 수', right: true, text: (row) => eventCount(row, 'STAGE_CHANGE') || '' },
  { label: '추천 이벤트 수', right: true, text: (row) => eventCount(row, 'RECOMMEND') || '' },
  { label: '처리 이벤트 수', right: true, text: (row) => eventCount(row, 'HANDLE') || '' },
  {
    label: '이벤트 내용',
    wide: true,
    text: (row) =>
      row.events
        .map(
          (item) =>
            `${formatHourMinute(item.occurredAt)} [${EVENT_LABELS[item.eventType] ?? item.eventType}] ${item.message}`,
        )
        .join(' / '),
  },
  // 구역별 합계 kW (예: Z2 브레이징 kW)
  ...zones.value.map((zone) => ({
    label: `${zone.code} ${zone.name} kW`,
    right: true,
    text: (row) => kw(zoneKw(row, zone.code)),
  })),
  // 설비별 kW (예: Z2 브레이징로 kW)
  ...equipmentList.value.map((item) => ({
    label: `${item.zoneCode} ${item.name} kW`,
    right: true,
    text: (row) => kw(equipmentKw(row, item.equipmentId) ?? null),
  })),
])

const cellClass = (column, row) => [
  column.right ? 'num right' : '',
  column.wide ? 'wide' : '',
  column.className ? column.className(row) : '',
]

// 위쪽 요약 숫자
const counts = computed(() => [
  { label: '구간', value: allRows.value.length },
  { label: '피크 초과', value: allRows.value.filter((row) => row.exceeded).length },
  { label: '이벤트', value: historyStore.powerEvents.length },
])

// ---- 엑셀 받기 ----
// 지금 표에 보이는 줄(거른 결과)과 칸을 엑셀에서 열 수 있는 CSV 파일로 만듦 (라이브러리 없이). 엑셀에서 한글이 깨지지 않게 맨 앞에 BOM(\uFEFF)을 붙임
function downloadExcel() {
  const header = columns.value.map((column) => column.label)
  const lines = rows.value.map((row) => columns.value.map((column) => column.text(row)))
  // 칸 안에 쉼표 · 따옴표 · 줄바꿈이 있어도 깨지지 않게 큰따옴표로 감쌈
  const cell = (value) => `"${String(value).replaceAll('"', '""')}"`
  const csv = [header, ...lines].map((line) => line.map(cell).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  // 파일 받기는 화면 이동이 아니라서 RouterLink 대신 임시 링크를 만들어 누름
  const link = document.createElement('a')
  link.href = url
  link.download = `전력이력_${fromDate.value}_${toDate.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

// 받은 뒤 고른 구간의 줄이 보이게 표를 내림
const tableWrap = ref(null)
watch(
  () => historyStore.powerLoading,
  async (loading) => {
    if (loading) return
    await nextTick()
    tableWrap.value?.querySelector('.focus')?.scrollIntoView({ block: 'center' })
  },
)
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="crumb">
        <span class="crumb-parent">운영 로그</span>
        <span class="crumb-parent" aria-hidden="true">›</span>
        <h1 class="crumb-title">전력 이력</h1>
      </div>
      <!-- 운영 로그의 두 탭 (추천 이력 / 전력 이력) -->
      <nav class="log-tabs" aria-label="운영 로그 탭">
        <RouterLink to="/logs" class="tab">추천 이력</RouterLink>
        <RouterLink to="/logs/power" class="tab active" aria-current="page">전력 이력</RouterLink>
      </nav>
      <ul class="counts">
        <li v-for="item in counts" :key="item.label">
          {{ item.label }} <strong class="num">{{ item.value }}</strong>
        </li>
      </ul>
      <div class="head-buttons">
        <button class="reset" :disabled="!rows.length" @click="downloadExcel">엑셀 받기</button>
        <button class="reset" @click="resetFilters">조건 초기화</button>
      </div>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button
            :class="{ active: preset === 'today' }"
            :aria-pressed="preset === 'today'"
            @click="(clearFocus(), applyPreset('today'))"
          >
            오늘
          </button>
          <button
            :class="{ active: preset === 'week' }"
            :aria-pressed="preset === 'week'"
            @click="(clearFocus(), applyPreset('week'))"
          >
            최근 7일
          </button>
        </div>
        <input
          v-model="fromDate"
          type="date"
          class="input num"
          aria-label="시작일"
          @change="((preset = 'custom'), clearFocus())"
        />
        <span aria-hidden="true">~</span>
        <input
          v-model="toDate"
          type="date"
          class="input num"
          aria-label="종료일"
          @change="((preset = 'custom'), clearFocus())"
        />
      </div>
      <div class="field">
        <span class="field-label">보기</span>
        <div class="presets" role="group" aria-label="보기">
          <button
            v-for="item in ROW_FILTERS"
            :key="item.value"
            :class="{ active: rowFilter === item.value }"
            :aria-pressed="rowFilter === item.value"
            @click="rowFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <p v-if="dateError" class="date-error" role="alert">{{ dateError }}</p>
    </section>

    <section class="card table-card">
      <p v-if="historyStore.powerError" class="notice" role="alert">
        전력 이력을 불러오지 못했습니다. ({{ historyStore.powerError }})
        <button class="retry" @click="load()">다시 시도</button>
      </p>
      <p v-if="historyStore.equipmentError" class="notice" role="alert">
        설비별 전력을 불러오지 못해 구역별 · 설비별 kW 칸이 비어 있습니다. ({{
          historyStore.equipmentError
        }})
      </p>

      <div v-if="historyStore.powerLoading" class="empty spinner-box"><LoadingSpinner /></div>
      <div v-else-if="!fromDate" class="empty spinner-box">
        <LoadingSpinner text="서버에서 현재 시각을 받으면 오늘 기록을 조회합니다." />
      </div>
      <p v-else-if="!rows.length && !historyStore.powerError" class="empty">
        조건에 맞는 구간이 없습니다. 기간이나 보기를 바꿔 보세요.
      </p>

      <div v-else-if="rows.length" ref="tableWrap" class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th v-for="column in columns" :key="column.label" :class="{ right: column.right }">
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.start"
              :class="{ focus: isFocus(row), 'no-record': !row.hasRecord }"
            >
              <td
                v-for="column in columns"
                :key="column.label"
                :class="cellClass(column, row)"
                :title="column.wide ? column.text(row) : undefined"
              >
                {{ column.text(row) }}
              </td>
            </tr>
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
.head-buttons {
  display: flex;
  gap: 8px;
  margin-left: auto;
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

/* 운영 로그 탭 (추천 이력 화면에도 같은 모양이 있음) */
.log-tabs {
  display: flex;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  overflow: hidden;
}
.log-tabs .tab {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  background: var(--card);
  color: var(--ink-2);
}
.log-tabs .tab + .tab {
  border-left: 1px solid var(--line-strong);
}
.log-tabs .tab:hover {
  background: var(--bg);
}
.log-tabs .tab.active {
  background: var(--ink);
  font-weight: 700;
  color: var(--card);
}

/* 조회 조건 */
.date-error {
  width: 100%;
  color: var(--crit);
}
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
.input {
  height: 34px;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink);
}
.reset {
  height: 34px;
  padding: 0 12px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink-2);
}
.reset:hover {
  background: var(--bg);
}
.reset:disabled {
  opacity: 0.5;
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
  padding: 10px 10px;
  background: var(--bg);
}
.table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}
.table .right {
  text-align: right;
}
/* 이벤트 내용 칸은 길어서 넘치면 말줄임 (마우스를 올리면 전체가 보임, CSV에는 전체가 들어감) */
.table td.wide {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 고른 15분 구간의 줄 */
.table tr.focus td {
  background: var(--pressed);
  font-weight: 700;
}
/* 기록이 없는 구간 (시뮬레이터가 꺼져 있던 시간 등) */
.table tr.no-record td {
  color: var(--faint);
}
.crit {
  color: var(--crit);
}
.warn {
  color: var(--warn);
}
.ok {
  color: var(--ok);
}
/* 불러오는 중: 이 칸을 덮는 공통 스피너(LoadingSpinner)를 칸 안에 둠 (통합 화면과 같은 모양) */
.spinner-box {
  position: relative;
  min-height: 160px;
}
</style>
