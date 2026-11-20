<script setup>
// 첫 로딩 스피너: 화면 전체를 덮고 가운데에 도는 원 + 글자를 보여 줌
// 처음 들어가거나 새로 고친 뒤 첫 값을 받기 전에, 빈 카드만 보여 화면이 멈춘 것처럼 보이지 않게 함
// 덮을 영역(부모)에 position: relative 가 있어야 함
// 사용 예: <LoadingSpinner v-if="!status" text="실시간 데이터를 불러오는 중입니다." />
defineProps({
  text: { type: String, default: '불러오는 중입니다.' },
})
</script>

<template>
  <div class="cover">
    <span class="loading" role="status" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <span>{{ text }}</span>
    </span>
  </div>
</template>

<style scoped>
/* 뒤의 빈 카드가 흐리게 보이고, 가운데에 스피너 */
.cover {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--label-bg);
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--ink-2);
  font-size: 15px;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--line-strong);
  border-top-color: var(--ink-2);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 움직임 줄이기를 켠 사용자는 천천히 돌게 함 */
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}
</style>
