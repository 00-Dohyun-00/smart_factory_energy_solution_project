<!--
  ESS 추천 카드: 현재(ESS가 지금 실제로 하는 일) → 추천(백엔드가 권하는 일)
  추천 판단은 백엔드가 하고, 여기서는 보여 주고 버튼 입력만 받습니다.

  2026-11-20 개편 (F안): "지금 상태인지 추천인지 헷갈린다"는 피드백으로, 현재와 추천을 두 칸으로 나눠 적음.
  - 답할 추천이 있음 → 현재 → 추천(색 테두리) + 이유 + [적용] [넘기기]
  - 적용 중 → 현재 한 칸으로 넓게 + "● 적용 중 · 처리 시각 · 남은 시간" + 진행 막대
  - 할 일 없음 · 넘김 · 추천 없음 → 현재 → 추천(점선, 지금 할 일이 아님) + 안내 문장
  현재는 추천이 아니라 실시간 기록(site-powers/latest)의 ESS 출력에서 가져옴.
  이번 15분 상황은 예상 피크 카드가 보여 주므로 여기에 다시 그리지 않음. 판단 근거 목록(usedValues)은 추천 이력 화면에서 봄.
-->
<script setup>
import { computed, ref } from 'vue'
import { formatHourMinute, formatKw } from '@/utils/format'

const props = defineProps({
  // 최신 추천 1건 (없으면 null)
  decision: { type: Object, default: null },
  // 최신 실시간 상태 1건: 현재 ESS 출력과 서버 시각에 씀 (없으면 null)
  status: { type: Object, default: null },
  // 적용/넘기기 요청을 보내는 중이면 true
  busy: { type: Boolean, default: false },
  // 서버 연결이 끊겨 버튼을 누를 수 없으면 true
  offline: { type: Boolean, default: false },
  // 운영 모드가 자동이면 true (실제 제어가 아니라 시뮬레이션)
  auto: { type: Boolean, default: false },
  // 운영 모드를 저장하는 중이면 true (버튼을 잠깐 막음)
  modeBusy: { type: Boolean, default: false },
})

// change-mode: 운영 모드 버튼을 눌러 확인까지 마침 (true = 자동, false = 운영자 승인). 저장은 부모가 함
const emit = defineEmits(['apply', 'ignore', 'change-mode'])

// 운영 모드 버튼: 지금 모드가 아닌 쪽을 누르면 한 번 묻고, 확인하면 부모에게 알림
// 운영 모드는 공용 DB의 환경 설정이라 바꾸면 팀원 모두의 화면에 바로 적용됨
// 확인 팝업 (브라우저 내장 <dialog>). 열려 있는 동안 바꾸려는 모드를 기억함 (true = 자동, false = 운영자 승인)
const modeDialog = ref(null)
const pendingAuto = ref(null)
function chooseMode(wantAuto) {
  if (wantAuto === props.auto || props.modeBusy) return
  pendingAuto.value = wantAuto
  modeDialog.value?.showModal() // 화면 맨 위에 띄우고 뒤는 어둡게, Esc로 닫힘
}
// [바꾸기]: 부모에게 알리고 닫음 / [취소]·Esc·바깥 누르기: 그냥 닫음
function confirmMode() {
  emit('change-mode', pendingAuto.value)
  modeDialog.value?.close()
}
function closeModeDialog() {
  modeDialog.value?.close()
}
// 팝업 바깥(어두운 배경)을 누르면 닫음 (팝업 상자 안을 누른 것은 무시)
function onDialogClick(event) {
  if (event.target === modeDialog.value) closeModeDialog()
}
// 팝업에 적을 모드 이름과 설명
const MODE_TEXT = {
  manual: {
    name: '운영자 승인 모드',
    desc: '추천이 오면 운영자가 보고 [적용] 또는 [넘기기]를 누릅니다.',
  },
  auto: {
    name: '자동 모드 (시뮬레이션)',
    desc: '추천을 바로 적용한 것으로 기록합니다. 실제 설비를 제어하지는 않습니다.',
  },
}

