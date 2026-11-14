<!--
  상태 표시. 색만으로 구분하지 않도록 항상 도형 + 글자를 함께 보여 줍니다.
  사용 예: <StatusChip status="WARNING" />
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  // NORMAL(정상) | WARNING(주의) | CRITICAL(경고) | LOST(통신 단절)
  status: { type: String, default: 'LOST' },
})

const STATUS_MAP = {
  NORMAL: { shape: '●', label: '정상', className: 'ok' },
  WARNING: { shape: '▲', label: '주의', className: 'warn' },
  CRITICAL: { shape: '■', label: '경고', className: 'crit' },
  LOST: { shape: '◆', label: '통신 단절', className: 'lost' },
}

const info = computed(() => STATUS_MAP[props.status] ?? STATUS_MAP.LOST)
</script>

<template>
  <span class="chip" :class="info.className">
    <span aria-hidden="true">{{ info.shape }}</span>
    {{ info.label }}
  </span>
</template>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 700;
  white-space: nowrap;
}
.ok {
  color: var(--ok);
}
.warn {
  color: var(--warn);
}
.crit {
  color: var(--crit);
}
.lost {
  color: var(--lost);
}
</style>
