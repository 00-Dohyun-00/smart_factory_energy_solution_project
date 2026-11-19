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
// - 뒤에 공백이 올 때만 나눔 → "1,800 kW"나 "0.5"처럼 숫자 안의 쉼표·점에서는 나누지 않음
// - 괄호 안에서는 나누지 않음 → "지금(경부하, 61원/kWh)"이 괄호 중간에서 끊기지 않게
const reasonLines = computed(() => {
  const lines = []
  let line = ''
  let depth = 0 // 지금 열려 있는 괄호 수
  const parts = (props.decision?.reason ?? '').split(/(?<=[.,])(\s+)/)
  for (const part of parts) {
    if (/^\s+$/.test(part)) {
      // 괄호 밖의 마침표·쉼표 뒤 공백이면 줄을 바꾸고, 괄호 안이면 공백을 그대로 둠
      if (depth === 0) {
        lines.push(line)
        line = ''
      } else {
        line += part
      }
      continue
    }
    line += part
    const opened = (part.match(/\(/g) ?? []).length
    const closed = (part.match(/\)/g) ?? []).length
    depth = Math.max(0, depth + opened - closed) // 닫는 괄호만 있는 문장이어도 음수가 되지 않게
  }
  lines.push(line)
  return lines.filter((item) => item)
})

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

