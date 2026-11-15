<!-- 여러 페이지가 공유하는 공통 틀: 위쪽 헤더 + 아래 페이지 내용 -->
<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatClock } from '@/utils/format'

const realtimeStore = useRealtimeStore()

const POLL_INTERVAL_MS = 5000 // 폴링 주기 (시뮬레이터 주기와 같음, 확인 필요)
let timer = null

// 최신 상태는 헤더(시각, 날씨, 연결 표시등)가 모든 화면에서 쓰므로 여기서 한 번만 폴링함
onMounted(() => {
  realtimeStore.fetchLatest()
  timer = setInterval(() => realtimeStore.fetchLatest(), POLL_INTERVAL_MS)
})

// 화면을 떠날 때 폴링을 반드시 멈춤
onBeforeUnmount(() => {
  clearInterval(timer)
})

const menus = [
  { to: '/', label: '실시간 관제' },
  { to: '/history', label: '운영 이력' },
  { to: '/report', label: '성적표' },
  { to: '/settings', label: '설정' },
]
</script>

<template>
  <div class="shell">
    <header class="header">
      <RouterLink to="/" class="brand">WATTever</RouterLink>

      <nav class="menu" aria-label="주 메뉴">
        <RouterLink v-for="menu in menus" :key="menu.to" :to="menu.to" class="menu-link">
          {{ menu.label }}
        </RouterLink>
      </nav>

      <div class="info">
        <!-- 시각은 브라우저 시계가 아니라 서버가 보낸 값(recordedAt)을 씀 -->
        <span class="clock num">{{ formatClock(realtimeStore.status?.recordedAt) }}</span>
        <span v-if="realtimeStore.status" class="weather">
          {{ realtimeStore.status.weather.text }}
          <span class="num">{{ realtimeStore.status.weather.tempC }}℃</span>
        </span>
        <span v-if="realtimeStore.connected" class="connection on">
          <span aria-hidden="true">●</span> 연결됨
        </span>
        <span v-else class="connection off"><span aria-hidden="true">◆</span> 연결 안 됨</span>
      </div>
    </header>

    <!-- 각 페이지 내용은 <RouterView /> 자리에 표시됩니다 -->
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.header {
  display: flex;
  align-items: center;
  gap: 32px;
  flex: none;
  height: 56px;
  padding: 0 24px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
}
.brand {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.menu {
  display: flex;
  align-self: stretch;
  gap: 4px;
}
.menu-link {
  display: flex;
  align-items: center;
  padding: 0 14px;
  font-size: 15px;
  color: var(--ink-2);
  border-bottom: 3px solid transparent;
  transition: color 0.15s;
}
.menu-link:hover {
  color: var(--ink);
}
/* 현재 페이지 메뉴 (Vue Router가 자동으로 붙여 주는 클래스) */
.menu-link.router-link-exact-active {
  font-weight: 700;
  color: var(--accent);
  border-bottom-color: var(--accent);
}
.info {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-left: auto;
}
.clock {
  font-size: 16px;
  font-weight: 700;
}
.weather {
  color: var(--ink-2);
}
.connection {
  font-weight: 700;
}
.connection.on {
  color: var(--ok);
}
.connection.off {
  color: var(--crit);
}
.content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
