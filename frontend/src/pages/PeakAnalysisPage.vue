<!--
  분석 > 피크 분석
  기간을 골라 "언제, 얼마나 높았나"를 들여다보는 화면
  조회 조건 → 요약 숫자 4개 → 일별 최대 피크와 저감량 → 피크가 몰리는 시간대(히트맵) · 피크가 높았던 구간

  요약 숫자와 구간 순위는 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
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
const STATUS_SHAPES = { APPLIED: '●', AUTO: '◇', IGNORED: '✕' }
const TOP_COUNT = 8 // "피크가 높았던 구간"에 보여 줄 개수

// 그 구간의 추천 처리를 한 줄 문구로 (명세 16번은 action·status 코드만 주므로 화면에서 글자로 바꿈)
function windowNote(item) {
  if (!item.action) return '방전 추천 없음'
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

const dateError = computed(() =>
  fromDate.value && toDate.value && fromDate.value > toDate.value
    ? '시작일이 종료일보다 늦습니다.'
    : '',
)

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
  analysisStore.fetchPeak(fromDate.value, toDate.value, showLoading)
  // "피크가 높았던 구간" 표는 명세 16번(demand-intervals)으로 따로 받음
  analysisStore.fetchPeakWindows(fromDate.value, toDate.value, TOP_COUNT)
}

// "피크가 높았던 구간" 표에서 누른 구간의 시작 시각 (히트맵에 그 칸을 표시)
const selectedStart = ref('')

// "피크가 높았던 구간" 표의 필터 (머리줄의 처리 · 결과 칸)
const windowStatus = ref('ALL') // ALL | APPLIED | AUTO | IGNORED | NONE(추천 없음)
const windowResult = ref('ALL') // ALL | DEFENDED(목표 달성) | EXCEEDED(목표 초과)

function toggleWindow(windowStart) {
  selectedStart.value = selectedStart.value === windowStart ? '' : windowStart
}

// 기간이 바뀌면 고른 구간을 지우고 다시 조회
watch([fromDate, toDate], () => {
  selectedStart.value = ''
  load()
})

// 표에 보여 줄 줄 (명세 16번 구간 순위 API 결과). 순위는 거르기 전 순위를 그대로 둠
// 결과는 목표 피크와 비교한 표시용 구분, 처리 글자는 windowNote로 만듦
const windowRows = computed(() => {
  const target = peak.value?.targetPeakKw
  if (!target) return []
  return analysisStore.peakWindows.map((item, index) => {
    const overKw = item.peakKw - target
    return {
      ...item,
      rank: index + 1,
      note: windowNote(item),
      statusKey: item.action ? item.status : 'NONE', // 추천이 없던 구간은 NONE
      result: overKw > 0 ? 'EXCEEDED' : 'DEFENDED',
      overKw,
    }
  })
})
const filteredWindows = computed(() =>
  windowRows.value.filter((row) => {
    if (windowStatus.value !== 'ALL' && row.statusKey !== windowStatus.value) return false
    if (windowResult.value !== 'ALL' && row.result !== windowResult.value) return false
    return true
  }),
)
const isWindowFiltered = computed(
  () => windowStatus.value !== 'ALL' || windowResult.value !== 'ALL',
)
function resetWindowFilter() {
  windowStatus.value = 'ALL'
  windowResult.value = 'ALL'
}

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

// '2026-11-02T09:30:00' → '11-02 09:30'
const shortTime = (dateTime) => formatMonthDayTime(dateTime).slice(0, 11)
// '2026-11-02' → '11-02'
const dayLabel = (date) => date.slice(5)

// 목표를 넘은 구간이 전체 15분 구간의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const exceededPercent = computed(() =>
  summary.value?.windowCount
    ? (summary.value.exceededCount / summary.value.windowCount) * 100
    : null,
)

