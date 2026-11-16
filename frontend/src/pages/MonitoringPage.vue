<!--
  모니터링 > 통합
  1줄: 실시간 전력 상태 · 최근 1시간 전력 추세
  2줄: 지금 15분 구간 · 금일 피크 차트 · ESS 추천
  3줄: 지금 많이 쓰는 설비 · 이번 달 부하 시간대 사용량 · 이번 달 피크 · 최근 이벤트
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
import EssAdviceCard from '@/components/EssAdviceCard.vue'
import PeakWindowCard from '@/components/PeakWindowCard.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useReportStore } from '@/stores/useReportStore'
import {
  formatClock,
  formatHourMinute,
  formatInteger,
  formatKw,
  formatMonthDayTime,
  formatOneDecimal,
} from '@/utils/format'

const realtimeStore = useRealtimeStore()
const reportStore = useReportStore()

const busy = ref(false) // 적용/넘기기 요청 중
const actionError = ref('') // 적용/넘기기 실패 메시지

const status = computed(() => realtimeStore.status)

// 최신 상태·추천·이벤트는 공통 틀(MainLayout)이 5초마다 받아 오므로, 여기서는 그래프용 목록만 받음
onMounted(() => {
  realtimeStore.fetchToday()
  realtimeStore.fetchRecent()
})

// 15분 구간이 바뀌면 금일 피크 차트에 막대가 하나 늘어나므로 다시 받음
watch(
  () => status.value?.windowStart,
  (now, before) => {
    if (now && before) realtimeStore.fetchToday()
  },
)

// 1분이 지나면 전력 추세를 다시 받음 (서버 시각의 "분"이 바뀔 때)
watch(
  () => status.value?.recordedAt.slice(0, 16),
  (now, before) => {
    if (now && before) realtimeStore.fetchRecent()
  },
)

// "이번 달" 카드에 쓸 성적표. 서버 시각으로 이번 달을 알게 되면 한 번 받음
const serverMonth = computed(() => status.value?.recordedAt.slice(0, 7) ?? '')
watch(
  serverMonth,
  (month) => {
    if (month) reportStore.fetchReport(month)
  },
  { immediate: true },
)
const month = computed(() => (reportStore.report?.hasData ? reportStore.report : null))

// "이번 달" 카드에 값이 없을 때 그 이유를 알려 주는 문구
const monthMessage = computed(() => {
  if (reportStore.errorMessage) return '이번 달 값을 불러오지 못했습니다.'
  if (reportStore.report && !reportStore.report.hasData) return '이번 달 기록이 아직 없습니다.'
  return '이번 달 값을 불러오는 중입니다.'
})

