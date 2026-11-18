<!--
  아직 만들지 않은 화면에 보여 주는 안내
  메뉴에는 이름이 있지만 화면이 없는 경우, router/index.js에서 이 페이지를 연결합니다.
  제목은 routes의 meta(parent, title)에서 가져옵니다.
-->
<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">{{ route.meta.parent }}</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">{{ route.meta.title }}</h1>
    </div>

    <div class="card empty">
      <p class="message">이 화면은 아직 준비 중입니다.</p>
      <RouterLink to="/" class="link">모니터링 통합으로 돌아가기</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 20px;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-height: 240px;
}
.message {
  font-size: 18px;
  color: var(--ink-2);
}
.link {
  border-bottom: 1px solid var(--ink-2);
  color: var(--ink-2);
}
</style>
