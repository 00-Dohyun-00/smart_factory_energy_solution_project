<!--
  15분 피크 게이지
  예상 평균(막대)을 목표 피크(굵은 선)·주의선(점선)과 비교해 보여 줍니다.
  예상값, 목표, 상태는 모두 백엔드가 계산한 값이고 여기서는 그리기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import StatusChip from '@/components/StatusChip.vue'
import { formatHourMinute, formatKw, formatRemaining } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
})

const WINDOW_SECONDS = 15 * 60

// 막대의 오른쪽 끝 = 목표의 125%. 목표를 넘는 값도 보이도록 여유를 둠 (표시용 계산)
function toPercent(kw) {
  const maxKw = props.status.targetPeakKw * 1.25
  return Math.min(100, Math.max(0, (kw / maxKw) * 100))
}

const predictedPercent = computed(() => (props.status ? toPercent(props.status.predictedKw) : 0))
const targetPercent = computed(() => (props.status ? toPercent(props.status.targetPeakKw) : 80))
const warnPercent = computed(() => (props.status ? toPercent(props.status.warnKw) : 77.6))

// 15분 구간에서 지나간 시간의 비율
const elapsedPercent = computed(() => {
  if (!props.status) return 0
  return ((WINDOW_SECONDS - props.status.remainingSeconds) / WINDOW_SECONDS) * 100
})

const statusClass = computed(() => {
  const map = { NORMAL: 'ok', WARNING: 'warn', CRITICAL: 'crit' }
  return map[props.status?.peakStatus] ?? 'lost'
})

const message = computed(() => {
  if (!props.status) return '값을 받지 못했습니다.'
  if (props.status.peakStatus === 'CRITICAL') {
    return `목표 초과 예상 · 방어 필요 ${formatKw(props.status.defenseKw)} kW`
  }
  if (props.status.peakStatus === 'WARNING') return '목표 피크에 가까워지고 있습니다.'
  return '목표 피크 이내로 예상됩니다.'
})
</script>

<template>
  <section class="card gauge" :class="statusClass">
    <div class="gauge-head">
      <h2 class="card-title">15분 피크</h2>
      <span class="tag">계산값</span>
      <span class="window num">
        {{ formatHourMinute(status?.windowStart) }}~{{ formatHourMinute(status?.windowEnd) }}
      </span>
    </div>

    <div class="numbers">
      <div>
        <p class="caption">예상 평균</p>
        <p class="predicted">
          <span class="num">{{ formatKw(status?.predictedKw) }}</span>
          <span class="unit">kW</span>
        </p>
      </div>
      <div>
        <p class="caption">목표 피크</p>
        <p class="target">
          <span class="num">{{ formatKw(status?.targetPeakKw) }}</span>
          <span class="unit">kW</span>
        </p>
      </div>
      <div class="remaining">
        <p class="caption">남은 시간</p>
        <p class="target num">{{ formatRemaining(status?.remainingSeconds) }}</p>
      </div>
    </div>

    <!-- 예상 평균 막대 + 주의선 + 목표선 -->
    <div class="track-wrap">
      <div class="track">
        <div
          class="zone-warn"
          :style="{ left: `${warnPercent}%`, right: `${100 - targetPercent}%` }"
        ></div>
        <div class="zone-over" :style="{ left: `${targetPercent}%` }"></div>
        <div class="bar" :style="{ width: `${predictedPercent}%` }"></div>
        <div class="line-warn" :style="{ left: `${warnPercent}%` }"></div>
        <div class="line-target" :style="{ left: `${targetPercent}%` }"></div>
      </div>
      <div class="track-labels">
        <span>0</span>
        <span class="label-target" :style="{ left: `${targetPercent}%` }">목표</span>
        <span class="label-over">초과 구간</span>
      </div>
    </div>

    <!-- 현재 15분 구간에서 지나간 시간 -->
    <div class="time-track" aria-hidden="true">
      <div class="time-bar" :style="{ width: `${elapsedPercent}%` }"></div>
    </div>

    <p class="state">
      <StatusChip :status="status ? status.peakStatus : 'LOST'" />
      <span class="state-message">{{ message }}</span>
    </p>
  </section>
</template>

<style scoped>
.gauge {
  display: flex;
  flex-direction: column;
  gap: 10px;
  /* 상태에 따라 바뀌는 색. 아래 .ok/.warn/.crit/.lost에서 정함 */
  --state: var(--lost);
}
.gauge.ok {
  --state: var(--ok);
}
.gauge.warn {
  --state: var(--warn);
}
.gauge.crit {
  --state: var(--crit);
  border-color: var(--crit);
}

.gauge-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.window {
  margin-left: auto;
  color: var(--ink-2);
}

.numbers {
  display: flex;
  align-items: flex-end;
  gap: 28px;
}
.remaining {
  margin-left: auto;
  text-align: right;
}
.caption {
  font-size: 12px;
  color: var(--mute);
}
.predicted {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.05;
  color: var(--state);
}
.target {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}
.unit {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 400;
  color: var(--ink-2);
}

.track {
  position: relative;
  height: 22px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 3px;
}
.zone-warn,
.zone-over {
  position: absolute;
  top: 0;
  bottom: 0;
  opacity: 0.14;
}
.zone-warn {
  background: var(--warn);
}
.zone-over {
  right: 0;
  background: var(--crit);
}
.bar {
  position: absolute;
  left: 0;
  top: 5px;
  bottom: 5px;
  background: var(--state);
  border-radius: 0 2px 2px 0;
  transition: width 0.4s ease;
}
.line-warn,
.line-target {
  position: absolute;
  top: -4px;
  bottom: -4px;
}
.line-warn {
  border-left: 1px dashed var(--warn);
}
.line-target {
  border-left: 2px solid var(--ink);
}
.track-labels {
  position: relative;
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
  font-size: 12px;
  color: var(--mute);
}
.label-target {
  position: absolute;
  transform: translateX(-50%);
  font-weight: 700;
  color: var(--ink);
}
.label-over {
  color: var(--crit);
}

.time-track {
  height: 3px;
  background: var(--line);
}
.time-bar {
  height: 100%;
  background: var(--ink-2);
}

.state {
  display: flex;
  align-items: center;
  gap: 10px;
}
.state-message {
  color: var(--ink-2);
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
