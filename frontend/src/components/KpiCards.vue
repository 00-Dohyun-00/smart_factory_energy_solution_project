<!--
  KPI 카드 5개: 공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량
  왼쪽부터 "공장 소비 = 한전 + 태양광 + ESS" 순서로 읽히게 놓았습니다.
  멀리서도 한눈에 보이도록 숫자 위에 반원 계기판(바늘)을 그립니다.
  모니터링의 통합 화면과 설비 모니터링 화면이 같이 씁니다.

  사용 예: <KpiCards :status="realtimeStore.status" :stale="!realtimeStore.connected" />
-->
<script setup>
import { computed } from 'vue'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatClock, formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 서버 연결이 끊겨 마지막으로 받은 값에 멈춰 있으면 true (값은 지우지 않고 회색으로 보여 줌)
  stale: { type: Boolean, default: false },
})

const settingsStore = useSettingsStore() // ESS 최대 출력, 잔량 하한·상한 (값은 공통 틀이 받아 둠)

const status = computed(() => props.status)

// ESS 카드의 숫자 앞에 붙이는 말
const PCS_SHORT = { DISCHARGING: '방전', CHARGING: '충전', STANDBY: '대기' }

// 계기판 눈금 끝(최대값)을 목표 피크의 몇 배로 잡을지. 화면에 그리는 범위일 뿐 판단 기준이 아님
const LOAD_SCALE = 1.5 // 공장 소비전력
const GRID_SCALE = 1.2 // 한전 구매전력

// 0~1 사이로 자름 (눈금 밖의 값은 바늘을 끝에 둠)
function clamp(ratio) {
  return Math.min(1, Math.max(0, ratio))
}

// 반원 위의 색 구간 하나. from·to는 반원 전체를 100으로 봤을 때의 위치(%)
function segment(from, to, className) {
  const start = clamp(from / 100) * 100
  const end = clamp(to / 100) * 100
  // 길이 0에서 시작 → start만큼 비움 → (end - start)만큼 칠함 → 나머지는 비움
  return { dash: `0 ${start} ${Math.max(0, end - start)} 200`, className }
}

// 바늘 끝 좌표. ratio 0이면 왼쪽 끝, 1이면 오른쪽 끝 (반원 중심 60,62 / 바늘 길이 40)
function needle(ratio) {
  const angle = Math.PI * (1 - clamp(ratio))
  return {
    x: (60 + 40 * Math.cos(angle)).toFixed(1),
    y: (62 - 40 * Math.sin(angle)).toFixed(1),
  }
}

// 한전 구매전력이 목표 피크의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const gridPercent = computed(() =>
  status.value ? (status.value.gridKw / status.value.targetPeakKw) * 100 : null,
)

