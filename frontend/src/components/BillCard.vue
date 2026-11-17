<!--
  성적표의 전기요금 카드
  요금은 모두 백엔드가 계산한 추정값이고, 여기서는 만원 단위로 바꿔 보여 주기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import { formatInteger, formatManwon, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 성적표의 bill 부분 (total, energyCharge, baseCharge, byTariff 등)
  bill: { type: Object, required: true },
})

// 요금 구성 막대에서 전력량요금이 차지하는 너비 (표시용 비율)
const energyPercent = computed(() => (props.bill.energyCharge / props.bill.total) * 100)
</script>

<template>
  <section class="card bill">
    <div class="head">
      <h2 class="card-title">전기요금 추정</h2>
      <span class="note">금액 단위 만원</span>
    </div>

    <!-- 요금 구성: 전력량요금 + 기본요금 -->
    <div class="bar" aria-hidden="true">
      <span class="bar-energy" :style="{ width: `${energyPercent}%` }"></span>
      <span class="bar-base"></span>
    </div>

    <dl class="parts">
      <div class="part">
        <dt><span class="dot energy" aria-hidden="true"></span>전력량요금 (쓴 만큼)</dt>
        <dd class="num">{{ formatManwon(bill.energyCharge) }}</dd>
      </div>
      <div class="part">
        <dt><span class="dot base" aria-hidden="true"></span>기본요금 (최대 피크 기준)</dt>
        <dd class="num">{{ formatManwon(bill.baseCharge) }}</dd>
      </div>
      <div class="part right">
        <dt>월말 예상</dt>
        <dd class="num small">{{ formatManwon(bill.monthEndEstimate) }}</dd>
      </div>
      <div class="part">
        <dt>지난달</dt>
        <dd class="num small">{{ formatManwon(bill.lastMonthTotal) }}</dd>
      </div>
    </dl>

    <!-- 요금 시간대별 전력량요금 -->
    <table class="table">
      <thead>
        <tr>
          <th>요금 시간대</th>
          <th class="right">구매전력</th>
          <th class="right">비중</th>
          <th class="right">전력량요금</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in bill.byTariff" :key="item.period">
          <td>
            <strong>{{ item.label }}</strong>
          </td>
          <td class="right num">{{ formatInteger(item.kwh) }} kWh</td>
          <td class="right num sub">{{ formatOneDecimal(item.percent) }}%</td>
          <td class="right num">
            <strong>{{ formatManwon(item.charge) }}</strong>
          </td>
        </tr>
      </tbody>
    </table>

    <p class="foot">
      기본요금은 이번 달 최대 피크로 계산했습니다.
      <template v-if="bill.baseChargeOverTarget > 0">
        목표 피크를 지켰다면 {{ formatManwon(bill.baseChargeOverTarget) }}만원 적습니다.
      </template>
      (계산 방식은 확인 필요)
    </p>
  </section>
</template>

<style scoped>
.bill {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.head {
  display: flex;
  align-items: center;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}

.bar {
  display: flex;
  gap: 2px;
  height: 22px;
}
.bar-energy,
.dot.energy {
  background: var(--src-grid);
}
.bar-base,
.dot.base {
  background: var(--ink);
}
.bar-base {
  flex: 1;
}

.parts {
  display: flex;
  gap: 28px;
}
.part.right {
  margin-left: auto;
}
.part dt {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--mute);
}
.part dd {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}
.part dd.small {
  font-size: 16px;
  line-height: 1.8;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.table {
  width: 100%;
  border-top: 1px solid var(--line);
}
.table th {
  padding: 8px 0 4px;
  font-size: 12px;
  color: var(--mute);
  text-align: left;
}
.table td {
  padding: 4px 0;
}
.table .right {
  text-align: right;
}
.sub {
  color: var(--ink-2);
}

.foot {
  padding-top: 8px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--mute);
}
</style>
