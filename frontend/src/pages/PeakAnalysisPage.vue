<!--
  분석 > 피크 분석
  기간을 골라 "언제, 얼마나 높았나"를 들여다보는 화면
  조회 조건 → 요약 숫자 4개 → 일별 최대 피크와 저감량 → 피크가 몰리는 시간대(히트맵) · 피크가 높았던 구간

  요약 숫자와 구간 순위는 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import FitChart from '@/components/FitChart.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { useAnalysisStore } from '@/stores/useAnalysisStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import {
  formatHourMinute,
  formatInteger,
  formatKw,
  formatMonthDayTime,
  formatOneDecimal,
} from '@/utils/format'

const analysisStore = useAnalysisStore()
const realtimeStore = useRealtimeStore()

// 처리 상태는 색만으로 구분하지 않도록 도형을 함께 표시 (운영 로그 화면과 같은 표시)
// RECOMMENDED(추천 중)도 추천 이력 화면처럼 ○로 표시 (빠져 있으면 글자만 보였음 — 이슈 #25)
const STATUS_SHAPES = { RECOMMENDED: '○', APPLIED: '●', AUTO: '◇', IGNORED: '✕' }

// 그 구간의 추천 처리를 한 줄 문구로 (status 코드를 화면에서 글자로 바꿈)
// 백엔드 note는 추천 이유 문장이라 표 칸에 넣기엔 길어서 쓰지 않음
function windowNote(item) {
  if (!item.status) return '방전 추천 없음'
  if (item.status === 'IGNORED') return '추천을 넘김'
  if (item.status === 'AUTO') return '자동 방전 (시뮬레이션)'
  if (item.status === 'APPLIED') return '방전 적용'
  return '추천 중'
}

// ---- 조회 조건 ----
const preset = ref('month') // month(이번 달) | lastMonth(지난 달) | week(최근 7일) | custom(직접 입력)
const fromDate = ref('') // 예: '2026-11-01'
const toDate = ref('')

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
  // 요청을 보내기 전에 막음 (보내면 400이 나고 [다시 시도]를 눌러도 계속 실패함 — 이슈 #25)
  if (dayCount(fromDate.value, toDate.value) > MAX_DAYS) {
    return `기간은 최대 ${MAX_DAYS}일까지 조회할 수 있습니다.`
  }
  return ''
})

// 날짜를 '2026-11-14' 모양의 글자로
function dateText(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  const [year, month, day] = serverDate.value.split('-').map(Number)
  if (name === 'month') {
    fromDate.value = dateText(new Date(year, month - 1, 1)) // 이번 달 1일
    toDate.value = serverDate.value
  }
  if (name === 'lastMonth') {
    fromDate.value = dateText(new Date(year, month - 2, 1)) // 지난달 1일
    toDate.value = dateText(new Date(year, month - 1, 0)) // 지난달 마지막 날 (이번 달 0일)
  }
  if (name === 'week') {
    fromDate.value = dateText(new Date(year, month - 1, day - 6))
    toDate.value = serverDate.value
  }
}

function load(showLoading = true) {
  if (!fromDate.value || !toDate.value || dateError.value) return
  // "피크가 높았던 구간" 표도 이 응답(topWindows)에 함께 들어 있음
  analysisStore.fetchPeak(fromDate.value, toDate.value, showLoading)
}

// "피크가 높았던 구간" 표에서 누른 줄 (히트맵에 그 칸을 표시)
// 피크 높은 순이면 구간 시작 시각('2026-11-02T08:30:00'), 평균보다 높은 구간이면 줄 key('2026-11-02T08')
const selectedStart = ref('')

function toggleWindow(windowStart) {
  selectedStart.value = selectedStart.value === windowStart ? '' : windowStart
}

// "피크가 높았던 구간" 표의 보기 방식
// - 'top': 피크 높은 순 (백엔드가 준 topWindows, 15분 구간)
// - 'aboveAverage': 기간 평균보다 높은 구간 (히트맵의 시간별 피크 중 평균보다 높은 시각을, 같은 날 이어지는 시각끼리 묶음)
// 평균 증감률이 큰 시간대, ESS를 주로 쓰는 시간, 이상치 같은 보기는 백엔드 데이터가 필요해서 나중에 추가
const WINDOW_MODES = [
  { value: 'top', label: '피크 높은 순' },
  { value: 'aboveAverage', label: '평균보다 높은 구간' },
]
const windowMode = ref('top')
// 보기 방식을 바꾸면 고른 줄은 풀어 둠 (두 보기의 줄 key가 다름)
watch(windowMode, () => {
  selectedStart.value = ''
})

