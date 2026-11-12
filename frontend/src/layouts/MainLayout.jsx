import { Outlet } from 'react-router'

// 여러 페이지가 공유하는 공통 틀 (헤더, 사이드바 등을 여기에 추가)
export default function MainLayout() {
  return (
    <div>
      {/* 각 페이지 내용이 <Outlet /> 자리에 표시됩니다 */}
      <Outlet />
    </div>
  )
}
