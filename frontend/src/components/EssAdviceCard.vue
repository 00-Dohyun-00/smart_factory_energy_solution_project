<!--
  ESS 추천 카드: 무엇을(충전·방전·대기, kW, 언제까지) → 하면 무엇이 좋은지(한두 문장) → [적용] [넘기기]
  추천 판단은 백엔드가 하고, 여기서는 보여 주고 버튼 입력만 받습니다.

  2026-11-18 개편: 이번 15분 상황은 예상 피크 카드, 배터리 잔량은 KPI 카드가 이미 보여 주므로
  이 카드는 "수락할지 넘길지"만 판단하게 함. 판단 근거 목록(usedValues)은 추천 이력 화면에서 봄.
  처리 상태는 지난 일("적용했습니다")이 아니라 지금 상태("적용 중")로 적음.
-->
<script setup>
import { computed } from 'vue'
import { formatClock, formatHourMinute, formatKw } from '@/utils/format'

const props = defineProps({
  // 최신 추천 1건 (없으면 null)
  decision: { type: Object, default: null },
  // 적용/넘기기 요청을 보내는 중이면 true
  busy: { type: Boolean, default: false },
  // 서버 연결이 끊겨 버튼을 누를 수 없으면 true
  offline: { type: Boolean, default: false },
  // 운영 모드가 자동이면 true (실제 제어가 아니라 시뮬레이션)
  auto: { type: Boolean, default: false },
})

const emit = defineEmits(['apply', 'ignore'])

const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }

const actionLabel = computed(() => ACTION_LABELS[props.decision?.action] ?? '—')

// 아직 답하지 않은 충·방전 추천만 버튼을 보여 줌
const needsAnswer = computed(
  () => props.decision?.status === 'RECOMMENDED' && props.decision?.action !== 'IDLE',
)

// 처리 상태 (지금 상태로 적음)
const stateBadge = computed(() => {
  const decision = props.decision
  if (!decision) return null
  if (decision.status === 'APPLIED') {
    return { text: `적용 중 · ${formatClock(decision.appliedAt)} ${decision.appliedBy}`, on: true }
  }
  if (decision.status === 'AUTO') return { text: '자동 적용 중 (시뮬레이션)', on: true }
  if (decision.status === 'IGNORED') return { text: '넘김', on: false }
  return null
})
</script>

<template>
  <section class="card advice">
    <div class="head">
      <h2 class="card-title">ESS 추천</h2>
      <!-- 모드 신호등: 지금 모드의 등만 켜짐 (승인 모드 = 주황 등, 자동 모드 = 초록 등). 글자도 함께 굵게 -->
      <div
        class="mode-signal"
        role="status"
        :aria-label="auto ? '지금 모드: 자동 모드 (시뮬레이션)' : '지금 모드: 운영자 승인 모드'"
      >
        <span class="lamp-item manual" :class="{ on: !auto }" aria-hidden="true">
          <span class="lamp"></span>운영자 승인
        </span>
        <span class="lamp-item auto" :class="{ on: auto }" aria-hidden="true">
          <span class="lamp"></span>자동<small v-if="auto"> (시뮬레이션)</small>
        </span>
      </div>
    </div>

    <p v-if="!decision" class="empty">아직 받은 추천이 없습니다.</p>

    <template v-else>
      <!-- 무엇을: 동작, 출력, 언제까지 + 지금 처리 상태 -->
      <div class="act-row">
        <p class="act" :class="{ idle: decision.action === 'IDLE' }">
          {{ actionLabel }}
          <template v-if="decision.action !== 'IDLE'">
            <span class="power num">{{ formatKw(decision.powerKw) }}</span>
            <span class="unit">kW</span>
          </template>
        </p>
        <span v-if="decision.windowEnd" class="until num">
          ~ {{ formatHourMinute(decision.windowEnd) }}
        </span>
        <span v-if="stateBadge" class="state" :class="{ off: !stateBadge.on }">
          {{ stateBadge.text }}
        </span>
      </div>

      <!-- 하면 무엇이 좋은지 (백엔드가 보낸 한두 문장) -->
      <p class="benefit">{{ decision.reason }}</p>

      <div v-if="needsAnswer" class="answer">
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
        <p v-else class="note">적용해도 실제 설비를 제어하지는 않습니다 (시뮬레이션).</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.advice {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.head {
  display: flex;
  align-items: baseline;
}
/* 모드 신호등 */
.mode-signal {
  display: flex;
  gap: 2px;
  margin-left: auto;
  padding: 3px;
  background: var(--bg);
  border: 1px solid var(--line-strong);
  border-radius: 999px;
}
.lamp-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 7px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--faint);
  white-space: nowrap;
}
.lamp-item small {
  font-size: 11px;
  font-weight: 400;
}
/* 꺼진 등: 회색 */
.lamp {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--line-strong);
}
/* 켜진 등: 색 + 빛 번짐, 글자는 굵고 진하게 */
.lamp-item.on {
  background: var(--card);
  font-weight: 700;
  color: var(--ink);
  box-shadow: 0 0 0 1px var(--line);
}
.lamp-item.manual.on .lamp {
  background: var(--warn);
  box-shadow: 0 0 6px 1px var(--warn);
}
.lamp-item.auto.on .lamp {
  background: var(--ok);
  box-shadow: 0 0 6px 1px var(--ok);
}
.empty {
  color: var(--mute);
}

/* 무엇을 */
.act-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.act {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}
.power {
  font-size: 48px;
  line-height: 1;
}
.unit {
  font-size: 16px;
  font-weight: 400;
  color: var(--ink-2);
}
.act.idle {
  font-size: 40px;
  color: var(--ink-2);
}
.until {
  font-size: 16px;
  color: var(--ink-2);
}
/* 처리 상태: 지금 상태라 테두리 배지로 */
.state {
  margin-left: auto;
  padding: 2px 10px;
  border: 1.5px solid var(--ok);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ok);
  white-space: nowrap;
}
.state.off {
  border-color: var(--line-strong);
  font-weight: 400;
  color: var(--ink-2);
}

/* 하면 무엇이 좋은지: 카드에서 가장 먼저 읽히는 문장 */
.benefit {
  padding: 12px 14px;
  background: var(--bg);
  border-left: 4px solid var(--ink);
  font-size: 16px;
  line-height: 1.5;
  color: var(--ink);
}

/* 버튼은 카드 맨 아래에 붙임 */
.answer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: auto;
}
.buttons {
  display: flex;
  gap: 8px;
}
.apply {
  flex: 1;
  height: 46px;
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
  height: 46px;
  padding: 0 22px;
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
.note {
  font-size: 12px;
  color: var(--mute);
}
</style>
