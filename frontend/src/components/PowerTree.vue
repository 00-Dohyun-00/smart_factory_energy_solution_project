<!--
  전력 계통도
  위에서 아래로: 공급원 3곳(한전·태양광·ESS) → 주 모선(굵은 가로선) → 구역 6곳 → 고른 구역의 설비
  구역을 누르면 그 구역이 선택되고 아래에 설비가 펼쳐집니다.

  사용 예: <PowerTree v-model="selectedZone" :status="realtimeStore.status" :stale="!realtimeStore.connected" />
  좌표는 SVG 좌표(전체 1190 × 660)입니다.
-->
<script setup>
import { computed } from 'vue'
import { formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 고른 구역 id (예: 'Z2')
  modelValue: { type: String, default: '' },
  // 서버 연결이 끊겨 마지막으로 받은 값에 멈춰 있으면 true (kW 숫자를 회색으로)
  stale: { type: Boolean, default: false },
  // 오른쪽 상세에서 고른 설비 id (안 골랐으면 빈 글자). 고른 설비 상자가 가장 진한 색이 됨
  selectedEquipment: { type: String, default: '' },
  // 설비별 전력 임계치 (설비 id → kW). 이 값을 넘은 설비 상자를 빨갛게 그림
  thresholds: { type: Object, default: () => ({}) },
  // 설비 게이지 보기 방식: 'relative'(구역 대비 비율) / 'absolute'(임계치를 게이지 90% 지점에 둔 kW 기준)
  gaugeMode: { type: String, default: 'relative' },
})

// update:modelValue: 구역을 누름 / select-equipment: 설비 상자를 누름 (설비 id를 넘김. 고르기·풀기는 부모가 정함)
const emit = defineEmits(['update:modelValue', 'select-equipment'])

// 구역 이름. id는 지금 상태 응답의 설비 구역(equipments[].zoneId)과 같아야 함 (예: 브레이징로 BRZ-01 → 'Z2', 유틸리티는 'Z6')
const ZONE_NAMES = [
  { id: 'Z1', name: '열교환기 가공' },
  { id: 'Z2', name: '브레이징' },
  { id: 'Z3', name: '사출' },
  { id: 'Z4', name: '모듈 조립' },
  { id: 'Z5', name: '검사' },
  { id: 'Z6', name: '유틸리티' },
]
const PCS_LABELS = { DISCHARGING: '방전 중', CHARGING: '충전 중', STANDBY: '대기' }
// 설비 운전 상태. 피크 상태(● 정상 등)와 헷갈리지 않게 상태색(초록·주황·빨강)은 쓰지 않고
// 상태마다 도형과 글자를 다르게 함
const STATE_INFO = {
  RUNNING: { shape: '●', label: '운전', className: 'running' },
  HEATING: { shape: '▲', label: '가열 중', className: 'heating' },
  STOPPED: { shape: '○', label: '정지', className: 'stopped' },
}

const WIDTH = 1190
const BUS_Y = 196
const ZONE_Y = 286
const ZONE_WIDTH = 178
const ZONE_HEIGHT = 124
const EQUIPMENT_Y = 520
const EQUIPMENT_WIDTH = 210
const EQUIPMENT_GAP = 40
// 한 구역 안 설비들의 색 진하기 (구역 색에 곱하는 불투명도). 오른쪽 상세(PowerDiagramPage.vue)도 같은 값을 씀
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

