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

// ---- 빈 곳 채움 아이콘 ----
// 처리할 추천([적용]/[넘기기] 버튼)이 없으면 카드 아래가 비어 보이므로, 빈 곳에 배터리 아이콘과 안내를 연하게 보여 줌.
// 새 충·방전 추천이 들어와 버튼이 나오면 사라지고 버튼이 그 자리를 씀
const showFill = computed(() => !needsAnswer.value)
// 그릴 모양: 적용 중(운영자·자동)인 충전·방전이면 그 동작, 그 밖(추천 없음·대기·넘김)은 멈춤
const fillKind = computed(() => {
  const decision = props.decision
  const running = decision?.status === 'APPLIED' || decision?.status === 'AUTO'
  if (running && decision.action === 'CHARGE') return 'CHARGE'
  if (running && decision.action === 'DISCHARGE') return 'DISCHARGE'
  return 'IDLE'
})
// 아직 처리 전인 대기 추천일 때만 "할 일 없음" 문장을 붙임
const isIdleNow = computed(
  () => props.decision?.action === 'IDLE' && props.decision?.status === 'RECOMMENDED',
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

    <!-- 빈 곳 채움: 배터리 아이콘 + 안내 (처리할 추천이 오면 사라짐, 남는 높이가 작으면 CSS에서 숨김) -->
    <div v-if="showFill" class="idle-fill">
      <div class="idle-inner" :class="fillKind.toLowerCase()">
        <svg class="idle-icon" viewBox="0 0 52 30" aria-hidden="true">
          <!-- 배터리 몸통과 꼭지 -->
          <rect class="outline" x="1.5" y="1.5" width="43" height="27" rx="4" />
          <rect class="solid" x="46" y="10" width="4.5" height="10" rx="1.5" />
          <!-- 대기: 멈춤 표시 (‖) -->
          <template v-if="fillKind === 'IDLE'">
            <rect class="solid" x="17" y="8.5" width="4" height="13" rx="1" />
            <rect class="solid" x="25" y="8.5" width="4" height="13" rx="1" />
          </template>
          <!-- 충전 중: 안쪽이 반쯤 차 있고 번개 (전기가 들어감) -->
          <template v-else-if="fillKind === 'CHARGE'">
            <rect class="level" x="5" y="5" width="18" height="20" rx="1.5" />
            <path class="solid" d="M27 4 L18 17 H24 L21 26 L31 12 H25 Z" />
          </template>
          <!-- 방전 중: 안쪽이 반쯤 차 있고 오른쪽(밖)으로 나가는 화살표 (전기를 내보냄) -->
          <template v-else>
            <rect class="level" x="5" y="5" width="18" height="20" rx="1.5" />
            <path class="arrow" d="M26 15 H39 M34 10 L39 15 L34 20" />
          </template>
        </svg>
        <template v-if="fillKind === 'IDLE'">
          <p v-if="isIdleNow" class="idle-title">지금은 할 일이 없습니다</p>
          <p class="idle-sub">새 추천이 오면 여기에 표시됩니다</p>
        </template>
        <p v-else class="idle-sub">
          배터리를 {{ fillKind === 'CHARGE' ? '충전' : '방전' }}하는 중입니다 (시뮬레이션)
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.advice {
  display: flex;
  flex-direction: column;
  gap: 9.6px;
}
.head {
  display: flex;
  align-items: baseline;
}
/* 모드 신호등 */
.mode-signal {
  display: flex;
  gap: 1.6px;
  margin-left: auto;
  padding: 2.4px;
  background: var(--bg);
  border: 1px solid var(--line-strong);
  border-radius: 999px;
}
.lamp-item {
  display: inline-flex;
  align-items: center;
  gap: 4.8px;
  padding: 2.4px 8px 2.4px 5.6px;
  border-radius: 999px;
  font-size: 10.4px;
  color: var(--faint);
  white-space: nowrap;
}
.lamp-item small {
  font-size: 8.8px;
  font-weight: 400;
}
/* 꺼진 등: 회색 */
.lamp {
  width: 8.8px;
  height: 8.8px;
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
  gap: 9.6px;
}
.act {
  display: flex;
  align-items: baseline;
  gap: 6.4px;
  font-size: 20.8px;
  font-weight: 700;
  line-height: 1.1;
}
.power {
  font-size: 38.4px;
  line-height: 1;
}
.unit {
  font-size: 12.8px;
  font-weight: 400;
  color: var(--ink-2);
}
.act.idle {
  font-size: 32px;
  color: var(--ink-2);
}
.until {
  font-size: 12.8px;
  color: var(--ink-2);
}
/* 처리 상태: 지금 상태라 테두리 배지로 */
.state {
  margin-left: auto;
  padding: 1.6px 8px;
  border: 1.5px solid var(--ok);
  border-radius: 999px;
  font-size: 10.4px;
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
  padding: 9.6px 11.2px;
  background: var(--bg);
  border-left: 4px solid var(--ink);
  font-size: 12.8px;
  line-height: 1.5;
  color: var(--ink);
}

/* 빈 곳 채움: 남은 높이를 차지하고 가운데에 연한 색으로 */
.idle-fill {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  container-type: size; /* 아래 @container로 남은 높이를 재서, 작으면 글이나 아이콘을 숨김 */
}
.idle-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3.2px;
  height: 100%;
  color: var(--faint);
  text-align: center;
}
/* 충전 중·방전 중 아이콘은 계기판의 충전(파랑)·방전(주황) 색을 옅게 씀 */
.idle-inner.charge {
  color: color-mix(in srgb, var(--gauge-charge) 75%, var(--card));
}
.idle-inner.discharge {
  color: color-mix(in srgb, var(--gauge-discharge) 75%, var(--card));
}
/* 잘린 채 보이지 않도록: 높이가 모자라면 글을 먼저 숨기고, 아이콘도 안 들어가면 통째로 숨김 */
@container (max-height: 84px) {
  .idle-title,
  .idle-sub {
    display: none;
  }
}
@container (max-height: 40px) {
  .idle-inner {
    display: none;
  }
}
.idle-icon {
  width: 41.6px;
  height: 24px;
  margin-bottom: 1.6px;
}
.idle-icon .outline {
  fill: none;
  stroke: currentColor;
  stroke-width: 2.5;
}
.idle-icon .solid {
  fill: currentColor;
}
.idle-icon .level {
  fill: currentColor;
  opacity: 0.35;
}
.idle-icon .arrow {
  fill: none;
  stroke: currentColor;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.idle-title {
  font-size: 11.2px;
  font-weight: 700;
  color: var(--mute);
}
.idle-sub {
  font-size: 9.6px;
}

/* 버튼은 카드 맨 아래에 붙임 */
.answer {
  display: flex;
  flex-direction: column;
  gap: 4.8px;
  margin-top: auto;
}
.buttons {
  display: flex;
  gap: 6.4px;
}
.apply {
  flex: 1;
  height: 36.8px;
  background: var(--ink);
  border: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--card);
  transition: background-color 0.15s;
}
.apply:hover:not(:disabled) {
  background: var(--ink-2);
}
.skip {
  height: 36.8px;
  padding: 0 17.6px;
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
  font-size: 10.4px;
  font-weight: 700;
  color: var(--ink-2);
}
.note {
  font-size: 9.6px;
  color: var(--mute);
}
</style>
