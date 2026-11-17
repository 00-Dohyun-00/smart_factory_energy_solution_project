<!--
  모니터링 > 설비 모니터링
  KPI 5개 → 가운데 전력 계통도 + 오른쪽에 고른 구역의 상세
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Chart from '@/components/Chart.vue'
import KpiCards from '@/components/KpiCards.vue'
import PowerTree from '@/components/PowerTree.vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatHourMinute, formatInteger, formatKw, formatOneDecimal } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const route = useRoute()
const router = useRouter()

const ZONE_LABELS = {
  Z1: '열교환기 가공',
  Z2: '브레이징',
  Z3: '사출',
  Z4: '모듈 조립',
  Z5: '검사',
  U: '유틸리티',
}
// 설비 운전 상태. 피크 상태(● 정상 등)와 헷갈리지 않게 상태색(초록·주황·빨강)은 쓰지 않고
// 상태마다 도형과 글자를 다르게 함 (PowerTree.vue와 같은 모양)
const STATE_INFO = {
  RUNNING: { shape: '●', label: '운전', className: 'running' },
  HEATING: { shape: '▲', label: '승온 중', className: 'heating' },
  STOPPED: { shape: '○', label: '정지', className: 'stopped' },
}

// 고른 구역은 주소에 적어 둠 (예: /monitoring/diagram?zone=Z3)
// 새로 고치거나 주소를 다른 사람에게 보내도 같은 구역이 열리고, 통합 화면의 설비 순위에서 바로 넘어올 수 있음
// 주소에 구역이 없거나 잘못된 값이면 전기를 가장 많이 쓰는 브레이징 구역(Z2)을 골라 둠
const zoneInAddress = route.query.zone
const selectedZone = ref(ZONE_LABELS[zoneInAddress] ? zoneInAddress : 'Z2')
const status = computed(() => realtimeStore.status)

// 구역을 고르면 그 구역의 상세(최근 1시간, 오늘 요약)를 받고 주소도 바꿈
onMounted(() => {
  realtimeStore.fetchZoneDetail(selectedZone.value)
})
watch(selectedZone, (zoneId) => {
  realtimeStore.fetchZoneDetail(zoneId)
  // replace: 구역을 누를 때마다 "뒤로 가기" 기록이 쌓이지 않게 함
  router.replace({ query: { zone: zoneId } })
})
// 1분이 지나면 다시 받음 (서버 시각의 "분"이 바뀔 때)
watch(
  () => status.value?.recordedAt.slice(0, 16),
  (now, before) => {
    if (now && before) realtimeStore.fetchZoneDetail(selectedZone.value)
  },
)

// 받은 상세가 지금 고른 구역의 것일 때만 보여 줌 (구역을 바꾼 직후에는 이전 구역 값이 남아 있을 수 있음)
const detail = computed(() =>
  realtimeStore.zoneDetail?.zoneId === selectedZone.value ? realtimeStore.zoneDetail : null,
)

// 고른 구역의 설비와 합계, 비율 (표시용 합계·비율)
const zoneEquipments = computed(() =>
  (status.value?.equipments ?? []).filter((item) => item.id.startsWith(`${selectedZone.value}-`)),
)
const zoneKw = computed(() =>
  zoneEquipments.value.length
    ? zoneEquipments.value.reduce((sum, item) => sum + item.loadKw, 0)
    : null,
)
const zonePercent = computed(() =>
  zoneKw.value === null ? null : (zoneKw.value / status.value.loadKw) * 100,
)
const equipmentRows = computed(() =>
  zoneEquipments.value.map((item) => ({
    ...item,
    percent: zoneKw.value ? (item.loadKw / zoneKw.value) * 100 : 0,
    stateInfo: STATE_INFO[item.state] ?? { shape: '', label: '—', className: '' },
  })),
)

// ---- 최근 1시간 그래프 ----
// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 값을 그대로 적습니다.
const COLOR = {
  mute: '#646e77', // --mute
  line: '#dde1e5', // --line
  grid: '#5b6472', // --src-grid
}

