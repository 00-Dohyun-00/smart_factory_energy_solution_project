<!--
  예상 피크 카드 (모니터링 > 통합)
  왼쪽: 단계·짧은 판정, 예상 피크(큰 숫자), 목표 피크·현재 누적·추가 방전
  오른쪽: 15분 흐름 그래프 (구간 시작부터 지금까지 쌓인 값 → 끝까지 예상, 목표선)

  2026-11-18 UI 피드백: "현재 15분 피크 구간"이 와닿지 않고 가로 막대 게이지가 별로라는 의견으로
  제목을 "예상 피크"로 바꾸고 막대 대신 흐름 그래프를 넣음. (이전 막대 모양은 커밋 dd472f1에 있음)

  그래프의 값(windowPoints), 예상, 목표, 단계는 모두 백엔드가 준 값이고 여기서는 그리기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import { useThemeStore } from '@/stores/useThemeStore'
import Chart from '@/components/Chart.vue'
import { formatHourMinute, formatInteger, formatKw, formatRemaining } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 서버 연결이 끊겨 마지막 값을 보여 주는 중이면 true
  stale: { type: Boolean, default: false },
})

const WINDOW_MINUTES = 15

// 그래프(ECharts)는 CSS 변수를 못 읽으므로, 테마 스토어가 global.css에서 읽어 둔 지금 테마 색을 씀
// (라이트·다크를 바꾸면 이 색이 바뀌고 그래프도 다시 그려짐)
const themeStore = useThemeStore()
const COLORS = themeStore.colors
// 그래프 툴팁도 지금 테마 색으로
const tooltipColors = () => ({
  backgroundColor: COLORS.card,
  borderColor: COLORS.line,
  textStyle: { color: COLORS.ink },
})

const STAGE_INFO = {
  NORMAL: { shape: '●', label: '정상', className: 'ok', color: 'ok' }, // 색 이름 (테마 스토어 colors의 키)
  WARNING: { shape: '▲', label: '주의', className: 'warn', color: 'warn' },
  CRITICAL: { shape: '■', label: '경고', className: 'crit', color: 'crit' },
  LOST: { shape: '◆', label: '통신 단절', className: 'lost', color: 'lost' },
  WAITING: { shape: '○', label: '불러오는 중', className: 'lost', color: 'lost' },
}

// 화면에 보여 줄 단계
// - 연결이 끊겼으면 통신 단절
// - 아직 첫 값을 받기 전이면 "불러오는 중" (처음 열었을 때 통신 단절로 잘못 보이지 않게)
const stage = computed(() => {
  if (props.stale) return STAGE_INFO.LOST
  if (!props.status) return STAGE_INFO.WAITING
  return STAGE_INFO[props.status.peakStatus]
})

// 제목 오른쪽: "14:00 – 14:15 / 남은 6분 0초"
const windowText = computed(() => {
  const status = props.status
  const range = `${formatHourMinute(status?.windowStart)} – ${formatHourMinute(status?.windowEnd)}`
  return status ? `${range} / 남은 ${formatRemaining(status.remainingSeconds)}` : range
})

// ---- 남은 시간 스톱워치 아이콘 ----
// 안쪽 부채꼴이 15분 구간 중 남은 비율만큼 차 있다가, 시간이 지나며 줄어듦
// 남은 시간은 서버가 준 remainingSeconds를 그대로 씀 (브라우저 시계로 따로 세지 않음)
const TIMER_R = 4.25 // 부채꼴 반지름 (아래 SVG 좌표 기준. 선 굵기 8.5와 합쳐 시계 테두리(반지름 8.5)까지 칠해짐)
const TIMER_LENGTH = 2 * Math.PI * TIMER_R // 부채꼴을 굵은 선(stroke)으로 그릴 때 한 바퀴 길이
const remainingRatio = computed(() => {
  const seconds = props.status?.remainingSeconds
  if (seconds == null) return 0
  return Math.min(1, Math.max(0, seconds / (WINDOW_MINUTES * 60)))
})
// stroke-dasharray: 남은 비율만큼만 선을 그리고 나머지는 비움 → 부채꼴이 남은 시간만큼만 보임
const timerDash = computed(
  () => `${(remainingRatio.value * TIMER_LENGTH).toFixed(2)} ${TIMER_LENGTH.toFixed(2)}`,
)

// 구간 시작부터 지금까지 쌓인 값 (백엔드의 windowPoints, 0에서 시작해 15분 끝에 그 구간의 피크가 됨)
const points = computed(() => props.status?.windowPoints ?? [])
// 지금 시점의 누적값 = 마지막 점
const currentPoint = computed(() => points.value[points.value.length - 1] ?? null)

// 구간 시작으로부터 n분 뒤의 시각 "14:05" (가로축 눈금과 툴팁용)
function clockAfter(minutes) {
  const start = props.status.windowStart
  const total = Number(start.slice(11, 13)) * 60 + Number(start.slice(14, 16)) + Math.round(minutes)
  const hour = String(Math.floor(total / 60) % 24).padStart(2, '0')
  const minute = String(total % 60).padStart(2, '0')
  return `${hour}:${minute}`
}

