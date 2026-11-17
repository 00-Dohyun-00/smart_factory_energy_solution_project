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
import { formatHourMinute, formatInteger, formatKw, formatMonthDayTime } from '@/utils/format'

const analysisStore = useAnalysisStore()
const realtimeStore = useRealtimeStore()

// 처리 상태는 색만으로 구분하지 않도록 도형을 함께 표시 (운영 로그 화면과 같은 표시)
const STATUS_SHAPES = { APPLIED: '●', AUTO: '◇', IGNORED: '✕' }

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
}

// 기간이 바뀌면 다시 조회
watch([fromDate, toDate], () => load())

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

    <p v-if="analysisStore.peakError" class="state error" role="alert">
      분석 결과를 불러오지 못했습니다. ({{ analysisStore.peakError }})
      <button class="retry" @click="load()">다시 시도</button>
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
            {{ summary.maxPeakKw > peak.targetPeakKw ? '목표 초과' : '목표 이내' }}
          </p>
        </div>
        <div class="card">
          <dt>목표를 넘은 구간</dt>
          <dd>
            <span class="num">{{ formatInteger(summary.exceededCount) }}</span> <small>회</small>
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
            <span class="note">칸 하나 = 그 날 그 시각의 최대 15분 피크 · 진할수록 높음</span>
          </div>
          <!-- key: 날짜 수가 바뀌면 그래프 높이도 바뀌므로 새로 만듦 -->
          <Chart :key="heatHeight" :option="heatOption" :height="heatHeight" />
        </section>

        <!-- 피크가 높았던 구간 -->
        <section class="card top">
          <div class="card-head">
            <h2 class="card-title">피크가 높았던 구간</h2>
            <span class="note">위에서 {{ peak.topWindows.length }}개</span>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th class="rank-col">순위</th>
                <th>15분 구간</th>
                <th class="right-text">피크</th>
                <th>그때의 추천 처리</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in peak.topWindows" :key="item.windowStart">
                <td class="rank-col num">{{ index + 1 }}</td>
                <td class="num">
                  {{ shortTime(item.windowStart) }}~{{ formatHourMinute(item.windowEnd) }}
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
                  {{ item.note }}
                </td>
              </tr>
            </tbody>
          </table>
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
  border-bottom: 1px solid var(--ink);
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
  text-align: left;
  white-space: nowrap;
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
.rank-col {
  width: 36px;
  color: var(--mute);
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

/* 화면이 좁으면 오른쪽 표를 줄여 히트맵 자리를 남김 */
@media (max-width: 1600px) {
  .top {
    width: 440px;
  }
}
</style>
