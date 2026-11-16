<!--
  지금 15분 구간 카드: 예상 평균, 한 줄 판정, 단계(경고·주의·정상), 남은 시간
  예상값, 목표, 주의선, 단계는 모두 백엔드가 준 값이고 여기서는 보여 주기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import { formatClock, formatHourMinute, formatKw, formatRemaining } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 서버 연결이 끊겨 마지막 값을 보여 주는 중이면 true
  stale: { type: Boolean, default: false },
})

const WINDOW_SECONDS = 15 * 60

// 화면에 보여 줄 단계. 연결이 끊겼거나 값이 없으면 'LOST'
const shown = computed(() => {
  if (props.stale || !props.status) return 'LOST'
  return props.status.peakStatus
})

const stateClass = computed(
  () => ({ NORMAL: 'ok', WARNING: 'warn', CRITICAL: 'crit' })[shown.value] ?? 'lost',
)

// 큰 숫자 아래 한 줄 판정
const verdict = computed(() => {
  const status = props.status
  if (!status) return '값을 기다리는 중입니다'
  if (props.stale) return `연결이 끊겨 ${formatClock(status.recordedAt)} 값에서 멈춰 있습니다`
  const target = `목표 ${formatKw(status.targetPeakKw)} kW`
  if (status.peakStatus === 'CRITICAL')
    return `${target}를 ${formatKw(status.defenseKw)} kW 넘습니다`
  if (status.peakStatus === 'WARNING') return `${target}에 거의 닿았습니다`
  return `${target} 안쪽입니다`
})

// 단계 표시 (위에서부터 경고 → 주의 → 정상). 기준값은 백엔드가 준 목표와 주의선
const steps = computed(() => {
  const target = formatKw(props.status?.targetPeakKw)
  const warn = formatKw(props.status?.warnKw)
  return [
    { key: 'CRITICAL', shape: '■', name: '경고', rule: `${target} kW 초과` },
    { key: 'WARNING', shape: '▲', name: '주의', rule: `${warn} kW 이상` },
    { key: 'NORMAL', shape: '●', name: '정상', rule: `${warn} kW 미만` },
  ]
})

// 15분 구간에서 지나간 시간의 비율 (표시용)
const elapsedPercent = computed(() => {
  if (!props.status) return 0
  return ((WINDOW_SECONDS - props.status.remainingSeconds) / WINDOW_SECONDS) * 100
})
</script>

<template>
  <section class="card window" :class="stateClass">
    <div class="head">
      <h2 class="card-title">지금 15분 구간</h2>
      <span class="range num">
        {{ formatHourMinute(status?.windowStart) }} – {{ formatHourMinute(status?.windowEnd) }}
      </span>
    </div>

    <p class="caption">예상 평균</p>
    <p class="big">
      <span class="num">{{ formatKw(status?.predictedKw) }}</span>
      <span class="unit">kW</span>
    </p>
    <p class="verdict">{{ verdict }}</p>

    <!-- 단계 표시: 지금 단계만 색을 채움 -->
    <ol class="steps" aria-label="피크 단계">
      <li v-for="step in steps" :key="step.key" class="step" :class="{ on: step.key === shown }">
        <span aria-hidden="true">{{ step.shape }}</span>
        <span class="step-name">{{ step.name }}</span>
        <span class="step-rule num">{{ step.rule }}</span>
      </li>
    </ol>
    <p v-if="shown === 'LOST'" class="lost"><span aria-hidden="true">◆</span> 통신 단절</p>

    <div class="remain">
      <span>구간 종료까지</span>
      <strong class="num">{{ formatRemaining(status?.remainingSeconds) }}</strong>
    </div>
    <div class="track" aria-hidden="true">
      <span class="bar" :style="{ width: `${elapsedPercent}%` }"></span>
    </div>
  </section>
</template>

<style scoped>
.window {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 단계에 따라 바뀌는 색 */
  --state: var(--lost);
}
.window.ok {
  --state: var(--ok);
}
.window.warn {
  --state: var(--warn);
}
.window.crit {
  --state: var(--crit);
  border: 2px solid var(--crit);
  /* 테두리가 1px 두꺼워진 만큼 안쪽 여백을 줄여 내용 위치를 유지 */
  padding: 13px 15px;
}

.head {
  display: flex;
  align-items: baseline;
}
.range {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.caption {
  font-size: 13px;
  color: var(--mute);
}
.big {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 52px;
  font-weight: 700;
  line-height: 1.05;
  color: var(--state);
}
.unit {
  font-size: 18px;
  font-weight: 400;
  color: var(--ink-2);
}
.verdict {
  font-weight: 700;
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}
.step {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--line);
  color: var(--mute);
}
.step-name {
  font-weight: 700;
}
.step-rule {
  margin-left: auto;
  font-size: 13px;
}
.step.on {
  background: var(--state);
  border-color: var(--state);
  color: var(--card);
}
.lost {
  font-weight: 700;
  color: var(--ink-2);
}

.remain {
  display: flex;
  align-items: baseline;
  margin-top: auto;
  font-size: 13px;
  color: var(--ink-2);
}
.remain strong {
  margin-left: auto;
  font-size: 18px;
  color: var(--ink);
}
.track {
  height: 5px;
  background: var(--line);
}
.bar {
  display: block;
  height: 100%;
  background: var(--ink);
}
</style>
