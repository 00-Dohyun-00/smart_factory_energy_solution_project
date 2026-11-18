<!--
  모니터링 > 설비 모니터링
  가운데 전력 계통도 + 오른쪽에 고른 구역의 상세 (KPI 카드는 2026-11-18에 뺌, 통합 화면에서 봄)
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FitChart from '@/components/FitChart.vue'
import ProcessLayout from '@/components/ProcessLayout.vue'
import { useThemeStore } from '@/stores/useThemeStore'
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
const STATE_LABELS = { RUNNING: '운전', HEATING: '승온 중', STOPPED: '정지' }

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
    stateLabel: STATE_LABELS[item.state] ?? '—',
  })),
)

// ---- 최근 1시간 그래프 ----
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

const trendOption = computed(() => ({
  animation: false,
  textStyle: { fontSize: 14, color: COLOR.ink2 }, // 그래프 글자 기본 크기·색 (기본 12px은 작아서 키움)
  grid: { left: 46, right: 12, top: 10, bottom: 24 },
  tooltip: {
    ...tooltipColors(),
    trigger: 'axis',
    valueFormatter: (value) => `${formatKw(value)} kW`,
  },
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
      areaStyle: { color: COLOR.grid, opacity: 0.18 }, // 선 아래를 옅게 칠함
    },
  ],
}))

// ---- 구역별 전력 비율 (원그래프) ----
// 지금 공장 전력에서 각 구역이 차지하는 비율. 고른 구역 조각은 바깥으로 빼고 진하게, 나머지는 옅게 그려 강조함
const ZONE_ORDER = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'U']
const zoneShares = computed(() => {
  const equipments = status.value?.equipments ?? []
  const rows = ZONE_ORDER.map((zoneId) => ({
    zoneId,
    name: ZONE_LABELS[zoneId],
    kw: equipments
      .filter((item) => item.id.startsWith(`${zoneId}-`))
      .reduce((sum, item) => sum + item.loadKw, 0),
  }))
  const total = rows.reduce((sum, row) => sum + row.kw, 0) // 표시용 합계
  return rows.map((row) => ({ ...row, percent: total ? (row.kw / total) * 100 : 0 }))
})
const selectedShare = computed(() =>
  zoneShares.value.find((row) => row.zoneId === selectedZone.value),
)

