<!--
  전력 흐름도: 한전 · 태양광 · ESS → 공장
  전력이 흐르는 선만 점선이 움직입니다. (0 kW면 멈춘 회색 선)
-->
<script setup>
import { computed } from 'vue'
import { formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
})

// ESS는 충전 / 방전 / 대기 중 하나만 함
const essState = computed(() => {
  if (!props.status) return { label: '—', kw: null }
  if (props.status.dischargeKw > 0) return { label: '방전', kw: props.status.dischargeKw }
  if (props.status.chargeKw > 0) return { label: '충전', kw: props.status.chargeKw }
  return { label: '대기', kw: 0 }
})

// 왼쪽 공급원 3개 (y는 상자의 위쪽 위치)
const sources = computed(() => [
  {
    key: 'solar',
    name: '태양광',
    tag: '가상',
    y: 28,
    // 선이 꺾이는 x 위치와 공장에 닿는 y 위치 (선끼리 겹치지 않게 서로 다르게)
    bendX: 520,
    entryY: 185,
    kw: props.status?.solarKw ?? null,
    flowing: (props.status?.solarKw ?? 0) > 0,
    sub: '',
  },
  {
    key: 'ess',
    name: 'ESS',
    tag: '가상',
    y: 163,
    bendX: 500,
    entryY: 215,
    kw: essState.value.kw,
    flowing: (props.status?.dischargeKw ?? 0) > 0,
    sub: `${essState.value.label} · 잔량 ${formatOneDecimal(props.status?.socPercent)}%`,
  },
  {
    key: 'grid',
    name: '한전',
    tag: '계산값',
    y: 298,
    bendX: 520,
    entryY: 245,
    kw: props.status?.gridKw ?? null,
    flowing: (props.status?.gridKw ?? 0) > 0,
    sub: '',
  },
])
</script>

<template>
  <svg class="flow" viewBox="0 0 960 430" role="img" aria-label="전력 흐름도">
    <g v-for="source in sources" :key="source.key" :class="source.key">
      <!-- 공급원 → 공장 선 -->
      <path
        class="wire"
        :class="{ flowing: source.flowing }"
        :d="`M 300 ${source.y + 52} L ${source.bendX} ${source.y + 52} L ${source.bendX} ${source.entryY} L 660 ${source.entryY}`"
      />
      <text class="wire-label" x="316" :y="source.y + 42">{{ formatKw(source.kw) }} kW</text>

      <!-- 공급원 상자 -->
      <rect class="box" x="40" :y="source.y" width="260" height="104" rx="4" />
      <rect class="mark" x="40" :y="source.y" width="6" height="104" />
      <text class="name" x="60" :y="source.y + 30">{{ source.name }}</text>
      <text class="small" x="288" :y="source.y + 30" text-anchor="end">{{ source.tag }}</text>
      <text class="value" x="60" :y="source.y + 72">
        {{ formatKw(source.kw) }}
        <tspan class="unit" dx="4">kW</tspan>
      </text>
      <text class="small" x="60" :y="source.y + 92">{{ source.sub }}</text>
    </g>

    <!-- 공장 -->
    <rect class="box factory" x="660" y="135" width="260" height="160" rx="4" />
    <text class="name" x="680" y="167">공장 소비전력</text>
    <text class="small" x="908" y="167" text-anchor="end">시뮬레이션</text>
    <text class="value factory-value" x="680" y="225">
      {{ formatKw(status?.loadKw) }}
      <tspan class="unit" dx="4">kW</tspan>
    </text>
    <text class="small" x="680" y="268">한전 + 태양광 + ESS 방전</text>
  </svg>
</template>

<style scoped>
.flow {
  width: 100%;
  height: 100%;
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

.box {
  fill: var(--card);
  stroke: var(--line-strong);
}
.box.factory {
  stroke: var(--ink-2);
  stroke-width: 1.5;
}
.mark {
  fill: var(--source);
}
.name {
  font-size: 15px;
  font-weight: 700;
  fill: var(--ink);
}
.small {
  font-size: 12px;
  fill: var(--mute);
}
.value {
  font-size: 28px;
  font-weight: 700;
  fill: var(--ink);
  font-variant-numeric: tabular-nums;
}
.factory-value {
  font-size: 38px;
}
.unit {
  font-size: 14px;
  font-weight: 400;
  fill: var(--ink-2);
}

.wire {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 3;
}
.wire.flowing {
  stroke: var(--source);
  stroke-dasharray: 10 8;
  animation: flow 1s linear infinite;
}
.wire-label {
  font-size: 14px;
  font-weight: 700;
  fill: var(--ink-2);
  font-variant-numeric: tabular-nums;
}

/* 점선이 공장 쪽으로 흘러가는 것처럼 보이게 함 */
@keyframes flow {
  to {
    stroke-dashoffset: -18;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wire.flowing {
    animation: none;
  }
}
</style>
