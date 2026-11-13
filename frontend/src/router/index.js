import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import MainPage from '@/pages/MainPage.vue'
import NotFoundPage from '@/pages/NotFoundPage.vue'

// 페이지(URL) 목록. 새 페이지를 만들면 여기에 추가합니다.
const routes = [
  {
    // MainLayout 안에 들어가는 페이지들 (공통 헤더/사이드바 공유)
    path: '/',
    component: MainLayout,
    children: [{ path: '', component: MainPage }],
  },

  // 위에 없는 주소로 들어오면 404 페이지
  { path: '/:pathMatch(.*)*', component: NotFoundPage },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
