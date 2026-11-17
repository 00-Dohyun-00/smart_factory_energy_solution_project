<!--
  분석 > 추이 분석
  조회 조건 → 기간 요약 → 왼쪽에 같은 시간 축을 쓰는 그래프 세 개(전력 · 배터리 잔량 · 온도와 날씨)
  → 오른쪽에 시간대별 사용 패턴 · 추천 처리별 결과 · 눈에 띄는 구간

  요약 숫자, 시간대별 평균, 처리별 결과는 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
import FitChart from '@/components/FitChart.vue'
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

const INTERVALS = [
  { value: 'QUARTER', label: '15분' },
  { value: 'HOUR', label: '1시간' },
  { value: 'DAY', label: '1일' },
]
// "보기"에서 켜고 끌 수 있는 항목
const ITEMS = [
  { key: 'load', label: '공장 소비' },
  { key: 'grid', label: '한전 구매' },
  { key: 'solar', label: '태양광' },
  { key: 'ess', label: 'ESS 충·방전' },
  { key: 'soc', label: '배터리 잔량' },
  { key: 'temp', label: '온도·날씨' },
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
const preset = ref('week') // today(오늘) | week(최근 7일) | month(이번 달) | custom(직접 입력)
const fromDate = ref('') // 예: '2026-11-10'
const toDate = ref('')
const interval = ref('HOUR')
const shown = ref(ITEMS.map((item) => item.key)) // 켜져 있는 항목들
const focusAt = ref('') // 이벤트 히스토리에서 고른 시각 (그래프에 세로선으로 표시)
const hourlyView = ref('time') // 시간대별 사용 패턴 보기: time(시간 순 막대) | rank(많이 쓴 순위)
// 이벤트 히스토리 필터
const historyKind = ref('ALL') // ALL | DECISION(추천) | HIGHLIGHT(구간)
const historyStatus = ref('ALL') // ALL | APPLIED | AUTO | IGNORED | RECOMMENDED (추천에만 해당)
const historyResult = ref('ALL') // ALL | DEFENDED | EXCEEDED (추천에만 해당)
const showPins = ref(true) // 전력 그래프 위에 추천 기호를 보일지

// "오늘"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함 (시뮬레이터가 배속으로 돌 수 있음)
const serverDate = computed(() => realtimeStore.status?.recordedAt.slice(0, 10) ?? '')

const dateError = computed(() =>
  fromDate.value && toDate.value && fromDate.value > toDate.value
    ? '시작일이 종료일보다 늦습니다.'
    : '',
)

// '2026-11-14'에서 며칠 전 날짜 구하기
function daysBefore(date, days) {
  const [year, month, day] = date.split('-').map(Number)
  const result = new Date(year, month - 1, day - days)
  const pad = (n) => String(n).padStart(2, '0')
  return `${result.getFullYear()}-${pad(result.getMonth() + 1)}-${pad(result.getDate())}`
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  toDate.value = serverDate.value
  if (name === 'today') fromDate.value = serverDate.value
  if (name === 'week') fromDate.value = daysBefore(serverDate.value, 6)
  if (name === 'month') fromDate.value = `${serverDate.value.slice(0, 8)}01` // 이번 달 1일
}

function load(showLoading = true) {
  if (!fromDate.value || !toDate.value || dateError.value) return
  analysisStore.fetchTrend(fromDate.value, toDate.value, interval.value, showLoading)
  // 같은 기간의 추천 이력도 받아 그래프 위에 핀으로 꽂음
  historyStore.fetchDecisions(fromDate.value, toDate.value, showLoading)
}

// 기간이나 간격이 바뀌면 다시 조회
watch([fromDate, toDate, interval], () => {
  focusAt.value = ''
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
const points = computed(() => trend.value?.points ?? [])
const summary = computed(() => trend.value?.summary ?? null)

// 한전 평균 구매전력이 목표 피크의 몇 %인지 (표시용 비율)
const avgGridPercent = computed(() =>
  summary.value ? (summary.value.avgGridKw / trend.value.targetPeakKw) * 100 : null,
)

// '2026-11-11T09:30:00' → '11-11 09:30'
const shortTime = (dateTime) => formatMonthDayTime(dateTime).slice(0, 11)

// 눈에 띄는 구간의 값: 단위에 맞는 자릿수로
const highlightValue = (item) =>
  item.unit === 'kW' ? formatKw(item.value) : formatOneDecimal(item.value)

function toggleFocus(at) {
  focusAt.value = focusAt.value === at ? '' : at
}

// ---- 이벤트 히스토리 ----
// 추천(몇 시에 무슨 추천이 나와 어떻게 처리됐나)과 눈에 띄는 구간을 한 줄씩 로그처럼 모아 최신 순으로 보여 줌
const RESULT_TEXTS = { DEFENDED: '목표 이내', EXCEEDED: '목표 초과' }

// 처리 글자 (자동은 실제 제어가 아니라 시뮬레이션이라는 점을 함께 적음)
function processText(item) {
  const label = PIN_INFO[item.status].label
  if (item.status === 'AUTO') return `${label} (시뮬레이션)`
  return item.appliedBy ? `${label} · ${item.appliedBy}` : label
}

const historyRows = computed(() => {
  const decisions = periodDecisions.value.map((item) => {
    const power = item.action === 'IDLE' ? '' : ` ${formatKw(item.powerKw)} kW`
    const info = PIN_INFO[item.status]
    return {
      key: `decision-${item.id}`,
      at: item.decidedAt,
      kind: 'DECISION',
      status: item.status,
      resultStatus: item.resultStatus,
      tag: '추천',
      shape: info.shape,
      text: `${item.ruleLabel} · ${ACTION_LABELS[item.action]}${power}`, // 구분 칸에 "추천"이 있어 뒤에 붙이지 않음
      // 처리: 적용 · 운영자 / 자동 (시뮬레이션) / 넘김 · 운영자 / 추천 중
      detail: processText(item),
      // 결과: 구간이 끝났으면 실제 피크와 목표 달성 여부
      value: item.resultStatus
        ? `${RESULT_TEXTS[item.resultStatus]} ${formatKw(item.resultPeakKw)} kW`
        : '구간 진행 중',
      over: item.resultStatus === 'EXCEEDED',
    }
  })
  const highlights = (trend.value?.highlights ?? []).map((item) => ({
    key: `highlight-${item.label}`,
    at: item.at,
    kind: 'HIGHLIGHT',
    status: null,
    resultStatus: null,
    tag: '구간',
    shape: item.level === 'CRITICAL' ? '■' : '',
    text: item.label,
    detail: '',
    value: `${highlightValue(item)} ${item.unit}`,
    over: item.level === 'CRITICAL',
  }))
  return [...decisions, ...highlights].sort((a, b) => (a.at < b.at ? 1 : -1))
})

// 필터를 적용한 목록. 처리·결과 조건을 고르면 그 값이 없는 구간 기록은 빠짐
const filteredHistory = computed(() =>
  historyRows.value.filter((row) => {
    if (historyKind.value !== 'ALL' && row.kind !== historyKind.value) return false
    if (historyStatus.value !== 'ALL' && row.status !== historyStatus.value) return false
    if (historyResult.value !== 'ALL' && row.resultStatus !== historyResult.value) return false
    return true
  }),
)
const isHistoryFiltered = computed(
  () =>
    historyKind.value !== 'ALL' || historyStatus.value !== 'ALL' || historyResult.value !== 'ALL',
)
function resetHistoryFilter() {
  historyKind.value = 'ALL'
  historyStatus.value = 'ALL'
  historyResult.value = 'ALL'
}

// ---- 그래프 공통 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 값을 그대로 적습니다.
const COLOR = {
  ink: '#14181c', // --ink
  ink2: '#3c444c', // --ink-2
  mute: '#646e77', // --mute
  faint: '#98a1a9', // --faint
  line: '#dde1e5', // --line
  crit: '#c8372d', // --crit
  grid: '#5b6472', // --src-grid
  solar: '#d19a00', // --src-solar
  ess: '#0f8f8a', // --src-ess
}

const isDaily = computed(() => trend.value?.interval === 'DAY') // 하루에 점 하나
const isSingleDay = computed(() => trend.value?.from === trend.value?.to) // 하루만 조회

// 가로축 글자: 1일 간격이면 '11-10', 그 밖에는 '11-10 09:00'
const labels = computed(() =>
  points.value.map((item) =>
    isDaily.value ? item.recordedAt.slice(5, 10) : shortTime(item.recordedAt),
  ),
)

// 날짜('11-10') → 날씨, 날짜 → 몇 번째 날인지
const weatherByDate = computed(() => {
  const result = {}
  for (const item of trend.value?.weather ?? []) result[item.date.slice(5)] = item.text
  return result
})
const dayNumbers = computed(() => {
  const result = {}
  ;(trend.value?.weather ?? []).forEach((item, index) => {
    result[item.date.slice(5)] = index
  })
  return result
})

// 날짜가 많으면 가로축 글자가 겹치므로 며칠에 하나씩만 적음
const dayStep = computed(() => Math.ceil((trend.value?.weather.length ?? 1) / 10))

// 그래프 글자 크기 (px). 멀리서도 읽히게 키우고, 그만큼 그래프 둘레 여백(grid)을 넓혀 겹치지 않게 함
const FONT = {
  axis: 14, // 축 눈금 글자
  legend: 14, // 범례
  mark: 13, // 기준선 글자 (목표 피크, 하한·상한 등)
  pin: 13, // 추천 기호
}
// 기준선 글자 밑에 까는 옅은 흰 바탕: 그래프 선 위에 글자가 겹쳐도 읽히게 함
const MARK_LABEL_BOX = { backgroundColor: 'rgba(255, 255, 255, 0.85)', padding: [1, 4] }

// 세 그래프가 같이 쓰는 가로축. withWeather = true면 날짜 아래에 날씨도 적음
function timeAxis(withWeather) {
  return {
    type: 'category',
    data: labels.value,
    axisLine: { lineStyle: { color: COLOR.line } },
    axisTick: { show: false },
    axisLabel: {
      color: COLOR.mute,
      fontSize: FONT.axis,
      lineHeight: FONT.axis + 4, // 날짜·날씨 두 줄일 때 줄 간격
      margin: 10, // 축과 글자 사이
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
        const date = value.slice(0, 5)
        const weather = weatherByDate.value[date]
        return withWeather && weather ? `${date}\n${weather}` : date
      },
    },
    // 날짜가 바뀌는 곳에 세로선
    splitLine: {
      show: !isDaily.value && !isSingleDay.value,
      interval: (index, value) => value.endsWith(' 00:00'),
      lineStyle: { color: COLOR.line },
    },
  }
}

const valueAxis = {
  type: 'value',
  axisLabel: { color: COLOR.mute, fontSize: FONT.axis, margin: 10 },
  splitLine: { lineStyle: { color: COLOR.line } },
}

const legend = {
  right: 0,
  top: 0,
  icon: 'rect',
  itemWidth: 12,
  itemHeight: 12,
  itemGap: 16,
  textStyle: { color: COLOR.ink2, fontSize: FONT.legend },
}

// 어떤 시각이 그래프의 어느 칸인지 (그 시각이 들어 있는 칸의 가로축 글자)
function slotLabelOf(at) {
  let found = null
  points.value.forEach((item, index) => {
    if (item.recordedAt <= at) found = labels.value[index]
  })
  return found
}

// "눈에 띄는 구간"에서 고른 시각이 그래프의 어느 칸인지
const focusLabel = computed(() => (focusAt.value ? slotLabelOf(focusAt.value) : null))

// ---- 추천 핀 ----
// 조회 기간 안의 추천 목록 (그래프 핀과 이벤트 히스토리가 같이 씀)
// 다른 화면에서 받아 둔 다른 기간의 이력이 섞이지 않게 기간 안의 것만 씀
const periodDecisions = computed(() => {
  if (!trend.value) return []
  const from = `${trend.value.from}T00:00:00`
  const to = `${trend.value.to}T23:59:59`
  return historyStore.decisions.filter(
    (item) => PIN_INFO[item.status] && item.decidedAt >= from && item.decidedAt <= to,
  )
})

// 조회 기간 안의 추천을 그래프 칸별로 묶음. 예: { '11-13 10:00': [추천, 추천] }
const pinsByLabel = computed(() => {
  const result = {}
  for (const item of periodDecisions.value) {
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
// lines 예: [{ value: 1800, text: '목표 피크 1,800', color: COLOR.crit, dashed: false }]
// withPins = true면 그래프 위쪽 띠에 추천 기호를 적음 (전력 그래프만)
function markLines(lines, withPins = false) {
  const data = lines.map((item) => ({
    yAxis: item.value,
    lineStyle: {
      color: item.color,
      width: item.dashed ? 1.5 : 2,
      type: item.dashed ? 'dashed' : 'solid',
    },
    label: {
      formatter: item.text,
      position: 'insideEndTop',
      color: item.color,
      fontWeight: 'bold',
      fontSize: FONT.mark,
      ...MARK_LABEL_BOX,
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
          color: COLOR.ink2,
          fontSize: FONT.pin,
        },
      })
    }
  }
  if (focusLabel.value) {
    data.push({
      xAxis: focusLabel.value,
      lineStyle: { color: COLOR.ink, width: 2, type: 'solid' },
      label: {
        formatter: '선택',
        position: 'end',
        color: COLOR.ink,
        fontWeight: 'bold',
        fontSize: FONT.mark,
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
    data: points.value.map((item) => item[field]),
    lineStyle: { color, width },
    itemStyle: { color },
  }
}

// ---- 전력 (kW) ----
const powerItems = computed(() => shown.value.filter((key) => key !== 'soc' && key !== 'temp'))

const powerOption = computed(() => {
  const series = []
  if (shown.value.includes('ess')) {
    series.push({
      name: 'ESS 방전(+) · 충전(−)',
      type: 'bar',
      barMaxWidth: 14,
      data: points.value.map((item) => item.essKw),
      itemStyle: { color: COLOR.ess },
    })
  }
  if (shown.value.includes('solar')) series.push(lineSeries('태양광', 'solarKw', COLOR.solar))
  if (shown.value.includes('grid')) series.push(lineSeries('한전 구매', 'gridKw', COLOR.grid))
  if (shown.value.includes('load')) series.push(lineSeries('공장 소비', 'loadKw', COLOR.ink))

  // 목표 피크선은 한전 구매전력과 비교하는 선이라, 한전 구매를 켰을 때만 그림
  const target = trend.value.targetPeakKw
  const lines = shown.value.includes('grid')
    ? [{ value: target, text: `목표 피크 ${formatKw(target)}`, color: COLOR.crit }]
    : []
  if (series.length) series[0].markLine = markLines(lines, true)

  return {
    animation: false,
    // 위쪽에 범례와 추천 핀 기호가 들어가므로 여백을 넉넉히 둠
    grid: { left: 64, right: 24, top: 58, bottom: 32 },
    legend,
    tooltip: {
      trigger: 'axis',
      // 값 목록 아래에 그 칸에 나온 추천을 함께 보여 줌
      formatter: (params) => {
        const label = params[0].axisValue
        const rows = params.map(
          (item) => `${item.marker}${item.seriesName} <strong>${formatKw(item.value)} kW</strong>`,
        )
        return `${label}<br/>${rows.join('<br/>')}${pinTooltip(label)}`
      },
    },
    xAxis: timeAxis(false),
    yAxis: valueAxis,
    series,
  }
})

// ---- 배터리 잔량 (%) ----
const socOption = computed(() => {
  const series = lineSeries('배터리 잔량', 'socPercent', COLOR.ess, 2.5)
  series.markLine = markLines([
    {
      value: trend.value.socLowLimit,
      text: `하한 ${trend.value.socLowLimit}%`,
      color: COLOR.mute,
      dashed: true,
    },
    {
      value: trend.value.socHighLimit,
      text: `상한 ${trend.value.socHighLimit}%`,
      color: COLOR.mute,
      dashed: true,
    },
  ])
  return {
    animation: false,
    grid: { left: 64, right: 24, top: 20, bottom: 32 },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatOneDecimal(value)} %` },
    xAxis: timeAxis(false),
    yAxis: { ...valueAxis, min: 0, max: 100, interval: 50 },
    series: [series],
  }
})

// ---- 온도 (℃) · 날씨 ----
const tempOption = computed(() => {
  const room = lineSeries('배터리실 온도', 'roomTempC', COLOR.ink, 2.5)
  const outside = lineSeries('바깥 기온', 'outsideTempC', COLOR.faint)
  const limit = trend.value.roomTempLimitC
  room.markLine = markLines([{ value: limit, text: `온도 제한 ${limit}℃`, color: COLOR.crit }])
  return {
    animation: false,
    // 가로축에 날짜와 날씨를 두 줄로 적으므로 아래 여백을 넉넉히 둠
    grid: { left: 64, right: 24, top: 40, bottom: 54 },
    legend,
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatOneDecimal(value)} ℃` },
    xAxis: timeAxis(true),
    yAxis: { ...valueAxis, min: 0, max: 40, interval: 10 },
    series: [outside, room],
  }
})

// ---- 시간대별 사용 패턴 ----
// 막대 단위는 고른 기간에 따라 바뀜: 오늘 = 1시간, 최근 7일 = 4시간, 이번 달 = 하루
// (직접 입력한 기간은 길이로 정함: 하루 = 1시간, 7일 이하 = 4시간, 그보다 길면 하루)
// 각 단위의 평균은 백엔드가 계산해 준 값(hourly / hourBlocks / daily)을 그대로 씀
const patternUnit = computed(() => {
  if (preset.value === 'today') return 'HOUR'
  if (preset.value === 'week') return 'BLOCK'
  if (preset.value === 'month') return 'DAY'
  const days = trend.value?.weather.length ?? 1
  if (days <= 1) return 'HOUR'
  return days <= 7 ? 'BLOCK' : 'DAY'
})
const PATTERN_TITLES = {
  HOUR: '시간대별 사용 패턴',
  BLOCK: '시간대별 사용 패턴 (4시간)',
  DAY: '날짜별 사용 패턴',
}
const PATTERN_NOTES = {
  HOUR: '1시간 단위 평균 한전 구매 (kW). 회색 띠는 요금이 비싼 최대부하 시간대입니다.',
  BLOCK: '4시간 단위 평균 한전 구매 (kW). 회색 띠는 최대부하 시간이 들어 있는 구간입니다.',
  DAY: '하루 단위 평균 한전 구매 (kW).',
}

// 막대 목록: [{ key, label, avgGridKw, period, isPeak }]
const patternItems = computed(() => {
  if (!trend.value) return []
  if (patternUnit.value === 'BLOCK') {
    return (trend.value.hourBlocks ?? []).map((item) => ({
      key: `b${item.fromHour}`,
      label: `${item.fromHour}~${item.toHour}시`,
      avgGridKw: item.avgGridKw,
      period: item.peak ? '최대부하 포함' : '',
      isPeak: item.peak,
    }))
  }
  if (patternUnit.value === 'DAY') {
    return (trend.value.daily ?? []).map((item) => ({
      key: item.date,
      label: item.date.slice(5),
      avgGridKw: item.avgGridKw,
      period: '',
      isPeak: false,
    }))
  }
  return trend.value.hourly.map((item) => ({
    key: `h${item.hour}`,
    label: `${item.hour}시`,
    avgGridKw: item.avgGridKw,
    period: PERIOD_LABELS[item.period] ?? '',
    isPeak: item.period === 'PEAK',
  }))
})

// 순위 보기: 같은 단위의 평균을 큰 순서로 정렬만 함. 막대 길이는 1위를 100%로 본 비율(표시용)
const RANK_COUNT = 10 // 순위 보기에서 보여 줄 개수
const hourlyRanks = computed(() => {
  const sorted = [...patternItems.value].sort((a, b) => b.avgGridKw - a.avgGridKw)
  const top = sorted[0]?.avgGridKw || 1
  return sorted.slice(0, RANK_COUNT).map((item, index) => ({
    ...item,
    rank: index + 1,
    width: (item.avgGridKw / top) * 100,
  }))
})

const hourlyOption = computed(() => {
  const items = patternItems.value
  const names = items.map((item) => item.label)

  // 최대부하 시간대(이어진 막대들)를 회색 띠로 표시. 시간대 구분은 백엔드가 준 값
  const areas = []
  let start = null
  items.forEach((item, index) => {
    const isPeak = item.isPeak
    if (isPeak && start === null) start = index
    const isLast = index === items.length - 1
    if (start !== null && (!isPeak || isLast)) {
      const end = isPeak ? index : index - 1
      areas.push([
        { xAxis: names[start], itemStyle: { color: 'rgba(20, 24, 28, 0.07)' } },
        { xAxis: names[end] },
      ])
      start = null
    }
  })

  const target = trend.value.targetPeakKw
  let labelStep = 1
  if (patternUnit.value === 'HOUR') labelStep = 6
  if (patternUnit.value === 'DAY') labelStep = Math.ceil(items.length / 6)
  return {
    animation: false,
    grid: { left: 60, right: 16, top: 28, bottom: 32 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      axisLabel: {
        color: COLOR.mute,
        fontSize: FONT.axis,
        margin: 10,
        // 1시간 단위는 6시간마다, 하루 단위는 날짜 수에 맞춰 몇 개마다, 4시간 단위는 모두 적음
        interval: (index) => index % labelStep === 0,
      },
    },
    // 그래프가 낮아 눈금 글자가 붙지 않게 눈금 수를 줄임
    yAxis: { ...valueAxis, splitNumber: 3 },
    series: [
      {
        name: '평균 한전 구매',
        type: 'bar',
        barCategoryGap: '25%',
        data: items.map((item) => item.avgGridKw),
        itemStyle: { color: COLOR.grid },
        markArea: { silent: true, data: areas },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.crit, width: 2, type: 'solid' },
          label: {
            formatter: `목표 ${formatKw(target)}`,
            position: 'insideEndTop',
            color: COLOR.crit,
            fontWeight: 'bold',
            fontSize: FONT.mark,
            ...MARK_LABEL_BOX,
          },
          data: [{ yAxis: target }],
        },
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
      <p class="crumb-note">전력, 배터리, 온도·날씨를 같은 시간 축에 놓고 봅니다</p>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button :class="{ active: preset === 'today' }" @click="applyPreset('today')">
            오늘
          </button>
          <button :class="{ active: preset === 'week' }" @click="applyPreset('week')">
            최근 7일
          </button>
          <button :class="{ active: preset === 'month' }" @click="applyPreset('month')">
            이번 달
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

      <div class="field">
        <span class="field-label">간격</span>
        <div class="presets" role="group" aria-label="간격">
          <button
            v-for="item in INTERVALS"
            :key="item.value"
            :class="{ active: interval === item.value }"
            @click="interval = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="field">
        <span class="field-label">보기</span>
        <label v-for="item in ITEMS" :key="item.key" class="check">
          <input v-model="shown" type="checkbox" :value="item.key" />
          {{ item.label }}
        </label>
      </div>

      <p v-if="analysisStore.loading" class="loading-note">불러오는 중입니다.</p>
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
          <dd :class="{ over: summary.maxPeakKw > trend.targetPeakKw }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">
            {{ shortTime(summary.maxPeakAt) }},
            {{ summary.maxPeakKw > trend.targetPeakKw ? '목표 초과' : '목표 이내' }}
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
        <div>
          <dt>배터리 잔량 범위</dt>
          <dd>
            <span class="num">
              {{ formatOneDecimal(summary.socMinPercent) }} ~
              {{ formatOneDecimal(summary.socMaxPercent) }}
            </span>
            <small>%</small>
          </dd>
          <p class="num">최저 {{ shortTime(summary.socMinAt) }}</p>
        </div>
        <div>
          <dt>배터리실 최고 온도</dt>
          <dd>
            <span class="num">{{ formatOneDecimal(summary.roomTempMaxC) }}</span> <small>℃</small>
          </dd>
          <p class="num">
            {{ shortTime(summary.roomTempMaxAt) }}, 제한 {{ trend.roomTempLimitC }}℃
          </p>
        </div>
      </dl>

      <div class="main">
        <!-- 왼쪽: 같은 시간 축을 쓰는 그래프 세 개 -->
        <section class="card charts">
          <template v-if="powerItems.length">
            <h2 class="chart-title">
              전력 <small>kW</small>
              <span v-if="showPins" class="chart-note">
                위쪽 기호 = 추천 (● 적용 · ◇ 자동 · ✕ 넘김 · ○ 추천 중), 올리면 자세히
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
            <!-- 남은 높이를 전력 2 : 잔량 1 : 온도 1.3 으로 나눠 채움 -->
            <FitChart
              :key="`${powerItems.join()}-${showPins}`"
              class="fit-power"
              :option="powerOption"
              :min-height="200"
            />
          </template>

          <template v-if="shown.includes('soc')">
            <h2 class="chart-title">
              배터리 잔량 <small>%</small>
              <span class="chart-note">점선은 잔량 하한과 상한</span>
            </h2>
            <FitChart class="fit-soc" :option="socOption" :min-height="100" />
          </template>

          <template v-if="shown.includes('temp')">
            <h2 class="chart-title">
              온도 <small>℃</small> · 날씨
              <span class="chart-note">날짜 아래에 그날 날씨</span>
            </h2>
            <FitChart class="fit-temp" :option="tempOption" :min-height="130" />
          </template>

          <p v-if="!shown.length" class="state">"보기"에서 볼 항목을 하나 이상 골라 주세요.</p>
        </section>

        <!-- 오른쪽 -->
        <div class="right">
          <section class="card">
            <div class="card-head">
              <h2 class="card-title">{{ PATTERN_TITLES[patternUnit] }}</h2>
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
              <!-- key: 단위가 바뀌면 막대 개수가 바뀌므로 그래프를 새로 만듦 -->
              <Chart :key="patternUnit" :option="hourlyOption" :height="190" />
              <p class="foot-note">{{ PATTERN_NOTES[patternUnit] }}</p>
            </template>
            <template v-else>
              <ol class="ranks">
                <li v-for="item in hourlyRanks" :key="item.key">
                  <span class="rank num">{{ item.rank }}</span>
                  <strong class="rank-hour num">{{ item.label }}</strong>
                  <span class="rank-track" aria-hidden="true">
                    <span class="rank-bar" :style="{ width: `${item.width}%` }"></span>
                  </span>
                  <span class="rank-kw num">{{ formatKw(item.avgGridKw) }} kW</span>
                  <span class="rank-period" :class="{ peak: item.isPeak }">{{ item.period }}</span>
                </li>
              </ol>
              <p class="foot-note">
                한전 구매가 많은 순서로 {{ hourlyRanks.length }}개 ({{
                  patternUnit === 'DAY'
                    ? '날짜별'
                    : patternUnit === 'BLOCK'
                      ? '4시간 단위'
                      : '시각별'
                }}
                평균)
              </p>
            </template>
          </section>

          <!-- 이벤트 히스토리: 추천과 눈에 띄는 구간을 시간순 로그로 (누르면 그래프에 그 시각을 표시) -->
          <section class="card history-card">
            <div class="card-head">
              <h2 class="card-title">이벤트 히스토리</h2>
              <span class="note">
                <span class="num">{{ filteredHistory.length }}건</span> · 최신 순 · 누르면 그래프에
                그 시각을 표시
              </span>
              <button v-if="isHistoryFiltered" class="filter-reset" @click="resetHistoryFilter">
                필터 초기화
              </button>
            </div>
            <p v-if="historyStore.errorMessage" class="pin-error" role="alert">
              추천 기록을 불러오지 못했습니다. ({{ historyStore.errorMessage }})
            </p>
            <!-- 칸: 시각 | 구분 | 내용 | 처리 | 결과 (머리줄과 각 줄이 같은 칸 너비를 씀) -->
            <!-- 머리줄의 구분 · 처리 · 결과 칸이 곧 필터 (처리·결과는 추천에만 있는 값이라 고르면 구간 기록은 빠짐) -->
            <div v-if="historyRows.length" class="history">
              <div class="history-head">
                <span class="col-time">시각</span>
                <span class="col-tag">
                  <select
                    v-model="historyKind"
                    class="head-select"
                    :class="{ on: historyKind !== 'ALL' }"
                    aria-label="구분 필터"
                  >
                    <option value="ALL">구분</option>
                    <option value="DECISION">추천</option>
                    <option value="HIGHLIGHT">구간</option>
                  </select>
                </span>
                <span class="col-main">내용</span>
                <span class="col-detail">
                  <select
                    v-model="historyStatus"
                    class="head-select"
                    :class="{ on: historyStatus !== 'ALL' }"
                    aria-label="처리 필터"
                  >
                    <option value="ALL">처리</option>
                    <option value="APPLIED">적용</option>
                    <option value="AUTO">자동</option>
                    <option value="IGNORED">넘김</option>
                    <option value="RECOMMENDED">추천 중</option>
                  </select>
                </span>
                <span class="col-value">
                  <select
                    v-model="historyResult"
                    class="head-select"
                    :class="{ on: historyResult !== 'ALL' }"
                    aria-label="결과 필터"
                  >
                    <option value="ALL">결과</option>
                    <option value="DEFENDED">목표 이내</option>
                    <option value="EXCEEDED">목표 초과</option>
                  </select>
                </span>
              </div>
              <ul v-if="filteredHistory.length">
                <li v-for="row in filteredHistory" :key="row.key">
                  <button
                    class="history-row"
                    :class="{ active: focusAt === row.at }"
                    :aria-pressed="focusAt === row.at"
                    @click="toggleFocus(row.at)"
                  >
                    <span class="history-time col-time num">{{ shortTime(row.at) }}</span>
                    <span class="col-tag">
                      <span class="history-tag" :class="{ decision: row.tag === '추천' }">
                        {{ row.tag }}
                      </span>
                    </span>
                    <span class="history-main col-main" :title="row.text">
                      <span
                        v-if="row.shape"
                        :class="{ over: row.over && row.tag === '구간' }"
                        aria-hidden="true"
                        >{{ row.shape }}</span
                      >
                      {{ row.text }}
                    </span>
                    <span class="history-detail col-detail" :title="row.detail">{{
                      row.detail || '—'
                    }}</span>
                    <strong class="history-value col-value num" :class="{ over: row.over }">
                      {{ row.value }}
                    </strong>
                  </button>
                </li>
              </ul>
              <p v-else class="history-empty">조건에 맞는 기록이 없습니다. 필터를 바꿔 보세요.</p>
            </div>
            <p v-else class="history-empty">이 기간에는 기록된 이벤트가 없습니다.</p>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 화면 높이를 넘지 않게 고정 (이벤트 히스토리가 길어지면 그 카드 안에서만 스크롤) */
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 20px;
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
.presets {
  display: flex;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  overflow: hidden;
}
.presets button {
  height: 32px;
  padding: 0 12px;
  background: var(--card);
  border: 0;
  color: var(--ink-2);
}
.presets button + button {
  border-left: 1px solid var(--line-strong);
}
.presets button:hover {
  background: var(--bg);
}
.presets button.active {
  background: var(--ink);
  font-weight: 700;
  color: var(--card);
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
.loading-note {
  margin-left: auto;
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
.retry {
  height: 34px;
  padding: 0 14px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink-2);
}
.over {
  color: var(--crit);
}

/* 기간 요약 (추이 분석 · 피크 분석이 같은 모양·같은 크기를 씀. 한쪽을 고치면 다른 쪽도 같이 고침) */
.summary {
  display: flex;
  padding: 16px 20px;
}
.summary > div {
  flex: 1;
  min-width: 0;
  margin-right: 20px;
  padding-right: 20px;
  border-right: 1px solid var(--line);
}
.summary > div:last-child {
  margin-right: 0;
  padding-right: 0;
  border-right: 0;
}
.summary dt {
  font-size: 14px;
  color: var(--ink-2);
}
.summary dd {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
}
.summary small {
  margin-left: 4px;
  font-size: 16px;
  font-weight: 400;
  color: var(--ink-2);
}
.summary .pct {
  margin-left: 6px;
  font-size: 20px;
  font-weight: 700;
  color: var(--ink-2);
}
.summary p {
  overflow: hidden;
  font-size: 13px;
  color: var(--mute);
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 카드·그래프 제목: 멀리서도 잘 보이게 키움 (두 분석 화면 같은 크기) */
.card-title {
  font-size: 19px;
}

/* 그래프와 오른쪽 열 */
.main {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}
/* 왼쪽 그래프 카드: 세로 flex로 두어 그래프들이 남은 높이를 나눠 가짐 */
.charts {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.fit-power {
  flex: 2;
}
.fit-soc {
  flex: 1;
}
.fit-temp {
  flex: 1.3;
}
.chart-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 19px;
  font-weight: 700;
}
.chart-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}
.chart-title small {
  font-size: 14px;
  font-weight: 400;
  color: var(--mute);
}
.chart-note {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: var(--mute);
}

/* 오른쪽 열(사용 패턴 · 이벤트 히스토리)은 본문 폭의 32%, 나머지는 그래프 */
.right {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 16px;
  width: 32%;
  min-height: 0;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
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
/* 순위 보기 */
.ranks {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ranks li {
  display: grid;
  grid-template-columns: 22px 64px minmax(0, 1fr) 76px 84px;
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

/* 이벤트 히스토리: 오른쪽 열의 남은 높이를 채우고, 기록이 많으면 카드 안에서만 스크롤 */
.history-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.history {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
/* 머리줄과 각 줄이 같은 칸 너비를 씀: 시각 | 구분 | 내용 | 처리 | 결과
   칸 사이 간격 없이 붙여서, 칸마다 칠한 옅은 바탕이 위아래로 이어진 띠처럼 보이게 함 */
.history-head,
.history-row {
  display: grid;
  grid-template-columns: 104px 62px minmax(0, 1fr) 128px 128px;
  align-items: stretch;
}
.history-head > span,
.history-row > span,
.history-row > strong {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  white-space: nowrap; /* 칸 안에서 줄이 꺾이지 않게 */
}
/* 구분 칸은 상자 하나만 들어가므로 좌우 여백을 줄임 */
.history-head > .col-tag,
.history-row > .col-tag {
  padding-right: 4px;
  padding-left: 6px;
}
/* 칸별 옅은 바탕: 시각(푸른 회색) · 처리(따뜻한 회색) · 결과(녹회색), 구분·내용은 흰 바탕 */
.col-time {
  background: var(--col-cool);
}
.col-detail {
  background: var(--col-warm);
}
.col-value {
  justify-content: flex-end;
  background: var(--col-sage);
}
/* 머리줄: 스크롤해도 위에 붙어 있음 */
.history-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--card);
  border-bottom: 1px solid var(--ink);
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-2);
}
.history-row {
  width: 100%;
  padding: 0;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-align: left;
}
/* 마우스를 올리거나 고른 줄은 칸 색 위에 옅은 먹색을 한 겹 더 얹음 */
.history-row:hover > span,
.history-row:hover > strong {
  background-image: linear-gradient(rgba(20, 24, 28, 0.05), rgba(20, 24, 28, 0.05));
}
.history-row.active > span,
.history-row.active > strong {
  background-image: linear-gradient(rgba(20, 24, 28, 0.09), rgba(20, 24, 28, 0.09));
}
/* 그래프에 표시 중인 줄: 맨 왼쪽 칸에 굵은 줄 */
.history-row.active .history-time {
  box-shadow: inset 4px 0 0 var(--ink);
}
.history-time {
  font-weight: 700;
}
/* 구분: 추천(먹색 테두리) / 구간(옅은 테두리) */
.history-tag {
  padding: 0 5px;
  border: 1px solid var(--line-strong);
  font-size: 11px;
  color: var(--mute);
}
.history-tag.decision {
  border-color: var(--ink-2);
  color: var(--ink-2);
}
/* 내용·처리: 길면 한 줄에서 말줄임 */
.history-row > .history-main,
.history-row > .history-detail {
  display: block; /* 말줄임(…)이 되도록 블록으로 둠 */
  overflow: hidden;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-main {
  color: var(--ink);
}
.history-detail {
  font-size: 13px;
  color: var(--ink-2);
}
.history-value {
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}
.right-text {
  text-align: right;
}
/* 머리줄의 필터 선택 상자: 평소엔 칸 이름처럼 보이고(테두리 없음), 고르면 진하게 */
.head-select {
  width: 100%; /* 칸 너비를 꽉 써서 고른 값(예: 목표 초과)이 잘리지 않게 */
  height: 24px;
  padding: 0 2px;
  background: transparent;
  border: 0;
  border-bottom: 1px dashed var(--faint);
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-2);
  cursor: pointer;
}
.head-select:hover {
  border-bottom-color: var(--ink-2);
}
/* 필터가 걸린 칸: 먹색 바탕에 흰 글자 */
.head-select.on {
  padding: 0 4px;
  background: var(--ink);
  border-bottom-color: var(--ink);
  color: var(--card);
}
.head-select option {
  background: var(--card);
  color: var(--ink);
}
.filter-reset {
  flex: none;
  height: 26px;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  font-size: 13px;
  color: var(--ink-2);
}
.filter-reset:hover {
  background: var(--bg);
}
.history-empty {
  padding: 16px 0;
  color: var(--mute);
}
</style>
