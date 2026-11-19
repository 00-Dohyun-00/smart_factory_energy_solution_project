<!--
  모니터링 > 설비 모니터링
  전력 계통도 + 오른쪽에 고른 구역의 상세 (KPI 5개는 2026-11-18에 뺌)
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Chart from '@/components/Chart.vue'
import FitChart from '@/components/FitChart.vue'
import PowerTree from '@/components/PowerTree.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatHourMinute, formatInteger, formatKw, formatOneDecimal } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const settingsStore = useSettingsStore()
const route = useRoute()
const router = useRouter()

const ZONE_LABELS = {
  Z1: '열교환기 가공',
  Z2: '브레이징',
  Z3: '사출',
  Z4: '모듈 조립',
  Z5: '검사',
  Z6: '유틸리티', // 백엔드 구역 코드 그대로 (예전 프론트 코드는 'U')
}
// 설비 운전 상태. 피크 상태(● 정상 등)와 헷갈리지 않게 상태색(초록·주황·빨강)은 쓰지 않고
// 상태마다 도형과 글자를 다르게 함 (PowerTree.vue와 같은 모양)
const STATE_INFO = {
  RUNNING: { shape: '●', label: '운전', className: 'running' },
  HEATING: { shape: '▲', label: '가열 중', className: 'heating' },
  STOPPED: { shape: '○', label: '정지', className: 'stopped' },
}

// 고른 구역은 주소에 적어 둠 (예: /monitoring/diagram?zone=Z3)
// 새로 고치거나 주소를 다른 사람에게 보내도 같은 구역이 열리고, 통합 화면의 설비 순위에서 바로 넘어올 수 있음
// 주소에 구역이 없거나 잘못된 값이면 전기를 가장 많이 쓰는 브레이징 구역(Z2)을 골라 둠
const zoneInAddress = route.query.zone
const selectedZone = ref(ZONE_LABELS[zoneInAddress] ? zoneInAddress : 'Z2')
const status = computed(() => realtimeStore.status)
// "이 구역의 설비"에서 고른 설비 id (안 골랐으면 빈 글자). 원그래프에서 그 설비 조각을 강조함
const selectedEquipment = ref('')

// 게이지를 보는 방식 (설비와 구역 모두)
// - 'relative'(상대값): 설비는 구역 전력 중 비율, 구역은 공장 전력 중 비율
// - 'absolute'(절대값): 막대 길이 = 전력 ÷ 게이지 끝 값. 임계치가 게이지의 90% 지점에 오고,
//   90%~100%(임계치를 넘는 부분)는 빨간 구간으로 칠함. 임계치가 없으면 막대를 그리지 않음
//   설비 임계치 = 환경 설정 값, 구역 임계치 = 그 구역 설비들의 임계치 합
const gaugeMode = ref('relative')
// 절대값 게이지에서 임계치를 놓는 위치 (게이지 전체의 90%)
const THRESHOLD_POINT = 0.9

// 게이지의 막대 길이(%), 옆에 적을 글자, 게이지 끝 kW(절대값일 때 눈금에 씀)
// loadKw: 지금 전력, threshold: 임계치, relativePercent·relativeText: 상대값일 때 막대와 글자
// absolutePrefix: 절대값일 때 글자 앞말 (예: '임계의' → "임계의 50.0%")
function gaugeOf(loadKw, threshold, relativePercent, relativeText, absolutePrefix) {
  if (gaugeMode.value === 'relative') {
    return { width: relativePercent, text: relativeText, fullKw: null }
  }
  if (threshold == null) return { width: 0, text: '임계 미설정', fullKw: null }
  const fullKw = threshold / THRESHOLD_POINT // 게이지 끝(100%)에 해당하는 kW
  return {
    width: Math.min((loadKw / fullKw) * 100, 100),
    text: `${absolutePrefix} ${formatOneDecimal((loadKw / threshold) * 100)}%`,
    fullKw,
  }
}

// 설비를 누르면 고르고, 이미 고른 설비를 다시 누르면 풀어 줌
function toggleEquipment(id) {
  selectedEquipment.value = selectedEquipment.value === id ? '' : id
}

