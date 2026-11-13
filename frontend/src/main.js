import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@/styles/reset.css'
import '@/styles/global.css'
import App from '@/App.vue'
import router from '@/router'

const app = createApp(App)

app.use(createPinia()) // 상태 관리 (Pinia)
app.use(router) // 페이지 이동 (Vue Router)

app.mount('#app')
