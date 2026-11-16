<!-- 모든 화면이 공유하는 틀: 왼쪽 메뉴 + 오른쪽 화면 내용 -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { formatClock } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const route = useRoute()

const POLL_INTERVAL_MS = 5000 // 폴링 주기 (시뮬레이터 주기와 같음, 확인 필요)
let timer = null

// 최신 상태는 왼쪽 메뉴(시각, 날씨, 연결 표시등)가 모든 화면에서 쓰므로 여기서 한 번만 폴링함
onMounted(() => {
  realtimeStore.fetchLatest()
  timer = setInterval(() => realtimeStore.fetchLatest(), POLL_INTERVAL_MS)
})

// 화면을 떠날 때 폴링을 반드시 멈춤
onBeforeUnmount(() => {
  clearInterval(timer)
})

// 왼쪽 메뉴. 하위 화면은 만들 때마다 children에 한 줄씩 추가합니다.
const menus = [
  {
    label: '모니터링',
    children: [
      { label: '통합', to: '/' },
      { label: '계통도', to: '/monitoring/diagram' },
    ],
  },
  { label: '분석', children: [{ label: '피크 분석', to: '/analysis/peak' }] },
  { label: '보고서', children: [{ label: '월간 성적표', to: '/report' }] },
  { label: '로그', children: [{ label: '추천 이력', to: '/logs' }] },
  { label: '환경 설정', children: [{ label: '기준값', to: '/settings' }] },
]

// 왼쪽 메뉴 전체를 접었는지. 접으면 좁은 띠만 남고 본문이 넓어짐
// 새로 고쳐도 접은 상태가 유지되도록 브라우저 저장소(localStorage)에 적어 둠
const STORAGE_KEY = 'wattever.menuCollapsed'

function readCollapsed() {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false // 저장소를 쓸 수 없는 브라우저에서는 펼친 상태로 시작
  }
}

const collapsed = ref(readCollapsed())

watch(collapsed, (value) => {
  try {
    localStorage.setItem(STORAGE_KEY, value ? '1' : '0')
  } catch {
    // 저장이 막혀 있어도 화면은 그대로 동작하므로 넘어감
  }
})

// 연결이 끊겼을 때 [지금 다시 시도] 버튼
const retrying = ref(false)

async function retryNow() {
  retrying.value = true
  await realtimeStore.fetchLatest() // 성공하면 Store가 connected를 true로 바꿔 알림이 사라짐
  retrying.value = false
}

// 펼쳐진 메뉴 이름들. 지금 보고 있는 화면이 속한 메뉴는 자동으로 펼침
const opened = ref([])

function toggle(label) {
  if (opened.value.includes(label)) {
    opened.value = opened.value.filter((item) => item !== label)
  } else {
    opened.value = [...opened.value, label]
  }
}

watch(
  () => route.path,
  (path) => {
    const current = menus.find((menu) => menu.children.some((child) => child.to === path))
    if (current && !opened.value.includes(current.label)) {
      opened.value = [...opened.value, current.label]
    }
  },
  { immediate: true },
)

// 다른 화면으로 옮기면 맨 위부터 보이게 함 (이전 화면에서 내려 둔 스크롤 위치가 남지 않도록)
const contentBox = ref(null)

watch(
  () => route.path,
  () => {
    if (contentBox.value) contentBox.value.scrollTop = 0
  },
)

// "통합" 옆에 지금 피크 단계를 도형으로 표시 (정상일 때는 표시하지 않음)
const peakMark = computed(() => {
  if (!realtimeStore.connected) return null
  const status = realtimeStore.status?.peakStatus
  if (status === 'CRITICAL') return { shape: '■', className: 'crit', label: '경고' }
  if (status === 'WARNING') return { shape: '▲', className: 'warn', label: '주의' }
  return null
})
</script>

