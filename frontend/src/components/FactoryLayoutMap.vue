<!--
  공장 레이아웃 (평면도)
  컨셉: 전기차 열관리 부품 공장 (가상). 구역·설비 배치는 실제 도면이 아니라 임시로 잡은 것입니다.
  공정 흐름: Z1 열교환기 가공 → Z2 브레이징 → (Z3 사출 부품과 함께) Z4 모듈 조립 → Z5 검사 → 출하

  - 설비를 누르면 아래에 자세한 값이 나옵니다.
  - 설비 id는 백엔드가 주는 equipments[].id 와 같아야 합니다. (예: Z4-MOTOR1)
  - 좌표는 SVG 좌표(전체 1140 × 380)입니다.
-->
<script setup>
import { computed, ref } from 'vue'
import MachineIcon from '@/components/MachineIcon.vue'
import { formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
})

// 구역: 위쪽 줄 → 가운데 물류 통로 → 아래쪽 줄
const ZONES = [
  { id: 'Z1', name: 'Z1 열교환기 가공', x: 18, y: 18, width: 246, height: 158 },
  { id: 'Z2', name: 'Z2 브레이징', x: 272, y: 18, width: 276, height: 158 },
  { id: 'Z3', name: 'Z3 사출', x: 556, y: 18, width: 254, height: 158 },
  { id: 'Z5', name: 'Z5 검사', x: 104, y: 206, width: 214, height: 156 },
  { id: 'Z4', name: 'Z4 모듈 조립', x: 326, y: 206, width: 270, height: 156 },
  { id: 'U', name: '유틸리티', x: 604, y: 206, width: 206, height: 156 },
]

// 설비 위치. centerX = 그림의 가운데 x, width = 누를 수 있는 영역의 너비, icon = MachineIcon 종류
const EQUIPMENT_SPOTS = [
  { id: 'Z1-FIN', icon: 'fin', centerX: 62, width: 76 },
  { id: 'Z1-TUBE', icon: 'tube', centerX: 141, width: 76 },
  { id: 'Z1-CORE', icon: 'core', centerX: 220, width: 76 },
  { id: 'Z2-FURN', icon: 'furnace', centerX: 366, width: 176 },
  { id: 'Z2-DRY', icon: 'dryer', centerX: 501, width: 84 },
  { id: 'Z3-INJ1', icon: 'injection', centerX: 601, width: 80 },
  { id: 'Z3-INJ2', icon: 'injection', centerX: 683, width: 80 },
  { id: 'Z3-TEMP', icon: 'temp', centerX: 765, width: 80 },
  { id: 'Z5-LEAK', icon: 'leak', centerX: 160, width: 96 },
  { id: 'Z5-EOL', icon: 'eol', centerX: 262, width: 96 },
  { id: 'Z4-MOTOR1', icon: 'conveyor', centerX: 421, width: 178 },
  { id: 'Z4-ROBOT', icon: 'robot', centerX: 552, width: 78 },
  { id: 'U-COMP', icon: 'comp', centerX: 640, width: 64 },
  { id: 'U-CHIL', icon: 'chil', centerX: 707, width: 64 },
  { id: 'U-HVAC', icon: 'hvac', centerX: 774, width: 64 },
]

// 공정 흐름 화살표 (angle: 0 = 오른쪽, 90 = 아래, 180 = 왼쪽)
const FLOW_ARROWS = [
  { x: 265, y: 97, angle: 0 }, // Z1 → Z2
  { x: 510, y: 180, angle: 90 }, // Z2 → Z4
  { x: 576, y: 180, angle: 90 }, // Z3 → Z4
  { x: 325, y: 284, angle: 180 }, // Z4 → Z5
  { x: 103, y: 284, angle: 180 }, // Z5 → 출하
]

// 통로의 전력 간선(y = 197)에서 각 구역으로 들어가는 선
const WIRE_DROPS = [
  { x: 141, toY: 176 },
  { x: 410, toY: 176 },
  { x: 683, toY: 176 },
  { x: 211, toY: 206 },
  { x: 461, toY: 206 },
  { x: 707, toY: 206 },
]

// 옥상 태양광 패널 (2줄 × 5장)의 왼쪽 위 좌표
const SOLAR_PANELS = [0, 1].flatMap((row) =>
  [0, 1, 2, 3, 4].map((column) => ({ x: 848 + column * 34, y: 34 + row * 24 })),
)

// ESS 배터리 랙 4개 (x 위치)
const BATTERY_RACKS = [990, 1024, 1058, 1092]
const RACK_HEIGHT = 58

