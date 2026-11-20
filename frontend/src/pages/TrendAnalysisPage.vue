<!--
  분석 > 추이 분석
  조회 조건 → 기간 요약 → 왼쪽에 전력 그래프 + 그 아래 일별 최대 피크 (배터리 잔량 · 온도 그래프는 2026-11-20에 뺌)
  → 오른쪽에 시간대별 사용 패턴 · 피크가 높았던 구간 (추천 처리별 결과 · 눈에 띄는 구간 카드는 2026-11-20에 뺌)

  요약 숫자, 시간대별 평균은 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import FitChart from '@/components/FitChart.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { useAnalysisStore } from '@/stores/useAnalysisStore'
import { useHistoryStore } from '@/stores/useHistoryStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import {
  formatHourMinute,
  formatInteger,
  formatKw,
  formatMonthDayTime,
  formatOneDecimal,
} from '@/utils/format'

const analysisStore = useAnalysisStore()
const historyStore = useHistoryStore() // 그래프 위 추천 핀용. 추천 이력 화면과 같은 API를 씀
const realtimeStore = useRealtimeStore()
const router = useRouter()

// 그래프 간격은 1시간으로 고정 (2026-11-20, 간격 고르기를 뺌).
// 1일로 묶으면 하루 중 피크가 평균에 묻히고, 15분은 계절(3~4달) 단위로 보면 점이 너무 많음
const INTERVAL = 'HOUR'
// 기간 빠른 선택. 고르면 옆 날짜 칸이 그 기간으로 채워짐
// 하절기 · 동절기는 한전 요금 계절 기준 (백엔드 seasonOf와 같은 기준). title은 버튼에 마우스를 올리면 보임
const PRESETS = [
  { value: 'today', label: '오늘' },
  { value: 'week', label: '최근 7일' },
  { value: 'month', label: '이번 달' },
  { value: 'summer', label: '하절기', title: '올해 6~8월' },
  { value: 'winter', label: '동절기', title: '작년 11월 ~ 올해 2월' },
]
// "보기"에서 켜고 끌 수 있는 항목
const ITEMS = [
  { key: 'load', label: '공장 소비' },
  { key: 'grid', label: '한전 구매' },
  { key: 'solar', label: '태양광' },
  { key: 'ess', label: 'ESS 충·방전' },
]
// 처리 상태는 색만으로 구분하지 않도록 도형 + 글자로 표시 (운영 로그 화면과 같은 표시)
const STATUS_INFO = {
  APPLIED: { shape: '●', label: '적용' },
  AUTO: { shape: '◇', label: '자동' },
  IGNORED: { shape: '✕', label: '넘김' },
}
// 그래프 위 추천 핀: 위의 처리 상태에 "추천 중"(아직 처리 전)을 더함
const PIN_INFO = { ...STATUS_INFO, RECOMMENDED: { shape: '○', label: '추천 중' } }

const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
// 요금 시간대 이름 (순위 보기에서 시각 옆에 적음)
const PERIOD_LABELS = { PEAK: '최대부하', MID: '중간부하', LIGHT: '경부하' }

// ---- 조회 조건 ----
// today(오늘) | week(최근 7일) | month(이번 달) | summer(하절기) | winter(동절기) | custom(직접 입력)
const preset = ref('week')
const fromDate = ref('') // 예: '2026-11-10'
const toDate = ref('')
const shown = ref(ITEMS.map((item) => item.key)) // 켜져 있는 항목들
// 전력 그래프에 세로선으로 표시할 시각. 위쪽 추천 기호(점선)나 "피크가 높았던 구간" 표의 줄(실선)을 눌러 고름
const focusAt = ref('')
const focusNote = ref('') // 표에서 고른 구간의 내용 (세로선 위에 적음). 추천 기호로 고른 칸이면 ''
const selectedWindow = ref('') // "피크가 높았던 구간" 표에서 고른 줄의 key
const hourlyView = ref('time') // 시간대별 사용 패턴 보기: time(시간 순 막대) | rank(많이 쓴 순위)
// 시간대별 사용 패턴의 막대 간격: AUTO(기간에 맞춰 자동) | HOUR(1시간) | HOUR4(4시간) | DAY(1일)
const patternStep = ref('AUTO')
const showPins = ref(true) // 전력 그래프 위에 추천 기호를 보일지

// "오늘"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함 (시뮬레이터가 배속으로 돌 수 있음)
const serverDate = computed(() => realtimeStore.status?.recordedAt.slice(0, 10) ?? '')

// 백엔드가 한 번에 조회해 주는 최대 기간 (넘으면 400을 줌). 동절기(최대 121일)가 들어가게 백엔드와 같이 늘림
const MAX_DAYS = 121
// 일별 최대 피크(피크 분석 API)는 백엔드가 62일까지만 받음. 기간이 이보다 길면 그 그래프만 숨김
const PEAK_MAX_DAYS = 62

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
  // 요청을 보내기 전에 막음 (보내면 400이 나고 [다시 시도]를 눌러도 계속 실패함 — 이슈 #25)
  if (dayCount(fromDate.value, toDate.value) > MAX_DAYS) {
    return `기간은 최대 ${MAX_DAYS}일까지 조회할 수 있습니다.`
  }
  return ''
})

// 기간이 일별 최대 피크를 받을 수 있는 길이(62일)를 넘는지
const peakTooLong = computed(
  () =>
    !!fromDate.value && !!toDate.value && dayCount(fromDate.value, toDate.value) > PEAK_MAX_DAYS,
)

// '2026-11-14'에서 며칠 전 날짜 구하기
function daysBefore(date, days) {
  const [year, month, day] = date.split('-').map(Number)
  const result = new Date(year, month - 1, day - days)
  const pad = (n) => String(n).padStart(2, '0')
  return `${result.getFullYear()}-${pad(result.getMonth() + 1)}-${pad(result.getDate())}`
}

// 올해(서버 날짜의 연도) 기준 계절의 시작일 ~ 종료일
// - 하절기: 올해 6월 1일 ~ 8월 31일
// - 동절기: 작년 11월 1일 ~ 올해 2월 말일 (한전 요금 계절 · 백엔드 seasonOf와 같은 기준)
// 계절이 진행 중이면 종료일은 오늘 (아직 오지 않은 날까지 넣으면 지금까지의 기록이 왼쪽에 좁게 몰림)
// 예: 오늘 2026-11-20 → 하절기 2026-06-01 ~ 2026-08-31, 동절기 2025-11-01 ~ 2026-02-28
function seasonRange(name, today) {
  const year = Number(today.slice(0, 4))
  const from = name === 'summer' ? `${year}-06-01` : `${year - 1}-11-01`
  // 동절기 끝: 올해 3월 1일의 하루 전 (윤년이면 2월 29일)
  const to = name === 'summer' ? `${year}-08-31` : daysBefore(`${year}-03-01`, 1)
  const inProgress = from <= today && today < to
  return { from, to: inProgress ? today : to }
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  if (name === 'summer' || name === 'winter') {
    const range = seasonRange(name, serverDate.value)
    fromDate.value = range.from
    toDate.value = range.to
    return
  }
  toDate.value = serverDate.value
  if (name === 'today') fromDate.value = serverDate.value
  if (name === 'week') fromDate.value = daysBefore(serverDate.value, 6)
  if (name === 'month') fromDate.value = `${serverDate.value.slice(0, 8)}01` // 이번 달 1일
}

function load(showLoading = true) {
  if (!fromDate.value || !toDate.value || dateError.value) return
  // 간격이 1시간이라, 스토어가 이 결과를 시간대별 사용 패턴에도 그대로 넣어 줌 (따로 받지 않음 — 이슈 #25)
  analysisStore.fetchTrend(fromDate.value, toDate.value, INTERVAL, showLoading)
  // 같은 기간의 추천 이력도 받아 그래프 위에 핀으로 꽂음
  historyStore.fetchDecisions(fromDate.value, toDate.value, showLoading)
  // 전력 그래프 아래 "일별 최대 피크" 그래프용 (피크 분석 화면과 같은 API)
  if (!peakTooLong.value) analysisStore.fetchPeak(fromDate.value, toDate.value, showLoading)
}

// 기간이 바뀌면 다시 조회
watch([fromDate, toDate], () => {
  clearFocus()
  load()
})

// 서버 날짜를 처음 알게 되면 "최근 7일"로 조회 시작
watch(
  serverDate,
  (date) => {
    if (date && !fromDate.value) applyPreset('week')
  },
  { immediate: true },
)

// 조회 기간에 오늘이 들어 있으면, 15분 구간이 바뀔 때마다 조용히 다시 받음
watch(
  () => realtimeStore.status?.windowStart,
  (now, before) => {
    if (now && before && toDate.value >= serverDate.value) load(false)
  },
)

// ---- 받은 결과 ----
const trend = computed(() => analysisStore.trend)

// ---- 기록이 없는 시간도 그래프 칸으로 남기기 ----
// 백엔드는 기록이 있는 칸만 줌. 그대로 그리면 기록 없는 날(예: 시뮬레이터가 꺼져 있던 날)이 가로축에서 사라지고
// 앞뒤 날짜가 붙어 보였음 (11-17 오후 바로 뒤에 11-19 값이 이어져, 11-17에 값이 떨어진 것처럼 보임).
// 그래서 조회 기간의 모든 칸을 만들고, 기록이 없는 칸은 값을 비워 둠 (선이 끊기고 빈칸으로 보임)
const STEP_MINUTES = { QUARTER: 15, HOUR: 60, DAY: 24 * 60 } // 간격별 칸 크기(분)
const pad2 = (number) => String(number).padStart(2, '0')