// 구역을 고르면 그 구역의 상세(최근 1시간, 오늘 요약)를 받고 주소도 바꿈
onMounted(() => {
  realtimeStore.fetchZoneDetail(selectedZone.value)
})
watch(selectedZone, (zoneId) => {
  realtimeStore.fetchZoneDetail(zoneId)
  selectedEquipment.value = '' // 구역을 바꾸면 고른 설비는 풀어 둠
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

// 설비별 전력 임계치 (설비 id → kW)
// 환경 설정에서 프론트가 받은 값만 사용함. 설정값이 없으면 임계치를 표시하지 않음.
const thresholds = computed(() => {
  const result = {}
  for (const item of status.value?.equipments ?? []) {
    const saved = settingsStore.values[`equipment.${item.id}.max_kw`]
    result[item.id] = typeof saved === 'number' ? saved : null
  }
  return result
})

// 고른 구역의 설비와 합계, 비율 (표시용 합계·비율)
const zoneEquipments = computed(() =>
  (status.value?.equipments ?? []).filter((item) => item.zoneId === selectedZone.value),
)
const zoneKw = computed(() =>
  zoneEquipments.value.length
    ? zoneEquipments.value.reduce((sum, item) => sum + item.loadKw, 0)
    : null,
)
const zonePercent = computed(() =>
  zoneKw.value === null ? null : (zoneKw.value / status.value.loadKw) * 100,
)
// 구역 임계치 = 이 구역 설비들의 임계치 합 (한 대라도 임계치가 없으면 합을 내지 않음)
const zoneThreshold = computed(() =>
  zoneEquipments.value.length &&
  zoneEquipments.value.every((item) => thresholds.value[item.id] != null)
    ? zoneEquipments.value.reduce((sum, item) => sum + thresholds.value[item.id], 0)
    : null,
)
// 구역 게이지 (상대값이면 공장 대비 비율, 절대값이면 임계 합 기준)
const zoneGauge = computed(() =>
  gaugeOf(
    zoneKw.value ?? 0,
    zoneThreshold.value,
    zonePercent.value ?? 0,
    `공장의 ${formatOneDecimal(zonePercent.value)}%`,
    '임계 합의',
  ),
)
// 구역 id → 그래프용 구역 색 이름 (테마 스토어 colors의 키, 예: Z1 → zoneZ1)
const zoneColorKey = (zoneId) => `zone${zoneId}`
// 한 구역 안 설비들의 색 진하기 (계통도 PowerTree.vue와 같은 값)
const EQUIPMENT_OPACITY = [1, 0.6, 0.35]

// 설비별 색 진하기 정하기: 고른 설비가 있으면 그 설비가 가장 진한 색(1번째 진하기)을 갖고,
// 나머지 설비는 원래 순서대로 그다음 진하기를 받음. 고른 설비가 없으면 목록 순서대로
function opacityOrder(list, selectedId) {
  const ordered = selectedId
    ? [
        ...list.filter((item) => item.id === selectedId),
        ...list.filter((item) => item.id !== selectedId),
      ]
    : list
  const result = {}
  ordered.forEach((item, rank) => {
    result[item.id] = EQUIPMENT_OPACITY[rank % EQUIPMENT_OPACITY.length]
  })
  return result
}
const equipmentRows = computed(() => {
  const opacities = opacityOrder(zoneEquipments.value, selectedEquipment.value)
  return zoneEquipments.value.map((item) => {
    const percent = zoneKw.value ? (item.loadKw / zoneKw.value) * 100 : 0
    return {
      ...item,
      opacity: opacities[item.id],
      threshold: thresholds.value[item.id],
      over: thresholds.value[item.id] != null && item.loadKw > thresholds.value[item.id],
      percent,
      gauge: gaugeOf(
        item.loadKw,
        thresholds.value[item.id],
        percent,
        `구역의 ${formatOneDecimal(percent)}%`,
        '임계의',
      ),
      stateInfo: STATE_INFO[item.state] ?? { shape: '', label: '—', className: '' },
    }
  })
})

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
    },
  ],
}))