const ORIGIN_LABELS = { MEASURED: '실측', SIMULATED: '시뮬레이션', CALCULATED: '계산값' }
const PCS_LABELS = { DISCHARGING: '방전 중', CHARGING: '충전 중', STANDBY: '대기' }

const selectedId = ref('Z4-MOTOR1')

// 위치 정보에 백엔드 값(이름, kW, 출처)을 합침. 값을 못 받은 설비는 '—'로 표시
const equipments = computed(() =>
  EQUIPMENT_SPOTS.map((spot) => {
    const data = props.status?.equipments.find((item) => item.id === spot.id)
    const zone = ZONES.find((item) => item.id === spot.id.split('-')[0])
    return {
      ...spot,
      top: zone.y + 28, // 그림이 시작되는 y (구역 제목 아래)
      zoneName: zone.name,
      name: data?.name ?? spot.id,
      loadKw: data?.loadKw ?? null,
      originLabel: ORIGIN_LABELS[data?.origin] ?? '—',
    }
  }),
)

// 구역별 합계 (표시용 합계). 값을 아직 못 받았으면 null
const zones = computed(() =>
  ZONES.map((zone) => {
    const loads = equipments.value
      .filter((item) => item.id.startsWith(`${zone.id}-`))
      .map((item) => item.loadKw)
    const hasValue = loads.some((load) => load !== null)
    return { ...zone, totalKw: hasValue ? loads.reduce((sum, load) => sum + (load ?? 0), 0) : null }
  }),
)

const selected = computed(() => equipments.value.find((item) => item.id === selectedId.value))

// 공급원 비율 (한전 / 태양광 / ESS). 비율은 백엔드가 준 값을 그대로 씀
const ratioParts = computed(() => {
  const ratio = props.status?.sourceRatio
  return [
    { key: 'grid', name: '한전', percent: ratio?.gridPercent ?? null },
    { key: 'solar', name: '태양광', percent: ratio?.solarPercent ?? null },
    { key: 'ess', name: 'ESS', percent: ratio?.essPercent ?? null },
  ]
})

// 배터리 랙에 채워 그릴 높이 (잔량만큼, 표시용 계산)
const rackFillHeight = computed(() => (RACK_HEIGHT * (props.status?.socPercent ?? 0)) / 100)

const pcsLabel = computed(() => PCS_LABELS[props.status?.pcsStatus] ?? '—')
const essFlowing = computed(
  () => (props.status?.dischargeKw ?? 0) > 0 || (props.status?.chargeKw ?? 0) > 0,
)
</script>