// ---- 그래프 공통 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 값을 그대로 적습니다.
const COLOR = {
  ink: '#14181c', // --ink
  mute: '#646e77', // --mute
  line: '#dde1e5', // --line
  lineStrong: '#b9c0c7', // --line-strong
  crit: '#c8372d', // --crit
  grid: '#5b6472', // --src-grid
}
// 히트맵 색 단계: 낮음(옅음) → 높음(진함). --src-grid 색을 흰색에 섞은 값
const HEAT_COLORS = ['#ebecee', '#cdd0d4', '#adb1b8', '#848b96', '#5b6472']

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
  const target = peak.value.targetPeakKw
  const days = peak.value.dailyPeaks
  return {
    animation: false,
    grid: { left: 52, right: 16, top: 34, bottom: 24 },
    legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
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
          itemStyle: { color: item.peakKw > target ? COLOR.crit : COLOR.grid },
        })),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
          label: {
            formatter: `목표 ${formatKw(target)}`,
            position: 'insideStartTop',
            color: COLOR.ink,
            fontWeight: 'bold',
          },
          data: [{ yAxis: target }],
        },
      },
    ],
  }
})

// ---- 피크가 몰리는 시간대 (날짜 × 시각 히트맵) ----
// 날짜가 많아도 칸이 너무 납작해지지 않게 날짜 수에 맞춰 높이를 잡음
const heatHeight = computed(() => Math.max(240, peak.value.dailyPeaks.length * 13 + 60))