const shareOption = computed(() => ({
  animation: false,
  textStyle: { fontSize: 14, color: COLOR.ink2 },
  tooltip: {
    ...tooltipColors(),
    trigger: 'item',
    formatter: (item) =>
      `${item.name}<br/>${formatKw(item.data.kw)} kW · ${formatOneDecimal(item.data.percent)}%`,
  },
  // 가운데: 고른 구역 이름과 비율
  title: {
    text: `${formatOneDecimal(selectedShare.value?.percent)}%`,
    subtext: ZONE_LABELS[selectedZone.value],
    left: 'center',
    top: 'center',
    itemGap: 4,
    textStyle: { fontSize: 26, fontWeight: 700, color: COLOR.ink },
    subtextStyle: { fontSize: 14, color: COLOR.ink2 },
  },
  series: [
    {
      type: 'pie',
      radius: ['38%', '60%'],
      center: ['50%', '50%'],
      selectedMode: 'single',
      selectedOffset: 12, // 고른 구역 조각을 바깥으로 빼서 보여 줌
      avoidLabelOverlap: true,
      label: {
        color: COLOR.ink2,
        fontSize: 13,
        formatter: (item) => `${item.name} ${formatOneDecimal(item.data.percent)}%`,
      },
      labelLine: { length: 8, length2: 8, lineStyle: { color: COLOR.lineStrong } },
      labelLayout: { hideOverlap: true },
      data: zoneShares.value.map((row) => {
        const isSelected = row.zoneId === selectedZone.value
        return {
          name: row.name,
          value: row.kw,
          kw: row.kw,
          percent: row.percent,
          selected: isSelected,
          // 고른 구역: 진한 색 + 굵은 글자 / 나머지: 옅은 회색
          itemStyle: {
            color: isSelected ? COLOR.ink : COLOR.faint,
            opacity: isSelected ? 1 : 0.55,
            borderColor: COLOR.card,
            borderWidth: 2,
          },
          label: isSelected ? { color: COLOR.ink, fontWeight: 700, fontSize: 15 } : {},
        }
      }),
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
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!-- 왼쪽: 공정 배치도 / 오른쪽: 고른 구역의 상세 -->
    <div class="row">
      <!-- 공정 배치도: 원자재 → 생산 구역 5칸 → 완제품, 공용 설비는 밖에. 누르면 오른쪽 상세가 바뀜 -->
      <section class="card plan-card">
        <div class="card-head">
          <h2 class="card-title">공정 배치</h2>
          <span class="note">열관리 부품 공장 (가상) · 구역을 누르면 오른쪽에 상세가 나옵니다</span>
        </div>
        <div class="plan-body">
          <ProcessLayout v-model="selectedZone" :status="status" />
        </div>
      </section>

      <section class="card detail">
        <div class="card-head">
          <h2 class="card-title">{{ ZONE_LABELS[selectedZone] }} 구역</h2>
          <span class="note">배치도에서 구역을 고르세요</span>
        </div>

        <!-- 지금 전력 (큰 숫자) -->
        <div class="now-box">
          <p class="now-caption">지금 전력</p>
          <p class="now-value">
            <span class="num">{{ formatKw(zoneKw) }}</span> <span class="now-unit">kW</span>
          </p>
          <p class="now-share num">공장의 {{ formatOneDecimal(zonePercent) }}%</p>
          <div class="track" aria-hidden="true">
            <span class="bar" :style="{ width: `${zonePercent ?? 0}%` }"></span>
          </div>
        </div>

        <h3 class="sub-title">최근 1시간</h3>
        <!-- 아래 원그래프와 남은 높이를 반씩 나눠 가짐 (2026-11-18, 예전 높이의 절반) -->
        <FitChart v-if="detail" :option="trendOption" :min-height="130" />
        <p v-else-if="realtimeStore.zoneError" class="empty" role="alert">
          불러오지 못했습니다.
          <button class="text-button" @click="realtimeStore.fetchZoneDetail(selectedZone)">
            다시 시도
          </button>
        </p>
        <p v-else class="empty">불러오는 중입니다.</p>

        <!-- 오늘 값 세 개 -->
        <dl class="today">
          <div>
            <dt>오늘 최대</dt>
            <dd>
              <span class="num">{{ formatKw(detail?.todayMaxKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>오늘 평균</dt>
            <dd>
              <span class="num">{{ formatKw(detail?.todayAvgKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>오늘 사용량</dt>
            <dd>
              <span class="num">{{ formatInteger(detail?.todayKwh) }}</span> <small>kWh</small>
            </dd>
          </div>
        </dl>

        <h3 class="sub-title">이 구역의 설비</h3>
        <ul class="equipments">
          <li v-for="item in equipmentRows" :key="item.id">
            <div class="equipment-text">
              <strong>{{ item.name }}</strong>
              <span class="state"><span aria-hidden="true">●</span> {{ item.stateLabel }}</span>
              <span class="equipment-kw"
                ><span class="num">{{ formatKw(item.loadKw) }}</span> kW</span
              >
            </div>
            <div class="track thin" aria-hidden="true">
              <span class="bar" :style="{ width: `${item.percent}%` }"></span>
            </div>
          </li>
        </ul>

        <h3 class="sub-title">구역별 전력 비율</h3>
        <!-- 고른 구역 조각을 바깥으로 빼고 진하게 보여 줌. 가운데에 고른 구역의 비율 -->
        <FitChart v-if="status" :option="shareOption" :min-height="180" />
        <p v-else class="empty">불러오는 중입니다.</p>
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
  font-size: 15px;
  color: var(--mute);
}
.plan-card {
  display: flex;
  flex-direction: column;
}
.plan-body {
  position: relative;
  flex: 1;
  min-height: 420px;
}
/* 배치도 그림은 칸 안에 비율을 지키며 들어가게 겹쳐 놓음
   (그림이 폭에 따라 커지면서 칸 높이를 밀어내 화면이 넘치지 않게) */
.plan-body > :deep(.layout) {
  position: absolute;
  inset: 0;
}

/* 오른쪽 상세 */
.detail {
  display: flex;
  flex-direction: column;
}
/* 지금 전력 상자 */
.now-box {
  padding: 14px 18px 16px;
  background: var(--bg);
  border-radius: 6px;
  text-align: center;
}
.now-caption {
  font-size: 15px;
  color: var(--mute);
}
.now-value {
  font-size: 52px;
  font-weight: 700;
  line-height: 1.1;
}
.now-unit {
  font-size: 20px;
  font-weight: 400;
  color: var(--ink-2);
}
.now-share {
  margin-bottom: 8px;
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
.text-button {
  padding: 0;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--ink-2);
  font-size: 15px;
  color: var(--ink-2);
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
.state {
  font-size: 14px;
  color: var(--ok);
}
.equipment-kw {
  margin-left: auto;
  font-size: 18px;
  font-weight: 700;
}
/* 오늘 값 세 개: 작은 상자 */
.today {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.today > div {
  padding: 8px 10px;
  background: var(--bg);
  border-radius: 6px;
}
.today small {
  font-size: 14px;
  font-weight: 400;
  color: var(--ink-2);
}
.today dt {
  font-size: 14px;
  color: var(--mute);
}
.today dd {
  font-size: 20px;
  font-weight: 700;
}

/* 화면이 좁으면 오른쪽 상세를 줄여 계통도 그림(글자)이 너무 작아지지 않게 함 */
@media (max-width: 1600px) {
  .row {
    grid-template-columns: minmax(0, 1fr) 360px;
  }
  .today {
    gap: 20px;
  }
  .today dd {
    font-size: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
