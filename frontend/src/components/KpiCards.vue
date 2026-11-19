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
// ring이 true면 안쪽 굵은 반원이 아니라 바깥쪽 가는 테(정상·주의·경고 구간 표시)에 그림
// 예전에는 반원 전체를 그리고 점선 간격(stroke-dasharray)으로 일부만 보이게 했는데,
// 브라우저·확대 배율에 따라 칠하기 시작하는 경계가 살짝 어긋나 비뚤어 보여서
// 구간마다 시작·끝 각도를 계산해 그 부분의 호만 직접 그림 (경계가 항상 반지름 방향의 곧은 선)
function segment(from, to, className, ring = false) {
  const start = clamp(from / 100)
  const end = clamp(to / 100)
  // 칠할 길이가 없으면 그리지 않음 (아래 template에서 d가 없는 구간은 건너뜀)
  if (end - start <= 0) return { d: '', className, ring }
  const radius = ring ? 59.5 : 50 // 바깥 테 / 안쪽 게이지 반지름 (중심 60,62)
  const point = (ratio) => {
    const angle = Math.PI * (1 - ratio) // 0이면 왼쪽 끝, 1이면 오른쪽 끝
    return `${(60 + radius * Math.cos(angle)).toFixed(3)} ${(62 - radius * Math.sin(angle)).toFixed(3)}`
  }
  // A 명령: 반지름, 회전 0, 작은 호(0), 시계 방향(1)으로 시작점 → 끝점
  return { d: `M${point(start)} A${radius} ${radius} 0 0 1 ${point(end)}`, className, ring }
}

