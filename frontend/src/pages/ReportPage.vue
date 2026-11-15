<!-- 성적표: 한 달 동안 ESS 추천 운영이 실제로 어땠는지 보여 줍니다 -->
<script setup>
import { computed, ref, watch } from 'vue'
import BillCard from '@/components/BillCard.vue'
import Chart from '@/components/Chart.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useReportStore } from '@/stores/useReportStore'
import {
  formatHourMinute,
  formatInteger,
  formatKw,
  formatManwon,
  formatMonthDayTime,
  formatOneDecimal,
} from '@/utils/format'

const reportStore = useReportStore()
const realtimeStore = useRealtimeStore()

const STATUS_LABELS = { RECOMMENDED: '추천 중', APPLIED: '적용', IGNORED: '무시', AUTO: '자동' }

// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 색 값을 그대로 적습니다.
const COLOR = {
  ink: '#1c1c1c', // --ink
  mute: '#6e6e6a', // --mute
  line: '#e3e3df', // --line
  lineStrong: '#d4d4cf', // --line-strong
  crit: '#c8372d', // --crit
  grid: '#5b6472', // --src-grid
  ess: '#0f8f8a', // --src-ess
}
// 히트맵 색 단계: 낮음(옅음) → 높음(진함). --src-grid 색을 흰색에 섞은 값
const HEAT_COLORS = ['#ebecee', '#cdd0d4', '#adb1b8', '#848b96', '#5b6472']

// ---- 조회할 달 ----
const month = ref('') // 예: '2026-11'

// "이번 달"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함
const serverMonth = computed(() => realtimeStore.status?.recordedAt.slice(0, 7) ?? '')
const isLatestMonth = computed(() => month.value >= serverMonth.value)

const monthLabel = computed(() => {
  if (!month.value) return '—'
  const [year, monthNumber] = month.value.split('-').map(Number)
  return `${year}년 ${monthNumber}월`
})

// 이전 달(-1) / 다음 달(+1)로 이동
function moveMonth(step) {
  const [year, monthNumber] = month.value.split('-').map(Number)
  const moved = new Date(year, monthNumber - 1 + step, 1)
  month.value = `${moved.getFullYear()}-${String(moved.getMonth() + 1).padStart(2, '0')}`
}

// 달이 바뀌면 다시 조회
watch(month, (value) => {
  if (value) reportStore.fetchReport(value)
})

// 서버 시각을 처음 알게 되면 이번 달로 조회 시작
watch(
  serverMonth,
  (value) => {
    if (value && !month.value) month.value = value
  },
  { immediate: true },
)

const report = computed(() => reportStore.report)

const periodLabel = computed(() => {
  if (!report.value?.hasData) return ''
  const monthNumber = Number(report.value.periodStart.slice(5, 7))
  const firstDay = Number(report.value.periodStart.slice(8, 10))
  const lastDay = Number(report.value.periodEnd.slice(8, 10))
  return `${monthNumber}월 ${firstDay}일 ~ ${lastDay}일 기준`
})

// ---- 맨 위 핵심 숫자 4개 ----
const kpis = computed(() => {
  const summary = report.value.summary
  const target = report.value.targetPeakKw
  const bill = report.value.bill
  return [
    {
      label: '피크 방어 성공률',
      value: formatOneDecimal(summary.defenseSuccessPercent),
      unit: '%',
      sub: `방전 추천 ${summary.defenseTotal}회 중 ${summary.defenseSuccess}회 목표 이내`,
    },
    {
      label: '최대 피크',
      value: formatKw(summary.monthPeakKw),
      unit: 'kW',
      sub: `목표 ${formatKw(target)} kW · ${formatMonthDayTime(summary.monthPeakAt).slice(0, 11)}`,
      over: summary.monthPeakKw > target, // 목표를 넘었으면 빨강으로 표시
    },
    {
      label: '평균 피크 저감량',
      value: formatKw(summary.avgReductionKw),
      unit: 'kW',
      sub: `예상 대비 · 가장 크게 줄인 날 ${formatKw(summary.maxReductionKw)} kW`,
    },
    {
      label: '전기요금 추정',
      value: formatManwon(bill.total),
      unit: '만원',
      sub: `월말 예상 ${formatManwon(bill.monthEndEstimate)}만원`,
    },
  ]
})