// 끊겼던 연결이 돌아오면, 받지 못했던 이번 달 값을 다시 받음
watch(
  () => realtimeStore.connected,
  (connected) => {
    if (connected && !reportStore.report && serverMonth.value) {
      reportStore.fetchReport(serverMonth.value)
    }
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

// ---- 표시용 비율 (백엔드 값 두 개를 나눈 것) ----
// 한전 구매전력이 목표 피크의 몇 %인지
const gridPercent = computed(() =>
  status.value ? (status.value.gridKw / status.value.targetPeakKw) * 100 : null,
)
// 이번 구간 예상 평균이 목표보다 몇 % 높은지(+) 낮은지(-)
const overPercent = computed(() => {
  if (!status.value) return null
  return ((status.value.predictedKw - status.value.targetPeakKw) / status.value.targetPeakKw) * 100
})

const PEAK_INFO = {
  NORMAL: { shape: '●', label: '정상', className: 'ok' },
  WARNING: { shape: '▲', label: '주의', className: 'warn' },
  CRITICAL: { shape: '■', label: '경고', className: 'crit' },
}
const peakInfo = computed(() => {
  if (!realtimeStore.connected || !status.value) {
    return { shape: '◆', label: '통신 단절', className: 'lost' }
  }
  return PEAK_INFO[status.value.peakStatus]
})

const sources = computed(() => [
  { key: 'grid', name: '한전', kw: status.value?.gridKw },
  { key: 'solar', name: '태양광', kw: status.value?.solarKw },
  { key: 'ess', name: 'ESS', kw: status.value?.dischargeKw },
])

// ---- 그래프 공통 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 값을 그대로 적습니다.
const COLOR = {
  ink: '#14181c', // --ink
  mute: '#646e77', // --mute
  line: '#dde1e5', // --line
  ok: '#2f7d4f', // --ok
  warn: '#9c5f08', // --warn
  crit: '#c8372d', // --crit
  lost: '#8a9299', // --lost
  grid: '#5b6472', // --src-grid
}
const stateColor = computed(() => COLOR[peakInfo.value.className])

function targetLine(targetKw) {
  return {
    silent: true,
    symbol: 'none',
    lineStyle: { color: COLOR.ink, width: 2, type: 'solid' },
    label: {
      formatter: `목표 ${formatKw(targetKw)}`,
      position: 'insideStartTop',
      color: COLOR.ink,
      fontWeight: 'bold',
    },
    data: [{ yAxis: targetKw }],
  }
}

const valueAxis = {
  type: 'value',
  axisLabel: { color: COLOR.mute },
  splitLine: { lineStyle: { color: COLOR.line } },
}

// ---- 실시간 전력 추세 (최근 1시간) ----
const trendOption = computed(() => {
  const list = realtimeStore.recentStatuses
  const values = list.map((item) => item.gridKw)
  // 세로축을 값이 있는 범위 근처로 잡아 변화가 잘 보이게 함 (표시용 계산)
  const lowest = Math.min(...values, status.value?.targetPeakKw ?? Infinity)
  return {
    animation: false,
    grid: { left: 52, right: 16, top: 14, bottom: 24 },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatKw(value)} kW` },
    xAxis: {
      type: 'category',
      data: list.map((item) => formatHourMinute(item.recordedAt)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      axisLabel: { color: COLOR.mute, interval: 14 },
    },
    yAxis: { ...valueAxis, min: Math.floor((lowest * 0.9) / 100) * 100 },
    series: [
      {
        name: '한전 구매전력',
        type: 'line',
        showSymbol: false,
        data: values,
        lineStyle: { color: COLOR.grid, width: 2.5 },
        itemStyle: { color: COLOR.grid },
        markLine: status.value ? targetLine(status.value.targetPeakKw) : undefined,
      },
    ],
  }
})

// ---- 금일 피크 차트 ----
const times = computed(() =>
  realtimeStore.todayStatuses.map((item) => formatHourMinute(item.recordedAt)),
)

// 요금이 비싼 시간대(최대부하)를 회색 띠로 표시. 시간대 구분은 백엔드가 준 값
const tariffAreas = computed(() => {
  const periods = status.value?.tariff.periods ?? []
  const lastTime = times.value[times.value.length - 1]
  return periods
    .filter((item) => item.period === 'PEAK' && item.from <= lastTime)
    .map((item) => [
      { xAxis: item.from, itemStyle: { color: 'rgba(20, 24, 28, 0.07)' } },
      // 아직 지나지 않은 시간은 그래프에 없으므로 마지막 시각까지만 칠함
      { xAxis: item.to <= lastTime ? item.to : lastTime },
    ])
})

const dayOption = computed(() => {
  const list = realtimeStore.todayStatuses
  const target = status.value?.targetPeakKw

  // 마지막 막대는 아직 끝나지 않은 지금 구간이므로 예상 평균으로 그림
  const bars = list.map((item, index) => {
    const isNow = index === list.length - 1 && status.value
    const value = isNow ? status.value.predictedKw : item.gridKw
    let color = COLOR.grid
    if (target && value > target) color = COLOR.crit
    if (isNow) color = stateColor.value
    return {
      value,
      itemStyle: { color },
      label: { show: Boolean(isNow), position: 'top', formatter: '지금', color: COLOR.ink },
    }
  })

  return {
    animation: false,
    grid: { left: 52, right: 16, top: 22, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    xAxis: {
      type: 'category',
      data: times.value,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      // 3시간마다 눈금 글자 (15분 막대 12개 = 3시간)
      axisLabel: { color: COLOR.mute, interval: (index) => index % 12 === 0 },
    },
    yAxis: valueAxis,
    series: [
      {
        name: '15분 평균',
        type: 'bar',
        barCategoryGap: '18%',
        data: bars,
        markArea: { silent: true, data: tariffAreas.value },
        markLine: target ? targetLine(target) : undefined,
      },
    ],
  }
})

// ---- 지금 많이 쓰는 설비: 많이 쓰는 순서로 5개 (표시용 정렬) ----
const topEquipments = computed(() => {
  const sorted = [...(status.value?.equipments ?? [])]
    .sort((a, b) => b.loadKw - a.loadKw)
    .slice(0, 5)
  const highest = sorted.length ? sorted[0].loadKw : 1
  return sorted.map((item) => ({ ...item, percent: (item.loadKw / highest) * 100 }))
})

// ---- 최근 이벤트 ----
const EVENT_INFO = {
  CRITICAL: { shape: '■', className: 'crit' },
  WARNING: { shape: '▲', className: 'warn' },
  NORMAL: { shape: '●', className: 'ok' },
  INFO: { shape: '○', className: 'info' },
}
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

    <!-- 1줄 -->
    <div class="row row-1">
      <section class="card state">
        <div class="card-head">
          <h2 class="card-title">실시간 전력 상태</h2>
          <span class="note">5초마다 갱신</span>
        </div>
        <p class="load">
          <span class="load-value num">{{ formatKw(status?.loadKw) }}</span>
          <span class="load-unit">kW</span>
          <span class="load-label">공장 소비전력</span>
        </p>
        <div class="usage">
          <span>
            한전 구매 <span class="num">{{ formatKw(status?.gridKw) }}</span> kW, 목표 피크 대비
          </span>
          <strong class="num" :class="{ over: gridPercent > 100 }">
            {{ formatOneDecimal(gridPercent) }} %
          </strong>
        </div>
        <div class="track" aria-hidden="true">
          <span
            class="bar"
            :class="{ over: gridPercent > 100 }"
            :style="{ width: `${Math.min(100, gridPercent ?? 0)}%` }"
          ></span>
        </div>
        <dl class="sources">
          <div v-for="source in sources" :key="source.key" :class="source.key">
            <dt><span class="dot" aria-hidden="true"></span>{{ source.name }}</dt>
            <dd>
              <span class="num">{{ formatKw(source.kw) }}</span> kW
            </dd>
          </div>
        </dl>
      </section>

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">실시간 전력 추세</h2>
          <span class="note">최근 1시간, 한전 구매전력 (kW)</span>
        </div>
        <Chart v-if="realtimeStore.recentStatuses.length" :option="trendOption" :height="176" />
        <p v-else class="empty tall">불러오는 중입니다.</p>
      </section>
    </div>

    <!-- 2줄 -->
    <div class="row row-2">
      <PeakWindowCard :status="status" :stale="!realtimeStore.connected" />

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">금일 피크 차트</h2>
          <span class="note">막대 하나가 15분 평균 (kW), 회색 띠는 요금 비싼 시간</span>
        </div>
        <dl class="stats">
          <div>
            <dt>최근 갱신</dt>
            <dd class="num">{{ formatClock(status?.recordedAt) }}</dd>
          </div>
          <div>
            <dt>현재 단계</dt>
            <dd :class="peakInfo.className">
              <span aria-hidden="true">{{ peakInfo.shape }}</span> {{ peakInfo.label }}
            </dd>
          </div>
          <div>
            <dt>목표 대비</dt>
            <dd class="num" :class="{ crit: overPercent > 0 }">
              {{ overPercent > 0 ? '+' : '' }}{{ formatOneDecimal(overPercent) }} %
            </dd>
          </div>
          <div>
            <dt>오늘 최대</dt>
            <dd>
              <span class="num">{{ formatKw(status?.todayPeakKw) }}</span> kW
            </dd>
          </div>
          <div>
            <dt>이번 달 최대</dt>
            <dd>
              <span class="num">{{ formatKw(month?.summary.monthPeakKw) }}</span> kW
            </dd>
          </div>
        </dl>
        <Chart v-if="realtimeStore.todayStatuses.length" :option="dayOption" :height="244" />
        <p v-else-if="realtimeStore.todayLoading" class="empty taller">불러오는 중입니다.</p>
        <p v-else-if="realtimeStore.todayError" class="empty taller" role="alert">
          그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
          <button class="text-button" @click="realtimeStore.fetchToday()">다시 시도</button>
        </p>
        <p v-else class="empty taller">오늘 기록이 아직 없습니다.</p>
      </section>

      <div class="advice-wrap">
        <EssAdviceCard
          class="advice"
          :decision="realtimeStore.decision"
          :status="status"
          :busy="busy"
          :offline="!realtimeStore.connected"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄 -->
    <div class="row row-3">
      <section class="card">
        <div class="card-head">
          <h2 class="card-title">지금 많이 쓰는 설비</h2>
          <span class="note">kW</span>
        </div>
        <ol v-if="topEquipments.length" class="rank">
          <li v-for="(item, index) in topEquipments" :key="item.id">
            <!-- 설비를 누르면 계통도에서 그 설비가 있는 구역이 펼쳐진 채로 열림 -->
            <RouterLink
              class="rank-link"
              :to="{ path: '/monitoring/diagram', query: { zone: item.id.split('-')[0] } }"
              :title="`${item.name}: 계통도에서 보기`"
            >
              <span class="rank-no num">{{ index + 1 }}</span>
              <span class="rank-name">{{ item.name }}</span>
              <span class="track thin" aria-hidden="true">
                <span class="bar dark" :style="{ width: `${item.percent}%` }"></span>
              </span>
              <strong class="num">{{ formatKw(item.loadKw) }}</strong>
              <!-- 누르면 다른 화면으로 간다는 표시 -->
              <span class="rank-go" aria-hidden="true">›</span>
            </RouterLink>
          </li>
        </ol>
        <p v-else class="empty">설비 값을 아직 받지 못했습니다.</p>
      </section>

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">이번 달 부하 시간대 사용량</h2>
        </div>
        <ul v-if="month" class="tariffs">
          <li v-for="item in month.bill.byTariff" :key="item.period">
            <div class="tariff-text">
              <strong>{{ item.label }}</strong>
              <span class="num">
                {{ formatInteger(item.kwh) }} kWh, {{ formatOneDecimal(item.percent) }}%
              </span>
            </div>
            <div class="track thin" aria-hidden="true">
              <span class="bar dark" :style="{ width: `${item.percent}%` }"></span>
            </div>
          </li>
        </ul>
        <p v-else class="empty">
          {{ monthMessage }}
          <button
            v-if="reportStore.errorMessage"
            class="text-button"
            @click="reportStore.fetchReport(serverMonth)"
          >
            다시 시도
          </button>
        </p>
      </section>

      <section class="card month">
        <div class="card-head">
          <h2 class="card-title">이번 달 피크</h2>
          <RouterLink to="/report" class="text-button">월간 성적표 보기</RouterLink>
        </div>
        <template v-if="month">
          <p class="month-peak" :class="{ over: month.summary.monthPeakKw > month.targetPeakKw }">
            <span class="num">{{ formatKw(month.summary.monthPeakKw) }}</span>
            <span class="load-unit">kW</span>
          </p>
          <p class="month-note">
            가장 높았던 피크
            <span class="num"
              >({{ formatMonthDayTime(month.summary.monthPeakAt).slice(0, 11) }})</span
            >, 목표 <span class="num">{{ formatKw(month.targetPeakKw) }}</span> kW
          </p>
          <dl class="stats bottom">
            <div>
              <dt>피크 방어</dt>
              <dd class="num">
                {{ month.summary.defenseSuccess }} / {{ month.summary.defenseTotal }}회
              </dd>
            </div>
            <div>
              <dt>성공률</dt>
              <dd class="num">{{ formatOneDecimal(month.summary.defenseSuccessPercent) }} %</dd>
            </div>
            <div>
              <dt>평균 저감</dt>
              <dd>
                <span class="num">{{ formatKw(month.summary.avgReductionKw) }}</span> kW
              </dd>
            </div>
          </dl>
        </template>
        <p v-else class="empty">
          {{ monthMessage }}
          <button
            v-if="reportStore.errorMessage"
            class="text-button"
            @click="reportStore.fetchReport(serverMonth)"
          >
            다시 시도
          </button>
        </p>
      </section>

      <section class="card">
        <div class="card-head">
          <h2 class="card-title">최근 이벤트</h2>
          <RouterLink to="/logs" class="text-button">추천 이력 보기</RouterLink>
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

/* 줄 배치 */
.row {
  display: grid;
  gap: 16px;
}
.row-1 {
  grid-template-columns: 480px minmax(0, 1fr);
}
.row-2 {
  grid-template-columns: 320px minmax(0, 1fr) 380px;
}
.row-3 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  flex: 1;
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
  margin-left: auto;
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
  height: 120px;
  color: var(--mute);
}
.empty.tall {
  height: 176px;
}
.empty.taller {
  height: 244px;
}
.empty .text-button {
  margin-left: 0;
}

/* 공통 막대 */
.track {
  height: 12px;
  background: var(--line);
}
.track.thin {
  height: 10px;
}
.bar {
  display: block;
  height: 100%;
  background: var(--ok);
  transition: width 0.4s ease;
}
.bar.over {
  background: var(--crit);
}
.bar.dark {
  background: var(--ink-2);
}

/* 실시간 전력 상태 */
.state {
  display: flex;
  flex-direction: column;
}
.load {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.load-value {
  font-size: 48px;
  font-weight: 700;
  line-height: 1.1;
}
.load-unit {
  font-size: 18px;
  color: var(--ink-2);
}
.load-label {
  color: var(--mute);
}
.usage {
  display: flex;
  align-items: baseline;
  margin: 6px 0 4px;
  font-size: 13px;
  color: var(--ink-2);
}
.usage strong {
  margin-left: auto;
  font-size: 16px;
  color: var(--ink);
}
.usage strong.over {
  color: var(--crit);
}
.sources {
  display: flex;
  gap: 32px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.sources dt {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--mute);
}
.sources dd {
  font-size: 18px;
  font-weight: 700;
}
.dot {
  width: 10px;
  height: 10px;
  background: var(--source);
}
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess {
  --source: var(--src-ess);
}

/* 금일 피크 차트 위의 요약 숫자 */
.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 28px;
  margin-bottom: 4px;
}
.stats dt {
  font-size: 12px;
  color: var(--mute);
}
.stats dd {
  font-size: 17px;
  font-weight: 700;
  /* 숫자와 단위가 두 줄로 갈라지지 않게 */
  white-space: nowrap;
}
.stats dd.ok {
  color: var(--ok);
}
.stats dd.warn {
  color: var(--warn);
}
.stats dd.crit {
  color: var(--crit);
}
/* 통신 단절: 연한 회색(--lost)은 흰 배경에서 글자로 읽기 어려워 진한 회색을 씀 */
.stats dd.lost {
  color: var(--ink-2);
}
.stats.bottom {
  gap: 28px;
  margin-top: auto;
  margin-bottom: 0;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.advice-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.advice {
  flex: 1;
}
.action-error {
  color: var(--crit);
}

/* 지금 많이 쓰는 설비 */
.rank {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rank-link {
  display: grid;
  grid-template-columns: 16px 88px minmax(0, 1fr) 48px 8px;
  align-items: center;
  gap: 10px;
  /* 누를 수 있는 줄이라 위아래로 여유를 주고, 카드 안쪽 여백만큼 좌우로 넓힘 */
  margin: 0 -8px;
  padding: 4px 8px;
}
.rank-link:hover {
  background: var(--bg);
}
.rank-go {
  color: var(--mute);
}
.rank-link:hover .rank-go {
  color: var(--ink);
}
.rank-no {
  color: var(--mute);
}
.rank-name {
  white-space: nowrap;
}
.rank strong {
  font-size: 15px;
  text-align: right;
}

/* 부하 시간대 */
.tariffs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.tariff-text {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}
.tariff-text span {
  font-size: 13px;
  color: var(--ink-2);
}

/* 이번 달 피크 */
.month {
  display: flex;
  flex-direction: column;
}
.month-peak {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.15;
}
.month-peak.over {
  color: var(--crit);
}
.month-note {
  font-size: 13px;
  color: var(--ink-2);
}

/* 최근 이벤트 */
.events {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.events li {
  display: flex;
  gap: 12px;
  font-size: 13px;
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

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