const heatOption = computed(() => {
  const target = peak.value.targetPeakKw
  const dates = peak.value.dailyPeaks.map((item) => item.date)
  const dayLabels = dates.map(dayLabel)

  // 히트맵 위에 테두리로 덧그리는 칸 묶음. x는 시각(0~23), y는 날짜 순번
  // 예: { x0: 8, x1: 8, y0: 1, y1: 1, lineWidth: 3, text: '11-02 08:30 · 1,841 kW', textAtTop: true }
  const outlines = []

  // 표에서 누른 구간 한 칸 (그 날 그 시각)
  if (selectedStart.value) {
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
            backgroundColor: '#fff', // --card
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
    grid: { left: 52, right: 12, top: 30, bottom: 22 },
    tooltip: {
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
        { lte: target * 0.72, color: HEAT_COLORS[0], label: '낮음' },
        { gt: target * 0.72, lte: target * 0.8, color: HEAT_COLORS[1], label: ' ' },
        { gt: target * 0.8, lte: target * 0.88, color: HEAT_COLORS[2], label: ' ' },
        { gt: target * 0.88, lte: target * 0.95, color: HEAT_COLORS[3], label: ' ' },
        { gt: target * 0.95, lte: target, color: HEAT_COLORS[4], label: '높음' },
        { gt: target, color: COLOR.crit, label: '목표 초과' },
      ],
    },
    series: [
      {
        type: 'heatmap',
        data: peak.value.heatmap.map((item) => [item.hour, dates.indexOf(item.date), item.peakKw]),
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
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
          <button :class="{ active: preset === 'month' }" @click="applyPreset('month')">
            이번 달
          </button>
          <button :class="{ active: preset === 'lastMonth' }" @click="applyPreset('lastMonth')">
            지난 달
          </button>
          <button :class="{ active: preset === 'week' }" @click="applyPreset('week')">
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

    <template v-else>
      <!-- 요약 숫자 4개 -->
      <!-- 추이 분석의 기간 요약과 같은 모양 (카드 하나를 칸으로 나눔) -->
      <dl class="card summary">
        <div>
          <dt>기간 중 최대 피크</dt>
          <dd :class="{ over: summary.maxPeakKw > peak.targetPeakKw }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">
            {{ shortTime(summary.maxPeakAt) }},
            {{ summary.maxPeakKw > peak.targetPeakKw ? '목표 초과' : '목표 달성' }}
          </p>
        </div>
        <div>
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
        <div>
          <dt>평균 피크 저감</dt>
          <dd>
            <span class="num">{{ formatKw(summary.avgReductionKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">
            방전하지 않았을 때의 예상 대비, 가장 크게 줄인 날
            {{ formatKw(summary.maxReductionKw) }} kW
          </p>
        </div>
        <div>
          <dt>피크가 잦은 시간</dt>
          <dd class="num">{{ summary.busyFrom }} ~ {{ summary.busyTo }}</dd>
          <p>{{ summary.busyPeriodLabel }} 시간대</p>
        </div>
      </dl>

      <!-- 일별 최대 피크와 저감량 -->
      <section class="card">
        <div class="card-head">
          <h2 class="card-title">일별 최대 피크와 저감량</h2>
          <span class="note">
            단위 kW · 빨강은 목표 초과 · 세로축은 {{ formatKw(peakAxisMin) }}부터
          </span>
        </div>
        <Chart :option="dailyOption" :height="230" />
      </section>

      <div class="bottom">
        <!-- 피크가 몰리는 시간대 -->
        <section class="card heat">
          <div class="card-head">
            <h2 class="card-title">피크가 몰리는 시간대</h2>
            <span class="note"> 진할수록 높음 · 굵은 테두리 = 표에서 누른 구간 </span>
          </div>
          <!-- key: 날짜 수가 바뀌면 그래프 높이도 바뀌므로 새로 만듦 -->
          <Chart :key="heatHeight" :option="heatOption" :height="heatHeight" />
        </section>

        <!-- 피크가 높았던 구간 -->
        <section class="card top">
          <div class="card-head">
            <h2 class="card-title">피크가 높았던 구간</h2>
            <span class="note">
              <span class="num">{{ filteredWindows.length }}건</span> · 누르면 히트맵에 표시
            </span>
            <button v-if="isWindowFiltered" class="filter-reset" @click="resetWindowFilter">
              필터 초기화
            </button>
          </div>
          <!-- 칸: 순위 | 대상 시간 | 피크 | 처리 | 결과. 머리줄의 처리 · 결과 칸이 곧 필터 -->
          <div class="windows">
            <div class="windows-head">
              <span>순위</span>
              <span class="col-time">대상 시간</span>
              <span class="right-text">피크</span>
              <span class="col-detail">
                <select
                  v-model="windowStatus"
                  class="head-select"
                  :class="{ on: windowStatus !== 'ALL' }"
                  aria-label="처리 필터"
                >
                  <option value="ALL">처리</option>
                  <option value="APPLIED">적용</option>
                  <option value="AUTO">자동</option>
                  <option value="IGNORED">넘김</option>
                  <option value="RECOMMENDED">추천 중</option>
                  <option value="NONE">추천 없음</option>
                </select>
              </span>
              <span class="col-value">
                <select
                  v-model="windowResult"
                  class="head-select"
                  :class="{ on: windowResult !== 'ALL' }"
                  aria-label="결과 필터"
                >
                  <option value="ALL">결과</option>
                  <option value="DEFENDED">목표 달성</option>
                  <option value="EXCEEDED">목표 초과</option>
                </select>
              </span>
            </div>
            <ul v-if="filteredWindows.length">
              <li v-for="row in filteredWindows" :key="row.windowStart">
                <!-- 줄 전체가 버튼: 누르면 히트맵에 그 칸을 표시, 다시 누르면 지움 -->
                <button
                  class="window-row"
                  :class="{ active: selectedStart === row.windowStart }"
                  :aria-pressed="selectedStart === row.windowStart"
                  @click="toggleWindow(row.windowStart)"
                >
                  <span class="rank num">{{ row.rank }}</span>
                  <span class="col-time num">
                    {{ shortTime(row.windowStart) }}~{{ formatHourMinute(row.windowEnd) }}
                  </span>
                  <strong class="right-text num" :class="{ over: row.overKw > 0 }">
                    {{ formatKw(row.peakKw) }} kW
                  </strong>
                  <span class="col-detail window-detail" :title="row.note">
                    <span v-if="row.action && STATUS_SHAPES[row.status]" aria-hidden="true">{{
                      STATUS_SHAPES[row.status]
                    }}</span>
                    {{ row.note }}
                  </span>
                  <span class="col-value window-result num" :class="{ over: row.overKw > 0 }">
                    <template v-if="row.overKw > 0">
                      목표 초과 +{{ formatKw(row.overKw) }}
                    </template>
                    <template v-else>목표 달성</template>
                  </span>
                </button>
              </li>
            </ul>
            <p v-else-if="analysisStore.peakWindowsError" class="windows-error" role="alert">
              구간 순위를 불러오지 못했습니다. ({{ analysisStore.peakWindowsError }})
            </p>
            <p v-else-if="!windowRows.length" class="windows-empty">
              이 기간에는 구간 기록이 없습니다.
            </p>
            <p v-else class="windows-empty">조건에 맞는 구간이 없습니다. 필터를 바꿔 보세요.</p>
          </div>
          <RouterLink to="/logs" class="more">추천 이력 보기</RouterLink>
        </section>
      </div>
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

/* 아래 줄: 히트맵 + 구간 순위 */
.bottom {
  display: flex;
  flex: 1;
  gap: 16px;
}
.heat {
  flex: 1;
  min-width: 0;
}
.top {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 620px;
}

/* 피크가 높았던 구간: 추이 분석의 이벤트 히스토리와 같은 칸 구분 방식
   칸: 순위 | 15분 구간 | 피크 | 처리 | 결과. 칸 사이 간격 없이 붙여 칸마다 칠한 옅은 바탕이 띠처럼 보이게 함 */
.windows-head,
.window-row {
  display: grid;
  grid-template-columns: 44px 156px 92px minmax(0, 1fr) 128px;
  align-items: stretch;
}
.windows-head > span,
.window-row > span,
.window-row > strong {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  white-space: nowrap;
}
.right-text {
  justify-content: flex-end;
}
/* 칸별 옅은 바탕: 15분 구간(푸른 회색) · 처리(따뜻한 회색) · 결과(녹회색) */
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
.windows-head {
  background: var(--card);
  border-bottom: 1px solid var(--ink);
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-2);
}
.window-row {
  width: 100%;
  padding: 0;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-align: left;
}
/* 마우스를 올리거나 고른 줄은 칸 색 위에 옅은 먹색을 한 겹 더 얹음 */
.window-row:hover > span,
.window-row:hover > strong {
  background-image: linear-gradient(rgba(20, 24, 28, 0.05), rgba(20, 24, 28, 0.05));
}
.window-row.active > span,
.window-row.active > strong {
  background-image: linear-gradient(rgba(20, 24, 28, 0.09), rgba(20, 24, 28, 0.09));
}
/* 고른 줄: 맨 왼쪽 칸에 굵은 줄 */
.window-row.active .rank {
  box-shadow: inset 4px 0 0 var(--ink);
}
.rank {
  color: var(--mute);
}
/* 처리: 길면 한 줄에서 말줄임 (마우스를 올리면 전체 글자) */
.window-row > .window-detail {
  display: block;
  overflow: hidden;
  line-height: 20px;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--ink-2);
}
.window-result {
  font-size: 13px;
}
.windows-empty {
  padding: 16px 0;
  color: var(--mute);
}
.windows-error {
  padding: 16px 0;
  color: var(--crit);
}
/* 머리줄의 필터 선택 상자: 평소엔 칸 이름처럼 보이고(테두리 없음), 고르면 먹색 바탕 */
.head-select {
  width: 100%;
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
/* 카드 맨 아래 오른쪽의 링크 */
.more {
  align-self: flex-end;
  margin-top: auto;
  padding-top: 10px;
  font-size: 13px;
  color: var(--ink-2);
  text-decoration: underline;
}
</style>
