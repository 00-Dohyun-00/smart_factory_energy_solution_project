import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'

// 화면 테마 (라이트 / 다크)
// - 고른 테마는 브라우저 저장소에 적어 두어 새로 고쳐도 유지됨. 처음에는 PC의 다크 모드 설정을 따름
// - 색 값은 global.css의 색 변수 한 곳에만 있음. 그래프(ECharts)는 CSS 변수를 못 읽으므로
//   지금 테마의 색을 CSS 변수에서 읽어 colors에 담아 두고, 그래프는 colors를 씀
const STORAGE_KEY = 'wattever.theme'

// colors의 이름 → CSS 변수 이름
const COLOR_VARS = {
  ink: '--ink',
  ink2: '--ink-2',
  mute: '--mute',
  faint: '--faint',
  line: '--line',
  lineStrong: '--line-strong',
  card: '--card',
  bg: '--bg',
  hover: '--hover',
  lost: '--lost',
  ok: '--ok',
  warn: '--warn',
  crit: '--crit',
  grid: '--src-grid',
  solar: '--src-solar',
  ess: '--src-ess',
  band: '--band',
  labelBg: '--label-bg',
  legendText: '--legend-text',
  heat1: '--heat-1',
  heat2: '--heat-2',
  heat3: '--heat-3',
  heat4: '--heat-4',
  heat5: '--heat-5',
  zoneZ1: '--zone-z1',
  zoneZ2: '--zone-z2',
  zoneZ3: '--zone-z3',
  zoneZ4: '--zone-z4',
  zoneZ5: '--zone-z5',
  zoneU: '--zone-u',
}

function readSaved() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // 저장소를 쓸 수 없는 브라우저면 PC 설정을 따름
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref(readSaved()) // 'light' | 'dark'
  const colors = reactive({}) // 그래프용 지금 테마 색. 예: colors.ink → '#14181c'

  // 테마를 화면에 적용하고 그래프용 색을 다시 읽음
  // name을 주면 저장된 테마와 상관없이 잠깐 그 테마로 보여 줌 (예: 인쇄할 때만 라이트)
  function apply(name = theme.value) {
    document.documentElement.dataset.theme = name
    const style = getComputedStyle(document.documentElement)
    for (const [name, cssVar] of Object.entries(COLOR_VARS)) {
      colors[name] = style.getPropertyValue(cssVar).trim()
    }
  }

  function toggle() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    try {
      localStorage.setItem(STORAGE_KEY, theme.value)
    } catch {
      // 저장이 막혀 있어도 이번 화면에서는 바뀐 테마가 적용됨
    }
    apply()
  }

  apply()
  return { theme, colors, toggle, apply }
})