// ---- 원그래프 조각 누르기 ----
// 공통 차트(Chart.vue)는 클릭을 알려 주지 않으므로, 원그래프를 감싼 상자에서 누른 위치를 받아
// "가운데에서 어느 방향(각도)을 눌렀는지"로 몇 번째 조각인지 계산함
// (원그래프는 12시 방향에서 시작해 시계 방향으로 값 비율만큼 조각을 그림. 아래 donutOption과 같은 반지름)
function sliceIndexAt(event, values) {
  const box = event.currentTarget.getBoundingClientRect()
  const x = event.clientX - box.left - box.width / 2
  const y = event.clientY - box.top - box.height / 2
  const radius = Math.min(box.width, box.height) / 2
  const distance = Math.hypot(x, y)
  // 도넛 구멍(반지름 50%) 안이나 바깥(86%) 밖을 누르면 아무 조각도 아님 (테두리 몇 px는 봐줌)
  if (distance < radius * 0.5 - 4 || distance > radius * 0.86 + 10) return -1
  // 12시 방향 0°, 시계 방향으로 커지는 각도
  let angle = (Math.atan2(x, -y) * 180) / Math.PI
  if (angle < 0) angle += 360
  const total = values.reduce((sum, value) => sum + value, 0)
  if (!total) return -1
  let end = 0
  for (let i = 0; i < values.length; i++) {
    end += (values[i] / total) * 360
    if (angle < end) return i
  }
  return -1
}

// 구역 원그래프에서 조각을 누르면 그 구역을 고름 (계통도에서 구역을 누른 것과 같음)
function onZoneDonutClick(event) {
  const values = zoneShareOption.value.series[0].data.map((item) => item.value)
  const index = sliceIndexAt(event, values)
  if (index >= 0) selectedZone.value = ZONE_ORDER[index]
}

// 설비 원그래프에서 조각을 누르면 그 설비 버튼을 누른 것과 같음 (다시 누르면 풀림)
function onEquipmentDonutClick(event) {
  const index = sliceIndexAt(
    event,
    equipmentRows.value.map((item) => item.loadKw),
  )
  if (index >= 0) toggleEquipment(equipmentRows.value[index].id)
}

// ---- 원그래프 두 개 (공장 안 구역 비율, 구역 안 설비 비율) ----
// 고른 조각은 진한 색으로 칠하고 바깥으로 조금 빼서(selected) 강조하고, 나머지는 옅게 그림
const ZONE_ORDER = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'Z6']

function donutOption(items, unitLabel) {
  return {
    animation: false, // 값이 바뀔 때 조각이 움직이지 않게
    tooltip: {
      ...tooltipColors(),
      trigger: 'item',
      formatter: (item) =>
        `${item.name}<br/>${formatKw(item.value)} kW · ${unitLabel} ${formatOneDecimal(item.percent)}%`,
    },
    series: [
      {
        type: 'pie',
        radius: ['50%', '86%'],
        selectedMode: 'single',
        selectedOffset: 6,
        label: { show: false }, // 칸이 작아서 이름은 툴팁과 옆 글자로 보여 줌
        emphasis: { scale: false },
        data: items,
      },
    ],
  }
}

// 공장 전체 전력에서 각 구역이 차지하는 비율 (고른 구역 강조)
const zoneShareOption = computed(() => {
  const equipments = status.value?.equipments ?? []
  const items = ZONE_ORDER.map((zoneId) => {
    const isSelected = zoneId === selectedZone.value
    return {
      name: ZONE_LABELS[zoneId],
      value: equipments
        .filter((item) => item.zoneId === zoneId)
        .reduce((sum, item) => sum + item.loadKw, 0), // 표시용 합계
      selected: isSelected,
      // 구역마다 자기 색. 고른 구역은 진하게 + 테두리, 나머지는 옅게
      itemStyle: {
        color: COLOR[zoneColorKey(zoneId)],
        opacity: isSelected ? 1 : 0.3,
        borderColor: isSelected ? COLOR.ink : COLOR.card,
        borderWidth: 2,
      },
    }
  })
  return donutOption(items, '공장의')
})

