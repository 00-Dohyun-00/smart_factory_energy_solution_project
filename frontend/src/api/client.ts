// 백엔드 API 호출 공통 함수. 모든 API 호출은 이 함수를 사용합니다.
export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  if (!res.ok) {
    throw new Error(`API 오류: ${res.status}`)
  }

  // 응답 본문이 없는 경우 (예: 삭제 성공 204)
  if (res.status === 204) {
    return undefined as T
  }

  return res.json() as Promise<T>
}