<template>
  <div class="layout">
    <svg class="map" viewBox="0 0 1140 380" role="group" aria-label="열관리 부품 공장 레이아웃">
      <!-- 생산동 외벽 + 출하장 출입구 -->
      <rect class="building" x="8" y="8" width="812" height="364" />
      <rect class="door" x="5" y="256" width="6" height="56" />

      <!-- 구역 -->
      <rect class="zone" x="18" y="206" width="78" height="156" rx="3" />
      <text class="zone-name" x="28" y="225">출하장</text>
      <rect class="pallet" x="30" y="246" width="24" height="24" />
      <rect class="pallet" x="60" y="246" width="24" height="24" />
      <rect class="pallet" x="30" y="276" width="24" height="24" />
      <text class="hint" x="57" y="326" text-anchor="middle">완성품 적재</text>

      <g v-for="zone in zones" :key="zone.id">
        <rect
          class="zone"
          :x="zone.x"
          :y="zone.y"
          :width="zone.width"
          :height="zone.height"
          rx="3"
        />
        <text class="zone-name" :x="zone.x + 10" :y="zone.y + 19">{{ zone.name }}</text>
        <text class="zone-total" :x="zone.x + zone.width - 10" :y="zone.y + 19" text-anchor="end">
          합계 {{ formatKw(zone.totalKw) }} kW
        </text>
      </g>

      <!-- 물류 통로와 전력 간선 (전기실 → 통로 → 각 구역) -->
      <text class="hint" x="22" y="190">물류 통로</text>
      <path class="bus" d="M 836 197 H 80" />
      <line
        v-for="drop in WIRE_DROPS"
        :key="drop.x"
        class="bus"
        :x1="drop.x"
        y1="197"
        :x2="drop.x"
        :y2="drop.toY"
      />

      <!-- 공정 흐름 화살표 -->
      <polygon
        v-for="arrow in FLOW_ARROWS"
        :key="`${arrow.x}-${arrow.y}`"
        class="flow-arrow"
        points="0,-6 8,0 0,6"
        :transform="`translate(${arrow.x} ${arrow.y}) rotate(${arrow.angle})`"
      />

      <!-- 설비: 기계 그림 + 이름 + 전력 (누르면 선택) -->
      <g
        v-for="item in equipments"
        :key="item.id"
        class="equipment"
        :class="{ selected: item.id === selectedId }"
        role="button"
        tabindex="0"
        :aria-label="`${item.name} 선택`"
        @click="selectedId = item.id"
        @keydown.enter="selectedId = item.id"
      >
        <rect
          class="equipment-area"
          :x="item.centerX - item.width / 2"
          :y="item.top - 3"
          :width="item.width"
          height="108"
          rx="4"
        />
        <MachineIcon
          :type="item.icon"
          :x="item.centerX"
          :y="item.top"
          :active="item.id === selectedId"
        />
        <text class="equipment-name" :x="item.centerX" :y="item.top + 76" text-anchor="middle">
          {{ item.name }}
          <tspan v-if="item.originLabel === '실측'" class="origin">(실측)</tspan>
        </text>
        <text class="equipment-value" :x="item.centerX" :y="item.top + 96" text-anchor="middle">
          {{ formatKw(item.loadKw) }}
          <tspan class="unit" dx="2">kW</tspan>
        </text>
      </g>

      <!-- 옥상 태양광 (가상) -->
      <g class="solar">
        <rect class="zone" x="836" y="8" width="296" height="92" rx="3" />
        <text class="zone-name" x="846" y="27">옥상 태양광 (가상)</text>
        <rect
          v-for="panel in SOLAR_PANELS"
          :key="`${panel.x}-${panel.y}`"
          class="panel"
          :x="panel.x"
          :y="panel.y"
          width="30"
          height="20"
        />
        <text class="node-value" x="1122" y="64" text-anchor="end">
          {{ formatKw(status?.solarKw) }}
          <tspan class="unit" dx="2">kW</tspan>
        </text>
        <text class="hint" x="1122" y="84" text-anchor="end">발전량</text>
        <path class="wire" :class="{ flowing: (status?.solarKw ?? 0) > 0 }" d="M 880 100 V 114" />
      </g>

      <!-- 전기실: 배전반(공장 전체) + 수전설비(한전) -->
      <rect class="zone" x="836" y="114" width="296" height="110" rx="3" />
      <text class="zone-name" x="846" y="133">전기실</text>
      <MachineIcon type="cabinet" :x="868" :y="148" />
      <text class="equipment-name" x="894" y="160">배전반</text>
      <text class="node-value" x="894" y="184">
        {{ formatKw(status?.loadKw) }}
        <tspan class="unit" dx="2">kW</tspan>
      </text>
      <text class="hint" x="894" y="202">공장 전체</text>
      <g class="grid">
        <path
          class="wire"
          :class="{ flowing: (status?.gridKw ?? 0) > 0 }"
          d="M 1140 216 H 1014 V 198"
        />
        <MachineIcon type="transformer" :x="1014" :y="148" />
        <text class="equipment-name" x="1046" y="160">수전설비</text>
        <text class="node-value" x="1046" y="184">
          {{ formatKw(status?.gridKw) }}
          <tspan class="unit" dx="2">kW</tspan>
        </text>
        <text class="hint" x="1046" y="202">한전 · 계산값</text>
      </g>

      <!-- ESS실 (가상): PCS + 배터리 랙 -->
      <g class="ess">
        <path class="wire" :class="{ flowing: essFlowing }" d="M 880 238 V 224" />
        <rect class="zone" x="836" y="238" width="296" height="134" rx="3" />
        <text class="zone-name" x="846" y="257">ESS실 (가상)</text>
        <text class="hint" x="1122" y="257" text-anchor="end">
          배터리실 {{ formatOneDecimal(status?.batteryTempC) }} ℃
        </text>

        <MachineIcon type="pcs" :x="868" :y="270" />
        <text class="equipment-name" x="894" y="282">PCS · {{ pcsLabel }}</text>
        <text class="node-value" x="894" y="308">
          {{ formatKw(status?.dischargeKw) }}
          <tspan class="unit" dx="2">kW</tspan>
        </text>
        <text class="hint" x="894" y="330">SOH {{ formatOneDecimal(status?.sohPercent) }} %</text>

        <!-- 배터리 랙: 잔량(SOC)만큼 아래에서부터 채워 그림 -->
        <g v-for="x in BATTERY_RACKS" :key="x">
          <rect class="rack" :x="x" y="270" width="28" :height="RACK_HEIGHT" rx="2" />
          <rect
            class="rack-fill"
            :x="x"
            :y="270 + RACK_HEIGHT - rackFillHeight"
            width="28"
            :height="rackFillHeight"
          />
        </g>
        <text class="value-small" x="990" y="350">
          잔량 {{ formatOneDecimal(status?.socPercent) }} %
        </text>
      </g>
    </svg>

    <!-- 선택한 설비의 자세한 값: 색 + 글자 + 막대를 함께 표시 -->
    <div v-if="selected" class="detail">
      <p class="detail-title">
        <span class="detail-zone">{{ selected.zoneName }}</span>
        <strong>{{ selected.name }}</strong>
        <span class="tag">{{ selected.originLabel }}</span>
        <span class="num detail-value">{{ formatKw(selected.loadKw) }} kW</span>
      </p>
      <div class="detail-bar" aria-hidden="true">
        <span
          v-for="part in ratioParts"
          :key="part.key"
          class="detail-bar-part"
          :class="part.key"
          :style="{ width: `${part.percent ?? 0}%` }"
        ></span>
      </div>
      <ul class="detail-list">
        <li v-for="part in ratioParts" :key="part.key">
          <span class="dot" :class="part.key" aria-hidden="true"></span>
          {{ part.name }}
          <span class="num">{{ formatOneDecimal(part.percent) }}%</span>
        </li>
        <li><span class="tag">배분 계산값</span></li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
}
.map {
  flex: 1;
  min-height: 0;
  width: 100%;
}

