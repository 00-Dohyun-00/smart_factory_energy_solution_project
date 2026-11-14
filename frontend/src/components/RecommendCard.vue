<!--
  ESS 추천 카드 (충전 / 방전 / 대기)
  추천 판단은 백엔드가 하고, 여기서는 보여 주고 [적용] [무시]만 받습니다.
-->
<script setup>
import { computed } from 'vue'
import { formatClock, formatKw } from '@/utils/format'

const props = defineProps({
  // 최신 추천 1건 (없으면 null)
  decision: { type: Object, default: null },
  // 적용/무시 요청을 보내는 중이면 true (버튼 중복 클릭 방지)
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['apply', 'ignore'])

const ACTION_MAP = {
  CHARGE: { label: '충전', className: 'charge' },
  DISCHARGE: { label: '방전', className: 'discharge' },
  IDLE: { label: '대기', className: 'idle' },
}

const action = computed(() => ACTION_MAP[props.decision?.action] ?? null)

// 대기 추천은 적용할 동작이 없으므로 버튼을 보여 주지 않음
const needsAnswer = computed(
  () => props.decision?.status === 'RECOMMENDED' && props.decision?.action !== 'IDLE',
)
</script>

<template>
  <section class="card reco">
    <div class="reco-head">
      <h2 class="card-title">ESS 추천</h2>
      <span class="tag">가상</span>
      <span class="mode">운영자 승인 모드</span>
    </div>

    <p v-if="!decision" class="empty">아직 받은 추천이 없습니다.</p>

    <template v-else>
      <p class="action" :class="action.className">
        {{ action.label }}
        <template v-if="decision.action !== 'IDLE'">
          <span class="num">{{ formatKw(decision.powerKw) }}</span>
          <span class="action-unit">kW</span>
          <span v-if="decision.durationMinutes" class="duration num">
            {{ decision.durationMinutes }}분
          </span>
        </template>
      </p>

      <p class="reason">{{ decision.reason }}</p>

      <dl class="used">
        <div v-for="item in decision.usedValues" :key="item.label" class="used-item">
          <dt>{{ item.label }}</dt>
          <dd class="num">{{ formatKw(item.value) }} {{ item.unit }}</dd>
        </div>
      </dl>

      <div class="footer">
        <div v-if="needsAnswer" class="buttons">
          <button class="button primary" :disabled="busy" @click="emit('apply')">적용</button>
          <button class="button" :disabled="busy" @click="emit('ignore')">무시</button>
        </div>
        <p v-else-if="decision.status === 'APPLIED'" class="result applied">
          적용함 (시뮬레이션) · {{ decision.appliedBy }}
          <span class="num">{{ formatClock(decision.appliedAt) }}</span>
        </p>
        <p v-else-if="decision.status === 'IGNORED'" class="result">무시함</p>
        <p v-else class="result">지금은 조치할 것이 없습니다.</p>

        <span class="decided num">추천 시각 {{ formatClock(decision.decidedAt) }}</span>
      </div>
    </template>
  </section>
</template>

<style scoped>
.reco {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}
.reco-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mode {
  margin-left: auto;
  font-size: 12px;
  color: var(--mute);
}
.empty {
  color: var(--mute);
}

.action {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.action.discharge,
.action.charge {
  color: var(--src-ess);
}
.action.idle {
  color: var(--ink-2);
}
.action-unit {
  margin-left: 2px;
  font-size: 15px;
  font-weight: 400;
}
.duration {
  margin-left: 10px;
  font-size: 18px;
  color: var(--ink-2);
}
.reason {
  color: var(--ink-2);
}

.used {
  display: grid;
  grid-template-columns: repeat(4, auto);
  justify-content: start;
  gap: 4px 20px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.used-item dt {
  font-size: 12px;
  color: var(--mute);
}
.used-item dd {
  font-weight: 700;
}

.footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
}
.buttons {
  display: flex;
  gap: 8px;
}
.button {
  min-width: 88px;
  padding: 8px 20px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  font-weight: 700;
  color: var(--ink-2);
  transition: background-color 0.15s;
}
.button:hover {
  background: var(--bg);
}
.button.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--card);
}
.button.primary:hover {
  filter: brightness(0.92);
}
.button:disabled {
  opacity: 0.5;
  cursor: default;
}
.result {
  color: var(--ink-2);
}
.result.applied {
  font-weight: 700;
  color: var(--src-ess);
}
.decided {
  margin-left: auto;
  font-size: 12px;
  color: var(--mute);
}
</style>