// 값이 어느 구간에 있는지에 따라 차오르는 게이지 색을 고름 (바깥 테와 같은 색)
function zoneClass(value, warnAt, critAt) {
  if (value >= critAt) return 'fill-crit'
  if (value >= warnAt) return 'fill-warn'
  return 'fill-ok'
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
  // 배터리 잔량이 있는 구간 (하한 아래 경고 · 상한 위 주의 · 그 사이 정상)
  const socClass = !s
    ? ''
    : s.socPercent < (socLow ?? 0)
      ? 'fill-crit'
      : s.socPercent > (socHigh ?? 100)
        ? 'fill-warn'
        : 'fill-ok'
  // 한전 구매전력이 있는 구간
  const gridClass = s ? zoneClass(s.gridKw, s.warnKw, target) : ''

  const essRatio = s && maxPowerKw ? 0.5 + ((charging ? -essKw : essKw) / maxPowerKw) * 0.5 : 0.5

  return [
    {
      key: 'load',
      dot: 'mix', // 계기판이 한전 · 태양광 · ESS 세 색으로 칠해지므로 세 색을 나란히
      name: '공장 소비전력',
      value: formatKw(s?.loadKw),
      unit: 'kW',
      // 아랫줄: 각 공급원이 계기판에서 어떤 색인지 작은 네모로 함께 보여 줌
      legend: s
        ? [
            {
              label: '한전',
              percent: Math.round(s.sourceRatio.gridPercent),
              className: 'fill-grid',
            },
            {
              label: '태양광',
              percent: Math.round(s.sourceRatio.solarPercent),
              className: 'fill-solar',
            },
            { label: 'ESS', percent: Math.round(s.sourceRatio.essPercent), className: 'fill-ess' },
          ]
        : null,
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
      dot: gridClass, // 지금 구간 색 (정상 초록 · 주의 주황 · 목표 초과 빨강)
      name: '한전 구매전력',
      value: formatKw(s?.gridKw),
      unit: 'kW',
      // 이 카드는 지금 이 순간의 값이라, 순간적으로 목표를 넘어도 피크(15분 평균) 경고는 아님
      // 그래서 큰 숫자는 그대로 두고, 아랫줄에만 도형과 글자로 "순간값"이라고 알림
      sub: !s
        ? ''
        : over
          ? '▲ 순간값이 목표 초과\n(피크 판정은 15분 평균)' // \n 자리에서 줄을 바꿈 (아래 white-space: pre-line)
          : `목표 ${formatKw(target)} kW의 ${formatOneDecimal(gridPercent.value)}%`,
      over,
      min: '0',
      max: gridMax ? formatKw(gridMax) : '',
      // 바깥 가는 테: 정상 · 주의 · 경고 구간 / 안쪽: 바늘 위치까지 차오르는 게이지 (색은 지금 구간 색)
      segments: s
        ? [
            segment(0, warnAt, 'ring-ok', true),
            segment(warnAt, targetAt, 'ring-warn', true),
            segment(targetAt, 100, 'ring-crit', true),
            segment(0, 100, 'track'),
            segment(0, (s.gridKw / gridMax) * 100, gridClass),
          ]
        : [segment(0, 100, 'track')],
      needle: s && gridMax ? needle(s.gridKw / gridMax) : null,
    },
    {
      key: 'solar',
      dot: 'fill-solar',
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
      // 충전 파랑 · 방전 주황 · 대기 회색 (계기판 안쪽 게이지와 같은 색)
      dot: !s
        ? ''
        : charging
          ? 'fill-charge'
          : s.pcsStatus === 'DISCHARGING'
            ? 'fill-discharge'
            : 'standby',
      name: 'ESS 충·방전',
      // 숫자 앞에 지금 무엇을 하는 중인지 적음 (방전 500 kW / 충전 300 kW / 대기 0.0 kW)
      prefix: s ? (PCS_SHORT[s.pcsStatus] ?? '') : '',
      prefixIcon: s?.pcsStatus ?? '', // 글자 앞 아이콘 (대기 · 방전 · 충전)
      value: formatKw(essKw),
      unit: 'kW',
      // 아랫줄을 글자 3개로 나눠 '|'를 카드(=계기판) 가운데에 고정, 양옆에 충전·방전 안내
      sides: ['왼쪽 충전', '오른쪽 방전'],
      min: maxPowerKw ? `충전 ${formatKw(maxPowerKw)}` : '충전',
      max: maxPowerKw ? `방전 ${formatKw(maxPowerKw)}` : '방전',
      // 바깥 가는 테: 가운데(대기)를 기준으로 왼쪽 충전 · 오른쪽 방전을 다른 색으로
      // 안쪽: 가운데에서 바늘까지 차오름 (충전이면 충전 색, 방전이면 방전 색)
      segments: [
        segment(0, 49.5, 'ring-charge', true),
        segment(50.5, 100, 'ring-discharge', true),
        segment(0, 100, 'track'),
        segment(
          Math.min(50, essRatio * 100),
          Math.max(50, essRatio * 100),
          charging ? 'fill-charge' : 'fill-discharge',
        ),
      ],
      needle: s && maxPowerKw ? needle(essRatio) : null,
    },
    {
      key: 'soc',
      dot: socClass, // 지금 구간 색
      name: '배터리 잔량 (SOC)',
      value: formatOneDecimal(s?.socPercent),
      unit: '%',
      sub: s ? `${formatOneDecimal(s.batteryTempC)}℃` : '',
      // 아랫줄: "배터리실" → 온도계 아이콘 → 온도 순서로 보여 줌
      subLabel: '배터리실',
      thermometer: true,
      min: '0',
      max: '100',
      // 바깥 가는 테: 하한 아래 경고 · 하한~상한 정상 · 상한 위 주의 (설정값)
      // 안쪽: 잔량만큼 차오르는 게이지 (색은 지금 구간 색)
      segments: [
        segment(0, socLow ?? 0, 'ring-crit', true),
        segment(socLow ?? 0, socHigh ?? 100, 'ring-ok', true),
        segment(socHigh ?? 100, 100, 'ring-warn', true),
        segment(0, 100, 'track'),
        ...(s ? [segment(0, s.socPercent, socClass)] : []),
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
          <!-- 이름 앞 색: 이 카드 계기판에서 지금 차오른 게이지의 색과 같음 -->
          <span class="dot" :class="kpi.dot" aria-hidden="true"></span>
          <h2 class="kpi-name">{{ kpi.name }}</h2>
        </div>
        <!-- 반원 계기판: 색 구간 + 바늘 (값을 못 받았으면 바늘 없이 회색 반원만) -->
        <svg class="gauge" viewBox="0 0 120 72" aria-hidden="true">
          <!-- 구간마다 그 부분의 호만 그림 (길이가 0인 구간은 d가 비어 있어 건너뜀) -->
          <template v-for="(item, index) in kpi.segments" :key="index">
            <path
              v-if="item.d"
              class="seg"
              :class="[item.className, { ring: item.ring }]"
              :d="item.d"
            />
          </template>
          <template v-if="kpi.needle">
            <line class="needle" x1="60" y1="62" :x2="kpi.needle.x" :y2="kpi.needle.y" />
            <circle class="hub" cx="60" cy="62" r="3.5" />
          </template>
          <text class="scale" x="4" y="71">{{ kpi.min }}</text>
          <text class="scale" x="116" y="71" text-anchor="end">{{ kpi.max }}</text>
        </svg>
        <p class="kpi-value">
          <span v-if="kpi.prefix" class="kpi-prefix" :class="kpi.prefixIcon.toLowerCase()">
            <!-- 대기: 일시정지 / 방전: 번개 / 충전: 플러그 (SVG 직접 그림, 색은 글자색) -->
            <svg class="prefix-icon" viewBox="0 0 24 24" aria-hidden="true">
              <template v-if="kpi.prefixIcon === 'DISCHARGING'">
                <path class="solid" d="M13 2 L4 14 H11 L9 22 L20 9 H13 Z" />
              </template>
              <template v-else-if="kpi.prefixIcon === 'CHARGING'">
                <path d="M9 2 V7 M15 2 V7 M6 7 H18 V11 a6 6 0 0 1 -12 0 Z M12 17 V22" />
              </template>
              <template v-else>
                <circle cx="12" cy="12" r="9" />
                <path d="M10 8.5 V15.5 M14 8.5 V15.5" />
              </template>
            </svg>
            {{ kpi.prefix }}
          </span>
          <span class="num">{{ kpi.value }}</span>
          <span class="kpi-unit">{{ kpi.unit }}</span>
        </p>
        <!-- 아랫줄 (높이를 두 줄로 고정해서 글자가 바뀌어도 카드 높이가 변하지 않음) -->
        <p v-if="kpi.legend" class="kpi-sub num">
          <span v-for="item in kpi.legend" :key="item.label" class="legend-item">
            <span class="swatch" :class="item.className" aria-hidden="true"></span>
            {{ item.label }} {{ item.percent }}%
          </span>
        </p>
        <!-- ESS: '왼쪽 충전' | '오른쪽 방전'. 양쪽 칸 폭을 같게(1fr) 두어 '|'가 언제나 정확히 가운데 -->
        <p v-else-if="kpi.sides" class="kpi-sub kpi-sides">
          <span class="side-left">{{ kpi.sides[0] }}</span>
          <span class="side-bar" aria-hidden="true">|</span>
          <span class="side-right">{{ kpi.sides[1] }}</span>
        </p>
        <p v-else class="kpi-sub num" :class="{ over: kpi.over }">
          <template v-if="kpi.subLabel && kpi.sub">{{ kpi.subLabel }}</template>
          <!-- 온도계 아이콘 (SVG 직접 그림) -->
          <svg
            v-if="kpi.thermometer && kpi.sub"
            class="thermo"
            viewBox="0 0 16 24"
            aria-hidden="true"
          >
            <path d="M6 3.5 a2 2 0 0 1 4 0 V14.5 a4 4 0 1 1 -4 0 Z" />
            <circle class="thermo-fill" cx="8" cy="17.5" r="2.2" />
            <line class="thermo-fill-line" x1="8" y1="8" x2="8" y2="16" />
          </svg>
          {{ kpi.sub }}
        </p>
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

/* 이름 앞 색 네모: 계기판 게이지 색과 같은 변수 (값을 아직 못 받았으면 회색) */
.dot {
  flex: none;
  width: 10px;
  height: 10px;
  background: var(--line-strong);
}
.dot.mix {
  background: linear-gradient(
    to right,
    var(--gauge-grid) 0 34%,
    var(--gauge-solar) 34% 67%,
    var(--gauge-ess) 67% 100%
  );
}
.dot.fill-ok {
  background: var(--gauge-ok);
}
.dot.fill-warn {
  background: var(--gauge-warn);
}
.dot.fill-crit {
  background: var(--gauge-crit);
}
.dot.fill-solar {
  background: var(--gauge-solar);
}
.dot.fill-charge {
  background: var(--gauge-charge);
}
.dot.fill-discharge {
  background: var(--gauge-discharge);
}
.dot.standby {
  background: var(--mute);
}
.stale .dot {
  background: var(--lost);
}
.kpi {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 0;
  padding: 7px 10px 8px;
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
  overflow: visible; /* 두꺼운 바깥 테가 그림 가장자리에서 잘리지 않게 */
  width: 100%;
  max-width: 150px;
  margin-top: 2px;
}
/* 값이 바뀌면 원호가 바로 그 자리로 감 (움직이는 효과 없음, 2026-11-18)
   색은 color에 두고 stroke와 빛 번짐(drop-shadow)이 같은 색을 따라가게 함 */
.seg {
  fill: none;
  stroke: currentColor;
  stroke-width: 10;
  stroke-linecap: butt;
}
/* 바깥 가는 테 (정상 · 주의 · 경고 구간 표시) */
.seg.ring {
  stroke-width: 4.5; /* 바깥 구간 테: 잘 보이게 두껍게, 안쪽 게이지와는 살짝 띄움 */
}
.seg.ring-ok {
  color: var(--gauge-ok);
}
.seg.ring-warn {
  color: var(--gauge-warn);
}
.seg.ring-crit {
  color: var(--gauge-crit);
}
/* ESS: 왼쪽 충전 · 오른쪽 방전 색 */
.seg.ring-charge,
.seg.fill-charge {
  color: var(--gauge-charge);
}
.seg.ring-discharge,
.seg.fill-discharge {
  color: var(--gauge-discharge);
}
/* 차오르는 게이지 색 = 지금 값이 있는 구간의 색 */
.seg.fill-ok {
  color: var(--gauge-ok);
}
.seg.fill-warn {
  color: var(--gauge-warn);
}
.seg.fill-crit {
  color: var(--gauge-crit);
}
.seg.track {
  color: var(--line);
}
.seg.fill-grid {
  color: var(--gauge-grid);
}
.seg.fill-solar {
  color: var(--gauge-solar);
}
.seg.fill-ess {
  color: var(--gauge-ess);
}
/* 다크 모드에서만 채워진 원호가 빛나 보이게 (라이트는 --gauge-glow가 0이라 그대로). 바늘은 빛나지 않음 */
.seg[class*='fill-'] {
  filter: drop-shadow(0 0 var(--gauge-glow) currentColor);
}
.needle {
  color: var(--gauge-needle);
  stroke: currentColor;
  stroke-width: 2.5;
  stroke-linecap: round;
}
.hub {
  fill: var(--gauge-needle);
}
.scale {
  font-size: 7px;
  fill: var(--mute);
}

.kpi-value {
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: 6px;
  margin-top: -4px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
/* 숫자 칸 폭을 5글자(예: 2,046)로 고정 → 자릿수가 바뀌어도 단위와 숫자 위치가 흔들리지 않음
   숫자는 오른쪽(단위 쪽)에 붙여 단위와 떨어져 보이지 않게 함 */
.kpi-value .num {
  display: inline-block;
  min-width: 5ch;
  text-align: right;
}
.kpi-prefix {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 14px;
}
.kpi-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-2);
}
.kpi-sub {
  align-self: stretch;
  white-space: pre-line; /* 글 안의 \n 에서 줄을 바꿈 */
  height: 26px;
  overflow: hidden;
  font-size: 11px;
  color: var(--mute);
  text-align: center;
  /* 좁은 화면에서는 잘라 내지 않고 두 줄로 내려 씀 (공급원 비율이 길어서) */
  word-break: keep-all;
}
/* ESS 글자 앞 아이콘 */
.prefix-icon {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.prefix-icon .solid {
  fill: currentColor;
  stroke: none;
}
.kpi-prefix.discharging .prefix-icon {
  color: var(--gauge-discharge);
}
.kpi-prefix.charging .prefix-icon {
  color: var(--gauge-charge);
}
.kpi-prefix.standby .prefix-icon {
  color: var(--mute);
}
/* 공장 소비전력 아랫줄: 공급원마다 계기판 색 네모 */
.legend-item {
  display: inline-flex; /* 네모 + 이름 + % 를 한 덩어리로 (중간에서 줄이 바뀌지 않음) */
  align-items: center;
  white-space: nowrap;
}
.legend-item + .legend-item {
  margin-left: 10px; /* 항목 사이는 / 대신 공백으로 구분 */
}
.swatch {
  flex: none;
  width: 9px;
  height: 9px;
  margin-right: 3px;
}
.swatch.fill-grid {
  background: var(--gauge-grid);
}
.swatch.fill-solar {
  background: var(--gauge-solar);
}
.swatch.fill-ess {
  background: var(--gauge-ess);
}
/* 온도계 아이콘 */
.thermo {
  width: 10px;
  height: 15px;
  margin: 0 2px 0 4px;
  vertical-align: -3px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
}
.thermo-fill {
  fill: var(--crit);
  stroke: none;
}
.thermo-fill-line {
  stroke: var(--crit);
  stroke-width: 2.4;
  stroke-linecap: round;
}
.kpi-sides {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: start;
  column-gap: 6px;
}
.side-left {
  text-align: right;
}
.side-right {
  text-align: left;
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
  color: var(--lost);
}
.stale .hub {
  fill: var(--lost);
}
</style>