// 날짜 문자열에서 '14일' 같은 라벨 만들기
function dayLabel(date) {
  return `${Number(date.slice(8, 10))}일`
}

const axisStyle = {
  axisLabel: { color: COLOR.mute },
  axisLine: { lineStyle: { color: COLOR.line } },
  axisTick: { show: false },
}

// ---- 일별 최대 피크와 저감량 ----
// 차이를 알아보기 쉽게 세로축을 0이 아닌 값에서 시작함 (표시용 계산)
const peakAxisMin = computed(() => {
  const lowest = Math.min(...report.value.dailyPeaks.map((item) => item.peakKw))
  return Math.floor((lowest * 0.9) / 100) * 100
})

const peakOption = computed(() => {
  const target = report.value.targetPeakKw
  return {
    animation: false,
    grid: { left: 52, right: 16, top: 34, bottom: 24 },
    legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    xAxis: {
      type: 'category',
      data: report.value.dailyPeaks.map((item) => dayLabel(item.date)),
      ...axisStyle,
    },
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
        data: report.value.dailyPeaks.map((item) => item.expectedKw),
        itemStyle: { color: COLOR.lineStrong },
      },
      {
        // 예상 막대와 같은 자리에 겹쳐 그림. 위로 남는 연한 부분이 그날의 저감량
        name: '실제',
        type: 'bar',
        barWidth: '56%',
        barGap: '-100%',
        itemStyle: { color: COLOR.grid },
        data: report.value.dailyPeaks.map((item) => ({
          value: item.peakKw,
          itemStyle: { color: item.peakKw > target ? COLOR.crit : COLOR.grid },
        })),
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
          label: { formatter: '목표', position: 'insideStartTop', color: COLOR.ink },
          data: [{ yAxis: target }],
        },
      },
    ],
  }
})

// ---- 피크가 몰리는 시간대 (날짜 × 시각 히트맵) ----
const heatOption = computed(() => {
  const target = report.value.targetPeakKw
  const dates = report.value.dailyPeaks.map((item) => item.date)
  const dayLabels = dates.map(dayLabel)

  return {
    animation: false,
    grid: { left: 44, right: 12, top: 30, bottom: 22 },
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
        data: report.value.heatmap.map((item) => [
          item.hour,
          dates.indexOf(item.date),
          item.peakKw,
        ]),
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        emphasis: { itemStyle: { borderColor: COLOR.ink, borderWidth: 1 } },
      },
    ],
  }
})

// ---- ESS 일별 방전량 ----
const essOption = computed(() => ({
  animation: false,
  grid: { left: 52, right: 12, top: 12, bottom: 24 },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    valueFormatter: (value) => `${formatInteger(value)} kWh`,
  },
  xAxis: {
    type: 'category',
    data: report.value.ess.dailyDischarge.map((item) => dayLabel(item.date)),
    ...axisStyle,
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: COLOR.mute },
    splitLine: { lineStyle: { color: COLOR.line } },
  },
  series: [
    {
      name: '방전량',
      type: 'bar',
      barWidth: '56%',
      data: report.value.ess.dailyDischarge.map((item) => item.kwh),
      itemStyle: { color: COLOR.ess },
    },
  ],
}))
</script>