// 기간이 바뀌면 고른 구간을 지우고 다시 조회
watch([fromDate, toDate], () => {
  selectedStart.value = ''
  load()
})

// 서버 날짜를 처음 알게 되면 "이번 달"로 조회 시작
watch(
  serverDate,
  (date) => {
    if (date && !fromDate.value) applyPreset('month')
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
const peak = computed(() => analysisStore.peak)
const summary = computed(() => peak.value?.summary ?? null)
// 목표 피크. 설정 값을 못 찾으면 백엔드가 null로 줌 → 목표선·목표 판정·히트맵 색 단계를 그리지 않음
const targetKw = computed(() => peak.value?.targetPeakKw ?? null)
const isOverTarget = (kw) => targetKw.value !== null && kw > targetKw.value
// 최대 피크 아래 줄의 판정. 목표 값이 없으면 판정 없이 시각만 보여 줌
const maxPeakVerdict = computed(() => {
  // 최대 피크 값이 없을 때도 판정하지 않음 (null은 목표보다 작다고 계산되어 "목표 달성"으로 보였음 — 이슈 #25)
  if (targetKw.value === null || summary.value?.maxPeakKw == null) return ''
  return isOverTarget(summary.value.maxPeakKw) ? ', 목표 초과' : ', 목표 달성'
})

// '2026-11-02T09:30:00' → '11-02 09:30'
const shortTime = (dateTime) => formatMonthDayTime(dateTime).slice(0, 11)
// '2026-11-02' → '11-02'
const dayLabel = (date) => date.slice(5)

// 기간의 모든 날짜 목록. 예: ('2026-11-17', '2026-11-19') → ['2026-11-17', '2026-11-18', '2026-11-19']
// 백엔드는 기록이 있는 날만 주므로, 그대로 그리면 기록 없는 날(예: 시뮬레이터가 꺼져 있던 날)이
// 그래프에서 사라지고 앞뒤 날짜가 붙어 보였음 → 모든 날짜로 칸을 만들고 기록 없는 날은 비워 둠
function datesBetween(from, to) {
  const dates = []
  const day = new Date(`${from}T00:00:00`)
  const end = new Date(`${to}T00:00:00`)
  while (day <= end) {
    const month = String(day.getMonth() + 1).padStart(2, '0')
    const date = String(day.getDate()).padStart(2, '0')
    dates.push(`${day.getFullYear()}-${month}-${date}`)
    day.setDate(day.getDate() + 1)
  }
  return dates
}

// 일별 그래프·히트맵에 쓰는 날짜별 목록: 기간의 모든 날짜, 기록이 없는 날은 값이 null (막대·칸 없이 빈자리)
function fillDays(list, from, to) {
  if (!list.length || !from || !to) return list
  const byDate = Object.fromEntries(list.map((item) => [item.date, item]))
  return datesBetween(from, to).map(
    (date) => byDate[date] ?? { date, peakKw: null, expectedKw: null, kwh: null },
  )
}

// 조회 기간의 모든 날짜 (기록 없는 날 포함)
const dayRows = computed(() =>
  fillDays(peak.value?.dailyPeaks ?? [], peak.value?.from, peak.value?.to),
)

// 목표를 넘은 구간이 전체 15분 구간의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const exceededPercent = computed(() =>
  summary.value?.windowCount
    ? (summary.value.exceededCount / summary.value.windowCount) * 100
    : null,
)

// ---- 그래프 공통 ----
// 그래프(ECharts)는 CSS 변수를 못 읽으므로, 테마 스토어가 global.css에서 읽어 둔 지금 테마 색을 씀
// (라이트·다크를 바꾸면 이 색이 바뀌고 그래프도 다시 그려짐)
const themeStore = useThemeStore()
const COLOR = themeStore.colors
// 그래프 툴팁도 지금 테마 색으로
const tooltipColors = () => ({
  backgroundColor: COLOR.card,
  borderColor: COLOR.line,
  textStyle: { color: COLOR.ink },
})
// 히트맵 색 단계: 낮음(옅음) → 높음(진함). --src-grid 색을 흰색에 섞은 값
const HEAT_COLORS = computed(() => [
  COLOR.heat1,
  COLOR.heat2,
  COLOR.heat3,
  COLOR.heat4,
  COLOR.heat5,
])

const axisStyle = {
  axisLabel: { color: COLOR.mute },
  axisLine: { lineStyle: { color: COLOR.line } },
  axisTick: { show: false },
}

// ---- 일별 최대 피크와 저감량 ----
// 차이를 알아보기 쉽게 세로축을 0이 아닌 값에서 시작함 (표시용 계산)
const peakAxisMin = computed(() => {
  const lowest = Math.min(...peak.value.dailyPeaks.map((item) => item.peakKw))
  return Math.floor((lowest * 0.9) / 100) * 100
})

const dailyOption = computed(() => {
  const target = targetKw.value
  const days = dayRows.value
  return {
    animation: false,
    textStyle: { fontSize: 10 }, // 그래프 글자도 80% 크기로 (기본 12px)
    grid: { left: 44, right: 16, top: 28, bottom: 20 },
    legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => (value == null ? '기록 없음' : `${formatKw(value)} kW`),
    },
    xAxis: { type: 'category', data: days.map((item) => dayLabel(item.date)), ...axisStyle },
    yAxis: {
      type: 'value',
      min: peakAxisMin.value,
      axisLabel: { color: COLOR.mute },
      splitLine: { lineStyle: { color: COLOR.line } },
    },
    series: [
      {
        name: '방전하지 않았을 때 예상',
        type: 'bar',
        barWidth: '56%',
        data: days.map((item) => item.expectedKw),
        itemStyle: { color: COLOR.lineStrong },
      },
      {
        // 예상 막대와 같은 자리에 겹쳐 그림. 위로 남는 연한 부분이 그날의 저감량
        name: '실제',
        type: 'bar',
        barWidth: '56%',
        barGap: '-100%',
        itemStyle: { color: COLOR.grid },
        data: days.map((item) => ({
          value: item.peakKw,
          itemStyle: { color: isOverTarget(item.peakKw) ? COLOR.crit : COLOR.grid },
        })),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
          label: {
            formatter: `목표 피크 ${formatKw(target)}`,
            position: 'insideStartTop',
            color: COLOR.ink,
            fontWeight: 'bold',
          },
          // 목표 값이 없으면 선을 그리지 않음 (null을 넣으면 ECharts 오류)
          data: target === null ? [] : [{ yAxis: target }],
        },
      },
    ],
  }
})