// 위쪽 공급원 3곳
const sources = computed(() => {
  const status = props.status
  // 공장 전체에서 차지하는 비율은 백엔드가 준 값(sourceRatio)을 그대로 씀
  // 상자 아래쪽에 비율 막대와 "공장의 N%" 글자로 보여 줌 (구역 상자와 같은 모양)
  // 값이 없으면 "공장의 —%"가 아니라 "—"만 보여 줌
  const share = (percent) =>
    percent === null || percent === undefined ? '—' : `공장의 ${formatOneDecimal(percent)}%`
  const ratio = (percent) => (percent === null || percent === undefined ? 0 : percent)
  // ESS는 충전 중이면 충전 전력을, 그 밖에는 방전 전력을 보여 줌
  const charging = status?.pcsStatus === 'CHARGING'
  return [
    {
      key: 'grid',
      name: '구매 전력',
      centerX: 205,
      kw: status?.gridKw ?? null,
      sub: '',
      percent: ratio(status?.sourceRatio?.gridPercent),
      shareText: share(status?.sourceRatio?.gridPercent),
    },
    {
      key: 'solar',
      name: '태양광 발전량',
      centerX: 595,
      kw: status?.solarKw ?? null,
      sub: '',
      percent: ratio(status?.sourceRatio?.solarPercent),
      shareText: share(status?.sourceRatio?.solarPercent),
    },
    {
      key: 'ess',
      name: 'ESS',
      centerX: 985,
      kw: status ? (charging ? status.chargeKw : status.dischargeKw) : null,
      sub: status
        ? `${PCS_LABELS[status.pcsStatus] ?? '—'}, 잔량 ${formatOneDecimal(status.socPercent)}%`
        : '—',
      percent: ratio(status?.sourceRatio?.essPercent),
      shareText: share(status?.sourceRatio?.essPercent),
      soc: status?.socPercent ?? 0, // 배터리 그림의 채움 정도 (0~100)
      discharging: status?.pcsStatus === 'DISCHARGING',
    },
  ]
})

// 구역 6곳: 그 구역 설비들의 전력을 더하고, 공장 전체에서 차지하는 비율을 구함 (표시용 합계·비율)
const zones = computed(() =>
  ZONE_NAMES.map((zone, index) => {
    const equipments = (props.status?.equipments ?? []).filter((item) => item.zoneId === zone.id)
    const totalKw = equipments.length
      ? equipments.reduce((sum, item) => sum + item.loadKw, 0)
      : null
    // 값이 없으면 0이 아니라 null로 둠 (화면에 "—"로 보이게)
    const percent =
      totalKw === null || !props.status.loadKw ? null : (totalKw / props.status.loadKw) * 100
    // colorVar: 이 구역 색 (CSS 변수 이름, 예: --zone-z1)
    const colorVar = `--zone-${zone.id.toLowerCase()}`
    // 임계치를 넘은 설비 수 (구역 상자에도 빨간 테두리로 알려 줌)
    const overCount = equipments.filter(
      (item) => props.thresholds[item.id] != null && item.loadKw > props.thresholds[item.id],
    ).length
    // 구역 임계치 = 이 구역 설비들의 임계치 합 (한 대라도 임계치가 없으면 합을 내지 않음)
    const thresholdSum =
      equipments.length && equipments.every((item) => props.thresholds[item.id] != null)
        ? equipments.reduce((sum, item) => sum + props.thresholds[item.id], 0)
        : null
    return {
      ...zone,
      centerX: 104 + index * 196,
      equipments,
      totalKw,
      percent,
      colorVar,
      overCount,
      thresholdSum,
      gauge: gaugeOf(
        totalKw ?? 0,
        thresholdSum,
        percent ?? 0,
        percent === null ? '—' : `공장의 ${formatOneDecimal(percent)}%`,
        '임계의', // 구역 상자는 좁아서 "임계 합의" 대신 짧게 (게이지 위에 "임계 합"이 적혀 있음)
      ),
    }
  }),
)

const selected = computed(() => zones.value.find((zone) => zone.id === props.modelValue))

// 고른 구역의 설비 상자들. 구역 바로 아래에 가운데 맞춰 놓되 그림 밖으로 나가지 않게 함
const children = computed(() => {
  const zone = selected.value
  if (!zone || !zone.equipments.length) return []
  const count = zone.equipments.length
  const total = count * EQUIPMENT_WIDTH + (count - 1) * EQUIPMENT_GAP
  const start = Math.min(Math.max(zone.centerX - total / 2, 10), WIDTH - total - 10)
  const opacities = opacityOrder(zone.equipments, props.selectedEquipment)
  return zone.equipments.map((item, index) => {
    const percent = zone.totalKw ? (item.loadKw / zone.totalKw) * 100 : 0
    const threshold = props.thresholds[item.id] ?? null
    return {
      ...item,
      centerX: start + EQUIPMENT_WIDTH / 2 + index * (EQUIPMENT_WIDTH + EQUIPMENT_GAP),
      percent,
      gauge: gaugeOf(
        item.loadKw,
        threshold,
        percent,
        `구역의 ${formatOneDecimal(percent)}%`,
        '임계의',
      ),
      opacity: opacities[item.id],
      threshold,
      over: threshold !== null && item.loadKw > threshold,
      stateInfo: STATE_INFO[item.state] ?? { shape: '', label: '—', className: '' },
    }
  })
})

