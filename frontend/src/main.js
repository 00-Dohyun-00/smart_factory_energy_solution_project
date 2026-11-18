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

app.mount('#app')
