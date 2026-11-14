<!--
  상단 실시간 지표 카드 1개
  사용 예: <KpiCard label="공장 소비전력" value="2,570" unit="kW" :tags="['시뮬레이션']" />
-->
<script setup>
defineProps({
  label: { type: String, default: '' },
  // format.js로 이미 모양을 맞춘 문자열 (값이 없으면 '—')
  value: { type: String, default: '—' },
  unit: { type: String, default: '' },
  // 값의 출처 표시 (예: ['가상', '시뮬레이션'])
  tags: { type: Array, default: () => [] },
  // 공급원 색 막대 (grid | solar | ess). 없으면 막대를 그리지 않음
  source: { type: String, default: '' },
})
</script>

<template>
  <div class="card kpi">
    <div class="kpi-head">
      <span v-if="source" class="source-mark" :class="source" aria-hidden="true"></span>
      <span class="kpi-label">{{ label }}</span>
      <span class="kpi-tags">
        <span v-for="tag in tags" :key="tag" class="tag">{{ tag }}</span>
      </span>
    </div>
    <p class="kpi-value">
      <span class="num">{{ value }}</span>
      <span class="kpi-unit">{{ unit }}</span>
    </p>
  </div>
</template>

<style scoped>
.kpi {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
}
.kpi-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.kpi-label {
  color: var(--ink-2);
  white-space: nowrap;
}
.kpi-tags {
  display: flex;
  gap: 4px;
  margin-left: auto;
}
.source-mark {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.source-mark.grid {
  background: var(--src-grid);
}
.source-mark.solar {
  background: var(--src-solar);
}
.source-mark.ess {
  background: var(--src-ess);
}
.kpi-value {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.01em;
}
.kpi-unit {
  margin-left: 6px;
  font-size: 15px;
  font-weight: 400;
  color: var(--ink-2);
}
</style>
