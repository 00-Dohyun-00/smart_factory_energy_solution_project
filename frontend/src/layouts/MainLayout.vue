<!-- 모든 화면이 공유하는 틀: 왼쪽 메뉴 + 오른쪽 화면 내용 -->
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { useThemeStore } from '@/stores/useThemeStore'
import WeatherIcon from '@/components/WeatherIcon.vue'
import { formatClock } from '@/utils/format'

const realtimeStore = useRealtimeStore()
const route = useRoute()

const settingsStore = useSettingsStore()
const themeStore = useThemeStore() // 라이트 / 다크 테마 (아래쪽 버튼으로 바꿈)

// 폴링 주기(초). 환경 설정의 "화면 갱신 주기"를 쓰고, 아직 못 받았으면 5초
const pollSeconds = computed(() => settingsStore.values['operation.poll_seconds'] ?? 5)
let timer = null

// 주기에 맞춰 최신 상태를 반복해서 받음. 주기가 바뀌면 이전 반복을 멈추고 새로 시작함
function startPolling() {
  clearInterval(timer)
  timer = setInterval(() => realtimeStore.fetchLatest(), pollSeconds.value * 1000)
}

// 최신 상태는 왼쪽 메뉴(시각, 날씨, 연결 표시등)가 모든 화면에서 쓰므로 여기서 한 번만 폴링함
onMounted(() => {
  realtimeStore.fetchLatest()
  settingsStore.fetchSettings()
  startPolling()
})

watch(pollSeconds, startPolling)

// 화면을 떠날 때 폴링을 반드시 멈춤
onBeforeUnmount(() => {
  clearInterval(timer)
})

// ---- 화면 전체를 비율 그대로 줄여 창에 맞추기 ----
// 화면은 가로 1920px 기준으로 만들었습니다. 창 크기가 다르면 배치는 그대로 두고 같은 비율로 줄여
// 글자 줄바꿈이나 그래프가 카드를 넘는 일 없이 보이게 합니다. 두 단계로 줄입니다.
//   1) 틀 전체(왼쪽 메뉴 + 본문)를 창 너비에 맞춰 줄임 → 창 크기로만 정해지므로 왼쪽 메뉴는 어느 화면에서나 같은 크기·위치
//   2) 그래도 세로로 넘치면 오른쪽 본문만 더 줄여 한 화면에 다 들어가게 함 (오른쪽에 빈 여백이 생김)
// 월간 성적표는 2)를 하지 않고 세로로 스크롤합니다.
const DESIGN_WIDTH = 1920 // 기준 가로 크기 (px)
const SCROLL_PAGES = ['/report'] // 세로로 길어도 되는 화면 (스크롤 허용)
// 화면(.page)의 기준 높이(px). 성적표를 뺀 모든 화면은 보이는 높이를 이 높이로 보고 같은 비율로 줄임
// → 어느 화면에서나 배율이 같아 공통 카드(KPI, 기간 선택 줄, 요약)가 같은 크기·같은 위치에 보임
// 가로는 줄인 만큼 넓게 그려서 화면이 사이드바를 뺀 본문 전체를 꽉 채움
const PAGE_HEIGHT = 1170
const shellBox = ref(null) // 틀 전체 (1단계로 줄이는 상자)
const stageBox = ref(null) // 본문 안쪽 상자 (2단계로 줄이는 상자)
const isScrollPage = computed(() => SCROLL_PAGES.includes(route.path))
let pageObserver = null

// 1단계: 틀 전체를 창 너비에 맞춤 (가로 배치는 항상 1920px)
function fitShell() {
  const scale = window.innerWidth / DESIGN_WIDTH
  const box = shellBox.value
  box.style.width = `${DESIGN_WIDTH}px`
  box.style.height = `${window.innerHeight / scale}px`
  box.style.transform = `scale(${scale})`
}

