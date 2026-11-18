// 환경 설정(기준값) API (명세 v0.2: settings, setting-histories)
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) export const getSettings = () => api('/settings')
//           export const updateSettings = (changes) =>
//             api('/settings', { method: 'PUT', body: JSON.stringify(changes) })
//           export const getSettingHistories = (from, to) => api(`/setting-histories?from=${from}&to=${to}`)
import { fakeGetSettingHistories, fakeGetSettings, fakeUpdateSettings } from '@/api/fakeBackend'

// 기준값 목록 (명세 11번: GET /api/settings)
// 한 줄 모양: { key: 'peak.target_kw', group, label, value: '1800', unit, updatedAt, updatedBy }
// value는 DB가 문자열이라 문자열로 옴. 숫자로 바꾸는 것은 Store가 함
export const getSettings = () => fakeGetSettings()

// 기준값 여러 개를 한 번에 저장 (명세 12번: PUT /api/settings)
// changes 예: [{ key: 'peak.target_kw', value: '1750' }]. 저장된 전체 목록(11번과 같은 모양)이 돌아옴
export const updateSettings = (changes) => fakeUpdateSettings(changes)

// 기준값 변경 기록, 최신 순 (명세 13번: GET /api/setting-histories?from=&to=)
// 한 줄 모양: { id, changedAt, key, label, oldValue, newValue, changedBy }
export const getSettingHistories = (from, to) => fakeGetSettingHistories(from, to)