// 이 구역 전력에서 각 설비가 차지하는 비율
// 색은 구역 색, 설비마다 진하기만 다름 (계통도 설비 상자와 같은 색). 설비를 고르면 그 조각만 테두리 + 나머지 옅게
const equipmentShareOption = computed(() => {
  const color = COLOR[zoneColorKey(selectedZone.value)]
  const items = equipmentRows.value.map((item) => {
    const isSelected = item.id === selectedEquipment.value
    return {
      name: item.name,
      value: item.loadKw,
      selected: isSelected,
      itemStyle: {
        color,
        opacity: item.opacity, // 고른 설비가 가장 진함 (opacityOrder)
        borderColor: isSelected ? COLOR.ink : COLOR.card,
        borderWidth: 2,
      },
    }
  })
  return donutOption(items, '구역의')
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">모니터링</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">설비 모니터링</h1>
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!--
      KPI 5개(통합 화면과 같은 카드)는 2026-11-18 UI 피드백으로 뺐음.
      이 화면은 설비가 주인공이어야 해서, 그 자리에는 설비 전체 통계를 넣을 예정 (구성은 팀 검토 중)
    -->

    <div class="row">
      <!-- 계통도 -->
      <section class="card tree-card">
        <div class="card-head tree-head">
          <h2 class="card-title">전력 계통</h2>
          <!-- 설비 게이지 보기 방식 (전력 계통 카드 제목 줄 오른쪽 끝): 계통도의 설비 상자와 오른쪽 설비 목록 막대에 함께 적용 -->
          <div class="gauge-switch">
            <span class="gauge-switch-label">설비 게이지</span>
            <div class="presets" role="group" aria-label="설비 게이지 보기 방식">
              <button
                :class="{ active: gaugeMode === 'relative' }"
                :aria-pressed="gaugeMode === 'relative'"
                title="구역 전력 중 그 설비가 차지하는 비율"
                @click="gaugeMode = 'relative'"
              >
                상대값 (구역 대비 %)
              </button>
              <button
                :class="{ active: gaugeMode === 'absolute' }"
                :aria-pressed="gaugeMode === 'absolute'"
                title="설비 전력(kW). 환경 설정의 임계치가 게이지 90% 지점, 그 뒤는 빨간 구간"
                @click="gaugeMode = 'absolute'"
              >
                절대값 (임계치 기준)
              </button>
            </div>
          </div>
          <!-- 다음 줄 오른쪽: 안내 글 + 차단기 범례 -->
          <div class="head-right">
            <span class="note">구역을 누르면 설비가 펼쳐집니다</span>
            <!-- 범례: 선 위의 작은 네모는 차단기 기호 (도면 표기, 상태 정보는 없음) -->
            <span class="legend"><span class="box" aria-hidden="true"></span>차단기</span>
          </div>
        </div>
        <div class="tree-body">
          <PowerTree
            v-model="selectedZone"
            :status="status"
            :stale="!realtimeStore.connected"
            :selected-equipment="selectedEquipment"
            :thresholds="thresholds"
            :gauge-mode="gaugeMode"
            @select-equipment="toggleEquipment"
          />
        </div>
      </section>

      <!-- 고른 구역의 상세 -->
      <section
        class="card detail"
        :style="{ '--zone': `var(--zone-${selectedZone.toLowerCase()})` }"
      >
        <div class="card-head">
          <h2 class="card-title">
            <span class="zone-dot" aria-hidden="true"></span>{{ ZONE_LABELS[selectedZone] }} 구역
          </h2>
        </div>
        <!-- 구역 전력 합계 (왼쪽) + 공장 전체에서 이 구역이 차지하는 비율 원그래프 (오른쪽) -->
        <div class="zone-summary">
          <div class="zone-text">
            <p class="zone-kw">
              <span class="zone-value num">{{ formatKw(zoneKw) }}</span>
              <span class="zone-unit">kW</span>
            </p>
            <!-- 막대 위: 왼쪽 비율, 오른쪽 임계 합 (임계치는 설비 카드와 같이 막대 위에 둠) -->
            <p class="zone-share num">
              <span>{{ zoneGauge.text }}</span>
              <span v-if="zoneGauge.fullKw !== null" class="zone-threshold">
                임계 합 {{ formatKw(zoneThreshold) }} kW
              </span>
            </p>
            <!-- 게이지: 상대값이면 공장 대비 비율, 절대값이면 설비 임계치 합이 90% 지점 -->
            <div class="track" :class="{ absolute: zoneGauge.fullKw !== null }" aria-hidden="true">
              <span class="bar zone-bar" :style="{ width: `${zoneGauge.width}%` }"></span>
            </div>
            <!-- 막대 아래: 시작점과 끝점 (두 보기 모두 같은 줄 높이) -->
            <div class="zone-scale num" aria-hidden="true">
              <template v-if="zoneGauge.fullKw !== null">
                <span>0</span>
                <span>{{ formatKw(zoneGauge.fullKw) }} kW</span>
              </template>
              <template v-else>
                <span>0%</span>
                <span>100%</span>
              </template>
            </div>
          </div>
          <!-- 원그래프 + 아래 설명 글 (설명을 그래프 상자 밖에 둬야 조각 누르기 위치 계산이 안 틀어짐) -->
          <figure class="donut-box">
            <div
              class="donut clickable"
              role="img"
              :aria-label="`공장 전체 전력 중 구역별 비율, ${ZONE_LABELS[selectedZone]} 강조. 조각을 누르면 그 구역을 고릅니다`"
              @click="onZoneDonutClick"
            >
              <!-- key: 고른 구역이 바뀌면 그래프를 새로 그림 (아래 설비 원그래프 설명 참고) -->
              <Chart
                v-if="status"
                :key="`zone-${selectedZone}`"
                :option="zoneShareOption"
                :height="124"
              />
            </div>
            <figcaption class="donut-caption">구역별 전력 비율</figcaption>
          </figure>
        </div>

        <h3 class="sub-title">최근 1시간</h3>
        <!-- 남은 높이를 채움 (화면이 낮으면 그래프가 줄어들어 스크롤이 생기지 않음) -->
        <FitChart v-if="detail" :option="trendOption" :min-height="60" />
        <p v-else-if="realtimeStore.zoneError" class="empty" role="alert">
          불러오지 못했습니다.
          <button class="retry" @click="realtimeStore.fetchZoneDetail(selectedZone)">
            다시 시도
          </button>
        </p>
        <p v-else class="empty">불러오는 중입니다.</p>

        <!-- 오늘 최대 · 평균 · 사용량: "이 구역의 설비" 위 (최근 1시간 그래프 바로 아래) -->
        <dl class="today">
          <div>
            <dt>오늘 최대</dt>
            <dd>
              <span class="num today-num">{{ formatKw(detail?.todayMaxKw) }}</span> kW
            </dd>
          </div>
          <div>
            <dt>오늘 평균</dt>
            <dd>
              <span class="num today-num">{{ formatKw(detail?.todayAvgKw) }}</span> kW
            </dd>
          </div>
          <div>
            <dt>오늘 사용량</dt>
            <dd>
              <span class="num today-num wide">{{ formatInteger(detail?.todayKwh) }}</span> kWh
            </dd>
          </div>
        </dl>
        <!-- 위쪽에 진한 구분선: 구역 요약(위)과 설비 목록(아래) 영역을 나눔 -->
        <h3 class="sub-title equipment-title">
          이 구역의 설비 <span class="sub-note">누르면 원그래프에 강조</span>
        </h3>
        <!-- 보기 방식(상대값·절대값)을 바꿔도 칸 높이가 같아서 위 그래프와 원그래프 크기·위치가 변하지 않음 -->
        <div class="equipment-area">
          <ul class="equipments">
            <li v-for="item in equipmentRows" :key="item.id">
              <button
                class="equipment-button"
                :class="{ selected: item.id === selectedEquipment, over: item.over }"
                :aria-pressed="item.id === selectedEquipment"
                @click="toggleEquipment(item.id)"
              >
                <!-- 설비 카드 (상대값·절대값 어느 쪽이든 크기와 줄 위치가 같음)
                     1줄: 설비명 ······ 현재 전력
                     2줄: 운전 상태 ······ 오른쪽 값 (절대값 = 임계치, 상대값 = 구역 안 비율)
                     3줄: 게이지 막대 (카드 안쪽 끝까지)
                     4줄: 막대 시작점 ······ 끝점 (절대값 = 0 · 게이지 끝 kW, 상대값 = 0% · 100%) -->
                <div class="equipment-text">
                  <strong class="equipment-name" :title="item.name">{{ item.name }}</strong>
                  <span class="equipment-kw"
                    ><span class="num">{{ formatKw(item.loadKw) }}</span> kW</span
                  >
                </div>
                <div class="equipment-info">
                  <span class="state" :class="item.stateInfo.className">
                    <span aria-hidden="true">{{ item.stateInfo.shape }}</span>
                    <span class="state-label">{{ item.stateInfo.label }}</span>
                  </span>
                  <span v-if="gaugeMode === 'relative'" class="info-value num">{{
                    item.gauge.text
                  }}</span>
                  <span v-else-if="item.threshold === null" class="info-value">임계 미설정</span>
                  <span v-else class="info-value threshold-value num">
                    {{ item.over ? '▲ 초과 · ' : '' }}임계 {{ formatKw(item.threshold) }} kW
                  </span>
                </div>
                <span
                  class="track thin equipment-track"
                  :class="{ absolute: item.gauge.fullKw !== null }"
                  :title="item.gauge.text"
                  aria-hidden="true"
                >
                  <span
                    class="bar zone-bar"
                    :style="{ width: `${item.gauge.width}%`, opacity: item.opacity }"
                  ></span>
                </span>
                <div class="equipment-scale num" aria-hidden="true">
                  <template v-if="gaugeMode === 'relative'">
                    <span>0%</span>
                    <span>100%</span>
                  </template>
                  <template v-else-if="item.gauge.fullKw !== null">
                    <span>0</span>
                    <span>{{ formatKw(item.gauge.fullKw) }} kW</span>
                  </template>
                </div>
              </button>
            </li>
          </ul>
          <figure class="donut-box">
            <div
              class="donut clickable"
              role="img"
              aria-label="이 구역 전력 중 설비별 비율. 조각을 누르면 그 설비를 고릅니다"
              @click="onEquipmentDonutClick"
            >
              <!-- key: 고른 설비가 바뀌면 그래프를 새로 그림.
                   공통 차트(Chart.vue)는 이전 설정에 새 설정을 덮어쓰는데, 원그래프의 "선택된 조각"은
                   다른 조각으로 바꿀 때 이전 조각의 선택이 풀리지 않아 두 조각이 함께 튀어나와 보였음 -->
              <Chart
                v-if="equipmentRows.length"
                :key="`equipment-${selectedZone}-${selectedEquipment}`"
                :option="equipmentShareOption"
                :height="124"
              />
            </div>
            <figcaption class="donut-caption">설비별 전력 비율</figcaption>
          </figure>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 스크롤 없이 한 화면에 들어가게 화면 높이에 고정 (2026-11-18) */
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 20px;
  overflow: hidden;
}
/* 전력 계통 카드 제목 줄: 제목(왼쪽) · 게이지 버튼(오른쪽 끝), 안내 글과 차단기 범례는 다음 줄 오른쪽 */
.tree-head {
  flex-wrap: wrap;
  align-items: center;
  row-gap: 4px;
}
.tree-head .head-right {
  flex-direction: row;
  flex-basis: 100%;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
.tree-head .head-right .note {
  margin-left: 0;
}
/* 설비 게이지 보기 방식 버튼 (카드 안 다른 글자와 맞춰 작게) */
.gauge-switch {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.gauge-switch-label {
  font-size: 12px;
  color: var(--mute);
}
.gauge-switch .presets button {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
}
.head-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  margin-left: auto;
}
.legend {
  display: flex;
  align-items: center;
  gap: 6px;
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
  min-height: 0;
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
  min-height: 0;
}
/* 계통도 그림은 칸 안에 비율을 지키며 들어가게 겹쳐 놓음 (그림이 칸 높이를 밀어내지 않게) */
.tree-body {
  position: relative;
  flex: 1;
  min-height: 0;
}
.tree-body > :deep(.tree) {
  position: absolute;
  inset: 0;
}

/* 오른쪽 상세
   단어 중간에서 줄이 바뀌지 않게(keep-all), 숫자와 단위는 같은 줄에 (아래 nowrap) */
.detail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  word-break: keep-all;
  border: 2px solid var(--ink);
}
/* 구역 합계 + 원그래프 한 줄 */
.zone-summary {
  display: flex;
  align-items: center;
  gap: 11.4px;
}
.zone-text {
  flex: 1;
  min-width: 0;
}
.donut {
  flex: none;
  width: 133px;
}
/* 원그래프와 그 아래 설명 글 묶음 */
.donut-box {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center; /* 설명 글이 원그래프보다 넓어도 원그래프와 설명이 같은 가운데 선에 오게 함 */
  margin: 0;
}
.donut-caption {
  font-size: 10.4px;
  color: var(--mute);
  text-align: center;
  white-space: nowrap;
}
.donut.clickable {
  cursor: pointer;
}
.zone-kw {
  display: flex;
  align-items: baseline;
  gap: 7.6px;
  white-space: nowrap;
}
/* 숫자 + 단위, 짧은 이름표는 한 덩어리로 (중간에서 줄바꿈 안 함) */
.zone-share,
.equipment-text strong,
.state,
.equipment-kw,
.equipment-share,
.today dt,
.today dd {
  white-space: nowrap;
}
.zone-value {
  font-size: 41.8px;
  font-weight: 700;
  line-height: 1.1;
}
.zone-unit {
  font-size: 15.2px;
  color: var(--ink-2);
}
/* 구역 막대 위 줄: 왼쪽 비율, 오른쪽 임계 합 */
.zone-share {
  display: flex;
  justify-content: space-between;
  gap: 7.6px;
  font-size: 12.3px;
  color: var(--mute);
}
.zone-threshold {
  font-size: 11.4px;
  font-weight: 700;
  color: var(--crit);
}
/* 구역 막대 아래 줄: 시작점과 끝점 */
.zone-scale {
  display: flex;
  justify-content: space-between;
  height: 12.3px;
  margin-top: 1.9px;
  font-size: 9.5px;
  line-height: 12.3px;
  color: var(--mute);
}
.track {
  height: 9.5px;
  margin-top: 5.7px;
  background: var(--line);
}
.track.thin {
  display: block; /* span이라 따로 줄을 차지하게 함 (절대값일 때 한 줄을 다 쓰는 게이지) */
  height: 7.6px;
  margin-top: 3.8px;
}
/* 절대값 게이지: 90% 지점(임계치)에 빨간 세로 눈금, 그 뒤(90%~100%)는 빨간 구간
   막대 위에 반투명으로 겹쳐서, 막대가 임계치를 넘으면 넘은 부분도 빨갛게 보임 */
.track.absolute {
  position: relative;
}
.track.absolute::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 90%;
  right: 0;
  background: color-mix(in srgb, var(--crit) 45%, transparent);
  border-left: 2px solid var(--crit);
}
.bar {
  display: block;
  height: 100%;
  background: var(--ink-2);
  /* 값이 바뀌면 막대가 바로 그 길이가 됨 (움직이는 효과 없음, 2026-11-18) */
}
.sub-title {
  margin: 9.5px 0 3.8px;
  padding-top: 9.5px;
  border-top: 1px solid var(--line);
  font-size: 12.3px;
  font-weight: 700;
}
/* "이 구역의 설비" 위 구분선은 다른 제목보다 진하고 두껍게 (구역 영역과 설비 영역을 나눔) */
.sub-title.equipment-title {
  border-top: 2px solid var(--line-strong);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 150px;
  color: var(--mute);
}
.sub-note {
  margin-left: 5.7px;
  font-size: 11.4px;
  font-weight: 400;
  color: var(--mute);
}
/* 설비 목록 + 원그래프 한 줄 */
/* 설비 목록 칸은 높이를 고정 (설비가 최대 3대라 3줄 높이). 어떤 구역을 골라도
   그 위의 "최근 1시간" 그래프와 원그래프 크기가 똑같이 나오게 함 (2026-11-18) */