// 2단계: 본문 상자를 scale 배율로 줄이되, 줄인 뒤에도 본문 전체(사이드바를 뺀 영역)를 꽉 채우도록
// 가로·세로를 "보이는 크기 ÷ 배율"로 그림. 사이드바를 접으면 본문이 넓어진 만큼 화면도 넓어짐
function applyStageScale(scale) {
  const stage = stageBox.value
  stage.style.width = `${contentBox.value.clientWidth / scale}px`
  stage.style.height = `${contentBox.value.clientHeight / scale}px`
  stage.style.transform = scale < 1 ? `scale(${scale})` : ''
}

function fitToWindow() {
  if (!shellBox.value || !stageBox.value || !contentBox.value) return
  fitShell()

  // 성적표: 본문은 줄이지 않고 내용 길이대로 두어 스크롤함 (가운데 정렬은 성적표 화면이 스스로 함)
  if (isScrollPage.value) {
    stageBox.value.style.width = ''
    stageBox.value.style.height = ''
    stageBox.value.style.transform = ''
    return
  }

  // 보이는 높이를 기준 높이로 보고 줄임 (창이 충분히 크면 줄이지 않음)
  let scale = Math.min(1, contentBox.value.clientHeight / PAGE_HEIGHT)
  applyStageScale(scale)

  // 어떤 화면의 내용이 그래도 넘치면 그 화면만 조금 더 줄임 (반올림 오차도 함께 정리)
  for (let tries = 0; tries < 5; tries++) {
    const contentHeight = stageBox.value.scrollHeight // 화면 내용 전체 높이
    const boxHeight = stageBox.value.clientHeight // 지금 상자 높이
    if (contentHeight <= boxHeight) break
    scale = (scale * boxHeight) / (contentHeight + 2)
    applyStageScale(scale)
  }
}

// 지금 화면 내용이나 본문 영역의 크기가 바뀌면(데이터 도착, 사이드바 접기 등) 다시 맞춤
function observePage() {
  pageObserver?.disconnect()
  const page = stageBox.value?.lastElementChild
  if (page) pageObserver.observe(page)
  // 본문 영역 크기가 바뀌어도(사이드바 접기·펴기) 다시 맞춤
  if (contentBox.value) pageObserver.observe(contentBox.value)
  fitToWindow()
}

onMounted(() => {
  pageObserver = new ResizeObserver(() => fitToWindow())
  observePage()
  window.addEventListener('resize', fitToWindow)
})

onBeforeUnmount(() => {
  pageObserver?.disconnect()
  window.removeEventListener('resize', fitToWindow)
})

// 자동 모드인지 (실제 제어가 아니라 시뮬레이션이라는 점을 함께 알림)
const isAuto = computed(() => settingsStore.values['operation.auto_mode'] === true)

