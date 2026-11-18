<!--
  공정 배치도 (설비 모니터링)
  왼쪽 원자재 → 생산동(가공 · 브레이징 · 사출 · 모듈 조립 · 검사) → 오른쪽 완제품으로 이어지는 공정 흐름을 가로로 그립니다.
  생산동 밖에는 공용 설비(유틸리티: 공기압축기 · 냉각 칠러 · 공조·조명)를 아이콘으로 두고 점선으로 잇습니다.
  구역 칸 위쪽에 구역 이름과 지금 전력(kW), 칸 안에 설비를 종류별 모양으로 그리고 운전 상태를 색으로 보여 줍니다.
  구역 칸이나 공용 설비를 누르면 그 구역이 선택됩니다.

  사용 예: <ProcessLayout v-model="selectedZone" :status="realtimeStore.status" />
  좌표는 SVG 좌표(전체 1200 × 532, 위아래를 예전 760의 70%로 줄임)이고, 그림은 칸 크기에 맞춰 비율을 지키며 커지거나 작아집니다.
  공장은 가상이고 배치는 공정 흐름에 맞춰 임시로 정한 것입니다.
-->
<script setup>
import { computed } from 'vue'
import { formatKw } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 고른 구역 id (예: 'Z2')
  modelValue: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

// 생산동 (가로 860, 칸 5개)
const HALL = { x: 170, y: 116, w: 860, h: 300 }
const COL_W = HALL.w / 5
// 생산 구역 칸 (왼쪽부터 공정 순서). kind: 설비를 그리는 모양
const ZONES = [
  { id: 'Z1', name: '가공', kind: 'cabinet' },
  { id: 'Z2', name: '브레이징', kind: 'furnace' },
  { id: 'Z3', name: '사출', kind: 'press' },
  { id: 'Z4', name: '모듈 조립', kind: 'box' },
  { id: 'Z5', name: '검사', kind: 'tester' },
]
// 공용 설비(유틸리티) 위치: 생산동 위쪽 2곳, 아래쪽 1곳
const UTILITIES = [
  { id: 'U-COMP', name: '공기압축기', cx: HALL.x + COL_W * 0.5, cy: 50, side: 'top' },
  { id: 'U-CHIL', name: '냉각 칠러', cx: HALL.x + COL_W * 2.5, cy: 50, side: 'top' },
  { id: 'U-HVAC', name: '공조·조명', cx: HALL.x + COL_W * 1.5, cy: 490, side: 'bottom' },
]
const STATE_LABELS = { RUNNING: '운전', HEATING: '승온 중', STOPPED: '정지' }

const equipments = computed(() => props.status?.equipments ?? [])
const findEquipment = (id) => equipments.value.find((item) => item.id === id)

// 설비 운전 상태 → 색 이름 (운전 = 초록, 승온 중 = 주황, 정지 = 회색)
function stateClass(state) {
  if (state === 'HEATING') return 'heat'
  if (state === 'STOPPED') return 'stop'
  return 'run'
}

// 구역마다 지금 전력 합계와, 칸 안에 그릴 설비의 위치·크기를 계산 (표시용 합계)
const zones = computed(() =>
  ZONES.map((zone, index) => {
    const x = HALL.x + COL_W * index
    const cx = x + COL_W / 2
    const list = equipments.value.filter((item) => item.id.startsWith(`${zone.id}-`))
    const totalKw = list.length ? list.reduce((sum, item) => sum + item.loadKw, 0) : null
    const machines = list.map((item, i) => {
      const n = list.length
      let box
      if (zone.kind === 'cabinet') {
        // 가공: 작은 장비 여러 대를 가로로
        const w = 40
        const gap = 12
        const start = cx - (n * w + (n - 1) * gap) / 2
        box = { x: start + i * (w + gap), y: 222, w, h: 60 }
      } else if (zone.kind === 'furnace') {
        // 브레이징: 긴 로를 위아래로
        box = { x: cx - 70, y: 214 + i * 70, w: 140, h: 42 }
      } else if (zone.kind === 'press') {
        // 사출: 긴 사출기를 위아래로 (왼쪽 동그라미는 호퍼)
        box = { x: cx - 52, y: 214 + i * 54, w: 118, h: 22 }
      } else if (zone.kind === 'box') {
        // 모듈 조립: 작은 장비를 가로로, 아래에 컨베이어
        const w = 40
        const gap = 30
        const start = cx - (n * w + (n - 1) * gap) / 2
        box = { x: start + i * (w + gap), y: 220, w, h: 40 }
      } else {
        // 검사: 시험기를 가로로
        const w = 58
        const gap = 18
        const start = cx - (n * w + (n - 1) * gap) / 2
        box = { x: start + i * (w + gap), y: 218, w, h: 70 }
      }
      return {
        ...item,
        ...box,
        stateClass: stateClass(item.state),
        stateLabel: STATE_LABELS[item.state] ?? '—',
      }
    })
    return { ...zone, x, cx, totalKw, machines }
  }),
)

