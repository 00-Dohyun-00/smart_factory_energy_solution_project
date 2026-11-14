<!--
  공장 레이아웃 (평면도)
  설비 위치는 실제 도면이 없어 임시로 잡은 것입니다. 설비를 누르면 아래에 자세한 값이 나옵니다.
  설비 id는 백엔드가 주는 equipments[].id 와 같아야 합니다. (예: Z1-MOTOR1)
-->
<script setup>
import { computed, ref } from 'vue'
import { formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
})

// 설비 상자의 위치와 크기 (SVG 좌표, 전체 960 × 430)
const EQUIPMENT_BOXES = [
  { id: 'Z1-PRESS', x: 44, y: 96, width: 196, height: 132 },
  { id: 'Z1-COMP', x: 256, y: 96, width: 196, height: 132 },
  { id: 'Z1-HVAC', x: 468, y: 96, width: 176, height: 132 },
  { id: 'Z1-MOTOR1', x: 44, y: 246, width: 196, height: 140 },
  { id: 'Z1-ETC', x: 256, y: 246, width: 388, height: 140 },
]

const ORIGIN_LABELS = { MEASURED: '실측', SIMULATED: '시뮬레이션', CALCULATED: '계산값' }

const selectedId = ref('Z1-MOTOR1')

// 위치 정보에 백엔드 값(이름, kW, 출처)을 합침. 값을 못 받은 설비는 '—'로 표시
const equipments = computed(() =>
  EQUIPMENT_BOXES.map((box) => {
    const data = props.status?.equipments.find((item) => item.id === box.id)
    return {
      ...box,
      name: data?.name ?? box.id,
      loadKw: data?.loadKw ?? null,
      originLabel: ORIGIN_LABELS[data?.origin] ?? '—',
    }
  }),
)

const selected = computed(() => equipments.value.find((item) => item.id === selectedId.value))

// 공급원 비율 막대 3칸 (한전 / 태양광 / ESS). 비율은 백엔드가 준 값을 그대로 씀
const ratioParts = computed(() => {
  const ratio = props.status?.sourceRatio
  return [
    { key: 'grid', name: '한전', percent: ratio?.gridPercent ?? null },
    { key: 'solar', name: '태양광', percent: ratio?.solarPercent ?? null },
    { key: 'ess', name: 'ESS', percent: ratio?.essPercent ?? null },
  ]
})

// 설비 상자 안의 작은 비율 막대: 각 칸의 시작 위치와 너비 (표시용 계산)
function ratioRects(box) {
  const fullWidth = box.width - 24
  let x = box.x + 12
  return ratioParts.value.map((part) => {
    const width = (fullWidth * (part.percent ?? 0)) / 100
    const rect = { key: part.key, x, width }
    x += width
    return rect
  })
}
</script>

<template>
  <div class="layout">
    <svg class="map" viewBox="0 0 960 430" role="group" aria-label="공장 레이아웃">
      <!-- 생산동 -->
      <rect class="zone" x="24" y="56" width="640" height="350" rx="4" />
      <text class="zone-name" x="44" y="82">생산동</text>

      <!-- 설비 (누르면 선택) -->
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
          class="equipment-box"
          :x="item.x"
          :y="item.y"
          :width="item.width"
          :height="item.height"
          rx="4"
        />
        <text class="equipment-name" :x="item.x + 12" :y="item.y + 26">{{ item.name }}</text>
        <text
          class="equipment-origin"
          :x="item.x + item.width - 12"
          :y="item.y + 26"
          text-anchor="end"
        >
          {{ item.originLabel }}
        </text>
        <text class="equipment-value" :x="item.x + 12" :y="item.y + 66">
          {{ formatKw(item.loadKw) }}
          <tspan class="equipment-unit" dx="4">kW</tspan>
        </text>
        <rect
          v-for="rect in ratioRects(item)"
          :key="rect.key"
          class="ratio"
          :class="rect.key"
          :x="rect.x"
          :y="item.y + item.height - 22"
          :width="rect.width"
          height="8"
        />
      </g>

      <!-- 공급원 3곳 -->
      <g class="source solar">
        <rect class="source-box" x="696" y="56" width="240" height="104" rx="4" />
        <rect class="source-mark" x="696" y="56" width="6" height="104" />
        <text class="source-name" x="716" y="84">태양광 (지붕)</text>
        <text class="source-tag" x="924" y="84" text-anchor="end">가상</text>
        <text class="source-value" x="716" y="130">
          {{ formatKw(status?.solarKw) }}
          <tspan class="equipment-unit" dx="4">kW</tspan>
        </text>
      </g>
      <g class="source ess">
        <rect class="source-box" x="696" y="179" width="240" height="104" rx="4" />
        <rect class="source-mark" x="696" y="179" width="6" height="104" />
        <text class="source-name" x="716" y="207">ESS실</text>
        <text class="source-tag" x="924" y="207" text-anchor="end">가상</text>
        <text class="source-value" x="716" y="253">
          {{ formatOneDecimal(status?.socPercent) }}
          <tspan class="equipment-unit" dx="4">%</tspan>
        </text>
        <text class="source-tag" x="924" y="253" text-anchor="end">
          배터리실 {{ formatOneDecimal(status?.batteryTempC) }} ℃
        </text>
      </g>
      <g class="source grid">
        <rect class="source-box" x="696" y="302" width="240" height="104" rx="4" />
        <rect class="source-mark" x="696" y="302" width="6" height="104" />
        <text class="source-name" x="716" y="330">수전설비 (한전)</text>
        <text class="source-tag" x="924" y="330" text-anchor="end">계산값</text>
        <text class="source-value" x="716" y="376">
          {{ formatKw(status?.gridKw) }}
          <tspan class="equipment-unit" dx="4">kW</tspan>
        </text>
      </g>
    </svg>

    <!-- 선택한 설비의 자세한 값: 색 + 글자 + 막대를 함께 표시 -->
    <div v-if="selected" class="detail">
      <p class="detail-title">
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

.zone {
  fill: var(--bg);
  stroke: var(--line-strong);
}
.zone-name {
  font-size: 14px;
  font-weight: 700;
  fill: var(--mute);
}

.equipment {
  cursor: pointer;
  outline: none;
}
.equipment-box {
  fill: var(--card);
  stroke: var(--line-strong);
  transition: stroke 0.15s;
}
.equipment:hover .equipment-box,
.equipment:focus-visible .equipment-box {
  stroke: var(--ink-2);
}
.equipment.selected .equipment-box {
  stroke: var(--accent);
  stroke-width: 2.5;
}
.equipment-name,
.source-name {
  font-size: 15px;
  font-weight: 700;
  fill: var(--ink);
}
.equipment-origin,
.source-tag {
  font-size: 12px;
  fill: var(--mute);
}
.equipment-value,
.source-value {
  font-size: 28px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.equipment-unit {
  font-size: 14px;
  font-weight: 400;
  fill: var(--ink-2);
}

.source-box {
  fill: var(--card);
  stroke: var(--line-strong);
}

/* 공급원 색: SVG 도형(fill)과 아래 HTML 막대(background)에 같이 씀 */
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess {
  --source: var(--src-ess);
}
.ratio,
.source-mark {
  fill: var(--source);
}

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
</style>
