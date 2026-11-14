<!--
  15분 피크 (수요시한 그래프)
  최대수요전력 감시 장치(디맨드 컨트롤러)가 보여 주는 방식대로,
  15분 구간 안에서 전력이 쌓여 가는 과정을 그립니다.

  - 실적: 구간 시작부터 지금까지 쌓인 값 (15분이 끝났을 때의 값이 그 구간의 피크)
  - 예측: 지금부터 구간 끝까지의 예상. 끝점이 "예상 평균"
  - 기준선: 0에서 목표 피크까지 곧게 그은 선. 실적이 이 선보다 위에 있으면 목표를 넘는 속도
  - 목표: 목표 피크

  예상값, 목표, 상태는 모두 백엔드가 계산한 값이고 여기서는 그리기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import Chart from '@/components/Chart.vue'
import StatusChip from '@/components/StatusChip.vue'
import { formatClock, formatHourMinute, formatKw, formatRemaining } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 서버 연결이 끊겨 마지막 값을 보여 주는 중이면 true
  stale: { type: Boolean, default: false },
})

// ECharts option에는 CSS 변수를 쓸 수 없어 global.css의 색 값을 그대로 적습니다.
const COLOR = {
  ink: '#1c1c1c', // --ink
  mute: '#6e6e6a', // --mute
  line: '#e3e3df', // --line
  ok: '#2f7d4f', // --ok
  warn: '#9c5f08', // --warn
  crit: '#c8372d', // --crit
  lost: '#8f8f8a', // --lost
}

// 화면에 보여 줄 상태. 연결이 끊겼거나 값이 없으면 '통신 단절'
const shownStatus = computed(() => {
  if (props.stale || !props.status) return 'LOST'
  return props.status.peakStatus
})

const statusClass = computed(() => {
  const map = { NORMAL: 'ok', WARNING: 'warn', CRITICAL: 'crit' }
  return map[shownStatus.value] ?? 'lost'
})

const message = computed(() => {
  if (!props.status) return '값을 받지 못했습니다.'
  if (props.stale) return `마지막으로 받은 값입니다. (${formatClock(props.status.recordedAt)})`
  if (props.status.peakStatus === 'CRITICAL') {
    return `목표 초과 예상 · 방어 필요 ${formatKw(props.status.defenseKw)} kW`
  }
  if (props.status.peakStatus === 'WARNING') return '목표 피크에 가까워지고 있습니다.'
  return '목표 피크 이내로 예상됩니다.'
})

const chartOption = computed(() => {
  const status = props.status
  const stateColor = COLOR[statusClass.value]
  const actual = status.windowPoints.map((point) => [point.minute, point.demandKw])
  const lastPoint = actual[actual.length - 1]

  // 세로축 끝: 목표나 예상값보다 조금 위 (표시용 계산)
  const highest = Math.max(status.targetPeakKw, status.predictedKw)
  const axisMax = Math.ceil((highest * 1.15) / 500) * 500

  return {
    animation: false, // 5초마다 갱신되므로 깜빡임 방지
    grid: { left: 46, right: 14, top: 28, bottom: 22 },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 18,
      itemHeight: 8,
      textStyle: { color: COLOR.mute, fontSize: 12 },
    },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${formatKw(value)} kW` },
    xAxis: {
      type: 'value',
      min: 0,
      max: 15,
      interval: 5,
      axisLabel: { color: COLOR.mute, formatter: '{value}분' },
      axisLine: { lineStyle: { color: COLOR.line } },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: axisMax,
      axisLabel: { color: COLOR.mute },
      splitLine: { lineStyle: { color: COLOR.line } },
    },
    // 계열을 색만으로 구분하지 않도록 선 모양도 다르게 함 (실선 / 긴 점선 / 짧은 점선)
    series: [
      {
        name: '실적',
        type: 'line',
        showSymbol: false,
        data: actual,
        lineStyle: { color: props.stale ? COLOR.lost : COLOR.ink, width: 2.5 },
        itemStyle: { color: props.stale ? COLOR.lost : COLOR.ink },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
          label: { formatter: '목표', position: 'insideStartTop', color: COLOR.ink },
          data: [{ yAxis: status.targetPeakKw }],
        },
      },
      {
        name: '예측',
        type: 'line',
        symbol: 'circle',
        symbolSize: 7,
        data: [lastPoint, [15, status.predictedKw]],
        lineStyle: { color: stateColor, width: 2.5, type: 'dashed' },
        itemStyle: { color: stateColor },
      },
      {
        name: '기준선',
        type: 'line',
        showSymbol: false,
        data: [
          [0, 0],
          [15, status.targetPeakKw],
        ],
        lineStyle: { color: COLOR.mute, width: 1.5, type: 'dotted' },
        itemStyle: { color: COLOR.mute },
      },
    ],
  }
})
</script>

<template>
  <section class="card gauge" :class="statusClass">
    <div class="gauge-head">
      <h2 class="card-title">15분 피크</h2>
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

    <!-- 15분 구간 안에서 전력이 쌓여 가는 과정 -->
    <div class="chart" role="img" aria-label="15분 구간의 실적, 예측, 기준선 그래프">
      <Chart v-if="status" :option="chartOption" :height="150" />
      <p v-else class="chart-empty">값을 받으면 그래프가 나타납니다.</p>
    </div>

    <p class="state">
      <StatusChip :status="shownStatus" />
      <span class="state-message">{{ message }}</span>
    </p>

    <!-- 기본요금은 한 달 중 가장 높았던 15분 값에 좌우되므로 지금까지의 최대도 함께 보여 줌 -->
    <dl class="records">
      <div class="record">
        <dt>오늘 최대</dt>
        <dd>
          <span class="num">{{ formatKw(status?.todayPeakKw) }}</span>
          kW
        </dd>
      </div>
      <div class="record">
        <dt>이번 달 최대</dt>
        <dd>
          <span class="num">{{ formatKw(status?.monthPeakKw) }}</span>
          kW
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.gauge {
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 상태에 따라 바뀌는 색. 아래 .ok/.warn/.crit에서 정함 */
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

.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 150px;
  color: var(--mute);
}

.state {
  display: flex;
  align-items: center;
  gap: 10px;
}
.state-message {
  color: var(--ink-2);
}

.records {
  display: flex;
  gap: 24px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.record {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.record dt {
  font-size: 12px;
  color: var(--mute);
}
.record dd {
  font-weight: 700;
}
</style>