// 추천 칸에 쓰는 말 (할 일)
const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
// 현재 칸에 쓰는 말 (하고 있는 일)
const CURRENT_LABELS = { CHARGE: '충전 중', DISCHARGE: '방전 중', IDLE: '대기' }
// 실시간 기록의 PCS 상태를 추천과 같은 이름으로 맞춤 (현재와 추천이 같은지 비교하려고)
const PCS_TO_ACTION = { CHARGING: 'CHARGE', DISCHARGING: 'DISCHARGE', STANDBY: 'IDLE' }

// 글자·테두리 색 이름: 충전 파랑 · 방전 주황 · 대기는 기본 글자색
function actionClass(action) {
  if (action === 'CHARGE') return 'charge'
  if (action === 'DISCHARGE') return 'discharge'
  return ''
}

const actionLabel = computed(() => ACTION_LABELS[props.decision?.action] ?? '—')

// 현재: 추천이 아니라 ESS가 지금 실제로 하는 일 (KPI 카드와 같은 값)
const current = computed(() => {
  const s = props.status
  if (!s) return null
  const action = PCS_TO_ACTION[s.pcsStatus] ?? null
  return {
    action,
    label: CURRENT_LABELS[action] ?? '—',
    kw: action === 'CHARGE' ? s.chargeKw : s.dischargeKw,
  }
})

// 카드 모양
// ask: 답할 충·방전 추천이 있음 / running: 적용(운영자·자동)한 충·방전이 진행 중
// idle: 대기 추천 (할 일 없음) / skipped: 넘긴 추천 / empty: 추천을 아직 못 받음
const view = computed(() => {
  const decision = props.decision
  if (!decision) return 'empty'
  if (decision.action === 'IDLE') return 'idle'
  if (decision.status === 'RECOMMENDED') return 'ask'
  if (decision.status === 'APPLIED' || decision.status === 'AUTO') return 'running'
  return 'skipped'
})

// 적용 중일 때 현재 칸의 kW: 기록이 1분마다라 적용 직후 잠깐은 아직 대기로 남아 있으므로,
// 기록에 나타나기 전에는 적용한 추천 kW를 보여 줌
const runningKw = computed(() =>
  current.value?.action === props.decision?.action ? current.value.kw : props.decision?.powerKw,
)

// 진행 막대: 서버 시각(구간 끝 − 남은 초)이 추천 구간의 어디쯤인지 (표시용 비율)
const progress = computed(() => {
  const decision = props.decision
  const s = props.status
  if (!decision?.windowStart || !decision.windowEnd) return null
  if (!s?.windowEnd || s.remainingSeconds == null) return null
  const start = new Date(decision.windowStart).getTime()
  const end = new Date(decision.windowEnd).getTime()
  const now = new Date(s.windowEnd).getTime() - s.remainingSeconds * 1000
  const ratio = Math.min(Math.max((now - start) / (end - start), 0), 1)
  return {
    percent: ratio * 100,
    remainingMinutes: Math.max(Math.ceil((end - now) / 60000), 0),
  }
})
</script>