// 절대값 게이지에서 임계치를 놓는 위치 (게이지 전체의 90%). 설비 모니터링 화면과 같은 값
const THRESHOLD_POINT = 0.9

// 게이지의 막대 길이(%), 아래에 적을 글자, 게이지 끝 kW(절대값일 때 눈금에 씀)
// loadKw: 지금 전력, threshold: 임계치(구역은 설비 임계치 합), relativePercent·relativeText: 상대값일 때 막대와 글자
// absolutePrefix: 절대값일 때 글자 앞말 (예: '임계의' → "임계의 50.0%")
function gaugeOf(loadKw, threshold, relativePercent, relativeText, absolutePrefix) {
  if (props.gaugeMode === 'relative') {
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

// 절대값이면 눈금 글자 한 줄이 늘어나서 상자를 조금 키움
// 구역 상자도 상대값·절대값 모두 절대값 기준 크기(136)와 글자 위치로 고정 (보기를 바꿔도 상자가 움직이지 않게)
// 상대값일 때는 절대값의 눈금 줄(0 · 끝 값) 자리가 비어 있음
const zoneHeight = ZONE_HEIGHT + 12
// 설비 상자는 상대값일 때도 절대값과 같은 높이(134)로 둠:
// 상대값에서는 임계치 글자(오른쪽)와 "구역의 N%"(왼쪽)가 한 줄에 있으면 겹쳐서,
// 임계치는 막대 바로 아래 줄(108)에, "구역의 N%"는 그 아래 줄(126)에 놓음
const equipmentHeight = 134
// 상자 안 맨 아래 글자 줄의 y (상자 위쪽에서부터)
const zoneBottomY = 128
const equipmentBottomY = 126

// 숫자 글자 수에 따라 "kW"를 놓을 x 위치를 대략 잡음
// 값이 없을 때 쓰는 줄표(—)는 숫자보다 두 배쯤 넓어서 한 글자로 세면 뒤의 "kW"와 겹침 (서버 연결이 끊겼을 때 등)
// → 줄표는 숫자 두 글자 폭으로 셈
function unitX(left, text, charWidth) {
  const width = [...text].reduce((sum, char) => sum + (char === '—' ? 2 : 1), 0)
  return left + width * charWidth + 8
}
</script>

<template>
  <svg class="tree" :class="{ stale }" viewBox="0 0 1190 660" role="group" aria-label="전력 계통도">
    <!-- 공급원 → 주 모선 -->
    <g v-for="source in sources" :key="source.key" :class="source.key">
      <line
        class="feed"
        :class="{ flowing: (source.kw ?? 0) > 0 }"
        :x1="source.centerX"
        y1="124"
        :x2="source.centerX"
        :y2="BUS_Y - 5"
      />
      <rect class="breaker" :x="source.centerX - 10" y="148" width="20" height="20" />

      <!-- 상자 높이 124: 아래쪽에 공장 대비 비율 막대와 글자를 넣으려고 102에서 늘림 -->
      <rect class="node" :x="source.centerX - 135" y="0" width="270" height="124" />
      <rect class="node-top" :x="source.centerX - 135" y="0" width="270" height="6" />
      <text class="name" :x="source.centerX - 119" y="32">{{ source.name }}</text>
      <text
        class="sub"
        :class="{ discharging: source.discharging }"
        :x="source.centerX + 119"
        y="32"
        text-anchor="end"
      >
        {{ source.sub }}
      </text>
      <text class="value big" :x="source.centerX - 119" y="80">{{ formatKw(source.kw) }}</text>
      <text class="unit" :x="unitX(source.centerX - 119, formatKw(source.kw), 21)" y="80">kW</text>
      <!-- 공장 전체에서 이 공급원이 차지하는 비율: 막대 + "공장의 N%" -->
      <!-- 막대는 오른쪽 아이콘(전봇대 등) 앞에서 끝나게 폭 180 -->
      <rect class="share-track" :x="source.centerX - 119" y="92" width="180" height="7" />
      <rect
        class="share-bar source-bar"
        :x="source.centerX - 119"
        y="92"
        :width="(180 * Math.min(100, Math.max(0, source.percent))) / 100"
        height="7"
      />
      <text class="sub" :x="source.centerX - 119" y="114">{{ source.shareText }}</text>

      <!-- 공급원 아이콘 (오른쪽 아래, 44 × 44 칸에 직접 그림): 한전 = 전봇대, 태양광 = 패널, ESS = 배터리 -->
      <g class="icon" :transform="`translate(${source.centerX + 72}, 48)`" aria-hidden="true">
        <template v-if="source.key === 'grid'">
          <line x1="22" y1="2" x2="22" y2="44" />
          <line x1="6" y1="10" x2="38" y2="10" />
          <line x1="11" y1="19" x2="33" y2="19" />
          <circle class="fill" cx="8" cy="7" r="2.2" />
          <circle class="fill" cx="22" cy="7" r="2.2" />
          <circle class="fill" cx="36" cy="7" r="2.2" />
          <line x1="14" y1="44" x2="30" y2="44" />
        </template>
        <template v-else-if="source.key === 'solar'">
          <polygon points="7,6 41,6 37,30 3,30" />
          <line x1="18.3" y1="6" x2="14.3" y2="30" />
          <line x1="29.7" y1="6" x2="25.7" y2="30" />
          <line x1="5" y1="18" x2="39" y2="18" />
          <line x1="22" y1="30" x2="22" y2="42" />
          <line x1="14" y1="42" x2="30" y2="42" />
        </template>
        <!-- ESS: 세로 배터리. 안쪽 막대가 잔량(SOC)만큼 아래에서부터 차오름. 방전 중이면 왼쪽에 번개 표시 -->
        <template v-else>
          <rect x="18" y="6" width="20" height="38" rx="3" />
          <rect class="fill" x="24" y="2" width="8" height="4" />
          <rect
            class="fill"
            x="21.5"
            :y="9.5 + 31 * (1 - Math.min(100, Math.max(0, source.soc)) / 100)"
            width="13"
            :height="31 * (Math.min(100, Math.max(0, source.soc)) / 100)"
          />
          <g v-if="source.discharging" class="bolt">
            <title>방전 중</title>
            <path d="M9 10 L2 26 H8 L5 40 L15 22 H9 L12 10 Z" />
          </g>
        </template>
      </g>
    </g>

    <!-- 주 모선 -->
    <rect class="bus" x="10" :y="BUS_Y - 5" :width="WIDTH - 20" height="10" />
    <text class="name small" x="10" :y="BUS_Y - 14">주 모선</text>
    <text class="name load-total" :x="WIDTH - 10" :y="BUS_Y - 14" text-anchor="end">
      공장 소비 {{ formatKw(status?.loadKw) }} kW
    </text>

    <!-- 주 모선 → 구역 (누르면 선택) -->
    <g
      v-for="zone in zones"
      :key="zone.id"
      class="zone"
      :class="{ selected: zone.id === modelValue, over: zone.overCount > 0 }"
      :style="{ '--zone': `var(${zone.colorVar})` }"
      role="button"
      tabindex="0"
      :aria-pressed="zone.id === modelValue"
      :aria-label="`${zone.name} 구역의 설비 보기`"
      @click="emit('update:modelValue', zone.id)"
      @keydown.enter="emit('update:modelValue', zone.id)"
      @keydown.space.prevent="emit('update:modelValue', zone.id)"
    >
      <line class="wire" :x1="zone.centerX" :y1="BUS_Y + 5" :x2="zone.centerX" :y2="ZONE_Y" />
      <rect class="breaker" :x="zone.centerX - 10" :y="BUS_Y + 34" width="20" height="20" />

      <rect
        class="node"
        :x="zone.centerX - ZONE_WIDTH / 2"
        :y="ZONE_Y"
        :width="ZONE_WIDTH"
        :height="zoneHeight"
      />
      <!-- 맨 위 구역 색 띠 -->
      <rect
        class="node-top"
        :x="zone.centerX - ZONE_WIDTH / 2"
        :y="ZONE_Y"
        :width="ZONE_WIDTH"
        height="4"
      />
      <text class="name" :x="zone.centerX - ZONE_WIDTH / 2 + 12" :y="ZONE_Y + 26">
        {{ zone.name }}
      </text>
      <text class="value" :x="zone.centerX - ZONE_WIDTH / 2 + 12" :y="ZONE_Y + 66">
        {{ formatKw(zone.totalKw) }}
      </text>
      <text
        class="unit"
        :x="unitX(zone.centerX - ZONE_WIDTH / 2 + 12, formatKw(zone.totalKw), 17.5)"
        :y="ZONE_Y + 66"
      >
        kW
      </text>
      <!-- 공장 전체에서 이 구역이 차지하는 비율 -->
      <rect
        class="share-track"
        :x="zone.centerX - ZONE_WIDTH / 2 + 12"
        :y="ZONE_Y + 84"
        :width="ZONE_WIDTH - 24"
        height="7"
      />
      <rect
        class="share-bar"
        :x="zone.centerX - ZONE_WIDTH / 2 + 12"
        :y="ZONE_Y + 84"
        :width="((ZONE_WIDTH - 24) * zone.gauge.width) / 100"
        height="7"
      />
      <!-- 절대값 게이지: 90% 지점 = 설비 임계치 합. 그 뒤는 빨간 구간, 위에 임계 합, 아래에 0과 끝 값 -->
      <g v-if="zone.gauge.fullKw !== null">
        <rect
          class="danger-zone"
          :x="zone.centerX - ZONE_WIDTH / 2 + 12 + (ZONE_WIDTH - 24) * THRESHOLD_POINT"
          :y="ZONE_Y + 84"
          :width="(ZONE_WIDTH - 24) * (1 - THRESHOLD_POINT)"
          height="7"
        />
        <line
          class="danger-tick"
          :x1="zone.centerX - ZONE_WIDTH / 2 + 12 + (ZONE_WIDTH - 24) * THRESHOLD_POINT"
          :y1="ZONE_Y + 80"
          :x2="zone.centerX - ZONE_WIDTH / 2 + 12 + (ZONE_WIDTH - 24) * THRESHOLD_POINT"
          :y2="ZONE_Y + 95"
        />
        <text
          class="scale threshold-scale"
          :x="zone.centerX - ZONE_WIDTH / 2 + 12 + (ZONE_WIDTH - 24) * THRESHOLD_POINT - 3"
          :y="ZONE_Y + 79"
          text-anchor="end"
        >
          임계 합 {{ formatKw(zone.thresholdSum) }}
        </text>
        <text class="scale" :x="zone.centerX - ZONE_WIDTH / 2 + 12" :y="ZONE_Y + 107">0</text>
        <text
          class="scale"
          :x="zone.centerX + ZONE_WIDTH / 2 - 12"
          :y="ZONE_Y + 107"
          text-anchor="end"
        >
          {{ formatKw(zone.gauge.fullKw) }}
        </text>
      </g>
      <text class="sub" :x="zone.centerX - ZONE_WIDTH / 2 + 12" :y="ZONE_Y + zoneBottomY">
        {{ zone.gauge.text }}
      </text>
      <text
        class="sub"
        :x="zone.centerX + ZONE_WIDTH / 2 - 12"
        :y="ZONE_Y + zoneBottomY"
        text-anchor="end"
      >
        설비 {{ zone.equipments.length }}대
      </text>
    </g>

    <!-- 고른 구역 → 설비 -->
    <g v-if="selected && children.length" :style="{ '--zone': `var(${selected.colorVar})` }">
      <line
        class="wire"
        :x1="selected.centerX"
        :y1="ZONE_Y + zoneHeight"
        :x2="selected.centerX"
        :y2="ZONE_Y + zoneHeight + 48"
      />
      <line
        class="wire"
        :x1="Math.min(selected.centerX, children[0].centerX)"
        :y1="ZONE_Y + zoneHeight + 48"
        :x2="Math.max(selected.centerX, children[children.length - 1].centerX)"
        :y2="ZONE_Y + zoneHeight + 48"
      />
      <!-- 설비 상자 (누르면 선택, 다시 누르면 풀림. 오른쪽 "설비"와 같은 선택을 씀) -->
      <g
        v-for="item in children"
        :key="item.id"
        class="equipment"
        :class="{ over: item.over, selected: item.id === selectedEquipment }"
        :style="{ '--equipment-opacity': item.opacity }"
        role="button"
        tabindex="0"
        :aria-pressed="item.id === selectedEquipment"
        :aria-label="`${item.name} 설비 고르기`"
        @click="emit('select-equipment', item.id)"
        @keydown.enter="emit('select-equipment', item.id)"
        @keydown.space.prevent="emit('select-equipment', item.id)"
      >
        <line
          class="wire"
          :x1="item.centerX"
          :y1="ZONE_Y + zoneHeight + 48"
          :x2="item.centerX"
          :y2="EQUIPMENT_Y"
        />
        <rect
          class="node"
          :x="item.centerX - EQUIPMENT_WIDTH / 2"
          :y="EQUIPMENT_Y"
          :width="EQUIPMENT_WIDTH"
          :height="equipmentHeight"
        />
        <!-- 맨 위 설비 색 띠 (구역 색을 설비마다 다른 진하기로) -->
        <rect
          class="node-top"
          :x="item.centerX - EQUIPMENT_WIDTH / 2"
          :y="EQUIPMENT_Y"
          :width="EQUIPMENT_WIDTH"
          height="4"
        />
        <text class="name" :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12" :y="EQUIPMENT_Y + 26">
          {{ item.name }}
        </text>
        <text
          class="sub state"
          :class="item.stateInfo.className"
          :x="item.centerX + EQUIPMENT_WIDTH / 2 - 12"
          :y="EQUIPMENT_Y + 26"
          text-anchor="end"
        >
          {{ item.stateInfo.shape }} {{ item.stateInfo.label }}
        </text>
        <text class="value" :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12" :y="EQUIPMENT_Y + 66">
          {{ formatKw(item.loadKw) }}
        </text>
        <text
          class="unit"
          :x="unitX(item.centerX - EQUIPMENT_WIDTH / 2 + 12, formatKw(item.loadKw), 17.5)"
          :y="EQUIPMENT_Y + 66"
        >
          kW
        </text>
        <rect
          class="share-track"
          :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12"
          :y="EQUIPMENT_Y + 84"
          :width="EQUIPMENT_WIDTH - 24"
          height="7"
        />
        <rect
          class="share-bar"
          :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12"
          :y="EQUIPMENT_Y + 84"
          :width="((EQUIPMENT_WIDTH - 24) * item.gauge.width) / 100"
          height="7"
        />
        <!-- 절대값 게이지: 90% 지점(임계치) 뒤를 빨간 구간으로 겹쳐 칠함 (막대가 넘으면 넘은 부분도 빨갛게 보임) -->
        <!-- 위에 임계치, 아래에 0과 게이지 끝 값 -->
        <g v-if="item.gauge.fullKw !== null">
          <rect
            class="danger-zone"
            :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12 + (EQUIPMENT_WIDTH - 24) * THRESHOLD_POINT"
            :y="EQUIPMENT_Y + 84"
            :width="(EQUIPMENT_WIDTH - 24) * (1 - THRESHOLD_POINT)"
            height="7"
          />
          <line
            class="danger-tick"
            :x1="item.centerX - EQUIPMENT_WIDTH / 2 + 12 + (EQUIPMENT_WIDTH - 24) * THRESHOLD_POINT"
            :y1="EQUIPMENT_Y + 80"
            :x2="item.centerX - EQUIPMENT_WIDTH / 2 + 12 + (EQUIPMENT_WIDTH - 24) * THRESHOLD_POINT"
            :y2="EQUIPMENT_Y + 95"
          />
          <text
            class="scale threshold-scale"
            :x="
              item.centerX - EQUIPMENT_WIDTH / 2 + 12 + (EQUIPMENT_WIDTH - 24) * THRESHOLD_POINT - 3
            "
            :y="EQUIPMENT_Y + 79"
            text-anchor="end"
          >
            임계 {{ formatKw(item.threshold) }}
          </text>
          <text class="scale" :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12" :y="EQUIPMENT_Y + 107">
            0
          </text>
          <text
            class="scale"
            :x="item.centerX + EQUIPMENT_WIDTH / 2 - 12"
            :y="EQUIPMENT_Y + 107"
            text-anchor="end"
          >
            {{ formatKw(item.gauge.fullKw) }}
          </text>
        </g>
        <text
          class="sub"
          :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12"
          :y="EQUIPMENT_Y + equipmentBottomY"
        >
          {{ item.gauge.text }}
        </text>
        <!-- 임계치는 두 보기 모두 막대 위에 적음 (절대값은 90% 눈금 위, 상대값은 눈금이 없어 막대 오른쪽 끝 위) -->
        <text
          v-if="gaugeMode === 'relative' && item.threshold !== null"
          class="scale threshold-scale"
          :x="item.centerX + EQUIPMENT_WIDTH / 2 - 12"
          :y="EQUIPMENT_Y + 79"
          text-anchor="end"
        >
          임계 {{ formatKw(item.threshold) }}
        </text>
        <!-- 임계치를 넘으면 맨 아래 줄 오른쪽에 "▲ 초과" (두 보기 같은 자리) -->
        <text
          v-if="item.over"
          class="sub threshold"
          :x="item.centerX + EQUIPMENT_WIDTH / 2 - 12"
          :y="EQUIPMENT_Y + equipmentBottomY"
          text-anchor="end"
        >
          ▲ 초과
        </text>
      </g>
    </g>
  </svg>
</template>

<style scoped>
.tree {
  display: block;
  width: 100%;
  height: 100%;
}

/* 공급원 색 */
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess {
  --source: var(--src-ess);
}

.bus {
  fill: var(--ink);
}
/* 공급원에서 내려오는 선: 전기가 흐를 때만 색이 들어옴 */
.feed {
  stroke: var(--line-strong);
  stroke-width: 5;
}
.feed.flowing {
  stroke: var(--source);
}
.wire {
  stroke: var(--ink);
  stroke-width: 3;
}
/* 차단기 기호 */
.breaker {
  fill: var(--card);
  stroke: var(--ink);
  stroke-width: 2;
}

.node {
  fill: var(--node-fill); /* 카드 바탕과 살짝 다른 색 (2026-11-18) */
  stroke: var(--line-strong);
  stroke-width: 1.5;
}
/* 공급원 상자는 그 공급원 색을 아주 옅게 섞음 */
.grid .node,
.solar .node,
.ess .node {
  fill: color-mix(in srgb, var(--source) calc(7% * var(--tint-k)), var(--card));
}
/* 공급원 아이콘 */
.icon {
  fill: none;
  stroke: var(--source);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.icon .fill {
  fill: var(--source);
  stroke: none;
}
/* 방전 중 표시: 번개 + 위쪽 글자를 주황으로 */
.bolt path {
  fill: var(--warn);
  stroke: none;
}
.sub.discharging {
  font-weight: 700;
  fill: var(--warn);
}
/* 구역 상자: 구역 색을 아주 옅게 섞고, 맨 위 띠와 비율 막대는 구역 색 */
.zone .node {
  fill: color-mix(in srgb, var(--zone) calc(9% * var(--tint-k)), var(--card));
}
.zone .node-top,
.zone .share-bar {
  fill: var(--zone);
}
/* 임계치: 빨간 글자. 넘으면 설비 상자를 빨갛게 (구역 상자는 빨간 테두리) */
.sub.threshold {
  fill: var(--crit);
  font-weight: 700;
}
.equipment.over .node {
  fill: color-mix(in srgb, var(--crit) calc(10% * var(--tint-k)), var(--card));
  stroke: var(--crit);
  stroke-width: 3;
}
.equipment.over .node-top,
.equipment.over .share-bar {
  fill: var(--crit);
  fill-opacity: 1;
}
.equipment.over .value {
  fill: var(--crit);
}
.zone.over .node {
  stroke: var(--crit);
  stroke-width: 2.5;
}
/* 설비 상자: 누를 수 있음 (구역 상자와 같은 마우스 올림·고름 표시) */
.equipment {
  cursor: pointer;
  outline: none;
}
.equipment:hover .node {
  stroke: var(--ink-2);
}
.equipment:focus-visible .node {
  stroke: var(--ink);
  stroke-width: 2;
}
.equipment.selected .node {
  fill: color-mix(in srgb, var(--zone) calc(16% * var(--tint-k)), var(--card));
  stroke: var(--ink);
  stroke-width: 3;
}
/* 설비 상자: 고른 구역 색, 설비마다 진하기만 다름 */
.equipment .node {
  fill: color-mix(in srgb, var(--zone) calc(7% * var(--tint-k)), var(--card));
}
.equipment .node-top,
.equipment .share-bar {
  fill: var(--zone);
  fill-opacity: var(--equipment-opacity);
}
.node-top {
  fill: var(--source);
}
.name {
  font-size: 16px;
  font-weight: 700;
  fill: var(--ink);
}
.name.small {
  font-size: 14px;
}
.sub {
  /* 그림이 화면 폭에 맞춰 줄어들기 때문에 작은 글자도 14px로 잡음 */
  font-size: 14px;
  fill: var(--mute);
  font-variant-numeric: tabular-nums;
}
/* 설비 운전 상태: 상태색 대신 먹색 계열로, 도형과 글자로 구분 */
.sub.state.running {
  fill: var(--ink-2);
}
.sub.state.heating {
  font-weight: 700;
  fill: var(--ink);
}
.sub.state.stopped {
  fill: var(--mute);
}
.value {
  font-size: 30px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.value.big {
  font-size: 36px;
}
/* 연결이 끊겨 멈춘 값은 회색으로 */
.tree.stale .value,
.tree.stale .load-total {
  fill: var(--lost);
}
.unit {
  font-size: 15px;
  fill: var(--ink-2);
}
.share-track {
  fill: var(--line);
}
/* 공급원 상자의 비율 막대는 그 공급원 색 */
.share-bar.source-bar {
  fill: var(--source);
}
/* 절대값 게이지의 빨간 구간(임계치 뒤)과 임계치 눈금 */
.danger-zone {
  fill: var(--crit);
  fill-opacity: 0.45;
}
.danger-tick {
  stroke: var(--crit);
  stroke-width: 2;
}
/* 절대값 게이지 눈금 글자 (0, 임계치, 게이지 끝 값) */
.scale {
  font-size: 12px;
  fill: var(--mute);
  font-variant-numeric: tabular-nums;
}
.scale.threshold-scale {
  font-weight: 700;
  fill: var(--crit);
}
.share-bar {
  fill: var(--ink-2);
}

/* 구역: 누를 수 있음 */
.zone {
  cursor: pointer;
  outline: none;
}
.zone:hover .node {
  stroke: var(--ink-2);
}
.zone:focus-visible .node {
  stroke: var(--ink);
  stroke-width: 2;
}
/* 고른 구역 상자는 색을 더 진하게 깔므로, 작은 글자도 한 단계 진한 글자색으로 (다크 모드 대비) */
.zone.selected .sub,
.zone.selected .unit {
  fill: var(--ink-2);
}
.zone.selected .node {
  fill: color-mix(in srgb, var(--zone) calc(16% * var(--tint-k)), var(--card));
  stroke: var(--ink);
  stroke-width: 3;
}
</style>