// ---- 15분 흐름 그래프 ----
const chartOption = computed(() => {
  const status = props.status
  const now = currentPoint.value
  if (!status || !now) return null

  const target = status.targetPeakKw
  const stateColor = COLORS[stage.value.color]
  // 연결이 끊기면 지금까지의 선도 회색으로 (멈춘 값임을 보여 줌)
  const actualColor = props.stale ? COLORS[stage.value.color] : COLORS.ink

  // 세로축 끝: 목표·예상 중 큰 값보다 조금 위 (100 단위, 표시용)
  const top = Math.max(target, status.predictedKw)
  const yMax = Math.ceil((top * 1.08) / 100) * 100

  // 예상 값이 목표선 가까이(세로축의 8% 이내)이면 "예상" 이름표를 점선 아래쪽에 붙임
  // (위쪽에 붙이면 목표선이 글자를 가로질러 숫자가 안 읽힘. 점선은 아래에서 올라오므로 아래쪽이 목표선과 먼 쪽)
  const labelBelow = Math.abs(status.predictedKw - target) < yMax * 0.08

  const markLines = [
    {
      yAxis: target,
      // 목표 피크선: 지금까지 쌓인 값(먹색 실선)과 섞이지 않게 전용 자주색 (2026-11-18)
      lineStyle: { color: COLORS.target, width: 1.5, type: 'solid' },
      label: {
        formatter: `목표 피크 ${formatKw(target)} kW`,
        position: 'insideStartTop',
        color: COLORS.target,
        fontWeight: 700,
        backgroundColor: COLORS.card,
        padding: [2, 4],
        borderRadius: 3,
      },
    },
  ]

  return {
    animation: false,
    grid: { left: 4, right: 20, top: 30, bottom: 4, containLabel: true },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 16,
      itemHeight: 8,
      textStyle: { color: COLORS.mute, fontSize: 12 },
      data: ['현재 누적', '끝까지 예상'],
    },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      formatter: (params) => {
        const rows = params.map(
          (item) => `${item.marker}${item.seriesName} <b>${formatKw(item.value[1])} kW</b>`,
        )
        return [clockAfter(params[0].value[0]), ...rows].join('<br/>')
      },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: WINDOW_MINUTES,
      interval: 5,
      axisLine: { lineStyle: { color: COLORS.lineStrong } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: COLORS.faint, formatter: (minute) => clockAfter(minute) },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: yMax,
      splitNumber: 4,
      axisLabel: { color: COLORS.faint, formatter: (value) => formatInteger(value) },
      splitLine: { lineStyle: { color: COLORS.bg } },
    },
    series: [
      {
        name: '현재 누적',
        type: 'line',
        data: points.value.map((point) => [point.minute, point.demandKw]),
        showSymbol: false,
        lineStyle: { color: actualColor, width: 2 },
        itemStyle: { color: actualColor },
        // 아직 오지 않은 시간은 옅게 칠함
        markArea: {
          silent: true,
          itemStyle: { color: COLORS.hover },
          data: [[{ xAxis: now.minute }, { xAxis: WINDOW_MINUTES }]],
        },
        markLine: { silent: true, symbol: 'none', data: markLines },
      },
      {
        // 지금 점에서 구간 끝의 예상 피크까지 점선으로 이음
        name: '끝까지 예상',
        type: 'line',
        data: [
          { value: [now.minute, now.demandKw], symbol: 'none' },
          { value: [WINDOW_MINUTES, status.predictedKw], symbol: 'circle', symbolSize: 8 },
        ],
        lineStyle: { color: stateColor, width: 2, type: 'dashed' },
        itemStyle: { color: stateColor },
        // "예상 1,346 kW" 이름표를 점선 기울기에 맞춰 선 위에 붙임.
        // ECharts의 markLine 이름표는 선 방향대로 기울어지므로, 같은 두 점을 잇는 보이지 않는 선을 하나 더 두고 이름표만 씀
        // (선을 opacity 0으로 숨기면 이름표까지 사라져서 투명한 색을 씀)
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: 'rgba(0, 0, 0, 0)' },
          label: {
            position: labelBelow ? 'insideEndBottom' : 'insideEndTop',
            formatter: `예상 ${formatKw(status.predictedKw)} kW`,
            color: stateColor,
            fontWeight: 700,
            // 글자 뒤를 카드 바탕색으로 칠해, 선이 지나가도 숫자가 읽히게 함
            backgroundColor: COLORS.card,
            padding: [2, 4],
            borderRadius: 3,
          },
          data: [
            [
              { coord: [now.minute, now.demandKw] },
              { coord: [WINDOW_MINUTES, status.predictedKw] },
            ],
          ],
        },
      },
    ],
  }
})
</script>