<template>
  <div class="page">
    <!-- 제목 줄: 달 선택 -->
    <div class="head">
      <h1 class="title">성적표</h1>
      <div class="month" role="group" aria-label="조회할 달">
        <button aria-label="이전 달" :disabled="!month" @click="moveMonth(-1)">◀</button>
        <span class="month-label num">{{ monthLabel }}</span>
        <button aria-label="다음 달" :disabled="!month || isLatestMonth" @click="moveMonth(1)">
          ▶
        </button>
      </div>
      <span class="period">{{ periodLabel }}</span>
      <span class="caution">
        피크 저감량은 "방전하지 않았을 때의 예상"과 비교한 값이고, 금액과 탄소는 추정값입니다.
      </span>
    </div>

    <p v-if="reportStore.errorMessage" class="state error" role="alert">
      성적표를 불러오지 못했습니다. ({{ reportStore.errorMessage }})
      <button class="retry" @click="reportStore.fetchReport(month)">다시 시도</button>
    </p>
    <p v-else-if="reportStore.loading || !report" class="state">불러오는 중입니다.</p>
    <p v-else-if="!report.hasData" class="state">이 달에는 아직 기록이 없습니다.</p>

    <template v-else>
      <!-- 핵심 숫자 4개 -->
      <div class="kpis">
        <div v-for="item in kpis" :key="item.label" class="card kpi">
          <p class="kpi-label">{{ item.label }}</p>
          <p class="kpi-value" :class="{ over: item.over }">
            <span class="num">{{ item.value }}</span>
            <span class="kpi-unit">{{ item.unit }}</span>
          </p>
          <p class="kpi-sub num">{{ item.sub }}</p>
        </div>
      </div>

      <div class="middle">
        <div class="column">
          <section class="card">
            <div class="card-head">
              <h2 class="card-title">일별 최대 피크와 저감량</h2>
              <span class="note">
                단위 kW · 빨강은 목표 초과 · 세로축은 {{ formatKw(peakAxisMin) }}부터
              </span>
            </div>
            <Chart :option="peakOption" :height="190" />
          </section>

          <section class="card">
            <div class="card-head">
              <h2 class="card-title">피크가 몰리는 시간대</h2>
              <span class="note">칸 하나 = 그 날 그 시각의 최대 15분 피크</span>
            </div>
            <Chart :option="heatOption" :height="224" />
          </section>
        </div>

        <div class="column">
          <BillCard :bill="report.bill" />

          <!-- 목표를 넘은 구간 -->
          <section class="card exceeded">
            <div class="card-head">
              <h2 class="card-title">목표를 넘은 구간 {{ report.exceeded.length }}건</h2>
              <RouterLink to="/history" class="link">운영 이력에서 보기</RouterLink>
            </div>
            <p v-if="!report.exceeded.length" class="empty">목표를 넘은 구간이 없습니다.</p>
            <div v-else class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>15분 구간</th>
                    <th class="right">결과 피크</th>
                    <th class="right">초과</th>
                    <th>추천</th>
                    <th>처리</th>
                    <th>원인</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in report.exceeded" :key="item.windowStart">
                    <td class="num">
                      {{ formatMonthDayTime(item.windowStart).slice(0, 11) }}~{{
                        formatHourMinute(item.windowEnd)
                      }}
                    </td>
                    <td class="right num">
                      <strong>{{ formatKw(item.resultPeakKw) }} kW</strong>
                    </td>
                    <td class="right num over">+{{ formatKw(item.overKw) }}</td>
                    <td class="num">방전 {{ formatKw(item.powerKw) }} kW</td>
                    <td>{{ STATUS_LABELS[item.status] }}</td>
                    <td class="cause">{{ item.cause }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>

      <div class="bottom">
        <!-- ESS 운용 -->
        <section class="card ess">
          <div class="card-head">
            <h2 class="card-title">ESS 운용</h2>
            <span class="note">일별 방전량 (kWh)</span>
          </div>
          <div class="ess-body">
            <div class="ess-chart">
              <Chart :option="essOption" :height="150" />
            </div>
            <dl class="stats">
              <div>
                <dt>충전</dt>
                <dd class="num">{{ formatInteger(report.ess.chargeKwh) }} kWh</dd>
              </div>
              <div>
                <dt>방전</dt>
                <dd class="num">{{ formatInteger(report.ess.dischargeKwh) }} kWh</dd>
              </div>
              <div>
                <dt>충·방전 효율</dt>
                <dd class="num">{{ formatOneDecimal(report.ess.efficiencyPercent) }} %</dd>
              </div>
              <div>
                <dt>사용 횟수</dt>
                <dd class="num">{{ formatOneDecimal(report.ess.cycles) }} 회</dd>
              </div>
              <div>
                <dt>평균 잔량</dt>
                <dd class="num">{{ formatOneDecimal(report.ess.avgSocPercent) }} %</dd>
              </div>
              <div>
                <dt>최저 잔량</dt>
                <dd class="num">{{ formatOneDecimal(report.ess.minSocPercent) }} %</dd>
              </div>
              <div class="wide">
                <dt>잔량 부족으로 방전 못 함</dt>
                <dd class="num">{{ formatInteger(report.ess.blockedCount) }} 회</dd>
              </div>
            </dl>
          </div>
        </section>

        <!-- 태양광 · 탄소 -->
        <section class="card solar">
          <h2 class="card-title">태양광 · 탄소</h2>
          <dl class="solar-main">
            <div>
              <dt>태양광 발전량</dt>
              <dd class="num">{{ formatInteger(report.solar.generationKwh) }} kWh</dd>
            </div>
            <div>
              <dt>탄소 절감 추정</dt>
              <dd class="num">{{ formatOneDecimal(report.solar.carbonTon) }} tCO₂</dd>
            </div>
          </dl>
          <dl class="rows">
            <div>
              <dt>공장에서 바로 쓴 양</dt>
              <dd class="num">
                {{ formatInteger(report.solar.selfUseKwh) }} kWh ({{
                  formatOneDecimal(report.solar.selfUsePercent)
                }}%)
              </dd>
            </div>
            <div>
              <dt>한전 구매전력 감소</dt>
              <dd class="num">{{ formatInteger(report.solar.gridReductionKwh) }} kWh</dd>
            </div>
            <div>
              <dt>적용 배출계수 (확인 필요)</dt>
              <dd class="num">{{ report.solar.emissionFactor }} kgCO₂/kWh</dd>
            </div>
          </dl>
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

/* 제목 줄 */
.head {
  display: flex;
  align-items: center;
  gap: 20px;
}
.title {
  font-size: 20px;
  font-weight: 700;
}
.month {
  display: flex;
  align-items: center;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
}
.month button {
  width: 36px;
  height: 34px;
  background: none;
  border: 0;
  font-size: 12px;
  color: var(--ink-2);
}
.month button:hover:not(:disabled) {
  background: var(--bg);
}
.month button:disabled {
  opacity: 0.35;
  cursor: default;
}
.month-label {
  min-width: 108px;
  font-size: 15px;
  font-weight: 700;
  text-align: center;
}
.period {
  color: var(--mute);
}
.caution {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-height: 240px;
  color: var(--mute);
}
.state.error {
  color: var(--crit);
}
.retry {
  padding: 4px 12px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink-2);
}
.retry:hover {
  background: var(--bg);
}