// Date → '2026-11-19T14:30:00' (백엔드 시각과 같은 모양)
function toDateTime(date) {
  const day = `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
  return `${day}T${pad2(date.getHours())}:${pad2(date.getMinutes())}:00`
}

// 시각이 들어가는 칸의 시작 시각. 예: 15분 간격이면 '…T14:42:00' → '…T14:30:00'
function slotStartOf(dateTime, interval) {
  if (interval === 'DAY') return `${dateTime.slice(0, 10)}T00:00:00`
  if (interval === 'HOUR') return `${dateTime.slice(0, 13)}:00:00`
  const minute = Math.floor(Number(dateTime.slice(14, 16)) / 15) * 15
  return `${dateTime.slice(0, 14)}${pad2(minute)}:00`
}

// 조회 기간(시작일 0시 ~ 종료일 끝)의 모든 칸에 받은 기록을 놓음. 기록이 없는 칸은 { recordedAt }만 있음
function fillSlots(list, from, to, interval) {
  const step = STEP_MINUTES[interval]
  if (!list.length || !from || !to || !step) return list
  const byStart = {}
  for (const item of list) byStart[slotStartOf(item.recordedAt, interval)] = item
  const slots = []
  const end = new Date(`${to}T23:59:59`).getTime()
  for (let time = new Date(`${from}T00:00:00`).getTime(); time <= end; time += step * 60000) {
    const start = toDateTime(new Date(time))
    slots.push(byStart[start] ?? { recordedAt: start })
  }
  return slots
}

// 그래프에 쓰는 칸 목록 (받은 기록이 하나도 없으면 빈 목록 → "기록이 없습니다" 안내)
const points = computed(() =>
  fillSlots(trend.value?.points ?? [], trend.value?.from, trend.value?.to, trend.value?.interval),
)
const summary = computed(() => trend.value?.summary ?? null)

// 목표 피크. 설정 값을 못 찾으면 백엔드가 null로 줌 → 그때는 목표선과 "목표 초과/달성"을 그리지 않음
const targetKw = computed(() => trend.value?.targetPeakKw ?? null)

// 목표 피크를 넘었는지 (목표 값이 없으면 넘었다고 하지 않음)
const isOverTarget = (kw) => targetKw.value !== null && kw > targetKw.value

// 최대 피크 아래 줄의 판정. 목표 값이 없으면 판정 없이 시각만 보여 줌
const maxPeakVerdict = computed(() => {
  // 최대 피크 값이 없을 때도 판정하지 않음 (null은 목표보다 작다고 계산되어 "목표 달성"으로 보였음 — 이슈 #25)
  if (targetKw.value === null || summary.value?.maxPeakKw == null) return ''
  return isOverTarget(summary.value.maxPeakKw) ? ', 목표 초과' : ', 목표 달성'
})

// 한전 평균 구매전력이 목표 피크의 몇 %인지 (표시용 비율)
const avgGridPercent = computed(() =>
  summary.value && targetKw.value ? (summary.value.avgGridKw / targetKw.value) * 100 : null,
)

// '2026-11-11T09:30:00' → '11-11 09:30'
const shortTime = (dateTime) => formatMonthDayTime(dateTime).slice(0, 11)

function clearFocus() {
  focusAt.value = ''
  focusNote.value = ''
  selectedWindow.value = ''
}
// 위쪽 추천 기호를 누름: 그 칸에 점선 (같은 칸을 다시 누르면 지움)
function toggleFocus(at) {
  const same = focusAt.value === at && !selectedWindow.value
  clearFocus()
  if (!same) focusAt.value = at
}
// "피크가 높았던 구간" 표의 줄을 누름: 그 시각에 실선 + 구간 내용 (같은 줄을 다시 누르면 지움)
function toggleWindow(key, at, note) {
  const same = selectedWindow.value === key
  clearFocus()
  if (same) return
  selectedWindow.value = key
  focusAt.value = at
  focusNote.value = note
}
// 세로선 위에 적는 글자.
// 표에서 고른 구간이면 그 내용, 추천 기호로 고른 칸이면 "● 10:00 방전 500 kW" (추천이 여러 건이면 첫 건 + 외 N건)
const focusText = computed(() => {
  if (focusNote.value) return focusNote.value
  const pins = focusLabel.value ? pinsByLabel.value[focusLabel.value] : null
  if (!pins) return ''
  const first = pins[0]
  const power = first.action === 'IDLE' ? '' : ` ${formatKw(first.powerKw)} kW`
  const more = pins.length > 1 ? ` 외 ${pins.length - 1}건` : ''
  return `${PIN_INFO[first.status].shape} ${formatHourMinute(first.decidedAt)} ${ACTION_LABELS[first.action]}${power}${more}`
})

// ---- 전력 그래프의 추천 기호 누르기 ----
// 그래프는 공통 차트(Chart.vue)가 만들므로, 여기서는 이미 만들어진 그래프를 찾아 마우스 알림만 연결함
// (예전에는 배터리 잔량 · 온도 그래프와 마우스 점선을 맞추는 연결도 했으나, 두 그래프를 없애 빠짐 — 2026-11-20)
const chartsBox = ref(null)

// 지금 화면에 있는 그래프 (전력 그래프 하나)
function chartInstances() {
  return [...(chartsBox.value?.querySelectorAll('[_echarts_instance_]') ?? [])]
    .map((element) => echarts.getInstanceByDom(element))
    .filter((instance) => instance)
}

// 마우스가 그래프 영역에 들어올 때마다 연결 (그래프를 새로 만들어도 다시 연결되도록, 이미 연결한 그래프는 건너뜀)
function linkCharts() {
  for (const instance of chartInstances()) {
    if (instance.trendLinked) continue
    instance.trendLinked = true
    // 전력 그래프 위쪽의 추천 기호: 마우스를 올리면 손가락 모양, 누르면 그 칸에 점선을 고정하고 값을 보여 줌
    instance.getZr().on('mousemove', (event) => {
      if (pinIndexAt(instance, event) !== null) instance.getZr().setCursorStyle('pointer')
    })
    // 피크 초과 점(●)을 누르면 전력 이력 화면으로 이동 (팀원 커밋 78fe23a에서 가져옴)
    instance.on('click', { seriesName: EXCEED_NAME }, (event) => {
      if (event.data?.top) openPowerHistory(event.data.top)
    })
    instance.getZr().on('click', (event) => {
      const index = pinIndexAt(instance, event)
      if (index === null) return
      toggleFocus(points.value[index].recordedAt)
      // 그 칸의 툴팁(값 + 추천 목록)을 띄움. 보이지 않는 선(맨 마지막 계열)은 모든 칸에 값이 있어 항상 뜸
      const seriesIndex = (instance.getOption().series?.length ?? 1) - 1
      instance.dispatchAction({ type: 'showTip', seriesIndex, dataIndex: index })
    })
  }
}

// ---- 피크 초과 점 (●) (팀원 커밋 78fe23a "추이 분석 그래프에 피크 초과 점 추가"를 이 화면에 맞춰 가져옴) ----
// 피크 분석 API의 15분 기준 초과 구간(exceededWindows)을 그래프 칸에 놓음. 한 칸(1시간)에 여러 번 넘었으면 가장 높은 값에 점 하나
// 그래프 면(1시간 평균)이 아니라 15분 값이라, 점이 면보다 위에 떠 보일 수 있음 (짧게 튄 피크가 평균에 묻힌 것)
// 이 화면은 일별 최대 피크용으로 피크 분석 결과(peak)를 이미 받으므로, 같은 API를 한 번 더 부르지 않고 그 결과를 씀
const EXCEED_NAME = '피크 초과 (15분)'
const exceededBySlot = computed(() => {
  const result = {} // 예: { '2026-11-19T14:00:00': [구간, 구간] }
  const interval = trend.value?.interval
  if (!interval) return result
  for (const item of peak.value?.exceededWindows ?? []) {
    const slot = slotStartOf(item.windowStart, interval)
    if (!result[slot]) result[slot] = []
    result[slot].push(item)
  }
  return result
})

function exceedSeries() {
  return {
    name: EXCEED_NAME,
    type: 'scatter',
    symbolSize: 9,
    z: 5,
    itemStyle: { color: COLOR.crit, borderColor: COLOR.card, borderWidth: 1 },
    cursor: 'pointer',
    // 칸마다 그 칸에서 가장 높았던 초과 구간 (없으면 비움). top · count는 클릭 · 툴팁용으로 같이 둠
    data: points.value.map((item) => {
      const list = exceededBySlot.value[slotStartOf(item.recordedAt, trend.value.interval)]
      if (!list) return null
      const top = list.reduce((a, b) => (b.peakKw > a.peakKw ? b : a))
      return { value: top.peakKw, top, count: list.length }
    }),
  }
}

// 점을 누르면 운영 로그 > 전력 이력으로 이동 (그 15분 구간 시각을 주소에 담아 감)
function openPowerHistory(top) {
  router.push({ path: '/logs/power', query: { at: top.windowStart } })
}

// 마우스 위치가 전력 그래프 위쪽 기호 띠의 추천 기호 위면 그 칸 번호, 아니면 null
// (기호는 그래프 칸 맨 위 바로 위, POWER_GRID_TOP 위쪽 20px 띠에 적힘. 기호가 작아서 좌우 2칸까지는 같은 기호로 봄)
function pinIndexAt(instance, event) {
  if (instance !== chartInstances()[0] || !showPins.value) return null
  if (event.offsetY > POWER_GRID_TOP || event.offsetY < POWER_GRID_TOP - 20) return null
  const center = instance.convertFromPixel({ xAxisIndex: 0 }, event.offsetX)
  if (typeof center !== 'number' || Number.isNaN(center)) return null
  for (const step of [0, -1, 1, -2, 2]) {
    const index = Math.round(center) + step
    if (pinsByLabel.value[labels.value[index]]) return index
  }
  return null
}

// ---- 그래프 공통 ----
// 그래프(ECharts)는 CSS 변수를 못 읽으므로, 테마 스토어가 global.css에서 읽어 둔 지금 테마 색을 씀
// (라이트·다크를 바꾸면 이 색이 바뀌고 그래프도 다시 그려짐)
const themeStore = useThemeStore()
const COLOR = themeStore.colors
// 그래프 툴팁도 지금 테마 색으로
const tooltipColors = () => ({
  // 툴팁 창을 페이지 맨 위(body)에 붙여, 카드·그래프 칸 밖으로 나가도 잘리지 않게 함
  appendTo: 'body',
  backgroundColor: COLOR.card,
  borderColor: COLOR.line,
  textStyle: { color: COLOR.ink },
})

const isDaily = computed(() => trend.value?.interval === 'DAY') // 하루에 점 하나
const isSingleDay = computed(() => trend.value?.from === trend.value?.to) // 하루만 조회

// 가로축 글자: 1일 간격이면 '11-10', 그 밖에는 '11-10 09:00'
const labels = computed(() =>
  points.value.map((item) =>
    isDaily.value ? item.recordedAt.slice(5, 10) : shortTime(item.recordedAt),
  ),
)

// 날짜('11-10') → 몇 번째 날인지
// 날짜 순번은 그래프 가로축 칸(labels)의 날짜에서 구함.
// (날씨 목록에서 구하면, 날씨 기록이 없는 기간에는 순번이 비어 가로축 날짜가 모두 사라졌음 — 이슈 #25)
const dayNumbers = computed(() => {
  const result = {}
  let count = 0
  for (const label of labels.value) {
    const date = label.slice(0, 5) // '11-10'
    if (!(date in result)) result[date] = count++
  }
  return result
})
// 날짜가 많으면 가로축 글자가 겹치므로 며칠에 하나씩만 적음
const dayStep = computed(() => Math.ceil(Object.keys(dayNumbers.value).length / 10) || 1)

// 전력 그래프의 가로축 (시각)
function timeAxis() {
  return {
    type: 'category',
    data: labels.value,
    axisLine: { lineStyle: { color: COLOR.line } },
    axisTick: { show: false },
    axisLabel: {
      color: COLOR.mute,
      // 어느 칸에 글자를 적을지
      interval: (index, value) => {
        const date = value.slice(0, 5)
        if (isSingleDay.value && !isDaily.value) {
          return value.endsWith(':00') && Number(value.slice(6, 8)) % 3 === 0 // 3시간마다
        }
        const startOfDay = isDaily.value || value.endsWith(' 00:00')
        return startOfDay && dayNumbers.value[date] % dayStep.value === 0
      },
      formatter: (value) => {
        if (isSingleDay.value && !isDaily.value) return value.slice(6) // 하루만 보면 시각
        return value.slice(0, 5) // 날짜
      },
    },
    // 날짜가 바뀌는 곳에 세로선. 글자를 적는 날에만 그음
    // (계절처럼 기간이 길면 매일 그은 선이 빗금처럼 촘촘해져 그래프를 가렸음)
    splitLine: {
      show: !isDaily.value && !isSingleDay.value,
      interval: (index, value) =>
        value.endsWith(' 00:00') && dayNumbers.value[value.slice(0, 5)] % dayStep.value === 0,
      lineStyle: { color: COLOR.line },
    },
  }
}

const valueAxis = {
  type: 'value',
  axisLabel: { color: COLOR.mute },
  splitLine: { lineStyle: { color: COLOR.line } },
}

// 전력 그래프 범례: 그래프 안에 두면 위쪽 한 줄을 차지해 그래프 칸이 좁아지므로 제목 줄에 글자로 둠
// (색은 그래프와 같은 CSS 변수. "보기"에서 끈 항목은 범례에서도 뺌)
const POWER_LEGEND = [
  { key: 'grid', name: '한전 구매', className: 'grid' },
  { key: 'solar', name: '태양광', className: 'solar' },
  { key: 'ess', name: 'ESS 방전', className: 'discharge' },
  { key: 'ess', name: 'ESS 충전(−)', className: 'charge' },
  { key: 'load', name: '공장 소비', className: 'load' },
  // 한전 구매를 켰을 때만 찍히는 빨간 점. 제목 줄이 꺾이지 않게 이름은 짧게 ("15분"은 툴팁에 적음)
  { key: 'grid', name: '피크 초과', className: 'exceed' },
]
const powerLegend = computed(() =>
  POWER_LEGEND.filter((item) => shown.value.includes(item.key)).filter(
    // 피크 초과는 이 기간에 점이 있을 때만 범례에 넣음 (점이 없는데 자리를 차지하면 1536 화면에서 "추천 표시"가 아랫줄로 밀림)
    (item) => item.className !== 'exceed' || Object.keys(exceededBySlot.value).length > 0,
  ),
)

// 어떤 시각이 그래프의 어느 칸인지 (그 시각이 들어 있는 칸의 가로축 글자)
function slotLabelOf(at) {
  let found = null
  points.value.forEach((item, index) => {
    if (item.recordedAt <= at) found = labels.value[index]
  })
  return found
}

// 추천 기호를 눌러 고른 시각이 그래프의 어느 칸인지
const focusLabel = computed(() => (focusAt.value ? slotLabelOf(focusAt.value) : null))

// ---- 추천 핀 ----
// 조회 기간 안의 추천을 그래프 칸별로 묶음. 예: { '11-13 10:00': [추천, 추천] }
const pinsByLabel = computed(() => {
  const result = {}
  if (!trend.value) return result
  const from = `${trend.value.from}T00:00:00`
  const to = `${trend.value.to}T23:59:59`
  for (const item of historyStore.decisions) {
    // 다른 화면에서 받아 둔 다른 기간의 이력이 섞이지 않게 기간 안의 것만 씀
    if (!PIN_INFO[item.status] || item.decidedAt < from || item.decidedAt > to) continue
    const label = slotLabelOf(item.decidedAt)
    if (!label) continue
    if (!result[label]) result[label] = []
    result[label].push(item)
  }
  return result
})

// 핀에 적는 기호: 그 칸에 나온 추천들의 처리 상태 (같은 기호는 한 번만)
function pinShapes(list) {
  return [...new Set(list.map((item) => PIN_INFO[item.status].shape))].join('')
}

// 전력 그래프 툴팁 아래에 붙이는 그 칸의 추천 목록
function pinTooltip(label) {
  if (!showPins.value) return ''
  const list = pinsByLabel.value[label] ?? []
  if (!list.length) return ''
  const rows = list.slice(0, 4).map((item) => {
    const power = item.action === 'IDLE' ? '' : ` ${formatKw(item.powerKw)} kW`
    const info = PIN_INFO[item.status]
    return `${info.shape} ${formatHourMinute(item.decidedAt)} ${ACTION_LABELS[item.action]}${power} · ${info.label}`
  })
  if (list.length > 4) rows.push(`외 ${list.length - 4}건`)
  return `<br/><strong>추천</strong><br/>${rows.join('<br/>')}`
}

// 기준선(가로), 추천 기호, 고른 시각(세로)을 그리는 설정
// 전력 그래프의 오른쪽 여백. 예전에는 배터리 잔량 · 온도 그래프의 기준선 이름표(상한 · 제한) 자리로 58px를 비웠으나,
// 두 그래프를 없애 그래프 칸을 넓힘 (목표 피크 이름표는 그래프 안쪽에 적음)
const GRID_RIGHT = 20
// 전력 그래프 칸의 위쪽 여백 (이 위에 추천 기호 띠 20px가 들어감. 범례는 제목 줄로 옮김)
const POWER_GRID_TOP = 24

// lines 예: [{ value: 1800, text: '목표 피크 1,800', color: COLOR.ink, dashed: false }]
// withPins = true면 그래프 위쪽 띠에 추천 기호를 적음 (전력 그래프만)
function markLines(lines, withPins = false) {
  // 기준값이 null이면(백엔드가 설정 값을 못 찾은 경우) 그 선은 빼고 그림. 넣으면 ECharts 오류가 남
  const data = lines
    .filter((item) => item.value !== null && item.value !== undefined)
    .map((item) => ({
      yAxis: item.value,
      lineStyle: {
        color: item.color,
        width: 1.5,
        type: item.dashed ? 'dashed' : 'solid',
      },
      // below: true면 이름표를 선 아래에 적음 (선 위쪽이 그래프 맨 위의 추천 기호 띠와 겹칠 때)
      label: {
        formatter: item.text,
        position: item.below ? 'insideEndBottom' : 'insideEndTop',
        color: item.color,
        fontWeight: 'bold',
        fontSize: 12,
        distance: 5,
      },
    }))
  // 추천은 세로선을 긋지 않고(선이 많으면 그래프가 지저분해짐) 그래프 위쪽 띠에 기호만 적음.
  // 선 색을 투명하게 두고 선 끝의 글자(기호)만 보이게 하는 방식. 자세한 내용은 툴팁으로 봄
  // (opacity: 0을 쓰면 글자까지 같이 사라져서 색을 투명하게 함)
  if (withPins && showPins.value) {
    for (const [label, list] of Object.entries(pinsByLabel.value)) {
      data.push({
        xAxis: label,
        lineStyle: { color: 'rgba(0, 0, 0, 0)' },
        label: {
          formatter: pinShapes(list),
          position: 'end',
          color: COLOR.legendText,
          fontSize: 11,
        },
      })
    }
  }
  if (focusLabel.value) {
    data.push({
      xAxis: focusLabel.value,
      // 표에서 고른 구간은 실선, 추천 기호로 고른 칸은 점선
      lineStyle: { color: COLOR.ink, width: 2, type: focusNote.value ? 'solid' : 'dashed' },
      // 세로선 위쪽 끝(그래프 위 여백)에 적음. 작은 그래프의 위 여백(16px)에도 들어가게 11px,
      // 바탕을 칠해 다른 선 · 글자와 겹쳐도 읽히게 함
      label: {
        formatter: focusText.value || '선택',
        position: 'end',
        distance: 1,
        color: COLOR.ink,
        fontSize: 11,
        fontWeight: 'bold',
        backgroundColor: COLOR.card,
        padding: [0, 3],
      },
    })
  }
  return { silent: true, symbol: 'none', data }
}

function lineSeries(name, field, color, width = 2) {
  return {
    name,
    type: 'line',
    showSymbol: false,
    data: points.value.map((item) => item[field] ?? null), // 기록 없는 칸은 비움
    lineStyle: { color, width },
    itemStyle: { color },
  }
}

// ---- 마우스를 끝까지 따라가는 보이지 않는 선 ----
// ECharts는 마우스 가까이에서 "값이 있는 점"을 찾아 점선·툴팁을 붙여서, 기록이 없는 구간으로 마우스를 옮기면
// 점선이 기록 끝에서 멈추고 마우스를 따라오지 않았음. 모든 칸에 값(0)이 있는 보이지 않는 선을 하나 두어
// 어느 칸이든 점선이 마우스를 따라가게 하고, 기록이 없는 칸이면 툴팁에 "기록 없음"을 보여 줌.
// 이 선은 숨긴 두 번째 세로축(0~1)에 그려서 원래 세로축 범위에는 영향을 주지 않음
const CURSOR_NAME = '마우스 위치'
const CURSOR_AXIS = { type: 'value', show: false, min: 0, max: 1 }
function cursorSeries() {
  return {
    name: CURSOR_NAME,
    type: 'line',
    yAxisIndex: 1,
    data: points.value.map(() => 0),
    symbol: 'none',
    lineStyle: { opacity: 0 },
    emphasis: { disabled: true },
    silent: true,
  }
}
// 툴팁 목록에서 보이지 않는 선을 뺌
const realParams = (params) => params.filter((item) => item.seriesName !== CURSOR_NAME)

// ---- 전력 (kW) ----
// 공장 소비 = 한전 구매 + 태양광 + ESS(방전 +, 충전 −) 이므로, 공장이 쓴 전기를 "어디서 채웠는지" 쌓아서 그림.
// - 0 위: 한전 구매(맨 아래) → 태양광 → ESS 방전 순서로 쌓은 면. 맨 위가 공장 소비 선과 만남
// - 0 아래: ESS 충전 (배터리로 들어간 만큼)
// - 한전 구매가 맨 아래 층이라, 그 윗선을 목표 피크선과 바로 비교할 수 있음
// (예전에는 선 3개 + 막대를 겹쳐 그려, 선이 서로 얽혀 무엇이 큰지 알기 어려웠음)

const ESS_DISCHARGE = 'ESS 방전'
const ESS_CHARGE = 'ESS 충전(−)'

// 쌓는 면 하나. 그라데이션 없이 단색을 옅게 칠하고, 윗선은 같은 색으로 얇게
function areaSeries(name, data, color, { stack, areaOpacity, lineWidth }) {
  return {
    name,
    type: 'line',
    stack,
    showSymbol: false,
    data,
    lineStyle: { color, width: lineWidth },
    itemStyle: { color },
    areaStyle: { color, opacity: areaOpacity },
    emphasis: { disabled: true },
  }
}

// 기록이 없는 구간을 옅은 띠로 칠하고 "기록 없음"이라고 적음 (값이 0으로 떨어진 것과 구분되게)
// 맨 끝(오늘의 아직 오지 않은 시간)은 기록이 "없는" 게 아니라 "아직 안 온" 것이라 칠하지 않음
function emptyAreas() {
  const isEmpty = (item) => item.loadKw == null && item.gridKw == null
  const lastIndex = points.value.findLastIndex((item) => !isEmpty(item))
  // 띠가 좁으면 글자가 띠 밖으로 넘쳐 옆 띠의 글자와 겹치므로, 전체 폭의 1/12 이상인 띠에만 글자를 적음
  const labelMin = points.value.length / 12
  const areas = []
  let start = null
  points.value.forEach((item, index) => {
    if (index > lastIndex) return
    if (isEmpty(item) && start === null) start = index
    if (!isEmpty(item) && start !== null) {
      // 한 칸짜리 빈칸은 선이 끊긴 것만으로 충분히 보여 띠를 칠하지 않음
      if (index - start >= 2) {
        areas.push([
          {
            xAxis: labels.value[start],
            itemStyle: { color: COLOR.band },
            label: {
              show: index - start >= labelMin,
              formatter: '기록 없음',
              position: 'inside',
              color: COLOR.mute,
              fontSize: 11,
            },
          },
          { xAxis: labels.value[index - 1] },
        ])
      }
      start = null
    }
  })
  return areas
}

const powerOption = computed(() => {
  const has = (key) => shown.value.includes(key)
  const pick = (field) => points.value.map((item) => item[field] ?? null) // 기록 없는 칸은 비움
  const series = []
  if (has('grid')) {
    series.push(
      areaSeries('한전 구매', pick('gridKw'), COLOR.grid, {
        stack: 'supply',
        areaOpacity: 0.35,
        lineWidth: 1.5, // 목표 피크선과 비교하는 선이라 조금 또렷하게
      }),
    )
  }
  if (has('solar')) {
    series.push(
      // 노랑은 옅게 칠하면 어두운 바탕에서 갈색으로 보여 진하게.
      // 윗선은 긋지 않음: 밤(태양광 0)에는 윗선이 한전 구매 윗선 위에 겹쳐 노란 선이 보였음
      areaSeries('태양광', pick('solarKw'), COLOR.solar, {
        stack: 'supply',
        areaOpacity: 0.8,
        lineWidth: 0,
      }),
    )
  }
  if (has('ess')) {
    // ESS 값 하나(방전 +, 충전 −)를 부호로 나눠, 방전은 위에 쌓고 충전은 0 아래에 그림
    const essOf = (pickSign) =>
      points.value.map((item) => (item.essKw == null ? null : pickSign(item.essKw, 0)))
    series.push(
      areaSeries(ESS_DISCHARGE, essOf(Math.max), COLOR.ess, {
        stack: 'supply',
        areaOpacity: 0.65,
        lineWidth: 0,
      }),
      areaSeries(ESS_CHARGE, essOf(Math.min), COLOR.ess, {
        stack: 'charge',
        areaOpacity: 0.3,
        lineWidth: 0,
      }),
    )
  }
  // 공장 소비는 쌓지 않은 선으로 맨 위에 그림 (쌓인 면의 맨 위와 만나는지 보면 됨)
  if (has('load')) series.push(lineSeries('공장 소비', 'loadKw', COLOR.ink))

  // 목표 피크선은 한전 구매전력과 비교하는 선이라, 한전 구매를 켰을 때만 그림
  const target = trend.value.targetPeakKw
  const lines = has('grid')
    ? // 목표 피크선은 공장 소비 선(먹색)과 구분되게 전용 자주색 (통합 화면의 목표 피크선과 같은 색)
      // 이름표는 선 아래에 둠: 목표선이 그래프 맨 위 가까이 있으면 위쪽 이름표가 추천 기호(● ◇ ✕ ○)와 겹쳤음
      [{ value: target, text: `목표 피크 ${formatKw(target)}`, color: COLOR.target, below: true }]
    : []
  if (series.length) {
    series[0].markLine = markLines(lines, true)
    series[0].markArea = { silent: true, data: emptyAreas() }
  }
  // 피크 초과 점은 한전 구매와 비교하는 값이라, 한전 구매를 켰을 때만 찍음
  if (has('grid')) series.push(exceedSeries())
  series.push(cursorSeries())

  return {
    animation: false,
    // 위쪽에 추천 핀 기호 띠가 들어감. 범례는 그래프 칸을 넓히려고 제목 줄(powerLegend)에 둠
    grid: { left: 52, right: GRID_RIGHT, top: POWER_GRID_TOP, bottom: 24 },
    legend: { show: false },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      // 공장 소비 → 한전 구매 → 태양광 → ESS 순서로, ESS는 방전/충전 중 한 줄만 보여 줌.
      // 값 목록 아래에 그 칸에 나온 추천을 함께 보여 줌
      formatter: (allParams) => {
        if (!allParams.length) return ''
        const label = allParams[0].axisValue
        const point = points.value[allParams[0].dataIndex]
        const params = realParams(allParams).filter((item) => item.seriesName !== EXCEED_NAME)
        // 기록이 없는 칸이면 값마다 "—"를 늘어놓지 않고 한 줄로 알림
        if (params.every((item) => item.value == null)) return `${label}<br/>기록 없음`
        const marker = (name) => params.find((item) => item.seriesName === name)?.marker ?? ''
        // kw를 주지 않으면 이름만 적음 (ESS 대기)
        const row = (name, text, kw) =>
          `${marker(name)}${text}${kw === undefined ? '' : ` <strong>${formatKw(kw)} kW</strong>`}`
        const rows = []
        if (has('load')) rows.push(row('공장 소비', '공장 소비', point.loadKw))
        if (has('grid')) rows.push(row('한전 구매', '한전 구매', point.gridKw))
        if (has('solar')) rows.push(row('태양광', '태양광', point.solarKw))
        if (has('ess')) {
          const ess = point.essKw
          if (ess != null && ess < 0) rows.push(row(ESS_CHARGE, 'ESS 충전', Math.abs(ess)))
          else if (ess > 0) rows.push(row(ESS_DISCHARGE, 'ESS 방전', ess))
          else if (ess === 0) rows.push(row(ESS_DISCHARGE, 'ESS 대기'))
          else rows.push(row(ESS_DISCHARGE, 'ESS', null)) // 이 칸만 ESS 기록이 없으면 "—"
        }
        // 이 칸에 목표를 넘은 15분 구간이 있으면 한 줄 더 (점을 누르면 전력 이력으로 감)
        const exceed = allParams.find((item) => item.seriesName === EXCEED_NAME && item.data)
        if (exceed) {
          const { top, count } = exceed.data
          const many = count > 1 ? ` 외 ${count - 1}번` : ''
          rows.push(
            `${exceed.marker}15분 피크 초과 <strong>${formatHourMinute(top.windowStart)} ${formatKw(top.peakKw)} kW</strong>${many}` +
              `<br/><span style="opacity:.7">점을 누르면 전력 이력을 봅니다</span>`,
          )
        }
        return `${label}<br/>${rows.join('<br/>')}${pinTooltip(label)}`
      },
    },
    // 충전(0 아래)이 있으면 가로축 선이 그래프 가운데(0)로 올라오므로, 가로축은 항상 맨 아래에 둠
    xAxis: { ...timeAxis(), axisLine: { onZero: false, lineStyle: { color: COLOR.line } } },
    yAxis: [
      {
        ...valueAxis,
        splitNumber: 3,
        // 그래프 칸이 낮으면 눈금 숫자가 겹치므로, 겹치는 숫자는 숨김
        axisLabel: { ...valueAxis.axisLabel, hideOverlap: true },
        // 값이 목표 피크보다 한참 낮으면(예: 1일 간격 평균) 세로축이 목표보다 낮게 끝나 목표선이 사라졌음.
        // 목표선을 그릴 때는 세로축 위쪽을 목표보다 조금 위(500 단위)까지 올림. null이면 ECharts가 알아서 정함
        max: (value) =>
          has('grid') && target != null && target >= value.max
            ? Math.ceil((target * 1.05) / 500) * 500
            : null,
        // 충전(0 아래)은 보통 수백 kW라, ECharts에 맡기면 칸이 낮을 때 -1,000까지 내려가 그래프 1/4이 비었음.
        // 가장 큰 충전값 바로 아래(250 단위)에서 자름
        min: (value) => (value.min < 0 ? Math.floor(value.min / 250) * 250 : null),
      },
      CURSOR_AXIS,
    ],
    series,
  }
})

// ---- 일별 최대 피크와 저감량 (피크 분석 화면의 그래프를 전력 그래프 아래로 옮겨 옴, 2026-11-20) ----
// 값은 피크 분석 API(dailyPeaks)가 준 그대로: 그날 15분 평균 중 최대(실제)와, 방전하지 않았다면 예상되는 최대.
// 위 전력 그래프는 1시간 평균이라, 같은 날이라도 이 값이 전력 그래프의 면보다 높거나 낮을 수 있음

// 지금 조회 기간의 피크 결과만 씀. 피크 분석 화면에서 받아 둔 다른 기간의 결과나,
// 기간을 바꾼 뒤 아직 새 결과가 오기 전의 이전 결과는 쓰지 않음
const peak = computed(() => {
  const result = analysisStore.peak
  if (!result || !trend.value) return null
  return result.from === trend.value.from && result.to === trend.value.to ? result : null
})

// 날짜별 한 줄. 위 전력 그래프 칸(1시간)의 날짜를 그대로 써서 두 그래프의 날짜 칸이 위아래로 맞음
// (전력 그래프 칸은 하루 24칸씩이라, 하루 1칸인 이 그래프와 날짜 폭이 같아짐). 기록 없는 날은 막대 없음
const dailyRows = computed(() => {
  const byDate = Object.fromEntries((peak.value?.dailyPeaks ?? []).map((item) => [item.date, item]))
  const dates = [...new Set(points.value.map((item) => item.recordedAt.slice(0, 10)))]
  return dates.map((date) => byDate[date] ?? { date, peakKw: null, expectedKw: null })
})
const hasDailyPeaks = computed(() => dailyRows.value.some((item) => item.peakKw != null))

// 피크 결과를 아직 그릴 수 없을 때의 안내 (일별 최대 피크 · 피크가 높았던 구간이 같이 씀). 그릴 수 있으면 ''
const peakNote = computed(() => {
  if (peakTooLong.value) {
    return `피크 기록은 ${PEAK_MAX_DAYS}일까지만 볼 수 있습니다. 기간을 줄이면 보입니다.`
  }
  if (analysisStore.peakError && !peak.value) {
    return `피크 기록을 불러오지 못했습니다. (${analysisStore.peakError})`
  }
  if (!peak.value) return '불러오는 중입니다.'
  return ''
})
const showPeakRetry = computed(() => !peakTooLong.value && !!analysisStore.peakError && !peak.value)

// ---- 기간 요약의 "목표를 넘은 구간" · "평균 피크 저감" (피크 분석 화면의 요약 카드에서 옮겨 옴, 2026-11-20) ----
// 피크 분석 API가 계산해 준 summary를 그대로 보여 줌. 아직 못 받았거나 기간이 62일을 넘으면 숫자는 "—"
const peakSummary = computed(() => peak.value?.summary ?? null)

// 목표를 넘은 구간이 전체 15분 구간의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const exceededPercent = computed(() =>
  peakSummary.value?.windowCount && peakSummary.value.exceededCount != null
    ? (peakSummary.value.exceededCount / peakSummary.value.windowCount) * 100
    : null,
)

// 피크 숫자를 보여 줄 수 없을 때 숫자 아래 줄에 적는 짧은 안내
// (요약 칸이 좁아 1366px 화면에서도 안 잘리게 peakNote보다 짧게 씀. 자세한 안내와 [다시 시도]는 일별 최대 피크 카드에 있음)
const peakSummaryNote = computed(() => {
  if (peakTooLong.value) return `${PEAK_MAX_DAYS}일까지만 볼 수 있습니다`
  if (analysisStore.peakError && !peak.value) return '불러오지 못했습니다'
  if (!peak.value) return '불러오는 중입니다'
  if (!peakSummary.value) return '피크 기록이 없습니다'
  return ''
})

const dailyOption = computed(() => {
  const rows = dailyRows.value
  const target = peak.value?.targetPeakKw ?? null
  // 차이(저감량)가 잘 보이게 세로축을 0이 아닌 값에서 시작함 (피크 분석 화면과 같은 방식)
  const lowest = Math.min(...rows.map((item) => item.peakKw).filter((value) => value != null))
  return {
    animation: false,
    // 왼쪽 · 오른쪽 여백을 전력 그래프와 같게 해야 날짜 칸이 위아래로 맞음
    grid: { left: 52, right: GRID_RIGHT, top: 8, bottom: 6 },
    legend: { show: false }, // 범례는 제목 줄에 글자로 둠
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = rows[params[0].dataIndex]
        if (row.peakKw == null) return `${row.date.slice(5)}<br/>기록 없음`
        const over = isOverTarget(row.peakKw) ? ' (목표 초과)' : ''
        const reduced = row.expectedKw - row.peakKw // 두 값의 차이 (표시용)
        return [
          row.date.slice(5),
          `실제 최대 피크 <strong>${formatKw(row.peakKw)} kW</strong>${over}`,
          `방전하지 않았을 때 예상 <strong>${formatKw(row.expectedKw)} kW</strong>`,
          reduced > 0 ? `저감 <strong>${formatKw(reduced)} kW</strong>` : '저감 없음',
        ].join('<br/>')
      },
    },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.date.slice(5)),
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      axisLabel: { show: false }, // 날짜는 바로 위 전력 그래프의 가로축 글자로 봄
      // 전력 그래프와 같은 날짜에 세로선 (날짜 경계)
      splitLine: {
        show: !isSingleDay.value,
        interval: (index) => index % dayStep.value === 0,
        lineStyle: { color: COLOR.line },
      },
    },
    yAxis: {
      ...valueAxis,
      splitNumber: 2,
      axisLabel: { ...valueAxis.axisLabel, hideOverlap: true },
      min: Math.floor((lowest * 0.9) / 100) * 100,
      // 막대가 모두 목표보다 낮으면 세로축이 목표보다 낮게 끝나 목표선이 사라지므로 목표 위까지 올림
      max: (value) =>
        target != null && target >= value.max ? Math.ceil((target * 1.05) / 100) * 100 : null,
    },
    series: [
      {
        name: '방전하지 않았을 때 예상',
        type: 'bar',
        barWidth: '56%',
        barMaxWidth: 48,
        data: rows.map((item) => item.expectedKw),
        itemStyle: { color: COLOR.lineStrong },
      },
      {
        // 예상 막대와 같은 자리에 겹쳐 그림. 위로 남는 연한 부분이 그날의 저감량
        name: '실제',
        type: 'bar',
        barWidth: '56%',
        barMaxWidth: 48,
        barGap: '-100%',
        data: rows.map((item) => ({
          value: item.peakKw,
          itemStyle: { color: isOverTarget(item.peakKw) ? COLOR.crit : COLOR.grid },
        })),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.target, width: 1.5, type: 'solid' }, // 위 전력 그래프 목표선과 같게 실선
          label: { show: false }, // 이름표는 위 전력 그래프의 목표선에 있음 (같은 색)
          // 목표 값이 없으면 선을 그리지 않음 (null을 넣으면 ECharts 오류)
          data: target === null ? [] : [{ yAxis: target }],
        },
      },
    ],
  }
})

// ---- 피크가 높았던 구간 (피크 분석 화면의 표를 옮겨 옴, 2026-11-20) ----
// 위 "일별 최대 피크"와 같은 피크 분석 결과(peak)를 씀. 줄을 누르면 전력 그래프에 그 시각을 세로선으로 표시
// 보기 방식
// - 'top': 피크 높은 순 (백엔드가 준 topWindows: 날마다 가장 높았던 15분 구간 중 높은 순)
// - 'aboveAverage': 기간 평균보다 높은 구간 (시간별 피크 중 평균보다 높은 시각을, 같은 날 이어지는 시각끼리 묶음)
const WINDOW_MODES = [
  { value: 'top', label: '피크 높은 순' },
  { value: 'aboveAverage', label: '평균보다 높은 구간' },
]
const windowMode = ref('top')
// 보기 방식을 바꾸면 표에서 고른 줄은 풀어 둠 (두 보기의 줄 key가 다름)
watch(windowMode, () => {
  if (selectedWindow.value) clearFocus()
})

// 그 구간의 추천 처리를 한 줄 문구로 (status 코드를 화면에서 글자로 바꿈. 피크 분석 화면과 같음)
// 백엔드 note는 추천 이유 문장이라 표 칸에 넣기엔 길어서 쓰지 않음
function windowNote(item) {
  if (!item.status) return '방전 추천 없음'
  if (item.status === 'IGNORED') return '추천을 넘김'
  if (item.status === 'AUTO') return '자동 방전 (시뮬레이션)'
  if (item.status === 'APPLIED') return '방전 적용'
  return '추천 중'
}

const topWindows = computed(() => peak.value?.topWindows ?? [])

// 평균보다 높은 구간 (표시용 계산: 백엔드가 준 시간별 피크(heatmap)의 평균과 비교해 거르기만 함)
const heatCells = computed(() => peak.value?.heatmap ?? [])
// 기간 평균: 날짜 × 시각 칸 피크의 평균
const heatAverageKw = computed(() =>
  heatCells.value.length
    ? heatCells.value.reduce((sum, item) => sum + item.peakKw, 0) / heatCells.value.length
    : null,
)
// 평균보다 높은 시각을 날짜별로 모아, 이어지는 시각(예: 8시·9시·10시)은 한 구간으로 묶음
// 한 줄 모양: { key, date, startHour, endHour, peakKw(구간 안 최대), abovePercent(최대 피크가 평균보다 몇 % 높은지) }
// 순서: 최대 피크가 높은 구간부터
const aboveAverageRanges = computed(() => {
  const average = heatAverageKw.value
  if (average === null) return []
  const cells = heatCells.value
    .filter((item) => item.peakKw > average)
    .sort((a, b) => (a.date === b.date ? a.hour - b.hour : a.date < b.date ? -1 : 1))
  const ranges = []
  for (const cell of cells) {
    const last = ranges[ranges.length - 1]
    if (last && last.date === cell.date && last.endHour === cell.hour - 1) {
      last.endHour = cell.hour
      last.peakKw = Math.max(last.peakKw, cell.peakKw)
    } else {
      ranges.push({
        date: cell.date,
        startHour: cell.hour,
        endHour: cell.hour,
        peakKw: cell.peakKw,
      })
    }
  }
  return ranges
    .map((range) => ({
      ...range,
      key: `${range.date}T${pad2(range.startHour)}`,
      abovePercent: ((range.peakKw - average) / average) * 100,
    }))
    .sort((a, b) => b.peakKw - a.peakKw)
})
// '8시~11시' 처럼 구간을 글자로 (끝 시각은 마지막 칸이 끝나는 시각)
const hourRangeText = (range) => `${range.startHour}시~${range.endHour + 1}시`

// 표의 줄을 누르면 전력 그래프의 그 시각에 세로선
function pickTopWindow(item) {
  const note = `${shortTime(item.windowStart)} 피크 ${formatKw(item.peakKw)} kW`
  toggleWindow(item.windowStart, item.windowStart, note)
}
function pickRange(item) {
  const note = `${item.date.slice(5)} ${hourRangeText(item)} 최대 ${formatKw(item.peakKw)} kW`
  toggleWindow(item.key, `${item.date}T${pad2(item.startHour)}:00:00`, note) // 구간이 시작하는 시각
}

// ---- 시간대별 사용 패턴 ----
// 순위 보기: 백엔드가 준 시각별 평균을 큰 순서로 정렬만 함. 막대 길이는 1위를 100%로 본 비율(표시용)
const RANK_COUNT = 10 // 순위 보기에서 보여 줄 개수
const hourlyRanks = computed(() => {
  // 기록이 없는 시각(avgGridKw가 null)은 빼고 정렬 (null이 0처럼 정렬되어 순위에 섞였음 — 이슈 #25)
  const sorted = (trend.value?.hourly ?? [])
    .filter((item) => item.avgGridKw != null)
    .sort((a, b) => b.avgGridKw - a.avgGridKw)
  const top = sorted[0]?.avgGridKw || 1
  return sorted.slice(0, RANK_COUNT).map((item, index) => ({
    rank: index + 1,
    hour: item.hour,
    avgGridKw: item.avgGridKw,
    period: PERIOD_LABELS[item.period] ?? '',
    isPeak: item.period === 'PEAK',
    width: (item.avgGridKw / top) * 100,
  }))
})

// ---- 시간대별 사용 패턴: 기간 동안의 막대 (1시간 · 4시간 · 1일 묶음) ----
const PATTERN_STEPS = [
  { value: 'AUTO', label: '자동' },
  { value: 'HOUR', label: '1시간' },
  { value: 'HOUR4', label: '4시간' },
  { value: 'DAY', label: '1일' },
]
// 한 화면에 보여 줄 막대 수 (간격과 상관없이 고정 → 막대 굵기가 항상 같음).
// 이보다 많으면 아래 스크롤 막대로 좌우로 옮겨 봄
const PATTERN_WINDOW = 24

// 고른 기간이 며칠인지 (시작일과 종료일 포함)
const periodDays = computed(() => {
  if (!fromDate.value || !toDate.value) return 0
  const start = new Date(`${fromDate.value}T00:00:00`)
  const end = new Date(`${toDate.value}T00:00:00`)
  return Math.round((end - start) / 86400000) + 1
})
// "자동"일 때의 간격: 하루면 1시간, 7일 이하면 4시간, 그보다 길면(한 달 등) 1일
const autoStep = computed(() => {
  if (periodDays.value <= 1) return 'HOUR'
  if (periodDays.value <= 7) return 'HOUR4'
  return 'DAY'
})
const activeStep = computed(() =>
  patternStep.value === 'AUTO' ? autoStep.value : patternStep.value,
)
const stepLabel = (value) => PATTERN_STEPS.find((item) => item.value === value)?.label ?? ''

// 1시간 간격 값을 고른 간격으로 묶어 평균 냄 (그래프에 그리기 위한 표시용 평균)
const patternBars = computed(() => {
  // 기록 없는 시간도 막대 자리로 남김 (위 그래프와 같은 이유)
  const pattern = analysisStore.pattern
  const list = fillSlots(pattern?.points ?? [], pattern?.from, pattern?.to, 'HOUR')
  const groups = []
  for (const item of list) {
    const date = item.recordedAt.slice(0, 10) // '2026-11-18'
    const hour = Number(item.recordedAt.slice(11, 13))
    let key = item.recordedAt.slice(0, 13) // 1시간: 날짜 + 시
    if (activeStep.value === 'HOUR4') key = `${date} ${Math.floor(hour / 4)}`
    if (activeStep.value === 'DAY') key = date
    const last = groups[groups.length - 1]
    if (last && last.key === key) {
      last.values.push(item.gridKw)
    } else {
      groups.push({
        key,
        date,
        hour: activeStep.value === 'HOUR4' ? Math.floor(hour / 4) * 4 : hour,
        values: [item.gridKw],
      })
    }
  }
  const oneDay = periodDays.value <= 1
  return groups.map((group) => {
    const values = group.values.filter((value) => value != null) // 기록 없는 시간은 평균에서 뺌
    const md = group.date.slice(5) // '11-18'
    let label = md
    if (activeStep.value === 'HOUR') label = oneDay ? `${group.hour}시` : `${md} ${group.hour}시`
    if (activeStep.value === 'HOUR4') label = `${md} ${group.hour}~${group.hour + 4}시`
    return {
      label,
      hour: group.hour,
      avgGridKw: values.length
        ? values.reduce((sum, value) => sum + value, 0) / values.length
        : null, // 묶음 전체에 기록이 없으면 막대 없음
    }
  })
})

const patternOption = computed(() => {
  const bars = patternBars.value
  const names = bars.map((item) => item.label)
  const target = analysisStore.pattern?.targetPeakKw

  // 1시간 간격일 때만 최대부하 시각의 막대 뒤에 회색 띠 (4시간 · 1일은 여러 요금 시간대가 섞여 표시하지 않음)
  const peakHours = new Set(
    (trend.value?.hourly ?? []).filter((item) => item.period === 'PEAK').map((item) => item.hour),
  )
  const areas =
    activeStep.value === 'HOUR'
      ? bars
          .filter((item) => peakHours.has(item.hour))
          .map((item) => [
            { xAxis: item.label, itemStyle: { color: COLOR.band } },
            { xAxis: item.label },
          ])
      : []

  // 처음에는 가장 최근 막대들을 보여 줌 (한 화면 막대 수만큼)
  const windowSize = PATTERN_WINDOW
  const startValue = Math.max(0, bars.length - windowSize)
  const endValue = Math.max(0, bars.length - 1)
  // 좌우 스크롤: 그래프 위에서 드래그 · 휠, 아래 막대로 옮김.
  // filterMode 'none' → 옮겨도 세로축 범위가 바뀌지 않음 (세로축은 왼쪽에 항상 그대로 보임)
  const zoomBase = { xAxisIndex: 0, filterMode: 'none', startValue, endValue }

  return {
    animation: false,
    grid: { left: 48, right: 12, top: 18, bottom: 40 },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    dataZoom: [
      { ...zoomBase, type: 'inside', zoomOnMouseWheel: false, moveOnMouseWheel: true },
      {
        ...zoomBase,
        type: 'slider',
        height: 12,
        bottom: 4,
        showDetail: false,
        brushSelect: false,
        zoomLock: true, // 막대 폭은 그대로, 위치만 옮김
        borderColor: COLOR.line,
        fillerColor: COLOR.band,
        handleStyle: { color: COLOR.card, borderColor: COLOR.mute },
        dataBackground: { lineStyle: { color: COLOR.faint }, areaStyle: { color: COLOR.band } },
      },
    ],
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      axisLabel: { color: COLOR.mute, hideOverlap: true },
    },
    yAxis: { ...valueAxis, min: 0, splitNumber: 3 }, // 칸이 낮아도 눈금 숫자가 겹치지 않게 3칸만
    series: [
      {
        name: `평균 한전 구매 (${stepLabel(activeStep.value)})`,
        type: 'bar',
        barCategoryGap: '25%',
        // Math.round(null)은 0이 되므로 기록 없는 막대는 null 그대로 둠
        data: bars.map((item) => (item.avgGridKw === null ? null : Math.round(item.avgGridKw))),
        itemStyle: { color: COLOR.grid },
        markArea: { silent: true, data: areas },
        markLine: target
          ? {
              silent: true,
              symbol: 'none',
              lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
              label: {
                formatter: `목표 피크 ${formatKw(target)}`,
                position: 'insideEndTop',
                color: COLOR.ink,
                fontWeight: 'bold',
              },
              data: [{ yAxis: target }],
            }
          : undefined,
      },
    ],
  }
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">분석</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">추이 분석</h1>
      <!-- 조회 중 안내는 제목 옆에 둠: 조회 조건 카드 안에 두면 1366 화면에서 자리가 모자라 "보기"가 아랫줄로 밀렸음 -->
      <p v-if="analysisStore.loading" class="loading-note">불러오는 중입니다.</p>
      <p class="crumb-note">공장이 쓴 전기를 한전 · 태양광 · ESS 중 어디서 채웠는지 봅니다</p>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <!-- 버튼을 누르면 옆 날짜 칸이 그 기간으로 채워짐. 날짜를 직접 바꾸면 버튼 선택이 풀림 -->
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button
            v-for="item in PRESETS"
            :key="item.value"
            :class="{ active: preset === item.value }"
            :aria-pressed="preset === item.value"
            :title="item.title"
            @click="applyPreset(item.value)"
          >
            {{ item.label }}
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

      <!-- 보기: 아래 오른쪽 열(시간대별 사용 패턴 카드)과 왼쪽 끝을 맞추고, 4개를 한 줄로 둠 (2026-11-20) -->
      <div class="field view-field">
        <span class="field-label">보기</span>
        <div class="checks">
          <label v-for="item in ITEMS" :key="item.key" class="check">
            <input v-model="shown" type="checkbox" :value="item.key" />
            {{ item.label }}
          </label>
        </div>
      </div>
      <p v-if="dateError" class="date-error" role="alert">{{ dateError }}</p>
    </section>

    <!-- 받아 둔 결과가 있으면 지우지 않고 위에 실패 띠만 띄움 (마지막으로 받은 값 유지) -->
    <p v-if="analysisStore.errorMessage && trend" class="error-band" role="alert">
      <span aria-hidden="true">◆</span>
      새 결과를 불러오지 못해 이전에 받은 결과를 보여 줍니다. ({{ analysisStore.errorMessage }})
      <button class="error-band-button" @click="load()">다시 시도</button>
    </p>
    <p v-if="analysisStore.errorMessage && !trend" class="state error" role="alert">
      분석 결과를 불러오지 못했습니다. ({{ analysisStore.errorMessage }})
      <button class="retry" @click="load()">다시 시도</button>
    </p>
    <!-- 조회 기간은 서버 시각으로 정하므로, 서버 시각을 받기 전에는 조회하지 않음 -->
    <p v-else-if="!trend && !serverDate" class="state">
      서버에서 현재 시각을 받으면 자동으로 조회합니다.
    </p>
    <p v-else-if="!trend" class="state">불러오는 중입니다.</p>
    <p v-else-if="!points.length" class="state">이 기간에는 기록이 없습니다. 기간을 바꿔 보세요.</p>

    <template v-else>
      <!-- 기간 요약 -->
      <dl class="card summary">
        <div>
          <dt>기간 중 최대 피크</dt>
          <dd :class="{ over: isOverTarget(summary.maxPeakKw) }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">{{ shortTime(summary.maxPeakAt) }}{{ maxPeakVerdict }}</p>
        </div>
        <!-- 목표를 넘은 구간 · 평균 피크 저감: 피크 분석 화면에서 옮겨 옴 (피크 분석 API 값, 2026-11-20) -->
        <div>
          <dt>목표를 넘은 구간</dt>
          <dd>
            <span class="num">{{ formatInteger(peakSummary?.exceededCount) }}</span>
            <small>회</small>
            <!-- 전체 15분 구간 중 몇 %였는지 -->
            <span v-if="exceededPercent !== null" class="pct num">
              ({{ formatOneDecimal(exceededPercent) }}%)
            </span>
          </dd>
          <p v-if="peakSummaryNote">{{ peakSummaryNote }}</p>
          <p v-else class="num">15분 구간 {{ formatInteger(peakSummary.windowCount) }}개 중</p>
        </div>
        <div>
          <dt>평균 피크 저감</dt>
          <dd>
            <span class="num">{{ formatKw(peakSummary?.avgReductionKw) }}</span> <small>kW</small>
          </dd>
          <p v-if="peakSummaryNote">{{ peakSummaryNote }}</p>
          <!-- 칸이 좁아 "방전하지 않았을 때의 예상 대비"는 마우스를 올리면 보이게 둠 -->
          <p v-else class="num" title="방전하지 않았을 때의 예상 피크 대비">
            가장 크게 줄인 날 {{ formatKw(peakSummary.maxReductionKw) }} kW
          </p>
        </div>
        <div>
          <dt>평균 한전 구매전력</dt>
          <dd>
            <span class="num">{{ formatKw(summary.avgGridKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">목표 피크의 {{ formatOneDecimal(avgGridPercent) }}%</p>
        </div>
        <div>
          <dt>태양광 발전</dt>
          <dd>
            <span class="num">{{ formatInteger(summary.solarKwh) }}</span> <small>kWh</small>
          </dd>
          <p class="num">공장 소비의 {{ formatOneDecimal(summary.solarPercentOfLoad) }}%</p>
        </div>
        <div>
          <dt>ESS 방전 / 충전</dt>
          <dd>
            <span class="num">
              {{ formatInteger(summary.dischargeKwh) }} / {{ formatInteger(summary.chargeKwh) }}
            </span>
            <small>kWh</small>
          </dd>
          <p class="num">충·방전 효율 {{ formatOneDecimal(summary.efficiencyPercent) }}%</p>
        </div>
        <!-- 배터리 잔량 범위 · 배터리실 최고 온도 카드는 2026-11-20에 뺌 (사용자 요청) -->
      </dl>

      <div class="main">
        <!-- 왼쪽: 전력 그래프 + 그 아래 얇은 일별 최대 피크 (배터리 잔량 · 온도 그래프는 2026-11-20에 뺌) -->
        <div ref="chartsBox" class="charts" @mouseenter="linkCharts">
          <section v-if="shown.length" class="card power-card">
            <h2 class="chart-title main-chart">
              전력 <small>kW</small>
              <span v-if="showPins" class="chart-note">
                추천: ● 적용 · ◇ 자동 · ✕ 넘김 · ○ 추천 중
              </span>
              <!-- 범례 (그래프의 면 · 선 색과 같음) -->
              <span class="power-legend">
                <span v-for="item in powerLegend" :key="item.name" class="power-legend-item">
                  <span class="power-swatch" :class="item.className" aria-hidden="true"></span>
                  {{ item.name }}
                </span>
              </span>
              <label class="check pin-toggle">
                <input v-model="showPins" type="checkbox" />
                추천 표시
              </label>
            </h2>
            <p v-if="historyStore.errorMessage" class="pin-error" role="alert">
              추천 기록을 불러오지 못해 핀을 표시하지 못했습니다. ({{ historyStore.errorMessage }})
            </p>
            <!--
              key: 켜 둔 항목이 바뀌면 그래프를 새로 만듦.
              Chart.vue는 이전 설정에 새 설정을 덮어쓰는 방식이라, 선의 개수가 줄면 꺼 둔 선이 남기 때문
            -->
            <!-- 카드의 남은 높이를 채움 -->
            <FitChart
              :key="`${shown.join()}-${showPins}`"
              :option="powerOption"
              :min-height="100"
            />
          </section>

          <p v-if="!shown.length" class="card state">"보기"에서 볼 항목을 하나 이상 골라 주세요.</p>

          <!-- 일별 최대 피크와 저감량: 전력 그래프 바로 아래, 날짜 칸을 위아래로 맞춤 -->
          <section class="card daily-card">
            <h2 class="chart-title">
              일별 최대 피크 <small>kW · 15분 평균 기준</small>
              <span class="power-legend">
                <span class="power-legend-item">
                  <span class="power-swatch expected" aria-hidden="true"></span>
                  방전하지 않았을 때 예상 (위로 남는 부분 = 저감량)
                </span>
                <span class="power-legend-item">
                  <span class="power-swatch actual" aria-hidden="true"></span>
                  실제
                </span>
                <span class="power-legend-item">
                  <span class="power-swatch over" aria-hidden="true"></span>
                  목표 초과
                </span>
              </span>
            </h2>
            <p v-if="peakNote" class="daily-note" :class="{ error: showPeakRetry }">
              {{ peakNote }}
              <button
                v-if="showPeakRetry"
                class="retry"
                @click="analysisStore.fetchPeak(fromDate, toDate)"
              >
                다시 시도
              </button>
            </p>
            <p v-else-if="!hasDailyPeaks" class="daily-note">이 기간에는 피크 기록이 없습니다.</p>
            <FitChart v-else :option="dailyOption" :min-height="50" />
          </section>
        </div>

        <!-- 오른쪽 -->
        <div class="right">
          <section class="card pattern-card">
            <div class="card-head">
              <h2 class="card-title">시간대별 사용 패턴</h2>
              <!-- 막대 간격 고르기 (시간 순 보기일 때만): 자동이면 기간에 맞춰 (하루 1시간 · 7일 4시간 · 한 달 1일)
                   공간을 아끼려고 따로 줄을 두지 않고 제목 줄에 둠. 지금 쓰는 간격은 아래 안내 글에 적음 -->
              <div
                v-if="hourlyView === 'time'"
                class="presets step-switch"
                role="group"
                aria-label="막대 간격"
              >
                <button
                  v-for="item in PATTERN_STEPS"
                  :key="item.value"
                  :class="{ active: patternStep === item.value }"
                  :aria-pressed="patternStep === item.value"
                  @click="patternStep = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
              <div class="presets view-switch" role="group" aria-label="보기 방식">
                <button
                  :class="{ active: hourlyView === 'time' }"
                  :aria-pressed="hourlyView === 'time'"
                  @click="hourlyView = 'time'"
                >
                  시간 순
                </button>
                <button
                  :class="{ active: hourlyView === 'rank' }"
                  :aria-pressed="hourlyView === 'rank'"
                  @click="hourlyView = 'rank'"
                >
                  순위
                </button>
              </div>
            </div>
            <!-- Chart.vue는 크기가 0이면 오류가 나므로 v-show 대신 v-if로 바꿔 끼움 -->
            <template v-if="hourlyView === 'time'">
              <FitChart v-if="patternBars.length" :option="patternOption" :min-height="40" />
              <p v-else-if="analysisStore.patternLoading" class="state">불러오는 중입니다.</p>
              <p v-else-if="analysisStore.patternError" class="state" role="alert">
                불러오지 못했습니다. ({{ analysisStore.patternError }})
                <button class="retry" @click="analysisStore.fetchPattern(fromDate, toDate)">
                  다시 시도
                </button>
              </p>
              <p v-else class="state">이 기간의 기록이 없습니다.</p>
              <!-- 문장마다(마침표 기준) 줄을 바꿈 -->
              <p class="foot-note">
                <span class="foot-line">
                  기간 동안의 평균 한전 구매 (kW, {{ stepLabel(activeStep) }} 간격{{
                    patternStep === 'AUTO' ? ', 자동' : ''
                  }}).
                </span>
                <span class="foot-line">그래프를 끌거나 아래 막대로 좌우로 옮겨 봅니다.</span>
                <span v-if="activeStep === 'HOUR'" class="foot-line">
                  회색 띠는 최대부하 시간대입니다.
                </span>
              </p>
            </template>
            <template v-else>
              <!-- 기록 없는 시각을 빼고 나면 남는 칸이 없을 수 있음 (이슈 #25) -->
              <p v-if="!hourlyRanks.length" class="foot-note">시각별 기록이 없습니다.</p>
              <div v-else class="rank-scroll">
                <ol class="ranks">
                  <li v-for="item in hourlyRanks" :key="item.hour">
                    <span class="rank num">{{ item.rank }}</span>
                    <strong class="rank-hour num">{{ item.hour }}시</strong>
                    <span class="rank-track" aria-hidden="true">
                      <span class="rank-bar" :style="{ width: `${item.width}%` }"></span>
                    </span>
                    <span class="rank-kw num">{{ formatKw(item.avgGridKw) }} kW</span>
                    <span class="rank-period" :class="{ peak: item.isPeak }">{{
                      item.period
                    }}</span>
                  </li>
                </ol>
              </div>
              <p class="foot-note">한전 구매가 많은 시간부터 {{ RANK_COUNT }}개 (시각별 평균)</p>
            </template>
          </section>

          <!-- 피크가 높았던 구간 (피크 분석 화면에서 옮겨 옴): 줄을 누르면 전력 그래프에 그 시각을 표시 -->
          <section class="card windows-card">
            <div class="card-head">
              <h2 class="card-title">피크가 높았던 구간</h2>
              <div class="presets window-modes" role="group" aria-label="구간 보기 방식">
                <button
                  v-for="mode in WINDOW_MODES"
                  :key="mode.value"
                  :class="{ active: windowMode === mode.value }"
                  :aria-pressed="windowMode === mode.value"
                  @click="windowMode = mode.value"
                >
                  {{ mode.label }}
                </button>
              </div>
            </div>
            <p v-if="peakNote" class="daily-note" :class="{ error: showPeakRetry }">
              {{ peakNote }}
              <button
                v-if="showPeakRetry"
                class="retry"
                @click="analysisStore.fetchPeak(fromDate, toDate)"
              >
                다시 시도
              </button>
            </p>
            <template v-else>
              <p class="mode-note">
                <span v-if="windowMode === 'top'"
                  >15분 구간 피크가 높은 순 · 누르면 그래프에 표시</span
                >
                <span v-else>
                  평균 <span class="num">{{ formatKw(heatAverageKw) }}</span> kW보다 높은 시각 ·
                  누르면 그래프에 표시
                </span>
                <RouterLink to="/logs" class="more-link">추천 이력 보기</RouterLink>
              </p>
              <!-- 표 높이를 넘는 줄은 표 안에서 스크롤 -->
              <!-- 피크 높은 순 -->
              <div v-if="windowMode === 'top' && topWindows.length" class="table-box">
                <table class="table window-table">
                  <thead>
                    <tr>
                      <th class="rank-col">순위</th>
                      <th>대상 시간</th>
                      <th class="right-text">피크</th>
                      <th>그때의 추천 처리</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(item, index) in topWindows"
                      :key="item.windowStart"
                      class="window-row"
                      :class="{ active: selectedWindow === item.windowStart }"
                      @click="pickTopWindow(item)"
                    >
                      <td class="rank-col num">{{ index + 1 }}</td>
                      <td class="num">
                        <!-- 키보드로도 고를 수 있게 버튼으로 둠 (줄 어디를 눌러도 같은 동작) -->
                        <button
                          class="window-button"
                          :aria-pressed="selectedWindow === item.windowStart"
                          @click.stop="pickTopWindow(item)"
                        >
                          {{ shortTime(item.windowStart) }}~{{ formatHourMinute(item.windowEnd) }}
                        </button>
                      </td>
                      <td class="right-text num">
                        <strong :class="{ over: isOverTarget(item.peakKw) }">
                          {{ formatKw(item.peakKw) }} kW
                        </strong>
                      </td>
                      <td>
                        <span v-if="item.status" aria-hidden="true">
                          {{ PIN_INFO[item.status]?.shape }}
                        </span>
                        {{ windowNote(item) }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <!-- 평균보다 높은 구간 -->
              <div
                v-else-if="windowMode === 'aboveAverage' && aboveAverageRanges.length"
                class="table-box"
              >
                <table class="table window-table">
                  <thead>
                    <tr>
                      <th class="rank-col">순위</th>
                      <th>대상 시간</th>
                      <th class="right-text">최대 피크</th>
                      <th class="right-text">평균 대비</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(item, index) in aboveAverageRanges"
                      :key="item.key"
                      class="window-row"
                      :class="{ active: selectedWindow === item.key }"
                      @click="pickRange(item)"
                    >
                      <td class="rank-col num">{{ index + 1 }}</td>
                      <td class="num">
                        <button
                          class="window-button"
                          :aria-pressed="selectedWindow === item.key"
                          @click.stop="pickRange(item)"
                        >
                          {{ item.date.slice(5) }} {{ hourRangeText(item) }}
                        </button>
                      </td>
                      <td class="right-text num">
                        <strong :class="{ over: isOverTarget(item.peakKw) }">
                          {{ formatKw(item.peakKw) }} kW
                        </strong>
                      </td>
                      <td class="right-text num">+{{ formatOneDecimal(item.abovePercent) }}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-else class="daily-note">이 기간에는 구간 기록이 없습니다.</p>
            </template>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%; /* 화면 높이에 고정: 그래프 · 카드가 남은 높이에 맞춰 줄어들어 스크롤이 생기지 않음 */
  padding: 20px;
  overflow: hidden;
  /* 오른쪽 열 폭. 조회 조건의 "보기" 칸도 이 값으로 폭을 맞춤 (좁은 화면 값은 맨 아래 @media) */
  --right-width: 480px;
}
.crumb-note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}

/* 조회 조건 */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
  padding-top: 10px;
  padding-bottom: 10px;
}
/* 보기 체크박스: 아래 오른쪽 열(.right)과 왼쪽 끝을 맞춘 칸에 4개를 한 줄로.
   폭 = 오른쪽 열 폭 − 이 카드의 오른쪽 테두리(1px)와 안쪽 여백(16px).
   그러면 왼쪽 구분선은 아래 카드의 왼쪽 테두리와, "보기" 글자는 아래 카드 제목과 같은 세로줄에 옴 */
.view-field {
  flex: none;
  width: calc(var(--right-width) - 17px);
  margin-left: auto;
  padding-left: 16px;
  border-left: 1px solid var(--line);
}
.checks {
  display: flex;
  gap: 14px;
}
.checks .check {
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap; /* "공장 소비"가 두 줄로 꺾이지 않게 */
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
.check {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}
.check input {
  width: 15px;
  height: 15px;
  accent-color: var(--ink);
}
/* 제목 옆 조회 중 안내 */
.loading-note {
  font-size: 13px;
  color: var(--mute);
}
.date-error {
  width: 100%;
  color: var(--crit);
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
.over {
  color: var(--crit);
}

/* 기간 요약 */
.summary {
  display: flex;
  padding: 7px 16px; /* 요약 숫자 줄은 세로를 30% 줄임 (그만큼 그래프에 줌) */
}
.summary > div {
  flex: 1;
  min-width: 0;
  margin-right: 16px;
  padding-right: 16px;
  border-right: 1px solid var(--line);
}
.summary > div:last-child {
  margin-right: 0;
  padding-right: 0;
  border-right: 0;
}
.summary dt {
  font-size: 11px;
  line-height: 1.35;
  color: var(--mute);
}
.summary dd {
  font-size: 17px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}
.summary small {
  margin-left: 4px;
  font-size: 11px;
  font-weight: 400;
  color: var(--ink-2);
}
/* 목표를 넘은 구간 옆 비율 (%) */
.summary .pct {
  margin-left: 4px;
  font-size: 13px;
  color: var(--ink-2);
}
.summary p {
  overflow: hidden;
  font-size: 11px;
  line-height: 1.35;
  color: var(--mute);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 그래프와 오른쪽 열 */
.main {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}
/* 왼쪽: 전력 카드가 높이를 모두 채움 */
.charts {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  min-height: 0;
}
.power-card {
  display: flex;
  flex: 3;
  flex-direction: column;
  min-height: 0;
}
/* 일별 최대 피크: 전력 카드 아래에 얇게 (전력 3 : 일별 1) */
.daily-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding-top: 8px;
  padding-bottom: 8px;
}
.daily-card > :not(.fit) {
  flex-shrink: 0;
}
.daily-card .chart-title {
  flex-wrap: wrap;
  row-gap: 2px;
  font-size: 13px;
}
.daily-note {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 12px;
  color: var(--mute);
}
.daily-note.error {
  color: var(--crit);
}
.chart-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 15px;
  font-weight: 700;
}
/* 전력 그래프 제목은 크게 */
.chart-title.main-chart {
  font-size: 18px;
}
.chart-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}
.chart-title small {
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
}
.chart-note {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--legend-text);
}

.right {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 10px;
  width: var(--right-width); /* 가로는 그대로 */
  min-height: 0;
}
/* 남은 높이를 채우는 그래프(.fit)만 줄어들고, 제목 · 고정 높이 그래프 · 안내 글은 높이를 지킴 */
.power-card > :not(.fit),
.pattern-card > :not(.fit, .rank-scroll) {
  flex-shrink: 0;
}
/* 오른쪽 열: 시간대별 사용 패턴 3 : 피크가 높았던 구간 2로 높이를 나눔
   (1 : 1이면 사용 패턴 카드는 제목 · 안내 글이 자리를 차지해 막대그래프가 너무 납작해짐. 표는 안에서 스크롤)
   (추천 처리별 결과 · 눈에 띄는 구간 카드는 2026-11-20에 뺌) */
.pattern-card {
  display: flex;
  flex: 3 1 0;
  flex-direction: column;
  min-height: 0;
}

/* 피크가 높았던 구간 (피크 분석 화면에서 옮겨 옴) */
.windows-card {
  display: flex;
  flex: 2 1 0;
  flex-direction: column;
  min-height: 0;
}
.windows-card > :not(.table-box, .daily-note) {
  flex-shrink: 0;
}
/* 제목 · 버튼은 시간대별 사용 패턴 카드와 같은 크기 */
.windows-card .card-head {
  align-items: center;
  gap: 8px;
}
.windows-card .card-title {
  font-size: 13px;
  white-space: nowrap;
}
.window-modes {
  margin-left: auto;
}
.window-modes button {
  height: 22px;
  padding: 0 5px;
  font-size: 11px;
  white-space: nowrap;
}
.mode-note {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 10.5px;
  color: var(--mute);
}
.mode-note .more-link {
  margin-left: auto;
  font-size: 10.5px;
  white-space: nowrap;
}
/* 표 상자: 남는 높이를 차지하고, 넘치는 줄은 이 안에서 스크롤 */
.table-box {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-color: var(--line-strong) transparent;
}
.window-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}
/* 머리글은 스크롤해도 위에 붙어 있음 */
.window-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 3px 10px 3px 0;
  background: var(--card);
  font-size: 10.5px;
  text-align: left;
}
.window-table td {
  padding: 3px 10px 3px 0;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}
.window-table th:last-child,
.window-table td:last-child {
  padding-right: 0;
}
.window-table .right-text {
  text-align: right;
}
/* 누르면 전력 그래프에 표시되는 줄 */
.window-row {
  cursor: pointer;
}
.window-row:hover {
  background: var(--bg);
}
.window-row.active {
  background: var(--bg);
  font-weight: 700;
}
/* 고른 줄은 왼쪽에 굵은 줄 (안 고른 줄도 같은 폭을 투명하게 두어 글자가 밀리지 않게 함) */
.window-row td:first-child {
  padding-left: 6px;
  border-left: 4px solid transparent;
}
.window-row.active td:first-child {
  border-left-color: var(--ink);
}
.window-button {
  padding: 0;
  background: none;
  border: 0;
  font: inherit;
  color: inherit;
  text-decoration: underline;
  text-decoration-color: var(--line-strong);
  text-underline-offset: 3px;
}
.rank-col {
  width: 36px;
  color: var(--mute);
}
/* 오른쪽 카드는 세로 여백을 줄여 그래프 · 목록이 쓸 높이를 확보 (가로 여백은 그대로) */
.right .card {
  padding-top: 9px;
  padding-bottom: 9px;
}
.right .card-head {
  margin-bottom: 4px;
}
.right .foot-note {
  font-size: 11px;
  line-height: 1.35;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.foot-note {
  font-size: 12px;
  color: var(--mute);
}
.pin-toggle {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
}
/* 전력 제목 줄: 화면이 좁아 한 줄에 안 들어가면 범례가 다음 줄로 내려감 */
.chart-title.main-chart {
  flex-wrap: wrap;
  row-gap: 2px;
}
.chart-title.main-chart .pin-toggle {
  margin-left: 12px; /* 범례가 오른쪽으로 붙도록 빈 공간은 범례 앞에 둠 */
}
.power-legend {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--legend-text);
}
.power-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
/* 면은 네모, 공장 소비(선)는 가는 막대. 그래프에서 칠한 진하기와 비슷하게 */
.power-swatch {
  width: 10px;
  height: 10px;
}
.power-swatch.grid {
  background: var(--src-grid);
  opacity: 0.6;
}
.power-swatch.solar {
  background: var(--src-solar);
  opacity: 0.85;
}
.power-swatch.discharge {
  background: var(--src-ess);
  opacity: 0.8;
}
.power-swatch.charge {
  background: var(--src-ess);
  opacity: 0.4;
}
.power-swatch.load {
  height: 2px;
  background: var(--ink);
}
/* 피크 초과 점 (그래프의 빨간 점과 같음) */
.power-swatch.exceed {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--crit);
}
/* 일별 최대 피크 범례 (막대 색과 같음) */
.power-swatch.expected {
  background: var(--line-strong);
}
.power-swatch.actual {
  background: var(--src-grid);
}
.power-swatch.over {
  background: var(--crit);
}
.pin-error {
  font-size: 12px;
  color: var(--crit);
}

/* 시간대별 사용 패턴: 보기 방식 전환 */
.view-switch {
  margin-left: auto;
}
.view-switch button {
  height: 28px;
  padding: 0 10px;
  font-size: 13px;
}
/* 시간대별 사용 패턴 카드: 제목 · 버튼 · 안내 글을 작게 해서 그래프 자리를 확보 */
.pattern-card .card-head {
  align-items: center;
  gap: 8px;
}
.pattern-card .card-title {
  font-size: 13px;
  white-space: nowrap;
}
.pattern-card .presets button {
  height: 22px;
  padding: 0 5px;
  font-size: 11px;
  white-space: nowrap; /* 버튼 글자가 두 줄로 깨지지 않게 */
}
.pattern-card .step-switch {
  margin-left: auto;
}
.pattern-card .step-switch + .view-switch {
  margin-left: 0;
}
.pattern-card .foot-note {
  height: calc(
    10.5px * 1.3 * 3
  ); /* 항상 3줄 높이: 1시간 간격일 때만 붙는 셋째 줄이 있어도 그래프 크기가 그대로 */
  overflow: hidden;
  font-size: 10.5px;
  line-height: 1.3;
}
.pattern-card .foot-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap; /* 한 문장이 두 줄로 늘어나 높이가 바뀌지 않게 */
}
.foot-line {
  display: block;
}
/* 시간대별 사용 패턴: 막대 간격 고르기 */
/* 순위 보기 */
/* 순위 목록: 카드의 남은 높이를 채우고, 넘치는 줄은 이 안에서 스크롤 (화면이 낮아도 잘리지 않게) */
.rank-scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  scrollbar-color: var(--line-strong) transparent;
}
.ranks {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ranks li {
  display: grid;
  grid-template-columns: 22px 40px minmax(0, 1fr) 76px 60px;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.rank {
  color: var(--mute);
  text-align: right;
}
.rank-track {
  height: 10px;
  background: var(--line);
}
.rank-bar {
  display: block;
  height: 100%;
  background: var(--src-grid);
}
.rank-kw {
  text-align: right;
}
.rank-period {
  font-size: 12px;
  color: var(--mute);
}
.rank-period.peak {
  font-weight: 700;
  color: var(--ink-2);
}

/* 화면이 좁으면 오른쪽 열을 줄여 그래프 자리를 남김 */
@media (max-width: 1600px) {
  .page {
    --right-width: 400px;
  }
  /* 오른쪽 열에 맞춘 "보기" 칸도 좁아지므로, 4개가 한 줄에 들어가게 글자 · 체크 칸 · 간격을 줄임 */
  .checks {
    gap: 10px;
  }
  .checks .check {
    gap: 4px;
    font-size: 11px;
  }
  .checks .check input {
    width: 13px;
    height: 13px;
  }
  /* 1366 화면에서 왼쪽 메뉴를 펼쳐도 기간과 보기가 한 줄에 들어가게 기간 쪽 여백을 줄임 (보기가 아랫줄로 내려가지 않게) */
  .filters {
    column-gap: 16px;
  }
  .filters .presets button {
    padding: 0 10px;
  }
}
</style>