// 공용 설비: 그 설비의 지금 전력과 상태
const utilities = computed(() =>
  UTILITIES.map((item) => {
    const equipment = findEquipment(item.id)
    return {
      ...item,
      loadKw: equipment?.loadKw ?? null,
      stateClass: stateClass(equipment?.state),
      stateLabel: STATE_LABELS[equipment?.state] ?? '—',
    }
  }),
)

// 선반(원자재·완제품): 기둥 4개
const RACKS = [
  { label: '원자재', x: 44 },
  { label: '완제품', x: 1064 },
]

function select(id) {
  emit('update:modelValue', id)
}
</script>

<template>
  <svg class="layout" viewBox="0 0 1200 532" role="group" aria-label="공정 배치도">
    <defs>
      <marker
        id="flow-arrow"
        viewBox="0 0 10 10"
        refX="5"
        refY="5"
        markerWidth="7"
        markerHeight="7"
        orient="auto"
      >
        <path class="flow-head" d="M 0 0 L 10 5 L 0 10 z" />
      </marker>
    </defs>

    <!-- 선반: 원자재(왼쪽) · 완제품(오른쪽) -->
    <g v-for="rack in RACKS" :key="rack.label">
      <rect class="rack" :x="rack.x" y="134" width="34" height="112" />
      <rect class="rack" :x="rack.x + 52" y="134" width="34" height="112" />
      <rect class="rack" :x="rack.x" y="286" width="34" height="112" />
      <rect class="rack" :x="rack.x + 52" y="286" width="34" height="112" />
      <text class="rack-label" :x="rack.x + 43" y="271">{{ rack.label }}</text>
    </g>

    <!-- 공정 흐름: 원자재에서 생산동 아래를 지나 완제품으로 -->
    <path class="flow" d="M 78 400 V 442 H 1124 V 408" marker-end="url(#flow-arrow)" />
    <line class="pipe" :x1="HALL.x" y1="428" :x2="HALL.x + HALL.w" y2="428" />
    <line class="pipe" :x1="HALL.x" y1="456" :x2="HALL.x + HALL.w" y2="456" />

    <!-- 공용 설비 → 생산동 연결선 -->
    <line
      v-for="item in utilities"
      :key="`link-${item.id}`"
      class="link"
      :x1="item.cx"
      :y1="item.side === 'top' ? item.cy + 28 : item.cy - 28"
      :x2="item.cx"
      :y2="item.side === 'top' ? HALL.y : HALL.y + HALL.h"
    />

    <!-- 생산동 테두리 -->
    <rect class="hall" :x="HALL.x" :y="HALL.y" :width="HALL.w" :height="HALL.h" rx="14" />

    <!-- 생산 구역 칸 (누르면 선택) -->
    <g
      v-for="(zone, index) in zones"
      :key="zone.id"
      class="zone"
      :class="{ selected: zone.id === modelValue }"
      role="button"
      tabindex="0"
      :aria-pressed="zone.id === modelValue"
      :aria-label="`${zone.name} 구역 상세 보기`"
      @click="select(zone.id)"
      @keydown.enter="select(zone.id)"
      @keydown.space.prevent="select(zone.id)"
    >
      <rect
        class="zone-area"
        :x="zone.x + 4"
        :y="HALL.y + 4"
        :width="COL_W - 8"
        :height="HALL.h - 8"
        rx="10"
      />
      <line
        v-if="index > 0"
        class="divider"
        :x1="zone.x"
        :y1="HALL.y + 14"
        :x2="zone.x"
        :y2="HALL.y + HALL.h - 14"
      />
      <text class="zone-name" :x="zone.cx" :y="HALL.y + 38">{{ zone.name }}</text>
      <text class="zone-kw" :x="zone.cx" :y="HALL.y + 66">
        {{ formatKw(zone.totalKw) }}
        <tspan class="zone-unit" dx="4">kW</tspan>
      </text>

      <!-- 설비 (종류별 모양, 색 = 운전 상태) -->
      <g
        v-for="machine in zone.machines"
        :key="machine.id"
        class="machine"
        :class="machine.stateClass"
      >
        <title>
          {{ machine.name }} · {{ machine.stateLabel }} · {{ formatKw(machine.loadKw) }} kW
        </title>
        <!-- 사출기의 호퍼(왼쪽 동그라미) -->
        <circle
          v-if="zone.kind === 'press'"
          class="body"
          :cx="machine.x - 13"
          :cy="machine.y + machine.h / 2"
          r="11"
        />
        <rect
          class="body"
          :x="machine.x"
          :y="machine.y"
          :width="machine.w"
          :height="machine.h"
          rx="5"
        />
        <!-- 가공 장비: 위 동그라미 + 아래 작은 칸 -->
        <template v-if="zone.kind === 'cabinet'">
          <circle class="detail" :cx="machine.x + machine.w / 2" :cy="machine.y + 20" r="9" />
          <rect
            class="detail"
            :x="machine.x + 7"
            :y="machine.y + 42"
            :width="machine.w - 14"
            height="11"
          />
        </template>
        <!-- 브레이징로: 세로 줄무늬 -->
        <template v-if="zone.kind === 'furnace'">
          <line
            v-for="k in 9"
            :key="k"
            class="detail"
            :x1="machine.x + k * 14"
            :y1="machine.y + 6"
            :x2="machine.x + k * 14"
            :y2="machine.y + machine.h - 6"
          />
        </template>
        <!-- 검사 시험기: 가운데 동그라미 -->
        <circle
          v-if="zone.kind === 'tester'"
          class="detail"
          :cx="machine.x + machine.w / 2"
          :cy="machine.y + machine.h / 2"
          r="15"
        />
      </g>
      <!-- 모듈 조립: 장비 아래 컨베이어 -->
      <g v-if="zone.kind === 'box'" class="conveyor">
        <rect :x="zone.cx - 70" y="274" width="140" height="20" />
        <line
          v-for="k in 13"
          :key="k"
          :x1="zone.cx - 70 + k * 10"
          y1="274"
          :x2="zone.cx - 70 + k * 10"
          y2="294"
        />
      </g>
    </g>

    <!-- 공용 설비 (누르면 유틸리티 구역 선택) -->
    <g
      v-for="item in utilities"
      :key="item.id"
      class="utility"
      :class="[item.stateClass, { selected: modelValue === 'U' }]"
      role="button"
      tabindex="0"
      :aria-pressed="modelValue === 'U'"
      :aria-label="`${item.name} (유틸리티 구역) 상세 보기`"
      @click="select('U')"
      @keydown.enter="select('U')"
      @keydown.space.prevent="select('U')"
    >
      <title>{{ item.name }} · {{ item.stateLabel }} · {{ formatKw(item.loadKw) }} kW</title>
      <circle class="body" :cx="item.cx" :cy="item.cy" r="26" />
      <circle class="detail" :cx="item.cx" :cy="item.cy" r="10" />
      <line class="detail" :x1="item.cx - 18" :y1="item.cy" :x2="item.cx + 18" :y2="item.cy" />
      <line class="detail" :x1="item.cx" :y1="item.cy - 18" :x2="item.cx" :y2="item.cy + 18" />
      <text class="utility-name" :x="item.cx + 42" :y="item.cy - 4">{{ item.name }}</text>
      <text class="utility-kw" :x="item.cx + 42" :y="item.cy + 22">
        {{ formatKw(item.loadKw) }} kW
      </text>
    </g>

    <!-- 범례: 설비 색 = 운전 상태 -->
    <g class="legend">
      <rect class="swatch run" x="880" y="490" width="16" height="16" rx="3" />
      <text x="904" y="503">운전</text>
      <rect class="swatch heat" x="960" y="490" width="16" height="16" rx="3" />
      <text x="984" y="503">승온 중</text>
      <rect class="swatch stop" x="1058" y="490" width="16" height="16" rx="3" />
      <text x="1082" y="503">정지</text>
    </g>
  </svg>
