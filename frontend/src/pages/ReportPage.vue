<!-- 성적표: 한 달 동안 ESS 추천 운영이 실제로 어땠는지 보여 줍니다 -->
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import BillCard from '@/components/BillCard.vue'
import Chart from '@/components/Chart.vue'
import { useThemeStore } from '@/stores/useThemeStore'
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

const STATUS_LABELS = { RECOMMENDED: '추천 중', APPLIED: '적용', IGNORED: '넘김', AUTO: '자동' }

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

// 전기요금 비교에 쓰는 작년 같은 달 이름 (예: '작년 11월')
const lastYearLabel = computed(() =>
  month.value ? `작년 ${Number(month.value.slice(5, 7))}월` : '작년 같은 달',
)

// 이전 달(-1) / 다음 달(+1)로 이동
function moveMonth(step) {
  const [year, monthNumber] = month.value.split('-').map(Number)
  const moved = new Date(year, monthNumber - 1 + step, 1)
  month.value = `${moved.getFullYear()}-${String(moved.getMonth() + 1).padStart(2, '0')}`
}

// 인쇄 창 열기 (인쇄할 때의 모양은 아래 @media print와 MainLayout.vue에서 정함)
// 다크 모드여도 종이에는 밝은 색으로 나오게, 인쇄하는 동안만 라이트 색으로 그래프를 다시 그림
async function printReport() {
  const isDark = themeStore.theme === 'dark'
  if (isDark) {
    themeStore.apply('light')
    await nextTick() // 그래프가 라이트 색으로 다시 그려질 때까지 기다림
  }
  window.print()
  if (isDark) themeStore.apply()
}

// ---- CSV 저장 (엑셀에서 열 수 있는 표 파일) ----
// 표 한 줄을 CSV 한 줄로 바꿈. 값에 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감쌈
function csvLine(cells) {
  return cells
    .map((cell) => {
      const text = String(cell ?? '')
      return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
    })
    .join(',')
}