// ---- 피크가 몰리는 시간대 (날짜 × 시각 히트맵) ----
// 날짜가 많아도 칸이 너무 납작해지지 않게 날짜 수에 맞춰 높이를 잡음
// 표 높이를 넘는 피크 구간은 표 내부에서 스크롤해 모두 확인함
const shownWindows = computed(() => analysisStore.peakWindows)

// ---- 평균보다 높은 구간 (표시용 계산: 백엔드가 준 히트맵 값의 평균과 비교해 거르기만 함) ----
const heatCells = computed(() => peak.value?.heatmap ?? [])
// 기간 평균: 히트맵 칸(날짜 × 시각) 피크의 평균
const heatAverageKw = computed(() =>
  heatCells.value.length
    ? heatCells.value.reduce((sum, item) => sum + item.peakKw, 0) / heatCells.value.length
    : null,
)
// 평균보다 높은 시각을 날짜별로 모아, 이어지는 시각(예: 8시·9시·10시)은 한 구간으로 묶음
// 한 줄 모양: { key, date, startHour, endHour, peakKw(구간 안 최대), abovePercent(최대 피크가 평균보다 몇 % 높은지) }
// 순서: 평균보다 많이 높은 구간부터
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
      key: `${range.date}T${String(range.startHour).padStart(2, '0')}`,
      abovePercent: ((range.peakKw - average) / average) * 100,
    }))
    .sort((a, b) => b.peakKw - a.peakKw)
})
// '8시 ~ 11시' 처럼 구간을 글자로 (끝 시각은 마지막 칸이 끝나는 시각)
const hourRangeText = (range) => `${range.startHour}시~${range.endHour + 1}시`