const kpis = computed(() => {
  const s = status.value
  const target = s?.targetPeakKw
  const over = gridPercent.value > 100
  // ESS는 충전 중이면 충전 전력을, 그 밖에는 방전 전력을 보여 줌
  const charging = s?.pcsStatus === 'CHARGING'
  const essKw = s ? (charging ? s.chargeKw : s.dischargeKw) : null
  const maxPowerKw = settingsStore.values['ess.max_power_kw']
  const socLow = settingsStore.values['ess.soc_low']
  const socHigh = settingsStore.values['ess.soc_high']

  // 공장 소비전력: 눈금 끝까지 중 지금 위치를 공급원 비율대로 나눠 칠함
  const loadMax = target ? target * LOAD_SCALE : null
  const loadFill = s && loadMax ? clamp(s.loadKw / loadMax) * 100 : 0
  const gridEnd = s ? (loadFill * s.sourceRatio.gridPercent) / 100 : 0
  const solarEnd = s ? gridEnd + (loadFill * s.sourceRatio.solarPercent) / 100 : 0

  // 한전 구매전력: 정상 · 주의 · 경고 띠 (기준선은 백엔드가 준 값)
  const gridMax = target ? target * GRID_SCALE : null
  const warnAt = s && gridMax ? (s.warnKw / gridMax) * 100 : 0
  const targetAt = s && gridMax ? (target / gridMax) * 100 : 0

  // ESS: 가운데가 대기, 왼쪽이 충전, 오른쪽이 방전
  const essRatio = s && maxPowerKw ? 0.5 + ((charging ? -essKw : essKw) / maxPowerKw) * 0.5 : 0.5

  return [
    {
      key: 'load',
      name: '공장 소비전력',
      value: formatKw(s?.loadKw),
      unit: 'kW',
      sub: s
        ? `한전 ${Math.round(s.sourceRatio.gridPercent)}% / 태양광 ${Math.round(s.sourceRatio.solarPercent)}% / ESS ${Math.round(s.sourceRatio.essPercent)}%`
        : '',
      min: '0',
      max: loadMax ? formatKw(loadMax) : '',
      segments: [
        segment(0, 100, 'track'),
        segment(0, gridEnd, 'fill-grid'),
        segment(gridEnd, solarEnd, 'fill-solar'),
        segment(solarEnd, loadFill, 'fill-ess'),
      ],
      needle: s && loadMax ? needle(s.loadKw / loadMax) : null,
    },
    {
      key: 'grid',
      name: '한전 구매전력',
      value: formatKw(s?.gridKw),
      unit: 'kW',
      // 이 카드는 지금 이 순간의 값이라, 순간적으로 목표를 넘어도 피크(15분 평균) 경고는 아님
      // 그래서 큰 숫자는 그대로 두고, 아랫줄에만 도형과 글자로 "순간값"이라고 알림
      sub: !s
        ? ''
        : over
          ? '▲ 순간값이 목표 초과 (피크 판정은 15분 평균)'
          : `목표 ${formatKw(target)} kW의 ${formatOneDecimal(gridPercent.value)}%`,
      over,
      min: '0',
      max: gridMax ? formatKw(gridMax) : '',
      segments: s
        ? [
            segment(0, warnAt, 'band-ok'),
            segment(warnAt, targetAt, 'band-warn'),
            segment(targetAt, 100, 'band-crit'),
          ]
        : [segment(0, 100, 'track')],
      needle: s && gridMax ? needle(s.gridKw / gridMax) : null,
    },
    {
      key: 'solar',
      name: '태양광 발전량',
      value: formatKw(s?.solarKw),
      unit: 'kW',
      sub: s ? `공장 소비의 ${formatOneDecimal(s.sourceRatio.solarPercent)}%` : '',
      // 공장 소비 전체를 눈금 끝으로 보고 태양광이 얼마나 채웠는지 그림
      min: '0',
      max: '공장 소비',
      segments: [
        segment(0, 100, 'track'),
        segment(0, s ? s.sourceRatio.solarPercent : 0, 'fill-solar'),
      ],
      needle: s ? needle(s.sourceRatio.solarPercent / 100) : null,
    },
    {
      key: 'ess',
      name: 'ESS 충·방전',
      // 숫자 앞에 지금 무엇을 하는 중인지 적음 (방전 500 kW / 충전 300 kW / 대기 0.0 kW)
      prefix: s ? (PCS_SHORT[s.pcsStatus] ?? '') : '',
      value: formatKw(essKw),
      unit: 'kW',
      sub: '왼쪽 충전 / 오른쪽 방전',
      min: maxPowerKw ? `충전 ${formatKw(maxPowerKw)}` : '충전',
      max: maxPowerKw ? `방전 ${formatKw(maxPowerKw)}` : '방전',
      segments: [
        segment(0, 100, 'track'),
        segment(Math.min(50, essRatio * 100), Math.max(50, essRatio * 100), 'fill-ess'),
      ],
      needle: s && maxPowerKw ? needle(essRatio) : null,
    },
    {
      key: 'soc',
      name: '배터리 잔량 (SOC)',
      value: formatOneDecimal(s?.socPercent),
      unit: '%',
      sub: s ? `배터리실 ${formatOneDecimal(s.batteryTempC)}℃` : '',
      min: '0',
      max: '100',
      // 하한 아래와 상한 위는 연한 띠로 표시 (설정값)
      segments: [
        segment(0, 100, 'track'),
        segment(0, socLow ?? 0, 'band-crit'),
        segment(socHigh ?? 100, 100, 'band-warn'),
        segment(0, s ? s.socPercent : 0, 'fill-ess thin'),
      ],
      needle: s ? needle(s.socPercent / 100) : null,
    },
  ]
})
</script>

