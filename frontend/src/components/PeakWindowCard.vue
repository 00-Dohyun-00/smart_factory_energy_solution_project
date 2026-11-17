<!--
  현재 15분 피크 구간 카드
  왼쪽: 예상 평균(큰 숫자), 한 줄 판정, 단계
  오른쪽: 피크 게이지(정상·주의·경고 띠 위에 예상 평균 표시), 목표·예상·필요 전력·남은 시간, 구간 진행 막대

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

const STAGE_INFO = {
  NORMAL: { shape: '●', label: '정상', className: 'ok' },
  WARNING: { shape: '▲', label: '주의', className: 'warn' },
  CRITICAL: { shape: '■', label: '경고', className: 'crit' },
  LOST: { shape: '◆', label: '통신 단절', className: 'lost' },
}

// 화면에 보여 줄 단계. 연결이 끊겼거나 값이 없으면 통신 단절
const stage = computed(() => {
  if (props.stale || !props.status) return STAGE_INFO.LOST
  return STAGE_INFO[props.status.peakStatus]
})

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

// ---- 게이지 ----
// 게이지에 그릴 범위는 목표 피크를 기준으로 화면에서 정함 (표시용, 목표 1,800이면 1,400 ~ 2,100)
const gaugeMin = computed(() => Math.floor((props.status.targetPeakKw * 0.78) / 100) * 100)
const gaugeMax = computed(
  () => props.status.targetPeakKw + (props.status.targetPeakKw - gaugeMin.value) * 0.75,
)

// kW 값이 게이지의 왼쪽에서 몇 % 위치인지 (범위를 벗어나면 양 끝에 붙임)
function position(kw) {
  const percent = ((kw - gaugeMin.value) / (gaugeMax.value - gaugeMin.value)) * 100
  return Math.min(100, Math.max(0, percent))
}

const gauge = computed(() => {
  if (!props.status) return null
  const predicted = position(props.status.predictedKw)
  return {
    warn: position(props.status.warnKw),
    target: position(props.status.targetPeakKw),
    predicted,
    // 예상값 글자가 게이지 밖으로 나가지 않게, 오른쪽 끝에 가까우면 글자를 표시선 왼쪽에 둠
    labelOnLeft: predicted > 78,
  }
})

// 15분 구간에서 지나간 시간 (표시용)
const elapsedSeconds = computed(() =>
  props.status ? WINDOW_SECONDS - props.status.remainingSeconds : null,
)
const elapsedPercent = computed(() =>
  elapsedSeconds.value === null ? 0 : (elapsedSeconds.value / WINDOW_SECONDS) * 100,
)
</script>