// 판단 근거 아이콘 (2026-11-18, UI 피드백): 이름(label)별 SVG 선 모양 (24×24 기준, 직접 그림)
// 이름은 백엔드가 글자 그대로 보내므로 여기 없는 이름이 오면 작은 점(dot)으로 그림
const ICON_PATHS = {
  trend: ['M3 17l6-6 4 4 8-8', 'M15 7h6v6'], // 오르는 선: 예상 피크
  shield: ['M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z'], // 방패: 방어선
  target: ['M12 3a9 9 0 1 0 0.01 0', 'M12 7a5 5 0 1 0 0.01 0', 'M12 11a1 1 0 1 0 0.01 0'], // 과녁: 목표 피크
  plan: ['M4 6h16v14H4z', 'M4 10h16', 'M8 3v4', 'M16 3v4'], // 달력: 계획 출력
  bolt: ['M13 2L4 14h7l-1 8 9-12h-7z'], // 번개: 추가 방전
  clock: ['M12 3a9 9 0 1 0 0.01 0', 'M12 7v5l3 2'], // 시계: 남은 시간
  battery: ['M3 7h15v10H3z', 'M21 10v4', 'M6.5 10v4', 'M10 10v4'], // 배터리: SOC
  tag: ['M3 12V4h8l10 10-8 8z', 'M7.5 7.5h0.01'], // 가격표: 요금 시간대
  gauge: ['M4 18a8 8 0 1 1 16 0', 'M12 18l4-6'], // 계기판: 최대 출력
  thermo: ['M10 14V5a2 2 0 1 1 4 0v9a4 4 0 1 1-4 0z'], // 온도계: 배터리실 온도, 기온
  sun: [
    'M12 8a4 4 0 1 0 0.01 0',
    'M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  ], // 해: 날씨
  factory: ['M3 21V10l6 4V10l6 4V6h6v15z'], // 공장: 공장 소비
  solar: ['M3 14l3-8h12l3 8z', 'M12 6v8', 'M4.5 10h15', 'M12 14v4', 'M8 20h8'], // 태양광 판: 태양광 발전
  dot: ['M12 9a3 3 0 1 0 0.01 0'], // 그 밖의 이름
}
const USED_ICONS = {
  '예상 피크': 'trend',
  방어선: 'shield',
  '목표 피크': 'target',
  '계획 출력': 'plan',
  '추가 방전': 'bolt',
  '남은 시간': 'clock',
  SOC: 'battery',
  '배터리 잔량': 'battery',
  '요금 시간대': 'tag',
  '최대 출력': 'gauge',
  '배터리실 온도': 'thermo',
  기온: 'thermo',
  날씨: 'sun',
  '공장 소비': 'factory',
  '태양광 발전': 'solar',
}

// 판단 근거: 백엔드가 준 목록(usedValues)을 그대로 보여 줌. 숫자는 자릿수만 맞춤
const usedValues = computed(() =>
  (props.decision?.usedValues ?? []).map((item) => ({
    icon: ICON_PATHS[USED_ICONS[item.label] ?? 'dot'],
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

// 버튼이 없고 판단 근거도 접혀 있으면 아래가 비어 보이므로, 빈 곳에 대기 아이콘과 안내를 연하게 보여 줌
// (2026-11-18, UI 피드백). 버튼이 나오거나 [모두 보기]를 누르면 사라지고 원래 내용이 그 자리를 씀
// 충전·방전 추천이 와서 버튼이 있을 때도 빈 곳이 남으면, 그 행동에 맞는 아이콘을 보여 줌 (2026-11-19, UI 피드백)
const showIdleFill = computed(() => !showAllUsed.value)
// 빈 곳에 그릴 모양: 처리 전인 충전·방전 추천이면 그 행동, 그 밖에는 대기(멈춤)
const fillKind = computed(() => {
  if (!needsAnswer.value) return 'IDLE'
  return props.decision?.action === 'CHARGE' ? 'CHARGE' : 'DISCHARGE'
})
// 아직 처리 전인 대기 추천일 때만 "할 일 없음" 문장을 붙임 (적용·넘김 뒤에는 아래 결과 상자가 따로 있음)
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
      <p class="act" :class="{ idle: decision.action === 'IDLE' }">
        {{ actionLabel }}
        <template v-if="decision.action !== 'IDLE'">
          <span class="power num">{{ formatKw(decision.powerKw) }}</span>
          <span class="unit">kW</span>
        </template>
      </p>
      <!-- 이유 문장은 마침표(.)와 쉼표(,) 뒤에서 줄을 바꿔 읽기 쉽게 함 (괄호 안의 쉼표는 제외) -->
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
            <dt>
              <svg class="used-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path v-for="(d, index) in item.icon" :key="index" :d="d" />
              </svg>
              {{ item.label }}
            </dt>
            <dd class="num">{{ item.text }} {{ item.unit }}</dd>
          </div>
        </dl>
      </div>

      <!-- 빈 곳 채움: 멈춘 배터리 아이콘 + 안내 (남는 높이가 작으면 CSS에서 숨김) -->
      <div v-if="showIdleFill" class="idle-fill">
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
            <!-- 충전: 안쪽이 반쯤 차 있고 번개 (전기가 들어감) -->
            <template v-else-if="fillKind === 'CHARGE'">
              <rect class="level" x="5" y="5" width="18" height="20" rx="1.5" />
              <path class="solid" d="M27 4 L18 17 H24 L21 26 L31 12 H25 Z" />
            </template>
            <!-- 방전: 안쪽이 반쯤 차 있고 오른쪽(밖)으로 나가는 화살표 (전기를 내보냄) -->
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
            적용하면 배터리를 {{ fillKind === 'CHARGE' ? '충전' : '방전' }}합니다
          </p>
        </div>
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
  padding-bottom: 7px;
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
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--mute);
}
/* 판단 근거 아이콘: 글자와 같은 색의 가는 선 */
.used-icon {
  flex: none;
  width: 13px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.used dd {
  font-size: 14px;
  font-weight: 700;
}

/* 빈 곳 채움: 남은 높이를 차지하고 가운데에 연한 회색으로 */
.idle-fill {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  container-type: size; /* 아래 @container로 남은 높이를 재서, 작으면 통째로 숨김 */
}
.idle-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 100%;
  color: var(--faint);
  text-align: center;
}
/* 충전·방전 아이콘은 계기판의 충전(파랑)·방전(주황) 색을 옅게 씀 */
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
  width: 52px;
  height: 30px;
  margin-bottom: 2px;
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
  font-size: 14px;
  font-weight: 700;
  color: var(--mute);
}
.idle-sub {
  font-size: 12px;
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
