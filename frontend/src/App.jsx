import { Route, Routes } from 'react-router'
import MainLayout from '@/layouts/MainLayout'
import MainPage from '@/pages/MainPage'
import NotFoundPage from '@/pages/NotFoundPage'

// 페이지(URL) 목록. 새 페이지를 만들면 여기에 Route를 추가합니다.
export default function App() {
  return (
    <Routes>
      {/* MainLayout 안에 들어가는 페이지들 (공통 헤더/사이드바 공유) */}
      <Route element={<MainLayout />}>
        <Route path="/" element={<MainPage />} />
      </Route>

      {/* 위에 없는 주소로 들어오면 404 페이지 */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
