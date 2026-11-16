// 환경 설정 API
// TODO: 백엔드 연결 후 교체. 지금은 가짜 데이터를 돌려줍니다.
//   교체 예) api('/energy-settings')
//           api(`/energy-settings/${key}`, { method: 'PUT', body: JSON.stringify({ value }) })
import { fakeGetSettingHistory, fakeGetSettings, fakeUpdateSetting } from '@/api/fakeBackend'

// 기준값 목록: [{ key, value, updatedAt, updatedBy }]
export const getEnergySettings = () => fakeGetSettings()

// 기준값 하나 바꾸기 (key 예: 'targetPeakKw')
export const updateEnergySetting = (key, value) => fakeUpdateSetting(key, value)

// 변경 기록 (최신 순): [{ id, changedAt, key, before, after, changedBy }]
export const getEnergySettingHistory = () => fakeGetSettingHistory()
