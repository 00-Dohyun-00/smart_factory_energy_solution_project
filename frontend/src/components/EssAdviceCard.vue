<!--
  ESS 추천 카드: 추천(충전·방전·대기), 이유, 판단 근거, [적용] [넘기기]
  추천 판단은 백엔드가 하고, 여기서는 보여 주고 버튼 입력만 받습니다.
-->
<script setup>
import { computed, ref } from 'vue'
import { formatClock, formatKw } from '@/utils/format'

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

// 이유 문장을 마침표(.)·쉼표(,) 뒤에서 나눈 줄들
// 뒤에 공백이 올 때만 나눔 → "1,800 kW"나 "0.5"처럼 숫자 안의 쉼표·점에서는 나누지 않음
const reasonLines = computed(() =>
  (props.decision?.reason ?? '').split(/(?<=[.,])\s+/).filter((line) => line),
)

// 대기 추천은 적용할 동작이 없으므로 버튼을 보여 주지 않음
const needsAnswer = computed(
  () => props.decision?.status === 'RECOMMENDED' && props.decision?.action !== 'IDLE',
)

// 카드 맨 아래(버튼 또는 처리 결과)에 보여 줄 것이 있는지. 아직 처리 전인 대기 추천은 없음
const showAnswer = computed(
  () =>
    props.decision?.action !== 'IDLE' ||
    ['APPLIED', 'AUTO', 'IGNORED'].includes(props.decision?.status),
)

// 판단 근거: 백엔드가 준 목록(usedValues)을 그대로 보여 줌. 숫자는 자릿수만 맞춤
const usedValues = computed(() =>
  (props.decision?.usedValues ?? []).map((item) => ({
    label: item.label,
    text: typeof item.value === 'number' ? formatKw(item.value) : item.value,
    unit: item.unit,
  })),
)

// 판단 근거는 항목이 많아(11개) 카드가 길어지면 좁은 화면에서 [적용] 버튼이 첫 화면 아래로 밀림.
// 그래서 앞의 몇 개(백엔드가 앞에 둔 중요한 값)만 보이고, 나머지는 [모두 보기]로 펼침
const SHOWN_COUNT = 4
const showAllUsed = ref(false)
const visibleUsed = computed(() =>
  showAllUsed.value ? usedValues.value : usedValues.value.slice(0, SHOWN_COUNT),
)
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
      <p class="act" :class="{ idle: decision.action === 'IDLE' }">
        {{ actionLabel }}
        <template v-if="decision.action !== 'IDLE'">
          <span class="power num">{{ formatKw(decision.powerKw) }}</span>
          <span class="unit">kW</span>
        </template>
      </p>
      <!-- 이유 문장은 마침표(.)와 쉼표(,) 뒤에서 줄을 바꿔 읽기 쉽게 함 -->
      <p class="why">
        <span v-for="(line, index) in reasonLines" :key="index" class="why-line">{{ line }}</span>
      </p>

      <!-- 판단 근거 -->
      <div v-if="usedValues.length" class="used">
        <div class="used-head">
          <h3>판단 근거</h3>
          <button
            v-if="usedValues.length > SHOWN_COUNT"
            class="more-used"
            :aria-expanded="showAllUsed"
            @click="showAllUsed = !showAllUsed"
          >
            {{ showAllUsed ? '접기' : `모두 보기 (${usedValues.length}개)` }}
          </button>
        </div>
        <dl>
          <div v-for="item in visibleUsed" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd class="num">{{ item.text }} {{ item.unit }}</dd>
          </div>
        </dl>
      </div>

      <div v-if="showAnswer" class="answer">
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
          <p v-else class="note">적용해도 실제 설비를 제어하지는 않습니다 (시뮬레이션).</p>
        </template>
        <p v-else-if="decision.status === 'APPLIED'" class="done">
          적용했습니다
          <span class="done-sub">
            <span class="num">{{ formatClock(decision.appliedAt) }}</span>, {{ decision.appliedBy }}
          </span>
        </p>
        <p v-else-if="decision.status === 'AUTO'" class="done">
          자동으로 적용했습니다 (시뮬레이션)
          <span class="done-sub num">{{ formatClock(decision.appliedAt) }}</span>
        </p>
        <p v-else-if="decision.status === 'IGNORED'" class="done quiet">이번 추천은 넘겼습니다</p>
        <!-- 대기 추천은 이유 문장이 이미 "할 일 없음"을 설명하므로 상자를 따로 띄우지 않음 (카드가 길어지지 않게) -->
        <p v-else-if="decision.action !== 'IDLE'" class="done quiet">지금은 할 일이 없습니다</p>
      </div>
    </template>
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
.why-line {
  display: block;
}

/* 판단 근거: 항목이 11개라 촘촘하게 잡아 옆의 15분 구간 카드보다 너무 길어지지 않게 함 */
.used {
  padding: 8px 12px;
  background: var(--bg);
}
.used-head {
  display: flex;
  align-items: baseline;
  margin-bottom: 4px;
}
.used h3 {
  font-size: 12px;
  font-weight: 700;
}
.more-used {
  margin-left: auto;
  padding: 0;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--ink-2);
  font-size: 12px;
  color: var(--ink-2);
}
.used dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px 12px;
}
.used dt {
  font-size: 11px;
  color: var(--mute);
}
.used dd {
  font-size: 14px;
  font-weight: 700;
}

/* 버튼 또는 처리 결과는 카드 맨 아래에 붙임 */
.answer {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
</style>
