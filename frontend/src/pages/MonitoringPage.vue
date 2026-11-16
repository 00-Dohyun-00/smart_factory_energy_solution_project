<!--
  모니터링 > 통합
  1줄: KPI 5개 (공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량)
  2줄: 지금 15분 구간(피크 게이지) · ESS 추천
  3줄: 오늘 전력 추이 · 이벤트 로그
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
import EssAdviceCard from '@/components/EssAdviceCard.vue'
import PeakWindowCard from '@/components/PeakWindowCard.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatHourMinute, formatKw, formatOneDecimal } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const settingsStore = useSettingsStore() // 운영 모드를 알기 위해 (값은 공통 틀이 받아 둠)

// ESS 카드의 숫자 앞에 붙이는 말
const PCS_SHORT = { DISCHARGING: '방전', CHARGING: '충전', STANDBY: '대기' }

const busy = ref(false) // 적용/넘기기 요청 중
const actionError = ref('') // 적용/넘기기 실패 메시지

const status = computed(() => realtimeStore.status)

// 최신 상태·추천은 공통 틀(MainLayout)이 5초마다 받아 오므로, 여기서는 그래프용 목록만 받음
onMounted(() => {
  realtimeStore.fetchToday()
})

// 15분 구간이 바뀌면 오늘 전력 추이에 점이 하나 늘어나므로 다시 받음
watch(
  () => status.value?.windowStart,
  (now, before) => {
    if (now && before) realtimeStore.fetchToday()
  },
)

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

// ---- KPI 5개 ----
// 왼쪽부터 "공장 소비 = 한전 + 태양광 + ESS" 순서로 읽히게 놓고, 마지막에 배터리 잔량을 둠

// 한전 구매전력이 목표 피크의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const gridPercent = computed(() =>
  status.value ? (status.value.gridKw / status.value.targetPeakKw) * 100 : null,
)

const kpis = computed(() => {
  const s = status.value
  const over = gridPercent.value > 100
  // ESS는 충전 중이면 충전 전력을, 그 밖에는 방전 전력을 보여 줌
  const charging = s?.pcsStatus === 'CHARGING'
  const discharging = s?.pcsStatus === 'DISCHARGING'
  return [
    {
      key: 'load',
      name: '공장 소비전력',
      value: formatKw(s?.loadKw),
      unit: 'kW',
      sub: '한전 + 태양광 + ESS',
      // 막대: 공급원 비율 (백엔드가 준 값)
      bars: s
        ? [
            { source: 'grid', width: s.sourceRatio.gridPercent },
            { source: 'solar', width: s.sourceRatio.solarPercent },
            { source: 'ess', width: s.sourceRatio.essPercent },
          ]
        : [],
    },
    {
      key: 'grid',
      name: '한전 구매전력',
      value: formatKw(s?.gridKw),
      unit: 'kW',
      sub: s ? `목표 ${formatKw(s.targetPeakKw)} kW의 ${formatOneDecimal(gridPercent.value)}%` : '',
      over, // 목표 피크를 넘으면 경고색
      // 막대: 목표 피크를 100%로 봤을 때 지금 얼마나 찼는지
      bars: s ? [{ source: over ? 'over' : 'grid', width: Math.min(100, gridPercent.value) }] : [],
    },
    {
      key: 'solar',
      name: '태양광 발전량',
      value: formatKw(s?.solarKw),
      unit: 'kW',
      sub: s ? `공장 소비의 ${formatOneDecimal(s.sourceRatio.solarPercent)}%` : '',
      bars: s ? [{ source: 'solar', width: s.sourceRatio.solarPercent }] : [],
    },
    {
      key: 'ess',
      name: 'ESS 충·방전',
      // 숫자 앞에 지금 무엇을 하는 중인지 적음 (방전 500 kW / 충전 300 kW / 대기 0.0 kW)
      prefix: s ? (PCS_SHORT[s.pcsStatus] ?? '') : '',
      value: formatKw(s ? (charging ? s.chargeKw : s.dischargeKw) : null),
      unit: 'kW',
      sub: discharging ? `공장 소비의 ${formatOneDecimal(s.sourceRatio.essPercent)}%` : '',
      bars: discharging ? [{ source: 'ess', width: s.sourceRatio.essPercent }] : [],
    },
    {
      key: 'soc',
      name: '배터리 잔량 (SOC)',
      value: formatOneDecimal(s?.socPercent),
      unit: '%',
      sub: s ? `배터리실 ${formatOneDecimal(s.batteryTempC)}℃` : '',
      bars: s ? [{ source: 'ess', width: s.socPercent }] : [],
    },
  ]
})

// ---- 이벤트 로그 ----
// 단계는 색만으로 구분하지 않도록 도형을 함께 표시
const EVENT_INFO = {
  CRITICAL: { shape: '■', className: 'crit' },
  WARNING: { shape: '▲', className: 'warn' },
  NORMAL: { shape: '●', className: 'ok' },
  INFO: { shape: '○', className: 'info' },
}

