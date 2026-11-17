<!--
  모니터링 > 통합
  1줄: KPI 5개 (공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량)
  2줄: 현재 15분 피크 구간(피크 게이지) · ESS 추천
  3줄: 오늘 공장 소비 / 오늘 한전 구매 그래프 · 이벤트 로그
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
import EssAdviceCard from '@/components/EssAdviceCard.vue'
import KpiCards from '@/components/KpiCards.vue'
import PeakWindowCard from '@/components/PeakWindowCard.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatHourMinute, formatKw } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const settingsStore = useSettingsStore() // 운영 모드를 알기 위해 (값은 공통 틀이 받아 둠)

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

// 공장 소비와 한전 구매는 그래프를 나눠 그림 (한 그래프에 선이 많으면 비교가 어려움)
// 두 그래프는 같은 가로축, 같은 요금 띠, 같은 "지금" 세로선을 씀
function makeTrendOption(lines, withTarget) {
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

  const series = lines.map((line) => ({
    name: line.name,
    type: 'line',
    showSymbol: false,
    connectNulls: false,
    data: values(line.field),
    lineStyle: { color: line.color, width: 2.5, type: line.dashed ? 'dashed' : 'solid' },
    itemStyle: { color: line.color },
  }))

  // 요금 띠와 "지금" 세로선은 첫 번째 선에, 목표 피크선은 한전 구매 그래프에만 붙임
  const first = series[0]
  first.markArea = { silent: true, data: tariffAreas }
  const markLines = []
  if (withTarget && target) {
    markLines.push({
      yAxis: target,
      // 목표 피크선은 모든 그래프에서 같은 모양 (먹색 실선). 빨강은 상태 표시에만 씀
      lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
      label: {
        formatter: `목표 피크 ${formatKw(target)}`,
        position: 'insideStartTop',
        color: COLOR.ink,
        fontWeight: 'bold',
      },
    })
  }
  if (lastTime) {
    // 세로선은 지금 구간의 칸에 긋고, 글자에는 서버가 보낸 지금 시각을 적음
    markLines.push({
      xAxis: lastTime,
      lineStyle: { color: COLOR.ink2, width: 1, type: 'solid' },
      label: {
        formatter: `지금 ${formatHourMinute(status.value?.recordedAt)}`,
        position: 'end',
        color: COLOR.ink,
      },
    })
  }
  first.markLine = { silent: true, symbol: 'none', data: markLines }

  return {
    animation: false,
    grid: { left: 52, right: 20, top: 28, bottom: 24 },
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
    series,
  }
}

// 오늘 그래프 두 개 (위: 공장 소비 / 아래: 한전 구매)
const trendCards = computed(() => [
  {
    key: 'load',
    title: '오늘 공장 소비',
    note: '15분 평균 (kW), 회색 띠는 요금 비싼 시간',
    option: makeTrendOption(
      [
        { name: '공장 소비', field: 'loadKw', color: COLOR.ink },
        { name: '태양광', field: 'solarKw', color: COLOR.solar, dashed: true },
      ],
      false,
    ),
  },
  {
    key: 'grid',
    title: '오늘 한전 구매',
    note: '15분 평균 (kW), 피크는 이 값으로 정해짐',
    option: makeTrendOption([{ name: '한전 구매', field: 'gridKw', color: COLOR.grid }], true),
  },
])
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
      <!-- 자동 모드일 때 멀리서도 보이게 제목 줄에 크게 표시 (실제 제어가 아니라 시뮬레이션) -->
      <p v-if="settingsStore.values['operation.auto_mode'] === true" class="mode-band">
        <strong>자동 모드 (시뮬레이션)</strong>
        <span class="mode-sub">추천을 자동으로 적용 중</span>
      </p>
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!-- 1줄: KPI 5개 -->
    <KpiCards :status="status" :stale="!realtimeStore.connected" />

    <!-- 2줄: 15분 피크 + ESS 추천 -->
    <div class="row middle">
      <PeakWindowCard class="peak" :status="status" :stale="!realtimeStore.connected" />

      <div class="advice-wrap">
        <EssAdviceCard
          class="advice"
          :decision="realtimeStore.decision"
          :busy="busy"
          :offline="!realtimeStore.connected"
          :auto="settingsStore.values['operation.auto_mode'] === true"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄: 오늘 그래프 두 개(공장 소비 / 한전 구매) + 이벤트 로그 -->
    <div class="row bottom">
      <div class="trends">
        <section v-for="card in trendCards" :key="card.key" class="card trend">
          <div class="card-head">
            <h2 class="card-title">{{ card.title }}</h2>
            <span class="note">{{ card.note }}</span>
          </div>
          <Chart v-if="realtimeStore.todayStatuses.length" :option="card.option" :height="170" />
          <p v-else-if="realtimeStore.todayLoading" class="empty chart-empty">불러오는 중입니다.</p>
          <p v-else-if="realtimeStore.todayError" class="empty chart-empty" role="alert">
            그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
            <button class="retry" @click="realtimeStore.fetchToday()">다시 시도</button>
          </p>
          <p v-else class="empty chart-empty">오늘 기록이 아직 없습니다.</p>
        </section>
      </div>

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
        <RouterLink to="/logs" class="more more-link">추천 이력 보기</RouterLink>
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
/* 자동 모드 띠: 상태색이 아니라 먹색 바탕으로 눈에 띄게 함 */
.mode-band {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-left: 16px;
  padding: 5px 12px;
  background: var(--ink);
  color: var(--card);
  font-size: 14px;
}
.mode-sub {
  font-size: 12px;
  color: var(--line);
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
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 330px;
  color: var(--mute);
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
.trends {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.chart-empty {
  height: 170px;
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
}

/* 화면이 좁으면 오른쪽 카드 폭을 줄여 왼쪽 그래프와 게이지 자리를 남김 */
@media (max-width: 1600px) {
  .advice-wrap,
  .events-card {
    width: 480px;
  }
}
</style>