.equipment-area {
  display: flex;
  flex: none;
  align-items: center;
  gap: 11.4px;
  height: 216.6px; /* 4줄짜리 설비 카드 3장(+간격)이 들어가는 높이. 상대값·절대값 모두 같은 높이 */
}
/* 구역 색: 제목 앞 점, 비율 막대 */
.zone-dot {
  display: inline-block;
  width: 11.4px;
  height: 11.4px;
  margin-right: 7.6px;
  border-radius: 2px;
  background: var(--zone);
}
.bar.zone-bar {
  background: var(--zone);
}
.equipment-name {
  min-width: 0;
  font-size: 12.3px;
  overflow: hidden;
  text-overflow: ellipsis; /* 이름이 아주 길면 줄을 바꾸지 않고 …로 줄임 (전체 이름은 마우스를 올리면 보임) */
}
/* 설비 카드 2줄: 왼쪽 운전 상태, 오른쪽 값 (임계치 또는 구역 안 비율) */
.equipment-info {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 7.6px;
  margin-top: 0.9px;
  font-size: 10.4px;
}
.equipment-info .state {
  font-size: 10.4px;
}
/* 상태 도형(●)과 글자(운전) 사이 띄움 */
.state-label {
  margin-left: 2.8px;
}
.info-value {
  color: var(--mute);
  white-space: nowrap;
}
.info-value.threshold-value {
  font-weight: 700;
  color: var(--crit);
}
/* 설비 카드 3줄: 막대는 카드 안쪽 끝까지 */
.track.equipment-track {
  margin-top: 3.8px;
}
/* 설비 카드 4줄: 막대 시작점과 끝점 */
.equipment-scale {
  display: flex;
  justify-content: space-between;
  height: 12.3px;
  margin-top: 0.9px;
  font-size: 9.5px;
  line-height: 12.3px;
  color: var(--mute);
}
.equipments {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3.8px; /* 4줄 카드 3장 + 간격이 칸 높이 안에 들어가게 */
  min-width: 0;
}
/* 설비 한 줄 전체가 버튼 (누르면 원그래프에서 강조) */
.equipment-button {
  display: block;
  width: 100%;
  padding: 4.8px 7.6px;
  line-height: 1.35; /* 설비 3대 카드가 칸(높이 고정) 안에 다 들어가게 줄 간격을 조금 좁힘 */
  background: color-mix(
    in srgb,
    var(--zone) calc(8% * var(--tint-k)),
    var(--card)
  ); /* 카드 바탕과 다르게 구역 색을 옅게 */
  border: 1px solid transparent;
  border-radius: 4px;
  color: inherit;
  text-align: left;
}
.equipment-button:hover {
  background: color-mix(in srgb, var(--zone) calc(14% * var(--tint-k)), var(--card));
}
.equipment-button.selected {
  background: color-mix(in srgb, var(--zone) calc(20% * var(--tint-k)), var(--card));
  border-color: var(--ink);
}
.threshold {
  flex: none;
  font-size: 10.4px;
  font-weight: 700;
  color: var(--crit);
  white-space: nowrap;
}
/* 임계치를 넘은 설비: 빨간 바탕 · 테두리 */
.equipment-button.over,
.equipment-button.over:hover {
  background: color-mix(in srgb, var(--crit) calc(10% * var(--tint-k)), var(--card));
  border-color: var(--crit);
}
.equipment-button.over .equipment-kw {
  color: var(--crit);
}
.equipment-share {
  flex: none;
  min-width: 39.9px;
  font-variant-numeric: tabular-nums;
  font-size: 11.4px;
  color: var(--mute);
  text-align: right;
}
.equipment-text {
  display: flex;
  align-items: baseline;
  gap: 7.6px;
}
/* 설비 운전 상태: 상태색 대신 먹색 계열로, 도형과 글자로 구분 */
.state {
  font-size: 11.4px;
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
  font-size: 13.3px;
  font-weight: 700;
}
/* 오늘 최대 · 평균 · 사용량: 칸 폭을 고정해서 값이 바뀌어도 위치가 움직이지 않음.
   사용량(kWh)은 자릿수가 많아 조금 더 넓게 (1 : 1 : 1.3) */