const trendOption = computed(() => ({
  animation: false,
  grid: { left: 46, right: 12, top: 10, bottom: 24 },
  tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatKw(value)} kW` },
  xAxis: {
    type: 'category',
    data: detail.value.points.map((item) => formatHourMinute(item.recordedAt)),
    boundaryGap: false,
    axisLine: { lineStyle: { color: COLOR.line } },
    axisTick: { show: false },
    axisLabel: { color: COLOR.mute, interval: 19 },
  },
  yAxis: {
    type: 'value',
    min: 0, // 0부터 그림. 범위를 좁히면 작은 변화가 크게 오르내리는 것처럼 보이기 때문
    axisLabel: { color: COLOR.mute },
    splitLine: { lineStyle: { color: COLOR.line } },
  },
  series: [
    {
      name: ZONE_LABELS[selectedZone.value],
      type: 'line',
      showSymbol: false,
      data: detail.value.points.map((item) => item.loadKw),
      lineStyle: { color: COLOR.grid, width: 2.5 },
      itemStyle: { color: COLOR.grid },
    },
  ],
}))
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">모니터링</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">설비 모니터링</h1>
      <p class="legend"><span class="box" aria-hidden="true"></span>차단기</p>
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!-- KPI 5개 (통합 화면과 같은 카드) -->
    <KpiCards :status="status" :stale="!realtimeStore.connected" />

    <div class="row">
      <!-- 계통도 -->
      <section class="card tree-card">
        <div class="card-head">
          <h2 class="card-title">전력 계통</h2>
          <span class="note">구역을 누르면 설비가 펼쳐집니다</span>
        </div>
        <div class="tree-body">
          <PowerTree v-model="selectedZone" :status="status" :stale="!realtimeStore.connected" />
        </div>
      </section>

      <!-- 고른 구역의 상세 -->
      <section class="card detail">
        <div class="card-head">
          <h2 class="card-title">{{ ZONE_LABELS[selectedZone] }} 구역</h2>
        </div>
        <p class="zone-kw">
          <span class="zone-value num">{{ formatKw(zoneKw) }}</span>
          <span class="zone-unit">kW</span>
          <span class="zone-share">
            공장의 <span class="num">{{ formatOneDecimal(zonePercent) }}</span>%
          </span>
        </p>
        <div class="track" aria-hidden="true">
          <span class="bar" :style="{ width: `${zonePercent ?? 0}%` }"></span>
        </div>

        <h3 class="sub-title">최근 1시간</h3>
        <Chart v-if="detail" :option="trendOption" :height="150" />
        <p v-else-if="realtimeStore.zoneError" class="empty" role="alert">
          불러오지 못했습니다.
          <button class="retry" @click="realtimeStore.fetchZoneDetail(selectedZone)">
            다시 시도
          </button>
        </p>
        <p v-else class="empty">불러오는 중입니다.</p>

        <h3 class="sub-title">이 구역의 설비</h3>
        <ul class="equipments">
          <li v-for="item in equipmentRows" :key="item.id">
            <div class="equipment-text">
              <strong>{{ item.name }}</strong>
              <span class="state" :class="item.stateInfo.className">
                <span aria-hidden="true">{{ item.stateInfo.shape }}</span>
                {{ item.stateInfo.label }}
              </span>
              <span class="equipment-kw"
                ><span class="num">{{ formatKw(item.loadKw) }}</span> kW</span
              >
            </div>
            <div class="track thin" aria-hidden="true">
              <span class="bar" :style="{ width: `${item.percent}%` }"></span>
            </div>
          </li>
        </ul>

        <dl class="today">
          <div>
            <dt>오늘 최대</dt>
            <dd>
              <span class="num">{{ formatKw(detail?.todayMaxKw) }}</span> kW
            </dd>
          </div>
          <div>
            <dt>오늘 평균</dt>
            <dd>
              <span class="num">{{ formatKw(detail?.todayAvgKw) }}</span> kW
            </dd>
          </div>
          <div>
            <dt>오늘 사용량</dt>
            <dd>
              <span class="num">{{ formatInteger(detail?.todayKwh) }}</span> kWh
            </dd>
          </div>
        </dl>
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
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 13px;
  color: var(--ink-2);
}
.box {
  width: 12px;
  height: 12px;
  background: var(--card);
  border: 2px solid var(--ink);
}

.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 16px;
  flex: 1;
}
.card-head {
  display: flex;
  align-items: baseline;
  margin-bottom: 8px;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.tree-card {
  display: flex;
  flex-direction: column;
}
.tree-body {
  flex: 1;
  min-height: 420px;
}

/* 오른쪽 상세 */
.detail {
  display: flex;
  flex-direction: column;
  border: 2px solid var(--ink);
}
.zone-kw {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.zone-value {
  font-size: 52px;
  font-weight: 700;
  line-height: 1.1;
}
.zone-unit {
  font-size: 18px;
  color: var(--ink-2);
}
.zone-share {
  color: var(--mute);
}
.track {
  height: 10px;
  margin-top: 6px;
  background: var(--line);
}
.track.thin {
  height: 8px;
  margin-top: 4px;
}
.bar {
  display: block;
  height: 100%;
  background: var(--ink-2);
  transition: width 0.4s ease;
}
.sub-title {
  margin: 14px 0 6px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-weight: 700;
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 150px;
  color: var(--mute);
}
.equipments {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.equipment-text {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
/* 설비 운전 상태: 상태색 대신 먹색 계열로, 도형과 글자로 구분 */
.state {
  font-size: 12px;
}
.state.running {
  color: var(--ink-2);
}
.state.heating {
  font-weight: 700;
  color: var(--ink);
}
.state.stopped {
  color: var(--mute);
}
.equipment-kw {
  margin-left: auto;
  font-size: 16px;
  font-weight: 700;
}
.today {
  display: flex;
  gap: 32px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.today dt {
  font-size: 12px;
  color: var(--mute);
}
.today dd {
  font-size: 18px;
  font-weight: 700;
}

/* 화면이 좁으면 오른쪽 상세를 줄여 계통도 그림(글자)이 너무 작아지지 않게 함 */
@media (max-width: 1600px) {
  .row {
    grid-template-columns: minmax(0, 1fr) 340px;
  }
  .zone-value {
    font-size: 44px;
  }
  .today {
    gap: 20px;
  }
  .today dd {
    font-size: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