</template>

<style scoped>
.layout {
  display: block;
  width: 100%;
  height: 100%;
}
/* 선반 · 흐름 · 연결선 */
.rack {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 2;
}
.rack-label {
  font-size: 15px;
  fill: var(--ink-2);
  text-anchor: middle;
  paint-order: stroke;
  stroke: var(--card);
  stroke-width: 8px;
}
.flow {
  fill: none;
  stroke: var(--ink-2);
  stroke-width: 2.5;
}
.flow-head {
  fill: var(--ink-2);
}
.pipe {
  stroke: var(--line-strong);
  stroke-width: 2;
  stroke-dasharray: 10 8;
}
.link {
  stroke: var(--line-strong);
  stroke-width: 2;
  stroke-dasharray: 5 5;
}
/* 생산동 */
.hall {
  fill: none;
  stroke: var(--ink-2);
  stroke-width: 2.5;
  stroke-dasharray: 12 8;
}
.divider {
  stroke: var(--line-strong);
  stroke-width: 1.5;
  stroke-dasharray: 3 6;
}
/* 구역 칸: 마우스를 올리면 옅게, 고르면 조금 더 진하게 */
.zone {
  cursor: pointer;
  outline: none;
}
.zone-area {
  fill: transparent;
  stroke: transparent;
  stroke-width: 2.5;
}
.zone:hover .zone-area {
  fill: var(--hover);
}
.zone.selected .zone-area {
  fill: var(--band);
  stroke: var(--ink);
}
.zone:focus-visible .zone-area {
  stroke: var(--ink);
}
.zone-name {
  font-size: 22px;
  font-weight: 700;
  fill: var(--ink);
  text-anchor: middle;
}
.zone-kw {
  font-size: 18px;
  fill: var(--ink-2);
  text-anchor: middle;
  font-variant-numeric: tabular-nums;
}
.zone-unit {
  font-size: 14px;
  fill: var(--mute);
}
/* 설비 색 = 운전 상태 (운전 초록 · 승온 중 주황 · 정지 회색), 글자 설명은 범례와 마우스 올림 */
.machine .body,
.utility .body {
  stroke-width: 2;
}
.run .body,
.swatch.run {
  fill: var(--tint-ok);
  stroke: var(--ok);
}
.heat .body,
.swatch.heat {
  fill: var(--tint-warn);
  stroke: var(--warn);
}
.stop .body,
.swatch.stop {
  fill: var(--line);
  stroke: var(--lost);
}
.machine .detail,
.utility .detail {
  fill: none;
  stroke-width: 1.5;
}
.run .detail {
  stroke: var(--ok);
}
.heat .detail {
  stroke: var(--warn);
}
.stop .detail {
  stroke: var(--lost);
}
.conveyor rect {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 1.5;
}
.conveyor line {
  stroke: var(--line-strong);
  stroke-width: 1.2;
}
/* 공용 설비 */
.utility {
  cursor: pointer;
  outline: none;
}
.utility.selected .body,
.utility:focus-visible .body {
  stroke: var(--ink);
  stroke-width: 3.5;
}
.utility-name {
  font-size: 17px;
  fill: var(--ink);
}
.utility-kw {
  font-size: 18px;
  font-weight: 700;
  fill: var(--ink-2);
  font-variant-numeric: tabular-nums;
}
.legend text {
  font-size: 14px;
  fill: var(--ink-2);
}
.swatch {
  stroke-width: 2;
}
</style>
