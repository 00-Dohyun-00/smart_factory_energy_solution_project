import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@/styles/reset.css'
import '@/styles/global.css'
import App from '@/App.vue'
import router from '@/router'
import { useThemeStore } from '@/stores/useThemeStore'

const app = createApp(App)

app.use(createPinia()) // 상태 관리 (Pinia)
app.use(router) // 페이지 이동 (Vue Router)

// 저장해 둔 테마(라이트/다크)를 화면이 그려지기 전에 먼저 적용 (처음에 밝게 번쩍이지 않게)
useThemeStore()

// 개발 서버에서 주소에 ?fake 가 있으면 가짜 백엔드로 답하게 한 뒤 화면을 띄움 (시연 영상 촬영용)
// import.meta.env.DEV는 빌드하면 false라서, 빌드 결과물에는 가짜 백엔드가 들어가지 않음
const useFakeBackend =
  import.meta.env.DEV && new URLSearchParams(window.location.search).has('fake')
const ready = useFakeBackend
  ? import('@/api/fakeFetch.js').then((module) => module.installFakeFetch())
  : Promise.resolve()
ready.then(() => app.mount('#app'))
