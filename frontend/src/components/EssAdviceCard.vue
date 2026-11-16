<!--
  ESS 추천 카드: 추천(충전·방전·대기), 이유, [적용] [넘기기], 배터리 상태
  추천 판단은 백엔드가 하고, 여기서는 보여 주고 버튼 입력만 받습니다.
-->
<script setup>
import { computed } from 'vue'
import { formatClock, formatKw, formatOneDecimal } from '@/utils/format'

const props = defineProps({
  // 최신 추천 1건 (없으면 null)
  decision: { type: Object, default: null },
  // 최신 상태 1건 (배터리 잔량, PCS 상태 등)
  status: { type: Object, default: null },
  // 적용/넘기기 요청을 보내는 중이면 true
  busy: { type: Boolean, default: false },
  // 서버 연결이 끊겨 버튼을 누를 수 없으면 true
  offline: { type: Boolean, default: false },
})

const emit = defineEmits(['apply', 'ignore'])

const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
const PCS_LABELS = { DISCHARGING: '방전 중', CHARGING: '충전 중', STANDBY: '대기' }

const actionLabel = computed(() => ACTION_LABELS[props.decision?.action] ?? '—')

// 대기 추천은 적용할 동작이 없으므로 버튼을 보여 주지 않음
const needsAnswer = computed(
  () => props.decision?.status === 'RECOMMENDED' && props.decision?.action !== 'IDLE',
)
</script>

<template>
  <section class="card advice">
    <div class="head">
      <h2 class="card-title">ESS 추천</h2>
      <span class="mode">운영자 승인 필요</span>
    </div>

    <p v-if="!decision" class="empty">아직 받은 추천이 없습니다.</p>

    <template v-else>
      <p class="act" :class="{ idle: decision.action === 'IDLE' }">
        {{ actionLabel }}
        <template v-if="decision.action !== 'IDLE'">
          <span class="power num">{{ formatKw(decision.powerKw) }}</span>
          <span class="unit">kW</span>
        </template>
      </p>
      <p class="why">{{ decision.reason }}</p>

      <template v-if="needsAnswer">
        <div class="buttons">
          <button class="apply" :disabled="busy || offline" @click="emit('apply')">
            <template v-if="busy">보내는 중…</template>
            <template v-else>
              {{ actionLabel }} <span class="num">{{ formatKw(decision.powerKw) }}</span> kW 적용
            </template>
          </button>
          <button class="skip" :disabled="busy || offline" @click="emit('ignore')">넘기기</button>
        </div>
        <!-- 버튼이 왜 눌리지 않는지 알려 줌 -->
        <p v-if="offline" class="blocked">
          <span aria-hidden="true">◆</span> 서버와 연결이 끊겨 지금은 적용할 수 없습니다
        </p>
      </template>
      <p v-else-if="decision.status === 'APPLIED'" class="done">
        적용했습니다
        <span class="done-sub">
          <span class="num">{{ formatClock(decision.appliedAt) }}</span>, {{ decision.appliedBy }}
        </span>
      </p>
      <p v-else-if="decision.status === 'IGNORED'" class="done quiet">이번 추천은 넘겼습니다</p>
      <p v-else class="done quiet">지금은 할 일이 없습니다</p>
    </template>

    <!-- 배터리 상태 -->
    <div class="battery">
      <div class="soc">
        <span>배터리 잔량</span>
        <strong class="num">{{ formatOneDecimal(status?.socPercent) }} %</strong>
      </div>
      <div class="track" aria-hidden="true">
        <span class="bar" :style="{ width: `${status?.socPercent ?? 0}%` }"></span>
      </div>
      <p class="facts">
        <span>
          <!-- 낯선 약어라 마우스를 올리면 풀이가 보이게 함 -->
          <abbr
            class="term"
            title="전력변환장치: 배터리의 전기를 공장에서 쓸 수 있게 바꿔 주는 장치"
            >PCS</abbr
          >
          {{ PCS_LABELS[status?.pcsStatus] ?? '—' }}
        </span>
        <span>
          배터리실 <span class="num">{{ formatOneDecimal(status?.batteryTempC) }}</span> ℃
        </span>
      </p>
    </div>
  </section>
</template>

<style scoped>
.advice {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.head {
  display: flex;
  align-items: baseline;
}
.mode {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.empty {
  color: var(--mute);
}

.act {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}
.power {
  font-size: 44px;
  line-height: 1;
}
.unit {
  font-size: 16px;
  font-weight: 400;
  color: var(--ink-2);
}
.act.idle {
  font-size: 36px;
  color: var(--ink-2);
}
.why {
  font-size: 13px;
  color: var(--ink-2);
}

.buttons {
  display: flex;
  gap: 8px;
}
.apply {
  flex: 1;
  height: 44px;
  background: var(--ink);
  border: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--card);
  transition: background-color 0.15s;
}
.apply:hover:not(:disabled) {
  background: var(--ink-2);
}
.skip {
  height: 44px;
  padding: 0 18px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  color: var(--ink-2);
}
.skip:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}
.apply:disabled,
.skip:disabled {
  opacity: 0.45;
  cursor: default;
}
.blocked {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-2);
}
.term {
  text-decoration: underline dotted;
  cursor: help;
}
.done {
  padding: 10px 12px;
  background: var(--bg);
  border-left: 4px solid var(--ink);
  font-weight: 700;
}
.done-sub {
  margin-left: 6px;
  font-weight: 400;
  color: var(--ink-2);
}
.done.quiet {
  border-left-color: var(--line-strong);
  font-weight: 400;
  color: var(--ink-2);
}

/* 배터리 상태 */
.battery {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.soc {
  display: flex;
  align-items: baseline;
  font-size: 13px;
  color: var(--ink-2);
}
.soc strong {
  margin-left: auto;
  font-size: 16px;
  color: var(--ink);
}
.track {
  height: 10px;
  margin-top: 4px;
  background: var(--line);
}
.bar {
  display: block;
  height: 100%;
  background: var(--src-ess);
  transition: width 0.4s ease;
}
.facts {
  display: flex;
  gap: 24px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--ink-2);
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}
</style>