<template>
  <section class="card window" :class="stage.className">
    <div class="head">
      <h2 class="card-title">예상 피크</h2>
      <span class="range num">{{ windowText }}</span>
      <!-- 스톱워치: 안쪽 부채꼴이 남은 시간만큼 차 있고 시간이 지나며 줄어듦 -->
      <svg
        v-if="status"
        class="timer"
        viewBox="0 0 24 24"
        role="img"
        :aria-label="`남은 시간 ${Math.round(remainingRatio * 100)}%`"
      >
        <!-- 위쪽 꼭지(버튼)와 오른쪽 위 작은 단추 -->
        <rect class="timer-solid" x="10" y="1" width="4" height="2.5" rx="1" />
        <line class="timer-line" x1="18.6" y1="6.4" x2="20.2" y2="4.8" />
        <!-- 남은 시간 부채꼴: 반지름 4.25짜리 원을 굵기 8.5의 선으로 그리면 꽉 찬 원이 되고,
             dasharray로 남은 비율만큼만 그려 부채꼴을 만듦. 12시 방향부터 시계 방향 -->
        <circle
          class="timer-pie"
          cx="12"
          cy="14"
          :r="TIMER_R"
          :stroke-dasharray="timerDash"
          transform="rotate(-90 12 14)"
        />
        <!-- 시계 테두리: 부채꼴보다 나중에 그려 부채꼴 위에 오게 함 -->
        <circle class="timer-line" cx="12" cy="14" r="8.5" />
      </svg>
    </div>

    <div class="body">
      <!-- 왼쪽: 단계와 숫자 -->
      <div class="left">
        <p class="chip">
          <span aria-hidden="true">{{ stage.shape }}</span> {{ stage.label }}
        </p>
        <p class="big">
          <span class="num">{{ formatKw(status?.predictedKw) }}</span>
          <span class="unit">kW</span>
        </p>

        <dl class="stats">
          <div>
            <dt>목표 피크</dt>
            <dd>
              <span class="num">{{ formatKw(status?.targetPeakKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt
              title="구간 시작부터 지금까지 쓴 전력을 15분 평균으로 바꾼 값. 15분이 끝날 때의 값이 그 구간의 피크입니다."
            >
              현재 누적
            </dt>
            <dd>
              <span class="num">{{ formatKw(currentPoint?.demandKw) }}</span> <small>kW</small>
            </dd>
          </div>
          <div>
            <dt>추가 방전</dt>
            <dd>
              <span class="num">{{ formatKw(status?.defenseKw) }}</span> <small>kW</small>
            </dd>
          </div>
        </dl>
      </div>

      <!-- 오른쪽: 15분 흐름 그래프 -->
      <div class="right">
        <Chart v-if="chartOption" :option="chartOption" :height="230" />
        <p v-else class="empty">
          {{ status ? '구간 흐름 값이 없습니다.' : '값을 기다리는 중입니다.' }}
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.window {
  display: flex;
  flex-direction: column;
  gap: 10px;
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
  font-size: 13px;
  color: var(--mute);
}
/* 스톱워치 아이콘: 테두리는 먹색, 남은 시간 부채꼴은 단계 색 */
.timer {
  flex: none;
  align-self: center;
  width: 22px;
  height: 22px;
  margin-left: 6px;
}
.timer-line {
  fill: none;
  stroke: var(--ink-2);
  stroke-width: 1.8;
  stroke-linecap: round;
}
.timer-solid {
  fill: var(--ink-2);
}
.timer-pie {
  fill: none;
  stroke: var(--state);
  stroke-width: 8.5;
  /* 값이 바뀔 때 부채꼴이 부드럽게 줄어들게 함 */
  transition: stroke-dasharray 1s linear;
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
  width: 250px;
}
/* 단계 표시: 테두리만 둔 작은 표시 (큰 숫자보다 눈에 덜 띄게) */
.chip {
  align-self: flex-start;
  padding: 1px 8px;
  border: 1px solid var(--state);
  border-radius: 3px;
  font-size: 13px;
  font-weight: 700;
  color: var(--state);
  white-space: nowrap;
}
.big {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 6px;
  font-size: 56px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--state);
}
.unit {
  font-size: 16px;
  font-weight: 400;
  color: var(--mute);
}

.stats {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.stats div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.stats dt {
  font-size: 13px;
  color: var(--mute);
  white-space: nowrap;
}
.stats dd {
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
}
.stats small {
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
}

/* 오른쪽 */
.right {
  flex: 1;
  min-width: 0;
}
.empty {
  padding-top: 40px;
  color: var(--mute);
}

/* 화면이 좁으면 왼쪽 칸과 큰 숫자를 줄여 그래프 자리를 남김 */
@media (max-width: 1600px) {
  .body {
    gap: 20px;
  }
  .left {
    width: 210px;
  }
  .big {
    font-size: 46px;
  }
  .stats dd {
    font-size: 17px;
  }
}
</style>
