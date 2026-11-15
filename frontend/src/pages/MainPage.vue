<!-- 실시간 관제 (메인 화면) -->
<script setup>
import { computed, onMounted, ref } from 'vue'
import Chart from '@/components/Chart.vue'
import FactoryLayoutMap from '@/components/FactoryLayoutMap.vue'
import PeakGauge from '@/components/PeakGauge.vue'
import PowerFlowDiagram from '@/components/PowerFlowDiagram.vue'
import RecommendCard from '@/components/RecommendCard.vue'
import SupplyStrip from '@/components/SupplyStrip.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatClock, formatHourMinute } from '@/utils/format'

const realtimeStore = useRealtimeStore()

const view = ref('layout') // 가운데 영역: 'layout'(공장 레이아웃) | 'flow'(전력 흐름)
const busy = ref(false) // 적용/무시 요청 중
const actionError = ref('') // 적용/무시 실패 메시지

// 최신 상태·추천은 공통 틀(MainLayout)이 5초마다 받아 오므로, 여기서는 오늘 그래프만 받음
onMounted(() => {
  realtimeStore.fetchToday()
})

async function answerDecision(newStatus) {
  busy.value = true
  actionError.value = ''
  try {
    await realtimeStore.changeDecisionStatus(newStatus)
  } catch (error) {
    actionError.value = error.message
  } finally {
    busy.value = false
  }
}

// ---- 오늘 그래프 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 색 값을 그대로 적습니다.
const COLOR = {
  ink: '#1c1c1c', // --ink
  mute: '#6e6e6a', // --mute
  line: '#e3e3df', // --line
  grid: '#5b6472', // --src-grid
  solar: '#d19a00', // --src-solar
  ess: '#0f8f8a', // --src-ess
}

const times = computed(() => realtimeStore.todayStatuses.map((s) => formatHourMinute(s.recordedAt)))

