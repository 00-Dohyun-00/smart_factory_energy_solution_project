// 백엔드 API 호출 공통 함수. 모든 API 호출은 이 함수를 사용합니다.

// 서버에 닿지 못했을 때 화면에 보여 줄 문구 (이슈 #25-6)
const SERVER_DOWN_MESSAGE = '서버에 연결할 수 없습니다.'

export async function api(path, options) {
  let res
  try {
    res = await fetch(`/api${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    // 응답 자체가 없음 (인터넷이 끊김 등). 브라우저 기본 문구 "Failed to fetch"는 영어라 바꿈
    throw new Error(SERVER_DOWN_MESSAGE)
  }

  // 실패 시 백엔드가 보낸 에러 메시지로 에러를 던짐 (예: "설비를 찾을 수 없습니다.")
  if (!res.ok) {
    const error = await res.json().catch(() => null)
    if (error?.message) throw new Error(error.message)
    // 백엔드가 꺼져 있으면 개발 서버(Vite)가 본문 없는 502 · 504를 돌려줌 → "API 오류: 502" 대신 알아듣기 쉬운 문구
    throw new Error(res.status >= 500 ? SERVER_DOWN_MESSAGE : `API 오류: ${res.status}`)
  }

  // 응답 본문이 없는 경우 (예: 삭제 성공 204)
  if (res.status === 204) {
    return null
  }

  return res.json()
}