// 지금 보고 있는 달의 성적표를 CSV 파일로 내려받음
// 엑셀에서 계산에 쓸 수 있도록 숫자는 화면용으로 꾸미지 않고 받은 값 그대로 넣음
function downloadCsv() {
  const data = report.value
  const summary = data.summary
  const dischargeByDate = {}
  for (const item of data.ess.dailyDischarge) dischargeByDate[item.date] = item.kwh

  const lines = [
    csvLine(['WATTever 월간 성적표', monthLabel.value, periodLabel.value]),
    csvLine(['피크 저감량은 방전하지 않았을 때의 예상과 비교한 값이고 금액과 탄소는 추정값입니다']),
    '',
    csvLine(['항목', '값', '단위']),
    csvLine(['목표 피크', data.targetPeakKw, 'kW']),
    csvLine(['피크 방어 성공률', summary.defenseSuccessPercent, '%']),
    csvLine(['방전 추천 횟수', summary.defenseTotal, '회']),
    csvLine(['목표 달성 횟수', summary.defenseSuccess, '회']),
    csvLine(['최대 피크', summary.monthPeakKw, 'kW']),
    csvLine(['최대 피크 시각', summary.monthPeakAt, '']),
    csvLine(['평균 피크 저감량', summary.avgReductionKw, 'kW']),
    csvLine(['전기요금 추정', data.bill.total, '원']),
    csvLine(['전력량요금 추정', data.bill.energyCharge, '원']),
    csvLine(['기본요금 추정', data.bill.baseCharge, '원']),
    csvLine(['월말 예상 요금', data.bill.monthEndEstimate, '원']),
    csvLine(['ESS 충전량', data.ess.chargeKwh, 'kWh']),
    csvLine(['ESS 방전량', data.ess.dischargeKwh, 'kWh']),
    csvLine(['태양광 발전량', data.solar.generationKwh, 'kWh']),
    csvLine(['탄소 절감 추정', data.solar.carbonTon, 'tCO2']),
    '',
    csvLine(['날짜', '최대 피크 (kW)', '방전하지 않았을 때 예상 (kW)', 'ESS 방전량 (kWh)']),
    ...data.dailyPeaks.map((item) =>
      csvLine([item.date, item.peakKw, item.expectedKw, dischargeByDate[item.date]]),
    ),
    '',
    csvLine(['요금 시간대', '구매전력 (kWh)', '비중 (%)', '전력량요금 (원)']),
    ...data.bill.byTariff.map((item) => csvLine([item.label, item.kwh, item.percent, item.charge])),
    '',
    csvLine([
      '목표를 넘은 구간 시작',
      '끝',
      '실제 피크 (kW)',
      '초과 (kW)',
      '추천 방전 (kW)',
      '처리',
      '원인',
    ]),
    ...data.exceeded.map((item) =>
      csvLine([
        item.windowStart,
        item.windowEnd,
        item.resultPeakKw,
        item.overKw,
        item.powerKw,
        STATUS_LABELS[item.status],
        item.cause,
      ]),
    ),
  ]

  // 맨 앞의 \uFEFF는 엑셀이 한글을 깨뜨리지 않게 하는 표시(BOM), 줄 사이는 엑셀이 쓰는 줄바꿈(\r\n)
  const text = '\uFEFF' + lines.join('\r\n')
  const blob = new Blob([text], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  // 내려받기는 보이지 않는 링크를 만들어 누르는 방식으로 함 (브라우저에서 파일을 저장하는 표준 방법)
  const link = document.createElement('a')
  link.href = url
  link.download = `WATTever_월간성적표_${month.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
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

// 받아 둔 성적표가 지금 고른 달의 것인지 (기록 없는 달은 month, 있는 달은 periodStart로 판단)
const isSameMonth = computed(() => {
  const data = report.value
  if (!data) return false
  return (data.month ?? data.periodStart?.slice(0, 7)) === month.value
})

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
      sub: `방전 추천 ${summary.defenseTotal}회 중 ${summary.defenseSuccess}회 목표 달성`,
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
    textStyle: { fontSize: 14, color: COLOR.ink2 }, // 그래프 글자 기본 크기·색 (기본 12px은 작아서 키움)
    grid: { left: 52, right: 16, top: 34, bottom: 24 },
    legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
    tooltip: {
      ...tooltipColors(),
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
// 세로로 긴 배치라 날짜 수에 맞춰 칸이 납작해지지 않게 높이를 잡음
const heatHeight = computed(() => Math.max(240, report.value.dailyPeaks.length * 14 + 60))

const heatOption = computed(() => {
  const target = report.value.targetPeakKw
  const dates = report.value.dailyPeaks.map((item) => item.date)
  const dayLabels = dates.map(dayLabel)

  return {
    animation: false,
    textStyle: { fontSize: 14, color: COLOR.ink2 }, // 그래프 글자 기본 크기·색 (기본 12px은 작아서 키움)
    grid: { left: 44, right: 12, top: 30, bottom: 22 },
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
      dimension: 2,
      orient: 'horizontal',
      top: 0,
      right: 0,
      itemWidth: 14,
      itemHeight: 10,
      itemGap: 4,
      textStyle: { color: COLOR.mute },
      pieces: [
        { lte: target * 0.72, color: COLOR.heat1, label: '낮음' },
        { gt: target * 0.72, lte: target * 0.8, color: COLOR.heat2, label: ' ' },
        { gt: target * 0.8, lte: target * 0.88, color: COLOR.heat3, label: ' ' },
        { gt: target * 0.88, lte: target * 0.95, color: COLOR.heat4, label: ' ' },
        { gt: target * 0.95, lte: target, color: COLOR.heat5, label: '높음' },
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
        itemStyle: { borderColor: COLOR.card, borderWidth: 1 },
        emphasis: { itemStyle: { borderColor: COLOR.ink, borderWidth: 1 } },
      },
    ],
  }
})

// ---- ESS 일별 방전량 ----
const essOption = computed(() => ({
  animation: false,
  textStyle: { fontSize: 14, color: COLOR.ink2 }, // 그래프 글자 기본 크기·색 (기본 12px은 작아서 키움)
  grid: { left: 52, right: 12, top: 12, bottom: 24 },
  tooltip: {
    ...tooltipColors(),
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
      <div class="crumb">
        <span class="crumb-parent">보고서</span>
        <span class="crumb-parent" aria-hidden="true">›</span>
        <h1 class="crumb-title">월간 성적표</h1>
      </div>
      <div class="month" role="group" aria-label="조회할 달">
        <button aria-label="이전 달" :disabled="!month" @click="moveMonth(-1)">◀</button>
        <span class="month-label num">{{ monthLabel }}</span>
        <button aria-label="다음 달" :disabled="!month || isLatestMonth" @click="moveMonth(1)">
          ▶
        </button>
      </div>
      <span class="period">{{ periodLabel }}</span>
      <div class="actions">
        <!-- 브라우저의 인쇄 창을 엶. 인쇄 창에서 "PDF로 저장"을 고르면 파일로 남길 수 있음 -->
        <button class="action" :disabled="!report || !report.hasData" @click="printReport">
          인쇄 · PDF 저장
        </button>
        <!-- 엑셀에서 열 수 있는 표 파일로 내려받음 -->
        <button class="action" :disabled="!report || !report.hasData" @click="downloadCsv">
          CSV 저장
        </button>
      </div>
    </div>

    <!--
      같은 달의 성적표를 받아 둔 상태에서 다시 받기만 실패했다면 지우지 않고 위에 실패 띠만 띄움.
      다른 달로 옮기다 실패했으면 이전 달 숫자가 새 달처럼 보이면 안 되므로 전체 오류로 보여 줌
    -->
    <p v-if="reportStore.errorMessage && isSameMonth" class="error-band" role="alert">
      <span aria-hidden="true">◆</span>
      새 값을 불러오지 못해 이전에 받은 성적표를 보여 줍니다. ({{ reportStore.errorMessage }})
      <button class="error-band-button" @click="reportStore.fetchReport(month)">다시 시도</button>
    </p>
    <p v-if="reportStore.errorMessage && !isSameMonth" class="state error" role="alert">
      성적표를 불러오지 못했습니다. ({{ reportStore.errorMessage }})
      <button class="retry" @click="reportStore.fetchReport(month)">다시 시도</button>
    </p>
    <!-- 조회할 달은 서버 시각으로 정하므로, 서버 시각을 받기 전에는 조회하지 않음 -->
    <p v-else-if="!month" class="state">서버에서 현재 시각을 받으면 자동으로 조회합니다.</p>
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

      <!-- 위에서 아래로 한 줄씩: 전기요금 → 일별 피크 → 시간대 히트맵 → 목표를 넘은 구간 → ESS → 태양광 -->
      <BillCard
        :bill="report.bill"
        :peak-kw="report.summary.monthPeakKw"
        :in-progress="isLatestMonth"
        :last-year-total="reportStore.lastYearTotal"
        :last-year-label="lastYearLabel"
      />

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">일별 최대 피크와 저감량</h2>
          <span class="note">
            단위 kW · 빨강은 목표 초과 · 세로축은 {{ formatKw(peakAxisMin) }}부터
          </span>
        </div>
        <Chart :option="peakOption" :height="230" />
      </section>

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">피크가 몰리는 시간대</h2>
          <span class="note">칸 하나 = 그 날 그 시각의 최대 15분 피크</span>
        </div>
        <!-- key: 날짜 수가 바뀌면 그래프 높이도 바뀌므로 새로 만듦 -->
        <Chart :key="heatHeight" :option="heatOption" :height="heatHeight" />
      </section>

      <!-- 목표를 넘은 구간 -->
      <section class="card exceeded">
        <div class="card-head">
          <h2 class="card-title">목표를 넘은 구간 {{ report.exceeded.length }}건</h2>
          <RouterLink to="/logs" class="link">추천 이력에서 보기</RouterLink>
        </div>
        <p v-if="!report.exceeded.length" class="empty">목표를 넘은 구간이 없습니다.</p>
        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>대상 시간</th>
                <th class="right">실제 피크</th>
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

      <p class="caution">
        피크 저감량은 "방전하지 않았을 때의 예상"과 비교한 값이고, 금액과 탄소는 추정값입니다.
      </p>
    </template>
  </div>
</template>

<style scoped>
/* 보고서는 위에서 아래로 읽는 세로 배치. 글줄이 너무 길어지지 않게 폭을 제한하고 가운데에 둠 */
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1200px;
  min-height: 100%;
  margin: 0 auto;
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
  font-size: 14px;
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
  font-size: 17px;
  font-weight: 700;
  text-align: center;
}
.period {
  color: var(--mute);
}
.actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.action {
  height: 36px;
  padding: 0 14px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink);
}
.action:hover:not(:disabled) {
  border-color: var(--ink);
}
.action:disabled {
  opacity: 0.45;
  cursor: default;
}
/* 화면 맨 아래의 주의 문구 */
.caution {
  font-size: 15px;
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
  font-size: 17px;
  font-weight: 400;
  color: var(--ink-2);
}
.kpi-sub {
  font-size: 15px;
  color: var(--mute);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.note {
  margin-left: auto;
  font-size: 15px;
  color: var(--mute);
}
.link {
  margin-left: auto;
  font-size: 15px;
  color: var(--accent);
}
.link:hover {
  text-decoration: underline;
}

/* 목표를 넘은 구간 */
.exceeded {
  display: flex;
  flex-direction: column;
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
  font-size: 14px;
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

/* 아래: ESS → 태양광·탄소 (세로로 이어서) */
.bottom {
  display: flex;
  flex-direction: column;
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
  font-size: 14px;
  color: var(--mute);
}
.stats dd {
  font-size: 18px;
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

/* 인쇄할 때: 누르는 것들은 숨기고, 안내 문구는 줄을 바꿔 다 보이게 함 */
@media print {
  .actions,
  .month button,
  .retry,
  .link {
    display: none;
  }
  .page {
    min-height: 0;
    padding: 0;
  }
  .head {
    flex-wrap: wrap;
  }
}
</style>
