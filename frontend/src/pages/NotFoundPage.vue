<!-- 없는 주소로 들어왔을 때 보여 주는 안내 (왼쪽 메뉴는 그대로 보임) -->
<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="page">
    <div class="crumb">
      <h1 class="crumb-title">페이지를 찾을 수 없습니다</h1>
    </div>

    <div class="card empty">
      <p class="message">
        <span class="path">{{ route.path }}</span> 주소에는 화면이 없습니다.
      </p>
      <p class="hint">주소가 바뀌었을 수 있습니다. 왼쪽 메뉴에서 화면을 골라 주세요.</p>
      <RouterLink to="/" class="link">모니터링 통합으로 가기</RouterLink>
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
  gap: 8px;
  flex: 1;
  min-height: 240px;
}
.message {
  font-size: 18px;
  color: var(--ink-2);
}
.path {
  font-weight: 700;
  color: var(--ink);
  overflow-wrap: anywhere;
}
.hint {
  color: var(--mute);
}
.link {
  margin-top: 4px;
  border-bottom: 1px solid var(--ink-2);
  color: var(--ink-2);
}
</style>
