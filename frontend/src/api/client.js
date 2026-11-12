// 백엔드 API 호출 공통 함수. 모든 API 호출은 이 함수를 사용합니다.
export async function api(path, options) {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  // 실패 시 백엔드가 보낸 에러 메시지로 에러를 던짐 (예: "설비를 찾을 수 없습니다.")
  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? `API 오류: ${res.status}`)
  }

  // 응답 본문이 없는 경우 (예: 삭제 성공 204)
  if (res.status === 204) {
    return null
  }

  return res.json()
}
