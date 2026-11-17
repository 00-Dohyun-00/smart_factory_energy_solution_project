<!--
  KPI 카드 5개: 공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량
  왼쪽부터 "공장 소비 = 한전 + 태양광 + ESS" 순서로 읽히게 놓았습니다.
  모니터링의 통합 화면과 설비 모니터링 화면이 같이 씁니다.

  사용 예: <KpiCards :status="realtimeStore.status" />
-->
<script setup>
import { computed } from 'vue'
import { formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
})

const status = computed(() => props.status)

// ESS 카드의 숫자 앞에 붙이는 말
const PCS_SHORT = { DISCHARGING: '방전', CHARGING: '충전', STANDBY: '대기' }

// 한전 구매전력이 목표 피크의 몇 %인지 (표시용 비율: 백엔드 값 두 개를 나눈 것)
const gridPercent = computed(() =>
  status.value ? (status.value.gridKw / status.value.targetPeakKw) * 100 : null,
)

const kpis = computed(() => {
  const s = status.value
  const over = gridPercent.value > 100
  // ESS는 충전 중이면 충전 전력을, 그 밖에는 방전 전력을 보여 줌
  const charging = s?.pcsStatus === 'CHARGING'
  const discharging = s?.pcsStatus === 'DISCHARGING'
  return [
    {
      key: 'load',
      name: '공장 소비전력',
      value: formatKw(s?.loadKw),
      unit: 'kW',
      sub: '한전 + 태양광 + ESS',
      // 막대: 공급원 비율 (백엔드가 준 값)
      bars: s
        ? [
            { source: 'grid', width: s.sourceRatio.gridPercent },
            { source: 'solar', width: s.sourceRatio.solarPercent },
            { source: 'ess', width: s.sourceRatio.essPercent },
          ]
        : [],
    },
    {
      key: 'grid',
      name: '한전 구매전력',
      value: formatKw(s?.gridKw),
      unit: 'kW',
      sub: s ? `목표 ${formatKw(s.targetPeakKw)} kW의 ${formatOneDecimal(gridPercent.value)}%` : '',
      over, // 목표 피크를 넘으면 경고색
      // 막대: 목표 피크를 100%로 봤을 때 지금 얼마나 찼는지
      bars: s ? [{ source: over ? 'over' : 'grid', width: Math.min(100, gridPercent.value) }] : [],
    },
    {
      key: 'solar',
      name: '태양광 발전량',
      value: formatKw(s?.solarKw),
      unit: 'kW',
      sub: s ? `공장 소비의 ${formatOneDecimal(s.sourceRatio.solarPercent)}%` : '',
      bars: s ? [{ source: 'solar', width: s.sourceRatio.solarPercent }] : [],
    },
    {
      key: 'ess',
      name: 'ESS 충·방전',
      // 숫자 앞에 지금 무엇을 하는 중인지 적음 (방전 500 kW / 충전 300 kW / 대기 0.0 kW)
      prefix: s ? (PCS_SHORT[s.pcsStatus] ?? '') : '',
      value: formatKw(s ? (charging ? s.chargeKw : s.dischargeKw) : null),
      unit: 'kW',
      sub: discharging ? `공장 소비의 ${formatOneDecimal(s.sourceRatio.essPercent)}%` : '',
      bars: discharging ? [{ source: 'ess', width: s.sourceRatio.essPercent }] : [],
    },
    {
      key: 'soc',
      name: '배터리 잔량 (SOC)',
      value: formatOneDecimal(s?.socPercent),
      unit: '%',
      sub: s ? `배터리실 ${formatOneDecimal(s.batteryTempC)}℃` : '',
      bars: s ? [{ source: 'ess', width: s.socPercent }] : [],
    },
  ]
})
</script>

<template>
  <div class="kpis">
    <section v-for="kpi in kpis" :key="kpi.key" class="card kpi" :class="kpi.key">
      <div class="kpi-top">
        <span class="dot" aria-hidden="true"></span>
        <h2 class="kpi-name">{{ kpi.name }}</h2>
      </div>
      <p class="kpi-value" :class="{ over: kpi.over }">
        <span v-if="kpi.prefix" class="kpi-prefix">{{ kpi.prefix }}</span>
        <span class="num">{{ kpi.value }}</span>
        <span class="kpi-unit">{{ kpi.unit }}</span>
      </p>
      <p class="kpi-sub num" :class="{ over: kpi.over }">{{ kpi.sub }}</p>
      <div class="track" aria-hidden="true">
        <span
          v-for="bar in kpi.bars"
          :key="bar.source"
          class="bar"
          :class="bar.source"
          :style="{ width: `${bar.width}%` }"
        ></span>
      </div>
    </section>
  </div>
</template>

<style scoped>
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

/* 공통 막대 */
.track {
  display: flex;
  height: 8px;
  background: var(--line);
}
.bar {
  display: block;
  height: 100%;
  background: var(--source);
  transition: width 0.4s ease;
}
.bar.over {
  background: var(--crit);
}

/* 1줄: KPI 5개. 낮게 잡아 아래 줄이 첫 화면에 들어오게 함 */
.kpi {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 12px 16px;
}
/* 공장 소비전력은 특정 공급원이 아니므로 점을 먹색으로 */
.kpi.load {
  --source: var(--ink);
}
.kpi-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kpi-name {
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
  white-space: nowrap;
}
.kpi-sub {
  min-height: 18px;
  margin-bottom: 6px;
  overflow: hidden;
  font-size: 12px;
  color: var(--mute);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kpi-sub.over {
  font-weight: 700;
  color: var(--crit);
}
.kpi-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.2;
}
.kpi-value.over {
  color: var(--crit);
}
.kpi-prefix {
  font-size: 18px;
}
.kpi-unit {
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