// ---- 오늘 전력 추이 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 값을 그대로 적습니다.
const COLOR = {
  ink: '#14181c', // --ink
  ink2: '#3c444c', // --ink-2
  mute: '#646e77', // --mute
  line: '#dde1e5', // --line
  crit: '#c8372d', // --crit
  grid: '#5b6472', // --src-grid
  solar: '#d19a00', // --src-solar
}

// 가로축: 하루를 15분으로 나눈 96칸 ('00:00', '00:15', … '23:45')
// 아직 오지 않은 시간은 값 없이 비워 두어, 하루 중 지금이 어디쯤인지 보이게 함
const SLOTS = Array.from({ length: 96 }, (_, index) => {
  const hour = String(Math.floor(index / 4)).padStart(2, '0')
  const minute = String((index % 4) * 15).padStart(2, '0')
  return `${hour}:${minute}`
})

const trendOption = computed(() => {
  const list = realtimeStore.todayStatuses
  const target = status.value?.targetPeakKw
  const lastTime = list.length ? formatHourMinute(list[list.length - 1].recordedAt) : null

  // 시각별 값을 96칸에 맞춰 넣음 (없는 칸은 null → 선을 그리지 않음)
  const byTime = {}
  for (const item of list) byTime[formatHourMinute(item.recordedAt)] = item
  const values = (field) => SLOTS.map((time) => (byTime[time] ? byTime[time][field] : null))

  // 요금이 비싼 시간대(최대부하)를 회색 띠로 표시. 시간대 구분은 백엔드가 준 값
  const tariffAreas = (status.value?.tariff.periods ?? [])
    .filter((item) => item.period === 'PEAK')
    .map((item) => [
      { xAxis: item.from, itemStyle: { color: 'rgba(20, 24, 28, 0.07)' } },
      { xAxis: item.to === '24:00' ? '23:45' : item.to },
    ])

  const lineSeries = (name, field, color) => ({
    name,
    type: 'line',
    showSymbol: false,
    connectNulls: false,
    data: values(field),
    lineStyle: { color, width: 2.5 },
    itemStyle: { color },
  })

  const load = lineSeries('공장 소비', 'loadKw', COLOR.ink)
  const grid = lineSeries('한전 구매', 'gridKw', COLOR.grid)
  const solar = lineSeries('태양광', 'solarKw', COLOR.solar)

  // 목표 피크선과 요금 띠는 한전 구매 선에, "지금" 세로선은 공장 소비 선에 붙임
  grid.markArea = { silent: true, data: tariffAreas }
  if (target) {
    grid.markLine = {
      silent: true,
      symbol: 'none',
      lineStyle: { color: COLOR.crit, width: 2, type: 'solid' },
      label: {
        formatter: `목표 피크 ${formatKw(target)}`,
        position: 'insideStartTop',
        color: COLOR.crit,
        fontWeight: 'bold',
      },
      data: [{ yAxis: target }],
    }
  }
  if (lastTime) {
    load.markLine = {
      silent: true,
      symbol: 'none',
      lineStyle: { color: COLOR.ink2, width: 1, type: 'solid' },
      // 세로선은 지금 구간의 칸에 긋고, 글자에는 서버가 보낸 지금 시각을 적음
      label: {
        formatter: `지금 ${formatHourMinute(status.value?.recordedAt)}`,
        position: 'end',
        color: COLOR.ink,
      },
      data: [{ xAxis: lastTime }],
    }
  }

  return {
    animation: false,
    grid: { left: 52, right: 20, top: 40, bottom: 24 },
    legend: {
      right: 0,
      top: 0,
      icon: 'rect',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: COLOR.ink2 },
    },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatKw(value)} kW` },
    xAxis: {
      type: 'category',
      data: SLOTS,
      boundaryGap: false,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      // 3시간마다 눈금 글자 (15분 칸 12개 = 3시간)
      axisLabel: { color: COLOR.mute, interval: (index) => index % 12 === 0 },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLOR.mute },
      splitLine: { lineStyle: { color: COLOR.line } },
    },
    series: [load, grid, solar],
  }
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">모니터링</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">통합</h1>
      <p v-if="status" class="tariff">
        요금 시간대 <strong>{{ status.tariff.label }}</strong>
        <span class="num">{{ status.tariff.until }}</span>부터 {{ status.tariff.nextLabel }}
      </p>
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!-- 1줄: KPI 5개 -->
    <div class="row kpis">
      <section v-for="kpi in kpis" :key="kpi.key" class="card kpi" :class="kpi.key">
        <div class="kpi-top">
          <span class="dot" aria-hidden="true"></span>
          <h2 class="kpi-name">{{ kpi.name }}</h2>
        </div>
        <p class="kpi-value" :class="{ over: kpi.over }">
          <span v-if="kpi.prefix" class="kpi-prefix">{{ kpi.prefix }}</span>
          <span class="num">{{ kpi.value }}</span>
          <span class="kpi-unit">{{ kpi.unit }}</span>
        </p>
        <p class="kpi-sub num" :class="{ over: kpi.over }">{{ kpi.sub }}</p>
        <div class="track" aria-hidden="true">
          <span
            v-for="bar in kpi.bars"
            :key="bar.source"
            class="bar"
            :class="bar.source"
            :style="{ width: `${bar.width}%` }"
          ></span>
        </div>
      </section>
    </div>

    <!-- 2줄: 15분 피크 + ESS 추천 -->
    <div class="row middle">
      <PeakWindowCard class="peak" :status="status" :stale="!realtimeStore.connected" />

      <div class="advice-wrap">
        <EssAdviceCard
          class="advice"
          :decision="realtimeStore.decision"
          :busy="busy"
          :offline="!realtimeStore.connected"
          :auto="settingsStore.values.operationMode === 'AUTO'"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄: 오늘 전력 추이 + 이벤트 로그 -->
    <div class="row bottom">
      <section class="card trend">
        <div class="card-head">
          <h2 class="card-title">오늘 전력 추이</h2>
          <span class="note">15분 평균 (kW), 회색 띠는 요금 비싼 시간</span>
        </div>
        <Chart v-if="realtimeStore.todayStatuses.length" :option="trendOption" :height="330" />
        <p v-else-if="realtimeStore.todayLoading" class="empty">불러오는 중입니다.</p>
        <p v-else-if="realtimeStore.todayError" class="empty" role="alert">
          그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
          <button class="text-button" @click="realtimeStore.fetchToday()">다시 시도</button>
        </p>
        <p v-else class="empty">오늘 기록이 아직 없습니다.</p>
      </section>

      <section class="card events-card">
        <div class="card-head">
          <h2 class="card-title">이벤트 로그</h2>
          <span class="note">단계 변화, 추천, 적용·넘김, 구간 종료</span>
        </div>
        <ul v-if="realtimeStore.events.length" class="events">
          <li v-for="event in realtimeStore.events" :key="event.id">
            <span class="event-time num">{{ formatHourMinute(event.occurredAt) }}</span>
            <span class="event-text" :class="EVENT_INFO[event.level].className">
              <span aria-hidden="true">{{ EVENT_INFO[event.level].shape }}</span>
              {{ event.message }}
            </span>
          </li>
        </ul>
        <p v-else class="empty">아직 기록된 이벤트가 없습니다.</p>
        <RouterLink to="/logs" class="more">추천 이력 보기</RouterLink>
      </section>
    </div>
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
.tariff {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.tariff strong {
  margin: 0 6px;
  font-size: 15px;
  color: var(--ink);
}

.row {
  display: flex;
  gap: 16px;
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
.text-button {
  padding: 0;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--ink-2);
  font-size: 13px;
  color: var(--ink-2);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 330px;
  color: var(--mute);
}

/* 공급원 색 */
.dot {
  flex: none;
  width: 10px;
  height: 10px;
  background: var(--source, var(--ink));
}
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess,
.soc {
  --source: var(--src-ess);
}

/* 공통 막대 */
.track {
  display: flex;
  height: 8px;
  background: var(--line);
}
.bar {
  display: block;
  height: 100%;
  background: var(--source);
  transition: width 0.4s ease;
}
.bar.over {
  background: var(--crit);
}

/* 1줄: KPI 5개. 낮게 잡아 아래 줄이 첫 화면에 들어오게 함 */
.kpi {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 12px 16px;
}
/* 공장 소비전력은 특정 공급원이 아니므로 점을 먹색으로 */
.kpi.load {
  --source: var(--ink);
}
.kpi-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kpi-name {
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
  white-space: nowrap;
}
.kpi-sub {
  min-height: 18px;
  margin-bottom: 6px;
  overflow: hidden;
  font-size: 12px;
  color: var(--mute);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kpi-sub.over {
  font-weight: 700;
  color: var(--crit);
}
.kpi-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.2;
}
.kpi-value.over {
  color: var(--crit);
}
.kpi-prefix {
  font-size: 18px;
}
.kpi-unit {
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}

/* 2줄 */
.peak {
  flex: 1;
  min-width: 0;
}
.advice-wrap {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 8px;
  width: 600px;
}
.advice {
  flex: 1;
}
.action-error {
  color: var(--crit);
}

/* 3줄 */
.bottom {
  flex: 1;
}
.trend {
  flex: 1;
  min-width: 0;
}
.events-card {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 600px;
}
.events li {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--line);
}
.events li:last-child {
  border-bottom: 0;
}
.event-time {
  flex: none;
  font-weight: 700;
}
.event-text.crit {
  color: var(--crit);
}
.event-text.warn {
  color: var(--warn);
}
.event-text.ok {
  color: var(--ok);
}
.event-text.info {
  color: var(--ink-2);
}
/* 카드 맨 아래 오른쪽의 링크 */
.more {
  align-self: flex-end;
  margin-top: auto;
  border-bottom: 1px solid var(--ink-2);
  font-size: 13px;
  color: var(--ink-2);
}

/* 화면이 좁으면 오른쪽 카드 폭을 줄여 왼쪽 그래프와 게이지 자리를 남김 */
@media (max-width: 1600px) {
  .advice-wrap,
  .events-card {
    width: 480px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
