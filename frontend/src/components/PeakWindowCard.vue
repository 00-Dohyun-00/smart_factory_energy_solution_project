<!--
  현재 15분 피크 구간 카드
  왼쪽: 피크 계기판 (가운데에 예상 피크 kW와 목표 대비 %)
  오른쪽: 한 줄 판정과 단계, 목표·예상·추가 방전·남은 시간, 구간 진행 막대
  (2026-11-18 통합 화면에서 카드 폭이 절반이 되어, 따로 있던 큰 숫자 칸을 계기판 가운데로 옮김)
    계기판은 KPI 카드(반원 + 바늘)와 다른 모양: 240° 원호, 바늘 없이 바깥 테에 정상·주의·경고 구간,
    안쪽 굵은 원호가 예상 피크만큼 차오르고, 목표 피크는 먹색 눈금, 가운데는 목표 대비 %

  예상값, 목표, 주의선, 단계는 모두 백엔드가 준 값이고 여기서는 보여 주기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import {
  formatClock,
  formatHourMinute,
  formatInteger,
  formatKw,
  formatRemaining,
} from '@/utils/format'

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
  WAITING: { shape: '○', label: '불러오는 중', className: 'lost' },
}

// 화면에 보여 줄 단계
// - 연결이 끊겼으면 통신 단절
// - 아직 첫 값을 받기 전이면 "불러오는 중" (처음 열었을 때 통신 단절로 잘못 보이지 않게)
const stage = computed(() => {
  if (props.stale) return STAGE_INFO.LOST
  if (!props.status) return STAGE_INFO.WAITING
  return STAGE_INFO[props.status.peakStatus]
})

// 큰 숫자 아래 한 줄 판정
const verdict = computed(() => {
  const status = props.status
  if (!status) return '값을 기다리는 중입니다'
  if (props.stale) return `연결이 끊겨 ${formatClock(status.recordedAt)} 값에서 멈춰 있습니다`
  const target = `목표 ${formatKw(status.targetPeakKw)} kW`
  // defenseKw는 "넘는 양"이 아니라 "더 방전해야 하는 전력"이라 여기에 쓰지 않음 (아래 숫자 칸에 따로 보임)
  if (status.peakStatus === 'CRITICAL') return `${target}를 넘을 것으로 보입니다`
  if (status.peakStatus === 'WARNING') return `${target}에 거의 닿았습니다`
  return `${target} 안쪽입니다`
})

// ---- 게이지 ----
// 계기판 범위는 0 ~ 목표 피크의 120% (표시용, 목표 1,800이면 0 ~ 2,160)
// 0부터 그려야 가운데의 "목표 대비 %"와 원호가 차오른 길이가 맞음
const gaugeMin = computed(() => 0)
const gaugeMax = computed(() => props.status.targetPeakKw * 1.2)

// kW 값이 계기판 범위에서 어디쯤인지 0~1 (범위를 벗어나면 양 끝에 붙임)
function ratioOf(kw) {
  const ratio = (kw - gaugeMin.value) / (gaugeMax.value - gaugeMin.value)
  return Math.min(1, Math.max(0, ratio))
}

// ---- 원호 그리기 (SVG) ----
// 그림 크기 200 × 170, 원의 가운데 (100, 100). 왼쪽 아래(150°)에서 시작해 시계 방향으로 240° 돌아 오른쪽 아래(30°)에서 끝남
const CX = 100
const CY = 100
const START_DEG = 150
const SWEEP_DEG = 240

// 0~1 위치 → 원 위의 점
function pointAt(ratio, radius) {
  const rad = ((START_DEG + SWEEP_DEG * ratio) * Math.PI) / 180
  return { x: CX + radius * Math.cos(rad), y: CY + radius * Math.sin(rad) }
}

// from~to(0~1) 사이를 잇는 원호 경로
function arcPath(from, to, radius) {
  const start = pointAt(from, radius)
  const end = pointAt(to, radius)
  const large = (to - from) * SWEEP_DEG > 180 ? 1 : 0
  return `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${radius} ${radius} 0 ${large} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`
}