/* 핵심 숫자 */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.kpi-label {
  color: var(--ink-2);
}
.kpi-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.25;
}
.kpi-value.over {
  color: var(--crit);
}
.kpi-unit {
  margin-left: 6px;
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}
.kpi-sub {
  font-size: 13px;
  color: var(--mute);
}

/* 가운데: 왼쪽 그래프 2개 + 오른쪽 요금·초과 구간 */
.middle {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(520px, 37vw, 700px);
  gap: 16px;
}
.column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.link {
  margin-left: auto;
  font-size: 13px;
  color: var(--accent);
}
.link:hover {
  text-decoration: underline;
}

/* 목표를 넘은 구간 */
.exceeded {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 80px;
  color: var(--mute);
}
.table-wrap {
  overflow: auto;
}
.table {
  width: 100%;
  white-space: nowrap;
}
.table th {
  padding: 4px 8px 6px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
  color: var(--mute);
  text-align: left;
}
.table td {
  padding: 7px 8px 7px 0;
  border-bottom: 1px solid var(--line);
}
.table .right {
  text-align: right;
}
.table td.over {
  font-weight: 700;
  color: var(--crit);
}
.cause {
  color: var(--ink-2);
}

/* 아래: ESS + 태양광·탄소 */
.bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(400px, 28vw, 520px);
  gap: 16px;
}
.ess-body {
  display: flex;
  gap: 32px;
}
.ess-chart {
  flex: 1;
  min-width: 0;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, auto);
  align-content: start;
  gap: 14px 32px;
}
.stats .wide {
  grid-column: span 2;
}
.stats dt,
.solar-main dt {
  font-size: 12px;
  color: var(--mute);
}
.stats dd {
  font-size: 16px;
  font-weight: 700;
}

.solar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.solar-main {
  display: flex;
  gap: 36px;
}
.solar-main dd {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.rows div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.rows dt {
  color: var(--ink-2);
}
.rows dd {
  font-weight: 700;
}
</style>
