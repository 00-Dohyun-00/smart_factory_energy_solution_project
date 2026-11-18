<!--
  분석 > 피크 분석
  기간을 골라 "언제, 얼마나 높았나"를 들여다보는 화면
  조회 조건 → 요약 숫자 4개 → 일별 최대 피크와 저감량 → 피크가 몰리는 시간대(히트맵) · 피크가 높았던 구간

  요약 숫자와 구간 순위는 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
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

function toggleWindow(windowStart) {
  selectedStart.value = selectedStart.value === windowStart ? '' : windowStart
}

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
  const target = peak.value.targetPeakKw
  const days = peak.value.dailyPeaks
  return {
    animation: false,
    grid: { left: 52, right: 16, top: 34, bottom: 24 },
    legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
    tooltip: {
      ...tooltipColors(),
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
            formatter: `목표 피크 ${formatKw(target)}`,
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
    grid: { left: 52, right: 12, top: 30, bottom: 22 },
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

    <template v-else>
      <!-- 요약 숫자 4개 -->
      <dl class="summary">
        <div class="card">
          <dt>기간 중 최대 피크</dt>
          <dd :class="{ over: summary.maxPeakKw > peak.targetPeakKw }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">
            {{ shortTime(summary.maxPeakAt) }},
            {{ summary.maxPeakKw > peak.targetPeakKw ? '목표 초과' : '목표 달성' }}
          </p>
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
          <p class="num">
            방전하지 않았을 때의 예상 대비, 가장 크게 줄인 날
            {{ formatKw(summary.maxReductionKw) }} kW
          </p>
        </div>
        <div class="card">
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
            <span class="note">누르면 히트맵에 표시</span>
          </div>
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
                v-for="(item, index) in analysisStore.peakWindows"
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
                  <strong :class="{ over: item.peakKw > peak.targetPeakKw }">
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
          <p v-if="analysisStore.peakWindowsError" class="windows-error" role="alert">
            구간 순위를 불러오지 못했습니다. ({{ analysisStore.peakWindowsError }})
          </p>
          <p v-else-if="!analysisStore.peakWindows.length" class="windows-empty">
            이 기간에는 구간 기록이 없습니다.
          </p>
          <RouterLink to="/logs" class="more more-link">추천 이력 보기</RouterLink>
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

/* 요약 숫자 4개 */
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.summary dt {
  font-size: 13px;
  color: var(--ink-2);
}
.summary dd {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
}
.summary small {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 400;
  color: var(--ink-2);
}
.summary .pct {
  margin-left: 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ink-2);
}
.summary p {
  font-size: 12px;
  color: var(--mute);
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
  width: 520px;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th {
  padding: 6px 12px 6px 0;
}
.table td {
  padding: 9px 12px 9px 0;
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
  align-self: flex-end;
  margin-top: auto;
  padding-top: 10px;
}

/* 화면이 좁으면 오른쪽 표를 줄여 히트맵 자리를 남김 */
@media (max-width: 1600px) {
  .top {
    width: 440px;
  }
}
</style>