.today {
  display: grid;
  grid-template-columns: 1fr 1fr 1.3fr;
  gap: 11.4px;
  margin-top: 9.5px;
  padding-top: 9.5px;
  border-top: 1px solid var(--line);
}
/* 세 칸 각각 안에서 가운데 정렬. 숫자 칸 폭이 고정이라 값이 바뀌어도 단위 위치는 그대로 */
.today > div {
  text-align: center;
}
.today dt {
  font-size: 10.4px;
  color: var(--mute);
}
.today dd {
  font-size: 15.2px;
  font-weight: 700;
}
/* 숫자는 정해진 폭 안에서 오른쪽 정렬 → 값의 자릿수가 바뀌어도 뒤의 단위(kW · kWh) 위치가 그대로
   (kW는 "1,234"까지 5자리, kWh는 "12,345"까지 6자리 폭) */
.today-num {
  display: inline-block;
  min-width: 5ch;
  text-align: right;
}
.today-num.wide {
  min-width: 6ch;
}

/* 세로가 낮은 화면: 간격과 큰 숫자를 줄여 오른쪽 상세가 스크롤 없이 다 들어가게 함 */
@media (max-height: 760px) {
  .zone-value {
    font-size: 30.4px;
  }
  .sub-title {
    margin: 7.6px 0 3.8px;
    padding-top: 7.6px;
  }
  .equipments {
    gap: 1.9px;
  }
  /* 설비 카드 3장이 칸 안에 다 들어가게 카드 안쪽 여백과 줄 간격을 줄임 (두 보기 모두 같은 크기) */
  .equipment-button {
    padding: 2.8px 7.6px;
  }
  .equipment-scale {
    height: 10.4px;
    margin-top: 0.9px;
    line-height: 10.4px;
  }
  .equipment-area {
    height: 195.7px; /* 낮은 화면의 4줄 카드 3장 + 간격 */
  }
  .today {
    padding-top: 7.6px;
  }
}

/* 화면이 좁으면 오른쪽 상세를 줄여 계통도 그림(글자)이 너무 작아지지 않게 함 */
@media (max-width: 1600px) {
  .row {
    grid-template-columns: minmax(0, 1fr) 340px;
  }
  .zone-value {
    font-size: 36.1px;
  }
  .donut {
    width: 104.5px;
  }
  .today dd {
    font-size: 14.2px;
  }
}
</style>