/* 공급원 색: SVG 도형(fill, stroke)과 아래 HTML 막대(background)에 같이 씀 */
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess {
  --source: var(--src-ess);
}

/* 건물과 구역 */
.building {
  fill: var(--bg);
  stroke: var(--ink-2);
  stroke-width: 2.5;
}
.door {
  fill: var(--bg);
}
.zone {
  fill: var(--card);
  stroke: var(--line-strong);
}
.zone-name {
  font-size: 13px;
  font-weight: 700;
  fill: var(--ink-2);
}
.zone-total {
  font-size: 12px;
  fill: var(--mute);
  font-variant-numeric: tabular-nums;
}
.hint {
  font-size: 12px;
  fill: var(--mute);
}
.pallet {
  fill: var(--bg);
  stroke: var(--line-strong);
}
.flow-arrow {
  fill: var(--ink-2);
}

/* 전력선: 전기가 흐르는 선만 점선이 움직임 */
.bus {
  fill: none;
  stroke: var(--mute);
  stroke-width: 1.5;
  stroke-dasharray: 6 5;
  animation: flow 1.2s linear infinite;
}
.wire {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 3.5;
}
.wire.flowing {
  stroke: var(--source);
  stroke-dasharray: 6 4;
  animation: flow 1s linear infinite;
}
@keyframes flow {
  to {
    stroke-dashoffset: -22;
  }
}

/* 설비 */
.equipment {
  cursor: pointer;
  outline: none;
}
/* 누를 수 있는 영역. 평소에는 안 보이고, 마우스를 올리거나 선택하면 테두리가 보임 */
.equipment-area {
  fill: transparent;
  stroke: transparent;
  transition: stroke 0.15s;
}
.equipment:hover .equipment-area,
.equipment:focus-visible .equipment-area {
  stroke: var(--line-strong);
}
.equipment.selected .equipment-area {
  fill: var(--bg);
  stroke: var(--accent);
  stroke-width: 2;
}
.equipment-name {
  font-size: 13px;
  font-weight: 700;
  fill: var(--ink);
}
.origin {
  font-size: 11px;
  font-weight: 400;
  fill: var(--mute);
}
.equipment-value {
  font-size: 16px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.unit {
  font-size: 12px;
  font-weight: 400;
  fill: var(--ink-2);
}

/* 태양광 패널, 전기실·ESS실 */
.panel {
  fill: var(--src-solar);
  fill-opacity: 0.2;
  stroke: var(--src-solar);
}
.node-value {
  font-size: 20px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.value-small {
  font-size: 14px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.rack {
  fill: var(--bg);
  stroke: var(--line-strong);
}
.rack-fill {
  fill: var(--source);
}

/* 아래 자세한 값 */
.detail {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 4px;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.detail-zone {
  color: var(--mute);
}
.detail-value {
  font-weight: 700;
}
.detail-bar {
  display: flex;
  flex: 1;
  min-width: 80px;
  height: 10px;
  background: var(--line);
}
.detail-bar-part,
.dot {
  background: var(--source);
}
.detail-list {
  display: flex;
  align-items: center;
  gap: 14px;
  white-space: nowrap;
}
.detail-list li {
  display: flex;
  align-items: center;
  gap: 5px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .bus,
  .wire.flowing {
    animation: none;
  }
}
</style>
