<!--
  상단 실시간 지표 띠
  "공장 소비전력 = 한전 + 태양광 + ESS" 관계를 막대 하나로 보여 주고, 오른쪽에 배터리 잔량을 둡니다.
  비율은 백엔드가 준 값(sourceRatio)을 그대로 그립니다.
-->
<script setup>
import { computed } from 'vue'
import { formatClock, formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 상태 1건 (아직 못 받았으면 null)
  status: { type: Object, default: null },
  // 서버 연결이 끊겨 마지막 값을 보여 주는 중이면 true
  stale: { type: Boolean, default: false },
})

// 공급원 3개: 색 + 이름 + 숫자 + 막대를 함께 표시
const sources = computed(() => [
  {
    key: 'grid',
    name: '한전 구매',
    kw: props.status?.gridKw ?? null,
    percent: props.status?.sourceRatio.gridPercent ?? 0,
  },
  {
    key: 'solar',
    name: '태양광 발전',
    kw: props.status?.solarKw ?? null,
    percent: props.status?.sourceRatio.solarPercent ?? 0,
  },
  {
    key: 'ess',
    name: 'ESS 방전',
    kw: props.status?.dischargeKw ?? null,
    percent: props.status?.sourceRatio.essPercent ?? 0,
  },
])
</script>

<template>
  <section class="card strip" :class="{ stale }" aria-label="실시간 지표">
    <!-- 공장 소비전력 -->
    <div class="block">
      <p class="label">공장 소비전력</p>
      <p class="value">
        <span class="num">{{ formatKw(status?.loadKw) }}</span>
        <span class="unit">kW</span>
      </p>
    </div>

    <!-- 공급 구성: 한전 / 태양광 / ESS -->
    <div class="block supply">
      <ul class="sources">
        <li v-for="source in sources" :key="source.key" class="source" :class="source.key">
          <span class="dot" aria-hidden="true"></span>
          <span class="source-name">{{ source.name }}</span>
          <span class="source-value num">{{ formatKw(source.kw) }}</span>
          <span class="unit">kW</span>
          <span class="percent num">{{ formatOneDecimal(source.percent) }}%</span>
        </li>
      </ul>
      <div class="bar" aria-hidden="true">
        <span
          v-for="source in sources"
          :key="source.key"
          class="bar-part"
          :class="source.key"
          :style="{ width: `${source.percent}%` }"
        ></span>
      </div>
    </div>

    <!-- 요금 시간대 (경부하 / 중간부하 / 최대부하) -->
    <div class="block">
      <p class="label">요금 시간대</p>
      <p class="value">
        {{ status?.tariff.label ?? '—' }}
        <span v-if="status" class="tariff-next num">
          {{ status.tariff.until }}까지 · 다음 {{ status.tariff.nextLabel }}
        </span>
      </p>
    </div>

    <!-- 연결이 끊겼을 때: 값은 지우지 않고, 마지막으로 받은 값이라는 것만 알림 -->
    <div v-if="stale" class="block stale-note">
      <p class="stale-title"><span aria-hidden="true">◆</span> 통신 단절</p>
      <p class="num">마지막 수신 {{ formatClock(status?.recordedAt) }}</p>
    </div>

    <!-- 배터리 잔량 -->
    <div class="block ess">
      <p class="label">배터리 잔량(SOC)</p>
      <p class="value">
        <span class="num">{{ formatOneDecimal(status?.socPercent) }}</span>
        <span class="unit">%</span>
        <span class="battery" aria-hidden="true">
          <span class="battery-fill" :style="{ width: `${status?.socPercent ?? 0}%` }"></span>
        </span>
      </p>
    </div>
  </section>
</template>

<style scoped>
.strip {
  display: flex;
  align-items: stretch;
  flex: none;
  padding: 10px 0;
}
.block {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
  padding: 0 20px;
}
.block + .block {
  border-left: 1px solid var(--line);
}
.supply {
  flex: 1;
  min-width: 0;
  gap: 8px;
}

.label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-2);
  white-space: nowrap;
}
.value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
}
.unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
}
.tariff-next {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
  white-space: nowrap;
}

/* 공급원 색 */
.grid {
  --source: var(--src-grid);
}
.solar {
  --source: var(--src-solar);
}
.ess {
  --source: var(--src-ess);
}

.sources {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 28px;
}
.source {
  display: flex;
  align-items: baseline;
  gap: 5px;
  white-space: nowrap;
}
.dot {
  align-self: center;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: var(--source);
}
.source-name {
  color: var(--ink-2);
}
.source-value {
  margin-left: 3px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
}
.percent {
  margin-left: 4px;
  color: var(--mute);
}

.bar {
  display: flex;
  height: 10px;
  background: var(--line);
  border-radius: 2px;
  overflow: hidden;
}
.bar-part {
  background: var(--source);
  transition: width 0.4s ease;
}

/* 연결이 끊기면 숫자와 막대를 회색으로 낮춰 "지금 값이 아님"을 보여 줌 */
.stale .value,
.stale .source-value {
  color: var(--mute);
}
.stale .bar-part,
.stale .battery-fill,
.stale .dot {
  background: var(--lost);
}
.stale-note {
  color: var(--ink-2);
  white-space: nowrap;
}
.stale-title {
  font-weight: 700;
}

.battery {
  align-self: center;
  width: 72px;
  height: 14px;
  margin-left: 8px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  overflow: hidden;
}
.battery-fill {
  display: block;
  height: 100%;
  background: var(--src-ess);
  transition: width 0.4s ease;
}

@media (prefers-reduced-motion: reduce) {
  .bar-part,
  .battery-fill {
    transition: none;
  }
}
</style>
