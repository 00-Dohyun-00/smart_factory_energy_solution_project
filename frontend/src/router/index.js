import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import HistoryPage from '@/pages/HistoryPage.vue'
import MonitoringPage from '@/pages/MonitoringPage.vue'
import NotFoundPage from '@/pages/NotFoundPage.vue'
import PowerDiagramPage from '@/pages/PowerDiagramPage.vue'
import PowerHistoryPage from '@/pages/PowerHistoryPage.vue'
import ReportPage from '@/pages/ReportPage.vue'
import SettingsPage from '@/pages/SettingsPage.vue'
import TrendAnalysisPage from '@/pages/TrendAnalysisPage.vue'

// 페이지(URL) 목록. 새 페이지를 만들면 여기에 추가하고, 왼쪽 메뉴(MainLayout.vue의 menus)에도 추가합니다.
// meta의 parent는 메뉴 이름, title은 화면 이름입니다. title은 브라우저 탭 제목에도 쓰입니다.
const routes = [
  {
    // MainLayout 안에 들어가는 페이지들 (왼쪽 메뉴 공유)
    path: '/',
    component: MainLayout,
    children: [
      // 모니터링
      { path: '', component: MonitoringPage, meta: { parent: '모니터링', title: '통합' } },
      {
        path: 'monitoring/diagram',
        component: PowerDiagramPage,
        meta: { parent: '모니터링', title: '설비 모니터링' },
      },
      // 분석
      {
        path: 'analysis/trend',
        component: TrendAnalysisPage,
        meta: { parent: '분석', title: '추이 분석' },
      },
      // 피크 분석 화면은 추이 분석에 합치고 지웠음(2026-11-21). 예전 주소로 들어오면 추이 분석을 염
      { path: 'analysis/peak', redirect: '/analysis/trend' },
      // 보고서
      { path: 'report', component: ReportPage, meta: { parent: '보고서', title: '월간 성적표' } },
      // 운영 로그
      { path: 'logs', component: HistoryPage, meta: { parent: '운영 로그', title: '추천 이력' } },
      // 전력 이력: 추이 그래프의 피크 초과 점(●)을 누르면 ?at=2026-11-19T14:15:00 으로 들어옴
      {
        path: 'logs/power',
        component: PowerHistoryPage,
        meta: { parent: '운영 로그', title: '전력 이력' },
      },
      { path: 'history', redirect: '/logs' }, // 예전 주소로 들어와도 열리게 함
      // 환경 설정
      {
        path: 'settings',
        component: SettingsPage,
        meta: { parent: '환경 설정', title: '기준값' },
      },

      // 위에 없는 주소로 들어오면 404 페이지 (왼쪽 메뉴는 그대로 보여 다른 화면으로 갈 수 있음)
      {
        path: ':pathMatch(.*)*',
        component: NotFoundPage,
        meta: { title: '페이지를 찾을 수 없습니다' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 화면을 옮길 때마다 브라우저 탭 제목을 화면 이름으로 바꿈 (탭이 여러 개일 때 구분하기 쉽게)
// failure: 이동이 취소된 경우(예: 환경 설정에서 "저장하지 않고 갈까요?"에 아니오)에는 값이 들어옴 → 제목을 바꾸지 않음
router.afterEach((to, from, failure) => {
  if (failure) return
  document.title = to.meta.title ? `${to.meta.title} · WATTever` : 'WATTever'
})

export default router
