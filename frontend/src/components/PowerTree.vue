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
})

const emit = defineEmits(['update:modelValue'])

// 구역 이름. id는 설비 id의 앞부분과 같아야 함 (예: 'Z2-FURN' → 'Z2')
const ZONE_NAMES = [
  { id: 'Z1', name: '열교환기 가공' },
  { id: 'Z2', name: '브레이징' },
  { id: 'Z3', name: '사출' },
  { id: 'Z4', name: '모듈 조립' },
  { id: 'Z5', name: '검사' },
  { id: 'U', name: '유틸리티' },
]
const PCS_LABELS = { DISCHARGING: '방전 중', CHARGING: '충전 중', STANDBY: '대기' }
// 설비 운전 상태. 피크 상태(● 정상 등)와 헷갈리지 않게 상태색(초록·주황·빨강)은 쓰지 않고
// 상태마다 도형과 글자를 다르게 함
const STATE_INFO = {
  RUNNING: { shape: '●', label: '운전', className: 'running' },
  HEATING: { shape: '▲', label: '승온 중', className: 'heating' },
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

// 위쪽 공급원 3곳
const sources = computed(() => {
  const status = props.status
  // 공장 전체에서 차지하는 비율은 백엔드가 준 값(sourceRatio)을 그대로 씀
  // 값이 없으면 "공장의 —%"가 아니라 "—"만 보여 줌
  const share = (percent) =>
    percent === null || percent === undefined ? '—' : `공장의 ${formatOneDecimal(percent)}%`
  // ESS는 충전 중이면 충전 전력을, 그 밖에는 방전 전력을 보여 줌
  const charging = status?.pcsStatus === 'CHARGING'
  return [
    {
      key: 'grid',
      name: '한전 수전',
      centerX: 205,
      kw: status?.gridKw ?? null,
      sub: share(status?.sourceRatio?.gridPercent),
    },
    {
      key: 'solar',
      name: '태양광 인버터',
      centerX: 595,
      kw: status?.solarKw ?? null,
      sub: share(status?.sourceRatio?.solarPercent),
    },
    {
      key: 'ess',
      name: 'ESS (PCS)',
      centerX: 985,
      kw: status ? (charging ? status.chargeKw : status.dischargeKw) : null,
      sub: status
        ? `${PCS_LABELS[status.pcsStatus] ?? '—'}, 잔량 ${formatOneDecimal(status.socPercent)}%`
        : '—',
    },
  ]
})

// 구역 6곳: 그 구역 설비들의 전력을 더하고, 공장 전체에서 차지하는 비율을 구함 (표시용 합계·비율)
const zones = computed(() =>
  ZONE_NAMES.map((zone, index) => {
    const equipments = (props.status?.equipments ?? []).filter((item) =>
      item.id.startsWith(`${zone.id}-`),
    )
    const totalKw = equipments.length
      ? equipments.reduce((sum, item) => sum + item.loadKw, 0)
      : null
    // 값이 없으면 0이 아니라 null로 둠 (화면에 "—"로 보이게)
    const percent =
      totalKw === null || !props.status.loadKw ? null : (totalKw / props.status.loadKw) * 100
    return { ...zone, centerX: 104 + index * 196, equipments, totalKw, percent }
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
  return zone.equipments.map((item, index) => ({
    ...item,
    centerX: start + EQUIPMENT_WIDTH / 2 + index * (EQUIPMENT_WIDTH + EQUIPMENT_GAP),
    percent: zone.totalKw ? (item.loadKw / zone.totalKw) * 100 : 0,
    stateInfo: STATE_INFO[item.state] ?? { shape: '', label: '—', className: '' },
  }))
})

// 숫자 글자 수에 따라 "kW"를 놓을 x 위치를 대략 잡음
function unitX(left, text, charWidth) {
  return left + text.length * charWidth + 8
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
        y1="102"
        :x2="source.centerX"
        :y2="BUS_Y - 5"
      />
      <rect class="breaker" :x="source.centerX - 10" y="136" width="20" height="20" />

      <rect class="node" :x="source.centerX - 135" y="0" width="270" height="102" />
      <rect class="node-top" :x="source.centerX - 135" y="0" width="270" height="6" />
      <text class="name" :x="source.centerX - 119" y="32">{{ source.name }}</text>
      <text class="sub" :x="source.centerX + 119" y="32" text-anchor="end">{{ source.sub }}</text>
      <text class="value big" :x="source.centerX - 119" y="80">{{ formatKw(source.kw) }}</text>
      <text class="unit" :x="unitX(source.centerX - 119, formatKw(source.kw), 21)" y="80">kW</text>
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
      :class="{ selected: zone.id === modelValue }"
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
        :height="ZONE_HEIGHT"
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
        :width="((ZONE_WIDTH - 24) * (zone.percent ?? 0)) / 100"
        height="7"
      />
      <text class="sub" :x="zone.centerX - ZONE_WIDTH / 2 + 12" :y="ZONE_Y + 110">
        {{ zone.percent === null ? '—' : `공장의 ${formatOneDecimal(zone.percent)}%` }}
      </text>
      <text class="sub" :x="zone.centerX + ZONE_WIDTH / 2 - 12" :y="ZONE_Y + 110" text-anchor="end">
        설비 {{ zone.equipments.length }}대
      </text>
    </g>

    <!-- 고른 구역 → 설비 -->
    <g v-if="selected && children.length">
      <line
        class="wire"
        :x1="selected.centerX"
        :y1="ZONE_Y + ZONE_HEIGHT"
        :x2="selected.centerX"
        :y2="ZONE_Y + ZONE_HEIGHT + 48"
      />
      <line
        class="wire"
        :x1="Math.min(selected.centerX, children[0].centerX)"
        :y1="ZONE_Y + ZONE_HEIGHT + 48"
        :x2="Math.max(selected.centerX, children[children.length - 1].centerX)"
        :y2="ZONE_Y + ZONE_HEIGHT + 48"
      />
      <g v-for="item in children" :key="item.id">
        <line
          class="wire"
          :x1="item.centerX"
          :y1="ZONE_Y + ZONE_HEIGHT + 48"
          :x2="item.centerX"
          :y2="EQUIPMENT_Y"
        />
        <rect
          class="node"
          :x="item.centerX - EQUIPMENT_WIDTH / 2"
          :y="EQUIPMENT_Y"
          :width="EQUIPMENT_WIDTH"
          height="120"
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
          :width="((EQUIPMENT_WIDTH - 24) * item.percent) / 100"
          height="7"
        />
        <text class="sub" :x="item.centerX - EQUIPMENT_WIDTH / 2 + 12" :y="EQUIPMENT_Y + 108">
          구역의 {{ formatOneDecimal(item.percent) }}%
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
  fill: var(--card);
  stroke: var(--line-strong);
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
.zone.selected .node {
  fill: var(--bg);
  stroke: var(--ink);
  stroke-width: 3;
}
</style>
