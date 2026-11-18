<!--
  성적표의 전기요금 카드
  통신비 청구서처럼 "이번 달 금액 → 지난달·작년 같은 달과 비교 → 항목별 내역" 순서로 보여 줍니다.
  요금은 모두 백엔드가 계산한 추정값이고, 여기서는 만원 단위로 바꿔 보여 주기만 합니다.
-->
<script setup>
import { computed } from 'vue'
import { formatInteger, formatKw, formatManwon, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 성적표의 bill 부분 (total, energyCharge, baseCharge, monthEndEstimate, lastMonthTotal, byTariff 등)
  bill: { type: Object, required: true },
  // 그 달의 최대 피크 (기본요금을 정하는 값, kW)
  peakKw: { type: Number, default: null },
  // 아직 끝나지 않은 달이면 true ("월말 예상"과 "오늘까지"로 나눠 보여 줌)
  inProgress: { type: Boolean, default: false },
  // 작년 같은 달 요금 합계(원). 기록이 없으면 null
  lastYearTotal: { type: Number, default: null },
  // 작년 같은 달 이름 (예: '작년 11월')
  lastYearLabel: { type: String, default: '작년 같은 달' },
})

// 이번 달과 비교: 이번 달은 월말 예상, 지난달·작년은 그 달 전체 요금
const compareRows = computed(() => {
  const current = props.bill.monthEndEstimate
  const rows = [
    // 지난 달을 볼 때는 "이번 달"이 아니라 "이 달"(보고 있는 달)로 부름
    { label: props.inProgress ? '이번 달 (예상)' : '이 달', value: current, current: true },
    { label: '전달', value: props.bill.lastMonthTotal },
    { label: props.lastYearLabel, value: props.lastYearTotal },
  ]
  // 막대 길이는 셋 중 가장 큰 금액을 100%로 본 비율 (표시용)
  const max = Math.max(...rows.map((row) => row.value ?? 0)) || 1
  return rows.map((row) => {
    let diff = ''
    let diffLabel = ''
    if (!row.current && row.value === null) diff = '기록 없음'
    if (!row.current && row.value !== null) {
      const gap = current - row.value
      if (gap === 0) diff = '같음'
      else diff = `${formatManwon(Math.abs(gap))}만원 ${gap < 0 ? '↓' : '↑'}`
      diffLabel = `이 달이 ${formatManwon(Math.abs(gap))}만원 ${gap < 0 ? '적음' : '많음'}`
    }
    return {
      ...row,
      width: row.value === null ? 0 : (row.value / max) * 100,
      diff,
      diffLabel: diffLabel || diff,
    }
  })
})

// 요금 내역 합계 줄의 구매전력 (시간대별 구매전력을 더한 표시용 합계)
const totalKwh = computed(() => props.bill.byTariff.reduce((sum, item) => sum + item.kwh, 0))
</script>

<template>
  <section class="card bill">
    <div class="head">
      <h2 class="card-title">전기요금 추정</h2>
      <span class="note">전달 · 작년 같은 달과 비교 (단위 만원)</span>
    </div>

    <!-- 위: 이번 달 금액 + 비교 막대 -->
    <div class="overview">
      <div class="main-amount">
        <p class="caption">{{ inProgress ? '이번 달 월말 예상' : '이 달 요금' }}</p>
        <p class="amount">
          <span class="num">{{ formatManwon(bill.monthEndEstimate) }}</span>
          <small>만원</small>
        </p>
        <p v-if="inProgress" class="sub num">오늘까지 {{ formatManwon(bill.total) }}만원</p>
      </div>

      <ul class="compare">
        <li v-for="row in compareRows" :key="row.label">
          <span class="compare-label">{{ row.label }}</span>
          <span class="compare-track" aria-hidden="true">
            <span
              class="compare-bar"
              :class="{ current: row.current }"
              :style="{ width: `${row.width}%` }"
            ></span>
          </span>
          <strong class="compare-value num">
            {{ row.value === null ? '—' : formatManwon(row.value) }}
          </strong>
          <span class="compare-diff num" :aria-label="row.diffLabel">{{ row.diff }}</span>
        </li>
      </ul>
    </div>

    <!-- 아래: 요금 내역 (영수증처럼 항목별로) -->
    <table class="table">
      <thead>
        <tr>
          <th>요금 항목</th>
          <th>기준</th>
          <th class="right">구매전력</th>
          <th class="right">비중</th>
          <th class="right">금액</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>기본요금</strong></td>
          <td class="sub num">
            <template v-if="peakKw !== null">최대 피크 {{ formatKw(peakKw) }} kW</template>
          </td>
          <td class="right sub">—</td>
          <td class="right sub">—</td>
          <td class="right num">
            <strong>{{ formatManwon(bill.baseCharge) }}</strong>
          </td>
        </tr>
        <tr v-for="item in bill.byTariff" :key="item.period">
          <td>전력량요금 · {{ item.label }}</td>
          <td class="sub">쓴 만큼</td>
          <td class="right num">{{ formatInteger(item.kwh) }} kWh</td>
          <td class="right num sub">{{ formatOneDecimal(item.percent) }}%</td>
          <td class="right num">
            <strong>{{ formatManwon(item.charge) }}</strong>
          </td>
        </tr>
        <tr class="total">
          <td>{{ inProgress ? '오늘까지 합계' : '합계' }}</td>
          <td></td>
          <td class="right num">{{ formatInteger(totalKwh) }} kWh</td>
          <td></td>
          <td class="right num">{{ formatManwon(bill.total) }}</td>
        </tr>
      </tbody>
    </table>

    <p class="foot">
      기본요금은 이 달 최대 피크로 계산했습니다.
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
  gap: 14px;
}
.head {
  display: flex;
  align-items: center;
}
.note {
  margin-left: auto;
  font-size: 15px;
  color: var(--mute);
}

/* 위: 이번 달 금액 + 비교 막대 */
.overview {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  align-items: center;
  gap: 28px;
}
.main-amount {
  padding-right: 24px;
  border-right: 1px solid var(--line);
}
.caption {
  font-size: 15px;
  color: var(--mute);
}
.amount {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.2;
}
.amount small {
  margin-left: 4px;
  font-size: 17px;
  font-weight: 400;
  color: var(--ink-2);
}
.main-amount .sub {
  font-size: 15px;
}

.compare {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.compare li {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr) 80px 110px;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}
.compare-label {
  color: var(--ink-2);
}
.compare-track {
  height: 14px;
  background: var(--bg);
}
.compare-bar {
  display: block;
  height: 100%;
  background: var(--faint);
}
/* 이번 달 막대만 진하게 */
.compare-bar.current {
  background: var(--ink);
}
.compare-value {
  font-size: 17px;
  text-align: right;
}
.compare-diff {
  color: var(--mute);
}

/* 아래: 요금 내역 */
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 15px;
}
.table th {
  padding: 6px 8px;
  border-bottom: 1px solid var(--line-strong);
  font-size: 14px;
  font-weight: 400;
  color: var(--mute);
  text-align: left;
}
.table td {
  padding: 8px;
  border-bottom: 1px solid var(--line);
}
.table th:first-child,
.table td:first-child {
  padding-left: 0;
}
.table th:last-child,
.table td:last-child {
  padding-right: 0;
}
.table .right {
  text-align: right;
}
.table .total td {
  border-top: 1px solid var(--ink);
  border-bottom: 0;
  font-size: 17px;
  font-weight: 700;
}
.sub {
  color: var(--ink-2);
}

.foot {
  font-size: 14px;
  color: var(--mute);
}
</style>