<template>
  <div class="shell">
    <aside class="side" :class="{ collapsed }">
      <div class="side-head">
        <!-- 왼쪽 메뉴를 접었다 펴는 버튼 (줄 세 개 모양) -->
        <button
          class="fold"
          :aria-expanded="!collapsed"
          :aria-label="collapsed ? '메뉴 펴기' : '메뉴 접기'"
          :title="collapsed ? '메뉴 펴기' : '메뉴 접기'"
          @click="collapsed = !collapsed"
        >
          <span class="fold-line" aria-hidden="true"></span>
          <span class="fold-line" aria-hidden="true"></span>
          <span class="fold-line" aria-hidden="true"></span>
        </button>
        <RouterLink v-if="!collapsed" to="/" class="brand">WATTever</RouterLink>
      </div>

      <!-- 접었을 때: 메뉴 대신 지금 피크 단계와 연결 상태만 도형으로 보여 줌 -->
      <div v-if="collapsed" class="rail">
        <span
          v-if="peakMark"
          class="mark"
          :class="peakMark.className"
          role="img"
          :aria-label="`피크 단계 ${peakMark.label}`"
          :title="`피크 단계 ${peakMark.label}`"
        >
          {{ peakMark.shape }}
        </span>
        <span
          class="rail-connection"
          :class="realtimeStore.connected ? 'on' : 'off'"
          role="img"
          :aria-label="realtimeStore.connected ? '서버 연결됨' : '서버 연결 안 됨'"
          :title="realtimeStore.connected ? '서버 연결됨' : '서버 연결 안 됨'"
        >
          {{ realtimeStore.connected ? '●' : '◆' }}
        </span>
      </div>

      <nav v-if="!collapsed" class="menu" aria-label="주 메뉴">
        <div v-for="menu in menus" :key="menu.label">
          <button
            class="menu-button"
            :class="{ open: opened.includes(menu.label) }"
            :aria-expanded="opened.includes(menu.label)"
            @click="toggle(menu.label)"
          >
            <span class="arrow" aria-hidden="true">{{
              opened.includes(menu.label) ? '▾' : '▸'
            }}</span>
            {{ menu.label }}
          </button>

          <ul v-if="opened.includes(menu.label)">
            <li v-for="child in menu.children" :key="child.to">
              <RouterLink :to="child.to" class="sub-link">
                {{ child.label }}
                <span
                  v-if="child.to === '/' && peakMark"
                  class="mark"
                  :class="peakMark.className"
                  role="img"
                  :aria-label="`피크 단계 ${peakMark.label}`"
                  :title="`피크 단계 ${peakMark.label}`"
                >
                  {{ peakMark.shape }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </nav>

      <div v-if="!collapsed" class="foot">
        <!-- 시각은 브라우저 시계가 아니라 서버가 보낸 값(recordedAt)을 씀 -->
        <p class="clock num">{{ formatClock(realtimeStore.status?.recordedAt) }}</p>
        <p v-if="realtimeStore.status" class="weather">
          {{ realtimeStore.status.weather.text }}
          <span class="num">{{ realtimeStore.status.weather.tempC }}℃</span>
        </p>
        <p v-if="realtimeStore.connected" class="connection on">
          <span aria-hidden="true">●</span> 연결됨
        </p>
        <p v-else class="connection off"><span aria-hidden="true">◆</span> 연결 안 됨</p>
      </div>
    </aside>

    <!-- 각 화면 내용은 <RouterView /> 자리에 표시됩니다 -->
    <main ref="contentBox" class="content">
      <!--
        서버 연결이 끊기면 어느 화면에서든 같은 자리에 알림을 띄움
        (화면의 값은 지우지 않고 마지막으로 받은 값을 그대로 둠)
      -->
      <div v-if="!realtimeStore.connected" class="notice" role="alert">
        <p>
          <span aria-hidden="true">◆</span>
          서버와 연결되지 않습니다.
          <template v-if="realtimeStore.status">
            화면의 값은
            <span class="num">{{ formatClock(realtimeStore.status.recordedAt) }}</span>에 마지막으로
            받은 값입니다.
          </template>
          <template v-else>아직 값을 한 번도 받지 못했습니다.</template>
          <span class="notice-sub">5초마다 자동으로 다시 연결합니다.</span>
        </p>
        <button class="notice-button" :disabled="retrying" @click="retryNow">
          {{ retrying ? '연결하는 중…' : '지금 다시 시도' }}
        </button>
      </div>

      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100%;
}

/* 왼쪽 메뉴 */
.side {
  display: flex;
  flex-direction: column;
  flex: none;
  width: 220px;
  padding: 12px 0 16px;
  background: var(--card);
  border-right: 1px solid var(--line);
}
/* 접었을 때는 버튼만 들어갈 좁은 띠 */
.side.collapsed {
  width: 56px;
}
.side-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px 12px;
}
.fold {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  flex: none;
  width: 36px;
  height: 36px;
  padding: 0 9px;
  background: none;
  border: 0;
}
.fold:hover {
  background: var(--bg);
}
.fold-line {
  display: block;
  height: 2px;
  background: var(--ink);
}
.rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}
.rail .mark {
  margin-left: 0;
  font-size: 14px;
}
.rail-connection {
  font-size: 12px;
}
.rail-connection.on {
  color: var(--ok);
}
.rail-connection.off {
  color: var(--crit);
}
.brand {
  padding: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.menu {
  flex: 1;
  overflow: auto;
}
.menu-button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 11px 16px 11px 20px;
  background: none;
  border: 0;
  font-size: 15px;
  color: var(--ink-2);
  text-align: left;
}
.menu-button:hover {
  background: var(--bg);
}
.menu-button.open {
  font-weight: 700;
  color: var(--ink);
}
.arrow {
  width: 12px;
  font-size: 11px;
  color: var(--mute);
}
.sub-link {
  display: flex;
  align-items: center;
  padding: 9px 16px 9px 40px;
  color: var(--ink-2);
}
.sub-link:hover {
  background: var(--bg);
}
/* 지금 보고 있는 화면 (Vue Router가 자동으로 붙여 주는 클래스) */
.sub-link.router-link-exact-active {
  padding-left: 36px;
  background: var(--bg);
  border-left: 4px solid var(--ink);
  font-weight: 700;
  color: var(--ink);
}
.mark {
  margin-left: auto;
  font-size: 12px;
}
.mark.crit {
  color: var(--crit);
}
.mark.warn {
  color: var(--warn);
}

.foot {
  padding: 12px 20px 0;
}
.clock {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}
.weather {
  font-size: 13px;
  color: var(--ink-2);
}
.connection {
  font-size: 13px;
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
  min-width: 0;
  overflow: auto;
}

/* 연결 끊김 알림 */
.notice {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 20px 20px 0;
  padding: 10px 14px;
  background: var(--card);
  border: 1px solid var(--crit);
  font-weight: 700;
  color: var(--crit);
}
.notice-sub {
  margin-left: 6px;
  font-weight: 400;
  color: var(--ink-2);
}
.notice-button {
  flex: none;
  height: 32px;
  margin-left: auto;
  padding: 0 14px;
  background: var(--card);
  border: 1px solid var(--ink);
  color: var(--ink);
}
.notice-button:hover:not(:disabled) {
  background: var(--bg);
}
.notice-button:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 인쇄할 때: 왼쪽 메뉴와 알림은 빼고 화면 내용만, 스크롤로 잘린 부분 없이 끝까지 나오게 함 */
@media print {
  .side,
  .notice {
    display: none;
  }
  .shell {
    display: block;
    height: auto;
  }
  .content {
    overflow: visible;
  }
}
</style>