function baseOption(unit) {
  return {
    animation: false, // 값이 계속 갱신되는 화면이라 깜빡임 방지
    grid: { left: 52, right: 16, top: 34, bottom: 26 },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value} ${unit}` },
    xAxis: {
      type: 'category',
      data: times.value,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisLabel: { color: COLOR.mute },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLOR.mute },
      splitLine: { lineStyle: { color: COLOR.line } },
    },
  }
}

// 요금 시간대 배경 띠: 최대부하는 진하게(이름 표시), 중간부하는 옅게, 경부하는 칠하지 않음
// 시간대 구분은 백엔드가 준 값(status.tariff.periods)을 그대로 씀
const tariffAreas = computed(() => {
  const periods = realtimeStore.status?.tariff.periods ?? []
  const lastTime = times.value[times.value.length - 1]
  return periods
    .filter((item) => item.period !== 'LIGHT' && item.from <= lastTime)
    .map((item) => {
      const isPeak = item.period === 'PEAK'
      return [
        {
          xAxis: item.from,
          name: isPeak ? item.label : '',
          itemStyle: { color: isPeak ? 'rgba(28, 28, 28, 0.1)' : 'rgba(28, 28, 28, 0.04)' },
        },
        // 아직 지나지 않은 시간은 그래프에 없으므로 마지막 시각까지만 칠함
        { xAxis: item.to <= lastTime ? item.to : lastTime },
      ]
    })
})

// 계열을 색만으로 구분하지 않도록 선 모양(실선/점선)도 다르게 함
const powerOption = computed(() => ({
  ...baseOption('kW'),
  legend: { top: 0, right: 0, textStyle: { color: COLOR.mute } },
  series: [
    {
      name: '공장 소비',
      type: 'line',
      showSymbol: false,
      data: realtimeStore.todayStatuses.map((s) => s.loadKw),
      lineStyle: { color: COLOR.ink, width: 2 },
      itemStyle: { color: COLOR.ink },
      markArea: {
        silent: true,
        label: { position: 'insideTop', color: COLOR.mute, fontSize: 11 },
        data: tariffAreas.value,
      },
    },
    {
      name: '한전 구매',
      type: 'line',
      showSymbol: false,
      data: realtimeStore.todayStatuses.map((s) => s.gridKw),
      lineStyle: { color: COLOR.grid, width: 2, type: 'dashed' },
      itemStyle: { color: COLOR.grid },
      // 목표 피크 기준선 (백엔드가 준 값)
      markLine: realtimeStore.status
        ? {
            silent: true,
            symbol: 'none',
            lineStyle: { color: COLOR.ink, type: 'dotted' },
            label: { formatter: '목표 피크', position: 'insideStartTop', color: COLOR.ink },
            data: [{ yAxis: realtimeStore.status.targetPeakKw }],
          }
        : undefined,
    },
    {
      name: '태양광',
      type: 'line',
      showSymbol: false,
      data: realtimeStore.todayStatuses.map((s) => s.solarKw),
      lineStyle: { color: COLOR.solar, width: 2 },
      itemStyle: { color: COLOR.solar },
    },
  ],
}))

const socOption = computed(() => {
  const option = baseOption('%')
  option.yAxis.min = 0
  option.yAxis.max = 100
  option.series = [
    {
      name: '배터리 잔량',
      type: 'line',
      showSymbol: false,
      data: realtimeStore.todayStatuses.map((s) => s.socPercent),
      lineStyle: { color: COLOR.ess, width: 2 },
      itemStyle: { color: COLOR.ess },
    },
  ]
  return option
})
</script>

<template>
  <div class="page">
    <!-- 서버 연결이 끊기면 마지막으로 받은 값을 그대로 두고 알림만 띄움 -->
    <p v-if="!realtimeStore.connected" class="notice" role="alert">
      <span aria-hidden="true">◆</span>
      서버와 연결되지 않습니다. 아래 값은
      <span class="num">{{ formatClock(realtimeStore.status?.recordedAt) }}</span>
      에 마지막으로 받은 값입니다.
    </p>

    <!-- 1줄: 실시간 지표 띠 (공장 소비 = 한전 + 태양광 + ESS, 배터리 잔량) -->
    <SupplyStrip :status="realtimeStore.status" :stale="!realtimeStore.connected" />

    <!-- 2줄: 공장 레이아웃(또는 전력 흐름) + 오른쪽에 피크 게이지·추천 -->
    <div class="middle">
      <section class="card visual">
        <div class="visual-head">
          <h2 class="card-title">{{ view === 'layout' ? '공장 레이아웃' : '전력 흐름' }}</h2>
          <span class="visual-sub">열관리 부품 공장</span>
          <ul class="legend">
            <li><span class="dot grid" aria-hidden="true"></span>한전</li>
            <li><span class="dot solar" aria-hidden="true"></span>태양광</li>
            <li><span class="dot ess" aria-hidden="true"></span>ESS</li>
          </ul>
          <div class="switch" role="group" aria-label="보기 전환">
            <button
              :class="{ active: view === 'layout' }"
              :aria-pressed="view === 'layout'"
              @click="view = 'layout'"
            >
              레이아웃
            </button>
            <button
              :class="{ active: view === 'flow' }"
              :aria-pressed="view === 'flow'"
              @click="view = 'flow'"
            >
              전력 흐름
            </button>
          </div>
        </div>
        <div class="visual-body">
          <FactoryLayoutMap v-if="view === 'layout'" :status="realtimeStore.status" />
          <PowerFlowDiagram v-else :status="realtimeStore.status" />
        </div>
      </section>

      <div class="side">
        <PeakGauge :status="realtimeStore.status" :stale="!realtimeStore.connected" />
        <RecommendCard
          class="side-reco"
          :decision="realtimeStore.decision"
          :busy="busy || !realtimeStore.connected"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄: 오늘 그래프 (kW와 %는 단위가 달라 그래프를 나눔) -->
    <div class="charts">
      <section class="card">
        <h2 class="card-title">
          오늘 전력
          <span class="chart-unit"
            >kW · 배경 띠는 요금 시간대 (진함: 최대부하, 옅음: 중간부하)</span
          >
        </h2>
        <Chart v-if="realtimeStore.todayStatuses.length" :option="powerOption" :height="170" />
        <p v-else-if="realtimeStore.todayLoading" class="chart-empty">불러오는 중입니다.</p>
        <p v-else-if="realtimeStore.todayError" class="chart-empty" role="alert">
          그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
          <button class="retry" @click="realtimeStore.fetchToday()">다시 시도</button>
        </p>
        <p v-else class="chart-empty">오늘 기록이 아직 없습니다.</p>
      </section>
      <section class="card">
        <h2 class="card-title">오늘 배터리 잔량 <span class="chart-unit">%</span></h2>
        <Chart v-if="realtimeStore.todayStatuses.length" :option="socOption" :height="170" />
        <p v-else-if="realtimeStore.todayLoading" class="chart-empty">불러오는 중입니다.</p>
        <p v-else-if="realtimeStore.todayError" class="chart-empty" role="alert">
          그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
          <button class="retry" @click="realtimeStore.fetchToday()">다시 시도</button>
        </p>
        <p v-else class="chart-empty">오늘 기록이 아직 없습니다.</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  /* 화면이 낮을 때(1366×768 등)는 내용이 찌그러지지 않고 세로 스크롤이 생기게 함 */
  min-height: 950px;
  padding: 20px;
}

.notice {
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--crit);
  border-radius: 6px;
  font-weight: 700;
  color: var(--crit);
}

.middle {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(400px, 27vw, 520px);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.visual {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
.visual-head {
  display: flex;
  align-items: center;
  gap: 16px;
}
.visual-sub {
  color: var(--mute);
}
.legend {
  display: flex;
  gap: 14px;
  margin-left: auto;
  color: var(--ink-2);
}
.legend li {
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.dot.grid {
  background: var(--src-grid);
}
.dot.solar {
  background: var(--src-solar);
}
.dot.ess {
  background: var(--src-ess);
}
.switch {
  display: flex;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  overflow: hidden;
}
.switch button {
  padding: 5px 14px;
  background: var(--card);
  border: 0;
  color: var(--ink-2);
  transition: background-color 0.15s;
}
.switch button:hover {
  background: var(--bg);
}
.switch button.active {
  background: var(--accent);
  font-weight: 700;
  color: var(--card);
}
.visual-body {
  flex: 1;
  min-height: 0;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}
.side-reco {
  /* 남는 높이를 채우되, 내용보다 작아지지는 않게 함 */
  flex: 1 0 auto;
}
.action-error {
  color: var(--crit);
}

.charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  flex: none;
}
.chart-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
}
.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 170px;
  color: var(--mute);
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
</style>