<template>
  <section class="card advice">
    <div class="head">
      <h2 class="card-title">ESS 추천</h2>
      <!-- 모드 신호등: 지금 모드의 등만 켜짐 (승인 모드 = 주황 등, 자동 모드 = 초록 등). 글자도 함께 굵게 -->
      <div class="mode-signal" role="group" aria-label="운영 모드 (누르면 바꿈)">
        <!-- 운영 모드 버튼: 지금 모드의 등이 켜져 있고, 다른 쪽을 누르면 확인 후 바꿈 -->
        <button
          type="button"
          class="lamp-item manual"
          :class="{ on: !auto }"
          :aria-pressed="!auto"
          :disabled="modeBusy"
          title="운영자 승인 모드로 바꾸기 (추천을 사람이 보고 적용)"
          @click="chooseMode(false)"
        >
          <span class="lamp" aria-hidden="true"></span>운영자 승인
        </button>
        <button
          type="button"
          class="lamp-item auto"
          :class="{ on: auto }"
          :aria-pressed="auto"
          :disabled="modeBusy"
          title="자동 모드로 바꾸기 (추천을 바로 반영한 것으로 표시, 시뮬레이션)"
          @click="chooseMode(true)"
        >
          <span class="lamp" aria-hidden="true"></span>자동<small v-if="auto"> (시뮬레이션)</small>
        </button>
      </div>
    </div>

    <!-- 적용 중: 현재가 곧 추천이라 현재 한 칸을 넓게 + 적용 표시 + 진행 막대 -->
    <template v-if="view === 'running'">
      <div class="box now wide" :class="actionClass(decision.action)">
        <span class="caption">현재</span>
        <strong class="big">{{ CURRENT_LABELS[decision.action] }}</strong>
        <span class="sub">
          <span class="num">{{ formatKw(runningKw) }}</span> kW
          <template v-if="decision.windowEnd">
            · <span class="num">{{ formatHourMinute(decision.windowEnd) }}</span>까지
          </template>
        </span>
      </div>
      <div class="running" :class="actionClass(decision.action)">
        <div class="running-row">
          <span class="on-pill">
            <span class="dot" aria-hidden="true"></span>
            {{ decision.status === 'AUTO' ? '자동 적용 중' : '적용 중' }}
          </span>
          <span v-if="decision.appliedAt" class="who">
            <span class="num">{{ formatHourMinute(decision.appliedAt) }}</span>
            {{ decision.appliedBy }}
          </span>
          <span v-if="progress" class="left">
            <template v-if="progress.remainingMinutes > 0">
              남은 <span class="num">{{ progress.remainingMinutes }}</span>분
            </template>
            <template v-else>끝남</template>
          </span>
        </div>
        <div v-if="progress" class="track">
          <div class="fill" :style="{ width: `${progress.percent}%` }"></div>
        </div>
      </div>
    </template>

    <!-- 그 밖: 현재 → 추천 두 칸 -->
    <template v-else>
      <div class="compare">
        <div class="box now" :class="actionClass(current?.action)">
          <span class="caption">현재</span>
          <strong class="big">{{ current?.label ?? '—' }}</strong>
          <!-- 대기면 kW를 적지 않음 (빈 줄로 두어 두 칸 높이를 맞춤) -->
          <span class="sub">
            <template v-if="current && current.action !== 'IDLE'">
              <span class="num">{{ formatKw(current.kw) }}</span> kW
            </template>
          </span>
        </div>
        <svg class="arrow" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 12 H19 M13 6 L19 12 L13 18" />
        </svg>
        <!-- 추천 칸: 답할 추천이면 색 테두리, 할 일 없음·넘김·추천 없음이면 점선 (지금 할 일이 아님) -->
        <div
          class="box next"
          :class="view === 'ask' ? ['active', actionClass(decision.action)] : 'quiet'"
        >
          <span class="caption">추천</span>
          <strong class="big">{{ view === 'empty' ? '—' : actionLabel }}</strong>
          <span class="sub">
            <template v-if="view === 'ask' || view === 'skipped'">
              <span class="num">{{ formatKw(decision.powerKw) }}</span> kW ·
              <template v-if="view === 'skipped'">넘김</template>
              <template v-else-if="decision.windowEnd">
                <span class="num">{{ formatHourMinute(decision.windowEnd) }}</span>까지
              </template>
            </template>
          </span>
        </div>
      </div>

      <!-- 하면 무엇이 좋은지 (백엔드가 보낸 한두 문장) / 할 일이 없을 때 안내 -->
      <p class="benefit">
        <template v-if="view === 'ask'">{{ decision.reason }}</template>
        <template v-else-if="view === 'idle'">
          {{ decision.reason }} 새 추천이 오면 오른쪽이 바뀝니다.
        </template>
        <template v-else-if="view === 'skipped'">
          이 추천을 넘겼습니다<template v-if="decision.appliedAt">
            (<span class="num">{{ formatHourMinute(decision.appliedAt) }}</span>
            {{ decision.appliedBy }})</template
          >. 새 추천이 오면 오른쪽이 바뀝니다.
        </template>
        <template v-else>아직 받은 추천이 없습니다. 새 추천이 오면 오른쪽이 바뀝니다.</template>
      </p>

      <div v-if="view === 'ask'" class="answer">
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
      </div>
    </template>

    <!-- 운영 모드 바꾸기 확인 팝업 -->
    <dialog
      ref="modeDialog"
      class="mode-dialog"
      aria-labelledby="mode-dialog-title"
      @click="onDialogClick"
      @close="pendingAuto = null"
    >
      <div v-if="pendingAuto !== null" class="dialog-box">
        <h3 id="mode-dialog-title" class="dialog-title">운영 모드를 바꿀까요?</h3>
        <!-- 지금 모드 → 바꿀 모드 -->
        <div class="dialog-change">
          <div class="dialog-mode from" :class="pendingAuto ? 'manual' : 'auto'">
            <span class="dialog-caption">지금</span>
            <strong
              ><span class="lamp" aria-hidden="true"></span
              >{{ (pendingAuto ? MODE_TEXT.manual : MODE_TEXT.auto).name }}</strong
            >
          </div>
          <span class="dialog-arrow" aria-hidden="true">→</span>
          <div class="dialog-mode to" :class="pendingAuto ? 'auto' : 'manual'">
            <span class="dialog-caption">바꿀 모드</span>
            <strong
              ><span class="lamp" aria-hidden="true"></span
              >{{ (pendingAuto ? MODE_TEXT.auto : MODE_TEXT.manual).name }}</strong
            >
          </div>
        </div>
        <p class="dialog-desc">{{ (pendingAuto ? MODE_TEXT.auto : MODE_TEXT.manual).desc }}</p>
        <!-- 공용 설정 주의 -->
        <p class="dialog-warn">
          <span aria-hidden="true">▲</span> 공용 설정이라 저장하면
          <strong>모든 팀원의 화면</strong>에 바로 적용됩니다.
        </p>
        <div class="dialog-buttons">
          <button type="button" class="dialog-cancel" @click="closeModeDialog">취소</button>
          <button type="button" class="dialog-ok" @click="confirmMode">
            {{ pendingAuto ? '자동 모드로 바꾸기' : '운영자 승인 모드로 바꾸기' }}
          </button>
        </div>
      </div>
    </dialog>
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
/* 운영 모드 버튼: 누를 수 있게 보이도록 테두리 · 마우스 올림 표시 */
.lamp-item {
  display: inline-flex;
  align-items: center;
  gap: 4.8px;
  padding: 3px 10px 3px 7px;
  background: none;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 10.4px;
  color: var(--ink-2);
  white-space: nowrap;
  cursor: pointer;
}
.lamp-item:not(.on):hover {
  background: var(--card);
  border-color: var(--line-strong);
  color: var(--ink);
}
.lamp-item:disabled {
  cursor: wait;
  opacity: 0.6;
}
/* 켜진(지금) 모드는 다시 눌러도 바뀌는 게 없어서 기본 화살표 */
.lamp-item.on {
  cursor: default;
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

/* ---- 칸: 위에서 아래로 이름(현재/추천) · 동작(크게) · kW, 가운데 정렬 ---- */
.compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36px minmax(0, 1fr);
  align-items: stretch;
}
.box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 8px;
  text-align: center;
}
/* 현재 칸: 카드보다 한 톤 다른 바탕 */
.box.now {
  background: var(--node-fill);
}
/* 추천 칸: 답할 추천이면 색 테두리 (테두리 두께만큼 안쪽 여백을 줄여 두 칸 크기를 맞춤) */
.box.next.active {
  padding: 4px 6px;
  border: 2px solid var(--ink);
}
.box.next.active.charge {
  border-color: var(--gauge-charge);
}
.box.next.active.discharge {
  border-color: var(--gauge-discharge);
}
/* 추천 칸: 할 일 없음·넘김·추천 없음은 점선 + 옅은 글자 (지금 할 일이 아님) */
.box.next.quiet {
  padding: 4.5px 6.5px;
  border: 1.5px dashed var(--line-strong);
}
.quiet .big {
  color: var(--ink-2);
}
.caption {
  font-size: 10.4px;
  font-weight: 700;
  color: var(--mute);
}
.big {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.sub {
  min-height: 1.4em; /* 대기처럼 kW를 적지 않아도 줄 높이를 남겨 두 칸 높이를 맞춤 */
  font-size: 12px;
  line-height: 1.4;
  color: var(--ink-2);
}
/* 충전 파랑 · 방전 주황 (KPI 계기판의 ESS 충전·방전 색과 같음). 대기는 기본 글자색 */
.charge .big,
.active.charge .caption {
  color: var(--gauge-charge);
}
.discharge .big,
.active.discharge .caption {
  color: var(--gauge-discharge);
}
.arrow {
  width: 22px;
  height: 22px;
  align-self: center;
  justify-self: center;
  fill: none;
  stroke: var(--ink);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 하면 무엇이 좋은지 / 안내 문장 */
.benefit {
  font-size: 12px;
  line-height: 1.5;
  color: var(--ink-2);
}

/* ---- 적용 중: 현재 칸을 줄 전체로 넓게 ---- */
.box.wide {
  padding: 10px 8px;
}
.box.wide .big {
  font-size: 28px;
}
.running {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.running-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.2px;
}
/* 적용 중 표시: 테두리만 둔 작은 표시 (도형 + 글자) */
.on-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 8px;
  border: 1.5px solid var(--ok);
  border-radius: 999px;
  font-weight: 700;
  color: var(--ok);
}
.on-pill .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ok);
}
.who {
  color: var(--mute);
}
.left {
  margin-left: auto;
  color: var(--ink-2);
}
.track {
  height: 6px;
  background: var(--bg);
  border-radius: 3px;
}
.fill {
  height: 100%;
  background: var(--ink);
  border-radius: 3px;
}
.charge .fill {
  background: var(--gauge-charge);
}
.discharge .fill {
  background: var(--gauge-discharge);
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
  height: 34px;
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
  height: 34px;
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

/* ---- 운영 모드 확인 팝업 ---- */
.mode-dialog {
  width: min(440px, calc(100vw - 32px));
  padding: 0;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  color: var(--ink);
}
/* 뒤 화면을 어둡게 (그림자 대신 반투명 덮개) */
.mode-dialog::backdrop {
  background: rgba(20, 24, 28, 0.4);
}
.dialog-box {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 22px 18px;
}
.dialog-title {
  font-size: 17px;
  font-weight: 700;
}
/* 지금 모드 → 바꿀 모드 */
.dialog-change {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}
.dialog-mode {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.dialog-mode.to {
  background: var(--card);
  border: 2px solid var(--ink);
}
.dialog-mode strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.dialog-mode.from strong {
  font-weight: 400;
  color: var(--ink-2);
}
.dialog-caption {
  font-size: 11px;
  color: var(--mute);
}
/* 모드 등 색: 운영자 승인 = 주황, 자동 = 초록 (카드 위 신호등과 같음) */
.dialog-mode .lamp {
  flex: none;
}
.dialog-mode.manual .lamp {
  background: var(--warn);
}
.dialog-mode.auto .lamp {
  background: var(--ok);
}
.dialog-arrow {
  font-size: 18px;
  color: var(--mute);
}
.dialog-desc {
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-2);
}
.dialog-warn {
  padding: 8px 10px;
  background: color-mix(in srgb, var(--warn) 10%, var(--card));
  border-left: 3px solid var(--warn);
  font-size: 12px;
  color: var(--ink);
}
.dialog-warn > span {
  color: var(--warn);
}
.dialog-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}
.dialog-cancel,
.dialog-ok {
  height: 36px;
  padding: 0 16px;
  border-radius: 4px;
  font-size: 13px;
}
.dialog-cancel {
  background: var(--card);
  border: 1px solid var(--line-strong);
  color: var(--ink-2);
}
.dialog-cancel:hover {
  border-color: var(--ink);
  color: var(--ink);
}
.dialog-ok {
  background: var(--ink);
  border: 0;
  font-weight: 700;
  color: var(--card);
}
.dialog-ok:hover {
  background: var(--ink-2);
}
</style>