const heatOption = computed(() => {
  const target = targetKw.value
  const dates = dayRows.value.map((item) => item.date)
  const dayLabels = dates.map(dayLabel)

  // 히트맵 위에 테두리로 덧그리는 칸 묶음. x는 시각(0~23), y는 날짜 순번
  // 예: { x0: 8, x1: 8, y0: 1, y1: 1, lineWidth: 3, text: '11-02 08:30 · 1,841 kW', textAtTop: true }
  const outlines = []

  // 평균보다 높은 구간 보기에서 누른 줄: 그 날의 이어진 시각 전체를 감쌈
  const pickedRange =
    windowMode.value === 'aboveAverage'
      ? aboveAverageRanges.value.find((item) => item.key === selectedStart.value)
      : null
  if (pickedRange) {
    const row = dates.indexOf(pickedRange.date)
    if (row >= 0) {
      outlines.push({
        x0: pickedRange.startHour,
        x1: pickedRange.endHour,
        y0: row,
        y1: row,
        lineWidth: 3,
        text: `${dayLabel(pickedRange.date)} ${hourRangeText(pickedRange)} · 최대 ${formatKw(pickedRange.peakKw)} kW`,
        textAtTop: true,
      })
    }
  }

  // 피크 높은 순 보기에서 누른 구간 한 칸 (그 날 그 시각)
  if (selectedStart.value && windowMode.value === 'top') {
    const hour = Number(selectedStart.value.slice(11, 13))
    const row = dates.indexOf(selectedStart.value.slice(0, 10))
    const picked = analysisStore.peakWindows.find(
      (item) => item.windowStart === selectedStart.value,
    )
    if (row >= 0 && picked) {
      outlines.push({
        x0: hour,
        x1: hour,
        y0: row,
        y1: row,
        lineWidth: 3,
        text: `${shortTime(picked.windowStart)} · ${formatKw(picked.peakKw)} kW`,
        textAtTop: true,
      })
    }
  }

  // 테두리 그리기: 칸의 가운데 좌표(api.coord)와 칸 한 개 크기(api.size)를 받아 칸 전체를 감싸는 사각형을 그림
  // (markArea는 칸 가운데에 맞춰져 반 칸씩 밀리므로 직접 그림)
  function renderOutline(params, api) {
    const item = outlines[params.dataIndex]
    const start = api.coord([item.x0, item.y0])
    const end = api.coord([item.x1, item.y1])
    const [cellWidth, cellHeight] = api.size([1, 1])
    const x = Math.min(start[0], end[0]) - cellWidth / 2
    const y = Math.min(start[1], end[1]) - cellHeight / 2
    const width = Math.abs(end[0] - start[0]) + cellWidth
    const height = Math.abs(end[1] - start[1]) + cellHeight
    return {
      type: 'group',
      children: [
        {
          type: 'rect',
          shape: { x, y, width, height },
          style: { fill: 'none', stroke: COLOR.ink, lineWidth: item.lineWidth },
        },
        {
          type: 'text',
          style: {
            text: item.text,
            x: x + width / 2,
            y: item.textAtTop ? y - 4 : y + height - 4,
            align: 'center',
            verticalAlign: 'bottom',
            fill: COLOR.ink,
            font: 'bold 12px sans-serif',
            backgroundColor: COLOR.card,
            borderColor: item.textAtTop ? COLOR.ink : 'transparent',
            borderWidth: 1,
            padding: [2, 5],
          },
        },
      ],
    }
  }

  return {
    animation: false,
    textStyle: { fontSize: 10 }, // 그래프 글자도 80% 크기로 (기본 12px)
    // top: 위쪽 색 단계 버튼(누르면 그 단계 칸을 숨기거나 보임)과 그래프가 붙지 않게 띄움
    grid: { left: 44, right: 12, top: 40, bottom: 18 },
    tooltip: {
      ...tooltipColors(),
      formatter: (params) =>
        `${dayLabels[params.value[1]]} ${params.value[0]}시 · ${formatKw(params.value[2])} kW`,
    },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, hour) => `${hour}시`),
      ...axisStyle,
      axisLabel: { color: COLOR.mute, interval: 5 },
    },
    yAxis: { type: 'category', data: dayLabels, inverse: true, ...axisStyle },
    // 색 단계는 목표 피크 대비 비율로 나눔 (표시용). 목표를 넘은 칸은 빨강
    visualMap: {
      type: 'piecewise',
      seriesIndex: 0, // 히트맵 칸에만 색을 입힘 (테두리 그리기에는 쓰지 않음)
      dimension: 2,
      orient: 'horizontal',
      top: 0,
      right: 0,
      itemWidth: 14,
      itemHeight: 10,
      itemGap: 4,
      textStyle: { color: COLOR.mute },
      pieces: [
        { lte: target * 0.72, color: HEAT_COLORS.value[0], label: '낮음' },
        { gt: target * 0.72, lte: target * 0.8, color: HEAT_COLORS.value[1], label: ' ' },
        { gt: target * 0.8, lte: target * 0.88, color: HEAT_COLORS.value[2], label: ' ' },
        { gt: target * 0.88, lte: target * 0.95, color: HEAT_COLORS.value[3], label: ' ' },
        { gt: target * 0.95, lte: target, color: HEAT_COLORS.value[4], label: '높음' },
        { gt: target, color: COLOR.crit, label: '목표 초과' },
      ],
    },
    series: [
      {
        type: 'heatmap',
        data: peak.value.heatmap.map((item) => [item.hour, dates.indexOf(item.date), item.peakKw]),
        itemStyle: { borderColor: COLOR.card, borderWidth: 1 },
        emphasis: { itemStyle: { borderColor: COLOR.ink, borderWidth: 1 } },
      },
      {
        // 칸 위에 덧그리는 테두리 (표에서 누른 구간). 마우스에는 반응하지 않음
        type: 'custom',
        silent: true,
        clip: false,
        z: 10,
        tooltip: { show: false },
        renderItem: renderOutline,
        data: outlines.map((_, index) => [index]),
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
      <h1 class="crumb-title">피크 분석</h1>
      <p class="crumb-note">기간을 골라 피크가 언제, 얼마나 높았는지 봅니다</p>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button
            :class="{ active: preset === 'month' }"
            :aria-pressed="preset === 'month'"
            @click="applyPreset('month')"
          >
            이번 달
          </button>
          <button
            :class="{ active: preset === 'lastMonth' }"
            :aria-pressed="preset === 'lastMonth'"
            @click="applyPreset('lastMonth')"
          >
            지난 달
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

      <p v-if="analysisStore.peakLoading" class="loading-note">불러오는 중입니다.</p>
      <p v-if="dateError" class="date-error" role="alert">{{ dateError }}</p>
    </section>

    <!-- 받아 둔 결과가 있으면 지우지 않고 위에 실패 띠만 띄움 (마지막으로 받은 값 유지) -->
    <p v-if="analysisStore.peakError && peak" class="error-band" role="alert">
      <span aria-hidden="true">◆</span>
      새 결과를 불러오지 못해 이전에 받은 결과를 보여 줍니다. ({{ analysisStore.peakError }})
      <button class="error-band-button" @click="load()">다시 시도</button>
    </p>
    <p v-if="analysisStore.peakError && !peak" class="state error" role="alert">
      분석 결과를 불러오지 못했습니다. ({{ analysisStore.peakError }})
      <button class="retry" @click="load()">다시 시도</button>
    </p>
    <!-- 조회 기간은 서버 시각으로 정하므로, 서버 시각을 받기 전에는 조회하지 않음 -->
    <p v-else-if="!peak && !serverDate" class="state">
      서버에서 현재 시각을 받으면 자동으로 조회합니다.
    </p>
    <p v-else-if="!peak" class="state">불러오는 중입니다.</p>
    <p v-else-if="!peak.hasData" class="state">이 기간에는 기록이 없습니다. 기간을 바꿔 보세요.</p>

    <!-- 기간 · 제목 줄을 뺀 나머지는 높이와 글자를 80% 크기로 (2026-11-18) -->
    <div v-else class="compact">
      <!-- 요약 숫자 4개 -->
      <dl class="summary">
        <div class="card">
          <dt>기간 중 최대 피크</dt>
          <dd :class="{ over: isOverTarget(summary.maxPeakKw) }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">{{ shortTime(summary.maxPeakAt) }}{{ maxPeakVerdict }}</p>
        </div>
        <div class="card">
          <dt>목표를 넘은 구간</dt>
          <dd>
            <span class="num">{{ formatInteger(summary.exceededCount) }}</span> <small>회</small>
            <!-- 전체 15분 구간 중 몇 %였는지 -->
            <span v-if="exceededPercent !== null" class="pct num">
              ({{ formatOneDecimal(exceededPercent) }}%)
            </span>
          </dd>
          <p class="num">15분 구간 {{ formatInteger(summary.windowCount) }}개 중</p>
        </div>
        <div class="card">
          <dt>평균 피크 저감</dt>
          <dd>
            <span class="num">{{ formatKw(summary.avgReductionKw) }}</span> <small>kW</small>
          </dd>
          <!-- 쉼표 뒤에서 줄을 바꿈 -->
          <p class="num">
            <span class="line">방전하지 않았을 때의 예상 대비,</span>
            <span class="line">가장 크게 줄인 날 {{ formatKw(summary.maxReductionKw) }} kW</span>
          </p>
        </div>
        <div class="card">
          <dt>피크가 잦은 시간</dt>
          <dd class="num">{{ summary.busyFrom }} ~ {{ summary.busyTo }}</dd>
          <p>{{ summary.busyPeriodLabel }} 시간대</p>
        </div>
      </dl>

      <!-- 일별 최대 피크와 저감량 (남은 높이의 2/5) -->
      <section class="card daily">
        <div class="card-head">
          <h2 class="card-title">일별 최대 피크와 저감량</h2>
          <span class="note">
            단위 kW · 빨강은 목표 초과 · 세로축은 {{ formatKw(peakAxisMin) }}부터
          </span>
        </div>
        <FitChart :option="dailyOption" :min-height="90" />
      </section>

      <div class="bottom">
        <!-- 피크가 몰리는 시간대 -->
        <section class="card heat">
          <div class="card-head">
            <h2 class="card-title">피크가 몰리는 시간대</h2>
            <span class="note"> 진할수록 높음 · 굵은 테두리 = 표에서 누른 구간 </span>
          </div>
          <!-- 남은 높이를 채움 (날짜가 많으면 한 줄 높이가 줄어듦) -->
          <!-- 목표 값이 없으면 색 단계(목표 대비 비율)를 나눌 수 없어서 그리지 않음 -->
          <FitChart v-if="targetKw !== null" :option="heatOption" :min-height="70" />
          <p v-else class="windows-empty">목표 피크 값이 없어 색 단계를 나눌 수 없습니다.</p>
        </section>

        <!-- 피크가 높았던 구간 -->
        <section class="card top">
          <div class="card-head">
            <h2 class="card-title">피크가 높았던 구간</h2>
            <!-- 보기 방식 선택 -->
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
          <p class="mode-note">
            <template v-if="windowMode === 'top'"
              >15분 구간 피크가 높은 순 · 누르면 히트맵에 표시</template
            >
            <template v-else>
              기간 평균 <span class="num">{{ formatKw(heatAverageKw) }}</span> kW보다 높은 시각
              (이어진 시각은 한 구간) · 누르면 히트맵에 표시
            </template>
          </p>
          <!-- 표 높이를 넘는 줄은 표 안에서 스크롤해 확인함 -->
          <!-- 피크 높은 순 -->
          <div v-if="windowMode === 'top'" class="table-box">
            <table class="table">
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
                  v-for="(item, index) in shownWindows"
                  :key="item.windowStart"
                  class="window-row"
                  :class="{ active: selectedStart === item.windowStart }"
                  @click="toggleWindow(item.windowStart)"
                >
                  <td class="rank-col num">{{ index + 1 }}</td>
                  <td class="num">
                    <!-- 키보드로도 고를 수 있게 버튼으로 둠 (줄 어디를 눌러도 같은 동작) -->
                    <button
                      class="window-button"
                      :aria-pressed="selectedStart === item.windowStart"
                      @click.stop="toggleWindow(item.windowStart)"
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
                    <span v-if="item.status" aria-hidden="true">{{
                      STATUS_SHAPES[item.status]
                    }}</span>
                    {{ windowNote(item) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <!-- 평균보다 높은 구간 -->
          <div v-else class="table-box">
            <table class="table">
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
                  :class="{ active: selectedStart === item.key }"
                  @click="toggleWindow(item.key)"
                >
                  <td class="rank-col num">{{ index + 1 }}</td>
                  <td class="num">
                    <button
                      class="window-button"
                      :aria-pressed="selectedStart === item.key"
                      @click.stop="toggleWindow(item.key)"
                    >
                      {{ dayLabel(item.date) }} {{ hourRangeText(item) }}
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
          <p
            v-if="
              windowMode === 'top' ? !analysisStore.peakWindows.length : !aboveAverageRanges.length
            "
            class="windows-empty"
          >
            이 기간에는 구간 기록이 없습니다.
          </p>
          <div class="top-foot">
            <RouterLink to="/logs" class="more more-link">추천 이력 보기</RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%; /* 화면 높이에 고정: 아래 카드들이 남은 높이에 맞춰 줄어들어 스크롤이 생기지 않음 */
  padding: 20px;
  overflow: hidden;
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
.over {
  color: var(--crit);
}

/* 기간 · 제목 줄 아래 내용: 높이(간격·여백)와 글자를 80%로 */
.compact {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  gap: 13px;
  font-size: 11px; /* 기본 14px의 80% */
}
.compact .card {
  padding: 11px 16px;
}
.compact .card-title {
  font-size: 13px; /* 16px의 80% */
}
.compact .table th {
  font-size: 10px;
}
.compact .more-link {
  font-size: 10.5px;
}

/* 요약 숫자 4개 */
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 13px;
}
.summary dt {
  font-size: 10.5px;
  color: var(--ink-2);
}
.summary dd {
  font-size: 22.5px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
}
.summary small {
  margin-left: 2px;
  font-size: 11px;
  font-weight: 400;
  color: var(--ink-2);
}
.summary .pct {
  margin-left: 6px;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--ink-2);
}
.summary p {
  font-size: 10px;
  color: var(--mute);
}
.summary .line {
  display: block;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 6px;
}
.note {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--mute);
}

/* 아래 줄: 히트맵 + 구간 순위 */
/* 요약 숫자 아래 남은 높이를 비율로 나눔: 일별 그래프 2 : 아래 줄(히트맵 · 구간 표) 3
   (화면 높이가 달라도 같은 비율로 채워 스크롤이 생기지 않음, 노트북 1536×705 기준으로 맞춤) */
.daily {
  display: flex;
  flex: 2;
  flex-direction: column;
  min-height: 0;
}
.bottom {
  display: flex;
  flex: 3;
  gap: 13px;
  min-height: 0;
}
.heat {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.top {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 520px;
  min-height: 0;
}
/* 표 상자: 남는 높이를 차지하고, 모든 구간을 내부에서 스크롤함 */
.table-box {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-color: var(--line-strong) transparent;
}
/* 구간 보기 방식 버튼: 카드 제목 줄에 들어가게 작게 */
.window-modes {
  margin-left: auto;
}
.window-modes button {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
}
.mode-note {
  margin-bottom: 6px;
  font-size: 10.5px;
  color: var(--mute);
}
.top-foot {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hidden-note {
  font-size: 10px;
  color: var(--mute);
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--card);
  padding: 5px 12px 5px 0;
}
.table td {
  padding: 5px 12px 5px 0;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}
.table th:last-child,
.table td:last-child {
  padding-right: 0;
}
.table .right-text {
  text-align: right;
}
/* 누르면 히트맵에 표시되는 줄 */
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
  border-left: 4px solid transparent;
  padding-left: 6px;
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
/* 카드 맨 아래 오른쪽의 링크 */
.more {
  margin-left: auto;
  padding-top: 8px;
}

/* 화면이 좁으면 오른쪽 표를 줄여 히트맵 자리를 남김 */
@media (max-width: 1600px) {
  .top {
    width: 440px;
  }
}
</style>