<template>
  <section class="card window" :class="stage.className">
    <div class="head">
      <h2 class="card-title">현재 15분 피크 구간</h2>
      <span class="range num">
        {{ formatHourMinute(status?.windowStart) }} – {{ formatHourMinute(status?.windowEnd) }}
      </span>
    </div>

    <div class="body">
      <!-- 왼쪽: 예상 평균과 판정 -->
      <div class="left">
        <p class="caption">예상 평균</p>
        <p class="big">
          <span class="num">{{ formatKw(status?.predictedKw) }}</span>
          <span class="unit">kW</span>
        </p>
        <p class="verdict">{{ verdict }}</p>
        <p class="chip">
          <span aria-hidden="true">{{ stage.shape }}</span> {{ stage.label }}
        </p>
      </div>

      <!-- 오른쪽: 게이지와 숫자 -->
      <div class="right">
        <div v-if="gauge" class="gauge" aria-hidden="true">
          <p
            class="predicted-label num"
            :class="{ 'on-left': gauge.labelOnLeft }"
            :style="{ left: `${gauge.predicted}%` }"
          >
            예상 {{ formatKw(status.predictedKw) }}
          </p>
          <div class="zones">
            <span class="zone zone-ok" :style="{ width: `${gauge.warn}%` }">● 정상</span>
            <span class="zone zone-warn" :style="{ width: `${gauge.target - gauge.warn}%` }"></span>
            <span class="zone zone-crit">■ 경고</span>
            <i class="target-line" :style="{ left: `${gauge.target}%` }"></i>
            <i class="predicted-line" :style="{ left: `${gauge.predicted}%` }"></i>
          </div>
          <div class="axis num">
            <span>{{ formatKw(gaugeMin) }}</span>
            <span class="axis-target" :style="{ left: `${gauge.target}%` }">
              목표 {{ formatKw(status.targetPeakKw) }}
            </span>
            <span class="axis-max">{{ formatKw(gaugeMax) }} kW</span>
          </div>
          <p class="warn-note num">
            <span aria-hidden="true">▲</span> 주의는 {{ formatKw(status.warnKw) }} kW부터
          </p>
        </div>
        <p v-else class="empty">값을 기다리는 중입니다.</p>

        <dl class="stats">
          <div>
            <dt>목표 피크</dt>
            <dd>
              <span class="num">{{ formatKw(status?.targetPeakKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>예상 평균</dt>
            <dd class="state">
              <span class="num">{{ formatKw(status?.predictedKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>방어 필요 전력</dt>
            <dd>
              <span class="num">{{ formatKw(status?.defenseKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>남은 시간</dt>
            <dd class="num">{{ formatRemaining(status?.remainingSeconds) }}</dd>
          </div>
        </dl>

        <div class="progress">
          <p>
            <span>구간 진행</span>
            <span class="num">{{ formatRemaining(elapsedSeconds) }} / 15분</span>
          </p>
          <div class="track" aria-hidden="true">
            <span class="bar" :style="{ width: `${elapsedPercent}%` }"></span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.window {
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 단계에 따라 바뀌는 색 */
  --state: var(--ink-2);
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
.body {
  display: flex;
  flex: 1;
  gap: 32px;
}

/* 왼쪽 */
.left {
  display: flex;
  flex-direction: column;
  flex: none;
  gap: 4px;
  width: 290px;
}
.caption {
  font-size: 13px;
  color: var(--mute);
}
.big {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 60px;
  font-weight: 700;
  line-height: 1.12;
  color: var(--state);
}
.unit {
  font-size: 18px;
  font-weight: 400;
  color: var(--ink-2);
}
.verdict {
  font-size: 15px;
  font-weight: 700;
  /* 줄이 바뀔 때 낱말 중간에서 끊기지 않게 */
  word-break: keep-all;
}
.chip {
  align-self: flex-start;
  margin-top: 6px;
  padding: 6px 12px;
  background: var(--state);
  font-weight: 700;
  color: var(--card);
}

/* 오른쪽 */
/* 옆의 ESS 추천 카드가 더 길어 이 카드가 늘어나도, 내용 사이가 벌어지지 않게 위로 모음 */
.right {
  display: flex;
  flex-direction: column;
  flex: 1;
  justify-content: flex-start;
  gap: 14px;
  min-width: 0;
}
.empty {
  color: var(--mute);
}

/* 게이지 */
.gauge {
  position: relative;
  padding-top: 26px;
}
.predicted-label {
  position: absolute;
  top: 0;
  margin-left: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--state);
  white-space: nowrap;
}
.predicted-label.on-left {
  margin-left: -8px;
  transform: translateX(-100%);
}
.zones {
  position: relative;
  display: flex;
  height: 44px;
}
.zone {
  display: flex;
  align-items: center;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}
/* 띠 색은 상태색을 옅게 깐 것 (global.css의 --ok, --warn, --crit와 같은 색) */
.zone-ok {
  padding-left: 12px;
  background: rgba(47, 125, 79, 0.22);
  color: var(--ok);
}
.zone-warn {
  background: rgba(156, 95, 8, 0.45);
}
.zone-crit {
  flex: 1;
  justify-content: flex-end;
  padding-right: 12px;
  background: rgba(200, 55, 45, 0.22);
  color: var(--crit);
}
.target-line {
  position: absolute;
  top: -4px;
  bottom: -10px;
  width: 2px;
  margin-left: -1px;
  background: var(--ink);
}
.predicted-line {
  position: absolute;
  top: -14px;
  bottom: -14px;
  width: 5px;
  margin-left: -2.5px;
  background: var(--state);
  transition: left 0.4s ease;
}
.axis {
  position: relative;
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  font-size: 12px;
  color: var(--mute);
}
.axis-target {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  white-space: nowrap;
}
.warn-note {
  margin-top: 2px;
  font-size: 12px;
  color: var(--warn);
}

/* 숫자 네 개 */
.stats {
  display: flex;
  gap: 16px;
}
.stats div {
  flex: 1;
}
.stats dt {
  font-size: 12px;
  color: var(--mute);
  white-space: nowrap;
}
.stats dd {
  font-size: 24px;
  font-weight: 700;
  white-space: nowrap;
}
.stats dd.state {
  color: var(--state);
}
.stats small {
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
}

/* 구간 진행 */
.progress p {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--mute);
}
.track {
  height: 6px;
  background: var(--line);
}
.bar {
  display: block;
  height: 100%;
  background: var(--ink);
}

/* 화면이 좁으면 왼쪽 숫자 칸과 글자를 줄여 게이지 자리를 남김 */
@media (max-width: 1600px) {
  .body {
    gap: 24px;
  }
  .left {
    width: 230px;
  }
  .big {
    font-size: 52px;
  }
  .stats dd {
    font-size: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .predicted-line {
    transition: none;
  }
}
</style>