// 왼쪽 메뉴. 하위 화면은 만들 때마다 children에 한 줄씩 추가합니다.
// 환경 설정은 자주 쓰지 않으므로 이 목록에서 빼고 메뉴 맨 아래에 따로 둡니다.
const menus = [
  {
    label: '모니터링',
    children: [
      { label: '통합', to: '/' },
      { label: '설비 모니터링', to: '/monitoring/diagram' },
    ],
  },
  {
    label: '분석',
    children: [
      { label: '추이 분석', to: '/analysis/trend' },
      { label: '피크 분석', to: '/analysis/peak' },
    ],
  },
  { label: '보고서', children: [{ label: '월간 성적표', to: '/report' }] },
  { label: '운영 로그', children: [{ label: '추천 이력', to: '/logs' }] },
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
  async () => {
    if (contentBox.value) contentBox.value.scrollTop = 0
    // 새 화면이 그려진 뒤 그 화면 크기에 맞춰 다시 줄임
    await nextTick()
    observePage()
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
  <!-- viewport: 창 크기 그대로의 바깥 상자. 안의 shell을 비율대로 줄여 이 상자를 꽉 채움 -->
  <div class="viewport">
    <div ref="shellBox" class="shell">
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

        <!-- 자동 모드는 어느 화면에서든 바로 보이게 메뉴 맨 위에 둠 (실제 제어가 아니라 시뮬레이션) -->
        <p v-if="isAuto && !collapsed" class="auto-badge">
          자동 모드 <span class="auto-sub">시뮬레이션</span>
        </p>

        <!-- 접었을 때: 메뉴 대신 자동 모드, 지금 피크 단계, 연결 상태만 짧게 보여 줌 -->
        <div v-if="collapsed" class="rail">
          <span
            v-if="isAuto"
            class="rail-auto"
            role="img"
            aria-label="자동 모드 (시뮬레이션)"
            title="자동 모드 (시뮬레이션)"
          >
            자동
          </span>
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
          <!-- 접었을 때도 날씨는 아이콘으로 보여 줌 (마우스를 올리면 글자와 기온) -->
          <span
            v-if="realtimeStore.status"
            class="rail-weather"
            :title="`${realtimeStore.status.weather.text} ${realtimeStore.status.weather.tempC}℃`"
          >
            <WeatherIcon :text="realtimeStore.status.weather.text" :size="22" />
          </span>
          <!-- 접었을 때도 라이트/다크를 바꿀 수 있게 -->
          <button
            class="rail-theme"
            :aria-label="
              themeStore.theme === 'dark' ? '라이트 모드로 바꾸기' : '다크 모드로 바꾸기'
            "
            :title="themeStore.theme === 'dark' ? '라이트 모드로 바꾸기' : '다크 모드로 바꾸기'"
            @click="themeStore.toggle()"
          >
            {{ themeStore.theme === 'dark' ? '☀' : '◐' }}
          </button>
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
          <!-- 환경 설정 링크와 라이트/다크 전환 버튼을 한 줄에 -->
          <div class="foot-top">
            <RouterLink to="/settings" class="settings-link">환경 설정</RouterLink>
            <button
              class="theme-button"
              :aria-label="
                themeStore.theme === 'dark' ? '라이트 모드로 바꾸기' : '다크 모드로 바꾸기'
              "
              :title="themeStore.theme === 'dark' ? '라이트 모드로 바꾸기' : '다크 모드로 바꾸기'"
              @click="themeStore.toggle()"
            >
              <span aria-hidden="true">{{ themeStore.theme === 'dark' ? '☀' : '◐' }}</span>
              {{ themeStore.theme === 'dark' ? '라이트' : '다크' }}
            </button>
          </div>
          <!-- 시각은 브라우저 시계가 아니라 서버가 보낸 값(recordedAt)을 씀 -->
          <p class="clock num">{{ formatClock(realtimeStore.status?.recordedAt) }}</p>
          <!-- 시계가 실제보다 빨리 가는 이유를 알려 줌 (시뮬레이션이 배속으로 돌 때만) -->
          <p v-if="realtimeStore.status?.simulationSpeed > 1" class="speed">
            시뮬레이션 <span class="num">{{ realtimeStore.status.simulationSpeed }}</span>배속
          </p>
          <!-- 날씨: 아이콘 + 기온 + 날씨 글자 (글자도 함께 적어 아이콘을 모르는 사람도 알 수 있게) -->
          <p v-if="realtimeStore.status" class="weather">
            <WeatherIcon :text="realtimeStore.status.weather.text" :size="30" />
            <span class="weather-temp num">{{ realtimeStore.status.weather.tempC }}℃</span>
            <span class="weather-text">{{ realtimeStore.status.weather.text }}</span>
          </p>
          <p v-if="realtimeStore.connected" class="connection on">
            <span aria-hidden="true">●</span> 연결됨
          </p>
          <p v-else class="connection off"><span aria-hidden="true">◆</span> 연결 안 됨</p>
        </div>
      </aside>

      <!-- 각 화면 내용은 <RouterView /> 자리에 표시됩니다 -->
      <main ref="contentBox" class="content" :class="{ fit: !isScrollPage }">
        <!-- stage: 화면이 세로로 넘칠 때 이 상자만 줄여 한 화면에 넣음 (fitToWindow 참고) -->
        <div ref="stageBox" class="stage">
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
                <span class="num">{{ formatClock(realtimeStore.status.recordedAt) }}</span>에
                마지막으로 받은 값입니다.
              </template>
              <template v-else>아직 값을 한 번도 받지 못했습니다.</template>
              <span class="notice-sub">5초마다 자동으로 다시 연결합니다.</span>
            </p>
            <button class="notice-button" :disabled="retrying" @click="retryNow">
              {{ retrying ? '연결하는 중…' : '지금 다시 시도' }}
            </button>
          </div>

          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
/* 창 크기 그대로. 줄인 화면이 밖으로 삐져나가 스크롤이 생기지 않게 막음 */
.viewport {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--bg); /* 오른쪽에 남는 여백도 본문 바탕색과 같게 */
}
/* 크기·위치·배율은 fitToWindow()가 정함. 왼쪽 위를 기준으로 줄임 */
.shell {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  width: 100%;
  height: 100%;
  transform-origin: 0 0;
}

/* 왼쪽 메뉴
   zoom 1.2: 메뉴 전체(폭, 글자, 간격)를 같은 비율로 20% 키움 (2026-11-18). 본문은 남은 폭을 씀 */
.side {
  zoom: 1.2;
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
  font-size: 16px;
}
.rail-connection {
  font-size: 14px;
}
.rail-auto {
  padding: 1px 4px;
  border: 1.5px solid var(--ink);
  font-size: 13px;
  font-weight: 700;
}
/* 메뉴 맨 위의 자동 모드 표시 */
.auto-badge {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0 12px 10px;
  padding: 7px 10px;
  border: 1.5px solid var(--ink);
  font-size: 16px;
  font-weight: 700;
}
.auto-sub {
  font-size: 14px;
  font-weight: 400;
  color: var(--mute);
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
  font-size: 17px;
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
  font-size: 13px;
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
  font-size: 14px;
}
.mark.crit {
  color: var(--crit);
}
.mark.warn {
  color: var(--warn);
}

.foot {
  padding: 12px 20px 0;
  border-top: 1px solid var(--line);
}
/* 메뉴 맨 아래의 환경 설정 링크 */
.foot-top {
  display: flex;
  align-items: center;
  margin: -4px -20px 10px;
}
.settings-link {
  display: block;
  flex: 1;
  padding: 9px 20px;
  font-size: 17px;
  color: var(--ink-2);
}
.settings-link:hover {
  background: var(--bg);
}
.settings-link.router-link-exact-active {
  padding-left: 16px;
  background: var(--bg);
  border-left: 4px solid var(--ink);
  font-weight: 700;
  color: var(--ink);
}
.clock {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}
.speed {
  font-size: 14px;
  color: var(--mute);
}
.weather {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 2px;
  color: var(--ink-2);
}
.weather-temp {
  font-size: 19px;
  font-weight: 700;
  color: var(--ink);
}
.weather-text {
  font-size: 15px;
}
.rail-weather {
  color: var(--ink-2);
}
/* 라이트/다크 전환 버튼 */
.theme-button {
  flex: none;
  height: 30px;
  margin-right: 16px;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  font-size: 14px;
  color: var(--ink-2);
}
.theme-button:hover,
.rail-theme:hover {
  background: var(--bg);
}
.rail-theme {
  width: 32px;
  height: 32px;
  padding: 0;
  background: none;
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  font-size: 16px;
  color: var(--ink-2);
}
.connection {
  font-size: 15px;
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
/* 한 화면에 맞추는 화면: 본문 스크롤 없이 stage를 줄여서 넣음 */
.content.fit {
  overflow: hidden;
}
/* 본문 안쪽 상자. 높이와 배율은 fitToWindow()가 정함 (성적표는 내용 길이대로) */
.stage {
  transform-origin: 0 0;
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
  .viewport {
    position: static;
    overflow: visible;
  }
  /* 인쇄할 때는 줄이지 않음 (종이 크기는 global.css의 인쇄 설정이 맞춤) */
  .shell {
    position: static;
    display: block;
    width: auto !important;
    height: auto !important;
    transform: none !important;
  }
  .content,
  .content.fit {
    overflow: visible;
  }
  .stage {
    width: auto !important;
    height: auto !important;
    transform: none !important;
  }
}
</style>