<template>
  <div class="kpi-wrap" :class="{ stale }">
    <!-- 연결이 끊기면 아래 숫자가 언제 값에서 멈춰 있는지 알려 줌 -->
    <p v-if="stale && status" class="stale-note" role="status">
      ◆ 연결이 끊겨 <span class="num">{{ formatClock(status.recordedAt) }}</span> 값에서 멈춰
      있습니다
    </p>
    <div class="kpis">
      <section v-for="kpi in kpis" :key="kpi.key" class="card kpi" :class="kpi.key">
        <div class="kpi-top">
          <span class="dot" aria-hidden="true"></span>
          <h2 class="kpi-name">{{ kpi.name }}</h2>
        </div>
        <!-- 반원 계기판: 색 구간 + 바늘 (값을 못 받았으면 바늘 없이 회색 반원만) -->
        <svg class="gauge" viewBox="0 0 120 72" aria-hidden="true">
          <path
            v-for="(item, index) in kpi.segments"
            :key="index"
            class="seg"
            :class="item.className"
            d="M10 62 A50 50 0 0 1 110 62"
            pathLength="100"
            :stroke-dasharray="item.dash"
          />
          <template v-if="kpi.needle">
            <line class="needle" x1="60" y1="62" :x2="kpi.needle.x" :y2="kpi.needle.y" />
            <circle class="hub" cx="60" cy="62" r="3.5" />
          </template>
          <text class="scale" x="4" y="71">{{ kpi.min }}</text>
          <text class="scale" x="116" y="71" text-anchor="end">{{ kpi.max }}</text>
        </svg>
        <p class="kpi-value">
          <span v-if="kpi.prefix" class="kpi-prefix">{{ kpi.prefix }}</span>
          <span class="num">{{ kpi.value }}</span>
          <span class="kpi-unit">{{ kpi.unit }}</span>
        </p>
        <p class="kpi-sub num" :class="{ over: kpi.over }">{{ kpi.sub }}</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpi-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stale-note {
  font-size: 13px;
  font-weight: 700;
  color: var(--lost);
}
.kpis {
  display: flex;
  gap: 16px;
}

/* 공급원 색 */
.dot {
  flex: none;
  width: 10px;
  height: 10px;
  background: var(--source, var(--ink));
}
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess,
.soc {
  --source: var(--src-ess);
}

.kpi {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
  padding: 10px 14px 12px;
}
/* 공장 소비전력은 특정 공급원이 아니므로 점을 먹색으로 */
.kpi.load {
  --source: var(--ink);
}
.kpi-top {
  display: flex;
  align-self: stretch;
  align-items: center;
  gap: 6px;
}
.kpi-name {
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
  white-space: nowrap;
}

/* 계기판 */
.gauge {
  width: 100%;
  max-width: 200px;
  margin-top: 2px;
}
.seg {
  fill: none;
  stroke-width: 10;
  stroke-linecap: butt;
  transition: stroke-dasharray 0.4s ease;
}
.seg.thin {
  stroke-width: 4;
}
.seg.track {
  stroke: var(--line);
}
.seg.fill-grid {
  stroke: var(--src-grid);
}
.seg.fill-solar {
  stroke: var(--src-solar);
}
.seg.fill-ess {
  stroke: var(--src-ess);
}
/* 정상 · 주의 · 경고 띠는 상태색을 연하게 깔아 바늘이 잘 보이게 함 */
.seg.band-ok {
  stroke: var(--ok);
  stroke-opacity: 0.25;
}
.seg.band-warn {
  stroke: var(--warn);
  stroke-opacity: 0.3;
}
.seg.band-crit {
  stroke: var(--crit);
  stroke-opacity: 0.25;
}
.needle {
  stroke: var(--ink);
  stroke-width: 2.5;
  stroke-linecap: round;
}
.hub {
  fill: var(--ink);
}
.scale {
  font-size: 7px;
  fill: var(--mute);
}

.kpi-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: -4px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
}
.kpi-prefix {
  font-size: 17px;
}
.kpi-unit {
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}
.kpi-sub {
  align-self: stretch;
  min-height: 18px;
  font-size: 12px;
  color: var(--mute);
  text-align: center;
  /* 좁은 화면에서는 잘라 내지 않고 두 줄로 내려 씀 (공급원 비율이 길어서) */
  word-break: keep-all;
}
.kpi-sub.over {
  font-weight: 700;
  color: var(--warn);
}

/* 연결이 끊겨 멈춘 값: 숫자와 바늘을 회색으로 */
.stale .kpi-value,
.stale .kpi-unit {
  color: var(--lost);
}
.stale .needle {
  stroke: var(--lost);
}
.stale .hub {
  fill: var(--lost);
}

@media (prefers-reduced-motion: reduce) {
  .seg {
    transition: none;
  }
}
</style>