const dial = computed(() => {
  if (!props.status) return null
  const s = props.status
  const warn = ratioOf(s.warnKw)
  const target = ratioOf(s.targetPeakKw)
  const predicted = ratioOf(s.predictedKw)
  const gap = 0.012 // 바깥 테 구간 사이의 틈
  const tickIn = pointAt(target, 60) // 안쪽 끝은 원호 안쪽 가장자리까지만 (가운데 숫자와 겹치지 않게)
  const tickOut = pointAt(target, 94)
  const marker = pointAt(predicted, 95) // 예상 위치 표시(삼각형)의 꼭짓점
  const markerAngle = START_DEG + SWEEP_DEG * predicted
  return {
    zoneOk: arcPath(0, Math.max(0, warn - gap), 86),
    zoneWarn: arcPath(Math.min(warn + gap, target - gap), Math.max(warn + gap, target - gap), 86),
    zoneCrit: arcPath(Math.min(1, target + gap), 1, 86),
    track: arcPath(0, 1, 68),
    fill: predicted > 0.002 ? arcPath(0, predicted, 68) : '',
    tick: { x1: tickIn.x, y1: tickIn.y, x2: tickOut.x, y2: tickOut.y },
    marker: { x: marker.x, y: marker.y, angle: markerAngle },
    // 목표 대비 % (표시용 비율: 백엔드 값 두 개를 나눈 것)
    percent: Math.round((s.predictedKw / s.targetPeakKw) * 100),
    minLabel: pointAt(0, 68),
    maxLabel: pointAt(1, 68),
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
      <!-- 오른쪽: 게이지와 숫자 -->
      <div class="right">
        <div class="meter">
          <!-- 계기판 (KPI 카드와 다른 모양: 240° 원호, 바늘 없음) -->
          <div v-if="dial" class="dial">
            <svg
              viewBox="0 0 200 170"
              role="img"
              :aria-label="`예상 피크 ${formatKw(status.predictedKw)} kW, 목표 대비 ${dial.percent}%`"
            >
              <!-- 바깥 테: 정상 · 주의 · 경고 구간 -->
              <path class="zone zone-ok" :d="dial.zoneOk" />
              <path class="zone zone-warn" :d="dial.zoneWarn" />
              <path class="zone zone-crit" :d="dial.zoneCrit" />
              <!-- 안쪽: 빈 원호 위에 예상 피크만큼 단계 색으로 차오름 -->
              <path class="track" :d="dial.track" />
              <path v-if="dial.fill" class="fill" :d="dial.fill" />
              <!-- 목표 피크 눈금 -->
              <line class="tick" v-bind="dial.tick" />
              <!-- 예상 위치 표시 (바깥 테 바로 밖의 삼각형) -->
              <polygon
                class="marker"
                points="0,0 -6,-9 6,-9"
                :transform="`translate(${dial.marker.x} ${dial.marker.y}) rotate(${dial.marker.angle - 90})`"
              />
              <!-- 가운데: 예상 피크(큰 숫자)와 목표 대비 % -->
              <text class="dial-caption" :x="CX" :y="CY - 24">예상 피크</text>
              <text class="dial-value" :x="CX" :y="CY + 8">
                {{ formatKw(status.predictedKw) }}
                <tspan class="dial-unit" dx="3">kW</tspan>
              </text>
              <text class="dial-caption" :x="CX" :y="CY + 30">목표 대비 {{ dial.percent }}%</text>
              <!-- 양 끝 값 -->
              <text class="dial-end" :x="dial.minLabel.x" :y="dial.minLabel.y + 22">
                {{ formatInteger(gaugeMin) }}
              </text>
              <text class="dial-end" :x="dial.maxLabel.x" :y="dial.maxLabel.y + 22">
                {{ formatKw(gaugeMax) }}
              </text>
            </svg>
            <p class="dial-note num">
              <span class="tick-mark" aria-hidden="true"></span> 목표
              {{ formatKw(status.targetPeakKw) }} ·
              <span class="warn-text">▲ 주의 {{ formatKw(status.warnKw) }}부터</span>
            </p>
          </div>
          <p v-else class="empty">값을 기다리는 중입니다.</p>

          <div class="info">
            <!-- 한 줄 판정과 단계 -->
            <div class="verdict-row">
              <p class="chip">
                <span aria-hidden="true">{{ stage.shape }}</span> {{ stage.label }}
              </p>
              <p class="verdict">{{ verdict }}</p>
            </div>
            <dl class="stats">
              <div>
                <dt>목표 피크</dt>
                <dd>
                  <span class="num">{{ formatKw(status?.targetPeakKw) }}</span> <small>kW</small>
                </dd>
              </div>
              <div>
                <dt>예상 피크</dt>
                <dd class="state">
                  <span class="num">{{ formatKw(status?.predictedKw) }}</span> <small>kW</small>
                </dd>
              </div>
              <div>
                <dt>추가 방전</dt>
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
              <div class="progress-track" aria-hidden="true">
                <span class="bar" :style="{ width: `${elapsedPercent}%` }"></span>
              </div>
            </div>
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
/* 통신 단절·불러오는 중: 회색으로 멈춘 값임을 보여 줌 */
.window.lost {
  --state: var(--lost);
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
  font-size: 15px;
  color: var(--mute);
}
.body {
  display: flex;
  flex: 1;
  gap: 32px;
}

.verdict-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.verdict {
  font-size: 17px;
  font-weight: 700;
  /* 줄이 바뀔 때 낱말 중간에서 끊기지 않게 */
  word-break: keep-all;
}
.chip {
  flex: none;
  padding: 4px 10px;
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

/* 계기판 + 숫자를 가로로 나란히 (카드 높이가 늘지 않게) */
/* 계기판을 위에 크게, 판정·숫자·구간 진행을 아래에 (카드가 세로로 긴 칸을 혼자 차지함, 2026-11-18) */
.meter {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: space-evenly;
  gap: 16px;
}
.dial {
  flex: none;
  width: 420px;
  max-width: 100%;
}
.dial svg {
  display: block;
  width: 100%;
  height: auto;
}
/* 바깥 테 구간: 상태색을 옅게 */
.zone {
  fill: none;
  stroke-width: 12;
}
.zone-ok {
  stroke: var(--tint-ok);
}
.zone-warn {
  stroke: var(--tint-warn);
}
.zone-crit {
  stroke: var(--tint-crit);
}
/* 안쪽 원호: 빈 길 + 예상 피크만큼 단계 색 */
.track {
  fill: none;
  stroke: var(--line);
  stroke-width: 18;
}
.fill {
  fill: none;
  stroke: var(--state);
  stroke-width: 18;
  transition: d 0.4s ease;
}
.tick {
  stroke: var(--ink);
  stroke-width: 3;
}
.marker {
  fill: var(--state);
}
.dial-value {
  font-size: 30px;
  font-weight: 700;
  fill: var(--state);
  text-anchor: middle;
  font-variant-numeric: tabular-nums;
}
.dial-unit {
  font-size: 13px;
  font-weight: 400;
  fill: var(--ink-2);
}
.dial-caption {
  font-size: 13px;
  fill: var(--mute);
  text-anchor: middle;
}
.dial-end {
  font-size: 12px;
  fill: var(--mute);
  text-anchor: middle;
  font-variant-numeric: tabular-nums;
}
.dial-note {
  margin-top: 2px;
  font-size: 14px;
  color: var(--ink-2);
  text-align: center;
  white-space: nowrap;
}
.tick-mark {
  display: inline-block;
  width: 3px;
  height: 12px;
  margin-right: 2px;
  background: var(--ink);
  vertical-align: -1px;
}
.warn-text {
  color: var(--warn);
}
/* 계기판 옆: 숫자 네 개와 구간 진행 */
.info {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  min-width: 0;
}

/* 숫자 네 개 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 16px;
}
.stats dt {
  font-size: 14px;
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
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}

/* 구간 진행 */
.progress p {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--mute);
}
.progress-track {
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
  .dial {
    width: 380px;
  }
  .stats dd {
    font-size: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    transition: none;
  }
}
</style>
