// 백엔드 API가 만들어지기 전까지 쓰는 "가짜 백엔드"입니다.
// TODO: 백엔드 연결 후 이 파일은 삭제합니다.
//
// - 여기 있는 피크 예측·추천 계산은 화면 확인용으로 단순하게 흉내 낸 것입니다. 실제 계산은 백엔드가 합니다.
// - 이 파일은 batteryStatusApi.js, batteryDecisionApi.js 에서만 불러옵니다. (Store, 화면에서 직접 쓰지 않음)
// - 이 파일은 서버 역할이라 "서버 시각"을 직접 만듭니다(아래 simNow). 화면 코드에서는 new Date()를 쓰지 않습니다.
// - 공장 전력과 태양광은 실제 데이터(fakeEnergyData.json)에서 가져옵니다. (아래 "실제 데이터로 만드는 전력" 참고)
// - 하루 계획은 실제 데이터 대신 AI가 예측한 공장 부하(fakeLoadForecast.json)로 세웁니다.
//   예측 파일은 ai/forecast/train_load_forecast.py 가 만듭니다.
// - 추천 로직(하루 계획, 지금 무엇을 추천할지, 이유 문장)은 fakeRecommendation.js에 따로 있습니다.
//   이 파일은 데이터를 모아 그 함수에 넘기고, 결과(지금 추천, 적용 중인 출력, 이벤트)를 관리합니다.

import energyData from '@/api/fakeEnergyData.json'
import loadForecast from '@/api/fakeLoadForecast.json'
import {
  ADVICE_RULES,
  START_SOC_PERCENT,
  correctedEssKw,
  decideAdvice,
  explainDecision,
  neededDischargeKw,
  peakRiskOf,
  planBlockEndOf,
  planDay,
  planInputsOf,
  ruleDay,
  socChangeOf,
} from '@/api/fakeRecommendation'

// 아래 기준값들은 환경 설정 화면에서 바꾸면 applySettings()가 고쳐 씁니다. 그래서 const가 아니라 let입니다.
let TARGET_PEAK_KW = 1800 // 목표 피크
let WARN_KW = 1746 // 주의선 (목표의 97%, 기준은 확인 필요)
let MAX_DISCHARGE_KW = 500 // ESS 최대 방전 출력

// 공장 컨셉: 전기차 열관리 부품 공장 (가상).
// 설비 id · 이름 · 구역 · 정격 전력은 실제 DB equipment 표와 같게 맞춤 (2026-11-18, GET /api/equipment)
// zoneId = 화면의 구역 id (DB zone_code. 유틸리티는 DB에서 U1이지만 화면은 U로 씀)
// share = 평소 공장 전력에서 그 설비가 차지하는 비중 (합계 1.0, 임시로 정한 값)
const EQUIPMENTS = [
  { id: 'HEX-01', zoneId: 'Z1', name: 'CNC 가공기 1호', ratedKw: 120, share: 0.05 },
  { id: 'HEX-02', zoneId: 'Z1', name: 'CNC 가공기 2호', ratedKw: 120, share: 0.04 },
  { id: 'HEX-03', zoneId: 'Z1', name: '프레스', ratedKw: 100, share: 0.03 },
  { id: 'BRZ-01', zoneId: 'Z2', name: '브레이징로', ratedKw: 600, share: 0.3 },
  { id: 'BRZ-02', zoneId: 'Z2', name: '탈지·건조기', ratedKw: 180, share: 0.06 },
  { id: 'INJ-01', zoneId: 'Z3', name: '사출기 1호', ratedKw: 220, share: 0.1 },
  { id: 'INJ-02', zoneId: 'Z3', name: '사출기 2호', ratedKw: 220, share: 0.1 },
  { id: 'INJ-03', zoneId: 'Z3', name: '사출기 3호', ratedKw: 220, share: 0.04 },
  { id: 'MOD-01', zoneId: 'Z4', name: '조립 라인 1', ratedKw: 40, share: 0.01 },
  { id: 'MOD-02', zoneId: 'Z4', name: '조립 라인 2', ratedKw: 40, share: 0.01 },
  { id: 'INS-01', zoneId: 'Z5', name: '리크 테스터', ratedKw: 120, share: 0.05 },
  { id: 'INS-02', zoneId: 'Z5', name: '비전 검사기', ratedKw: 80, share: 0.03 },
  { id: 'UTL-01', zoneId: 'U', name: '공기압축기', ratedKw: 200, share: 0.08 }, // 실측 설비 (DB is_virtual = false)
  { id: 'UTL-02', zoneId: 'U', name: '냉각탑', ratedKw: 150, share: 0.06 },
  { id: 'UTL-03', zoneId: 'U', name: '공조기', ratedKw: 120, share: 0.04 },
]

// 실패 상황을 화면에서 확인하기 위한 장치. 주소 뒤에 붙여서 씁니다.
//   http://localhost:5173/?fake=offline      → 화면을 연 지 10초 뒤부터 서버 연결 끊김 흉내
//   http://localhost:5173/?fake=chart-error  → 오늘 그래프 불러오기 실패 흉내
const fakeMode = new URLSearchParams(window.location.search).get('fake')
const openedAt = Date.now()

function failIfOffline() {
  if (fakeMode === 'offline' && Date.now() - openedAt > 10000) {
    throw new Error('서버에 연결할 수 없습니다.')
  }
}

// ---- 시뮬레이션 배속 ----
// 가짜 서버의 시계를 실제보다 빠르게 돌립니다. 실제 속도로는 15분 구간 하나를 보려면 15분을 기다려야 하기 때문입니다.
// 기본은 10배속: 15분 구간이 1분 30초 만에 지나가고, 화면이 5초마다 받을 때마다 서버 시각이 50초씩 흐릅니다.
// 주소 뒤에 ?speed=30 처럼 적으면 배속을 바꿀 수 있습니다. (1 = 실제 속도, 최대 120)
//   http://localhost:5173/?speed=1   → 실제 속도
//   http://localhost:5173/?speed=30  → 30배속 (15분 구간이 30초)
const DEFAULT_SPEED = 10
const speedInAddress = Number(new URLSearchParams(window.location.search).get('speed'))
const SIMULATION_SPEED =
  speedInAddress >= 1 && speedInAddress <= 120 ? speedInAddress : DEFAULT_SPEED

// 지금의 "서버 시각". 화면을 연 순간은 실제 시각과 같고, 그 뒤로는 배속만큼 빠르게 흐릅니다.
function simNow() {
  return new Date(openedAt + (Date.now() - openedAt) * SIMULATION_SPEED)
}

let currentDecision = null // 가장 최근 추천 1건
let nextDecisionId = 1
let appliedEssKw = 0 // 적용 중인 ESS 전력 (+방전, −충전, kW)

// ---- 최근 이벤트 (모니터링 화면의 "최근 이벤트" 목록용) ----
// level: CRITICAL(경고) | WARNING(주의) | NORMAL(정상) | INFO(그 밖의 기록)
// category: STAGE(단계 변화) | RECOMMEND(추천) | ACTION(적용·넘김) | WINDOW(구간 종료) | BATTERY(배터리) | SETTING(설정 변경)
//   (category는 이벤트 로그의 "종류" 칼럼·필터용으로 임시로 넣은 값. 백엔드와 확정 필요)
const events = []
let nextEventId = 1
let lastPeakStatus = null // 단계가 바뀌었는지 알기 위해 직전 단계를 기억

function addEvent(level, category, message, date = simNow()) {
  events.unshift({
    id: nextEventId++,
    occurredAt: toLocalDateTime(date),
    level,
    category,
    message,
  })
  if (events.length > 50) events.pop()
}

// 화면을 처음 열었을 때 목록이 비어 있지 않도록, 조금 전에 있었던 일처럼 몇 건을 넣어 둠
let eventsSeeded = false
function seedEventsOnce(now) {
  if (eventsSeeded) return
  eventsSeeded = true
  const ago = (minutes) => new Date(now.getTime() - minutes * 60000)
  addEvent('INFO', 'ACTION', '계획 방전 추천을 넘김 (운영자)', ago(47))
  addEvent('WARNING', 'STAGE', '주의 단계 진입', ago(31))
  addEvent('CRITICAL', 'RECOMMEND', '목표 초과 예상, 방전 500 kW 추천', ago(30))
  addEvent('INFO', 'ACTION', '방전 500 kW 적용 (운영자)', ago(29))
  addEvent('NORMAL', 'STAGE', '정상 단계로 돌아옴', ago(17))
}

// 1분 평균: 그 시각 직전 1분을 10초 간격으로 여섯 번 재서 평균 냄 (그래프가 톱니처럼 튀지 않게)
function minuteAverage(valueAt, time) {
  let total = 0
  for (let step = 0; step < 6; step++) {
    total += valueAt(new Date(time.getTime() - step * 10000))
  }
  return total / 6
}

const pad = (n) => String(n).padStart(2, '0')

// 백엔드의 LocalDateTime과 같은 모양 (예: 2026-11-14T14:07:32)
function toLocalDateTime(date) {
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  return `${day}T${time}`
}

// 브레이징로가 가열할 때 늘어나는 전력 (약 4분 주기로 반복되게 만든 값)
// 15분 평균을 내면 거의 0이 되어, 순간값만 출렁이고 구간 평균은 크게 바꾸지 않음
function furnaceSwingAt(seconds) {
  return 330 * Math.sin(seconds / 40)
}

// 사출기가 한 사이클 돌 때마다 출렁이는 전력
function injectionSwingAt(seconds) {
  return 40 * Math.sin(seconds / 7)
}

// ---- 실제 데이터로 만드는 전력 ----
// fakeEnergyData.json = 원본 CSV(frontend/data)에서 필요한 숫자만 뽑은 파일
//   loadKwh : 제철소 실측 15분 전력량 (2018년 1년, 35,040칸)
//             출처: UCI Machine Learning Repository, Steel Industry Energy Consumption (CC BY 4.0)
//   solarMwh: 경상남도 태양광 시간별 발전량 (2025년 1년, 8,760칸)
//             출처: 공공데이터포털, 한국전력거래소_지역별 시간별 태양광 및 풍력 발전량 (공공누리)
// 우리 공장 크기로 바꾸는 식은 데이터 README의 초안을 따름 (백엔드 시뮬레이터와 같은 식)
const LOAD_YEAR = 2018
const SOLAR_YEAR = 2025
const BASE_LOAD_KW = 1300 // 공장 kW = 1,300 + 1.35 × (usage_kwh × 4)
const LOAD_SCALE = 1.35
const SOLAR_SCALE = 1.536 // 태양광 kW = generation_mwh × 1.536 (1,000 kW 지붕 태양광으로 환산)

// 주소 뒤에 ?scenario=peak 를 붙이면 오늘을 "1년 중 피크가 가장 높았던 날"로 바꿈.
// 평범한 날에는 피크 장면이 드물어서, 시연에서 피크 방어를 보여 줄 때 씀
//   http://localhost:5173/?scenario=peak
const scenario = new URLSearchParams(window.location.search).get('scenario')

// 그해 1월 1일부터 며칠째인지 (0부터). month는 0(1월)~11(12월)
function dayOfYear(year, month, day) {
  return Math.round((Date.UTC(year, month, day) - Date.UTC(year, 0, 1)) / 86400000)
}

// 데이터 두 해(2018, 2025)는 2월 29일이 없어서 28일로 씀
function dayOfMonthIn(date) {
  return date.getMonth() === 1 ? Math.min(date.getDate(), 28) : date.getDate()
}

// 공장 전력은 같은 달·같은 요일인 2018년 날을 씀 (요일마다 평일·주말 모양이 달라서)
// 예: 2026-11-17(화) → 2018-11-20(화)
function loadDayIndexOf(date) {
  const day = dayOfMonthIn(date)
  let shift = date.getDay() - new Date(LOAD_YEAR, date.getMonth(), day).getDay()
  if (shift > 3) shift -= 7
  if (shift < -3) shift += 7
  const index = dayOfYear(LOAD_YEAR, date.getMonth(), day) + shift
  if (index < 0) return index + 7
  if (index > 364) return index - 7
  return index
}

// 태양광은 같은 월·일인 2025년 날을 씀 (날씨는 요일과 상관없음)
function solarDayIndexOf(date) {
  return dayOfYear(SOLAR_YEAR, date.getMonth(), dayOfMonthIn(date))
}

// 데이터 한 칸의 값을 우리 공장 크기로 바꾼 값 (kW)
function loadKwOfSlot(index) {
  return BASE_LOAD_KW + LOAD_SCALE * energyData.loadKwh[index] * 4
}

function solarKwOfHour(index) {
  const hour = index % 24
  // 밤(21시~05시)의 작은 값은 태양광 연계 ESS가 내보낸 양으로 보여 0으로 처리 (데이터 README)
  if (hour >= 21 || hour < 5) return 0
  return energyData.solarMwh[index] * SOLAR_SCALE
}

// 1년 중 (공장 − 태양광)이 가장 높았던 날. ?scenario=peak 에서 씀 (처음 한 번만 계산)
let peakDayIndex = null
function peakDayIndexOf() {
  if (peakDayIndex !== null) return peakDayIndex
  let best = -Infinity
  for (let day = 0; day < 365; day++) {
    for (let slot = 0; slot < 96; slot++) {
      const netKw = loadKwOfSlot(day * 96 + slot) - solarKwOfHour(day * 24 + Math.floor(slot / 4))
      if (netKw > best) {
        best = netKw
        peakDayIndex = day
      }
    }
  }
  return peakDayIndex
}

// 그 시각이 들어 있는 15분 칸이 데이터의 몇 번째 칸인지
function loadSlotIndexOf(time) {
  const day = isPeakScenarioDay(time) ? peakDayIndexOf() : loadDayIndexOf(time)
  return day * 96 + time.getHours() * 4 + Math.floor(time.getMinutes() / 15)
}

// 그 시각이 들어 있는 15분 칸의 공장 전력 (kW)
function loadKwOfTime(time) {
  return loadKwOfSlot(loadSlotIndexOf(time))
}

// AI가 예측한 그 칸의 공장 전력 (kW). 실제 값과 같은 식으로 우리 공장 크기로 바꿈
//   mean: 평균 예측 (요금 계산용) / peak: 상위 90% 예측 (방어선 판단용, 피크를 낮게 보지 않게)
function forecastLoadKwOfTime(time, kind) {
  const values = kind === 'peak' ? loadForecast.peakLoadKwh : loadForecast.loadKwh
  return BASE_LOAD_KW + LOAD_SCALE * values[loadSlotIndexOf(time)] * 4
}

// 그 시각이 들어 있는 1시간 칸의 태양광 (kW)
function solarKwOfTime(time) {
  const day = isPeakScenarioDay(time) ? peakDayIndexOf() : solarDayIndexOf(time)
  return solarKwOfHour(day * 24 + time.getHours())
}

// 데이터는 칸마다 평균 하나뿐이라, 칸 가운데에 그 값을 두고 사이는 앞뒤 칸을 직선으로 이음
// (칸 경계에서 계단처럼 뛰지 않게 하려는 것. 칸 가운데 시각에서는 데이터 값 그대로)
function smoothValueAt(time, valueOfTime, stepMs) {
  const shifted = time.getTime() - stepMs / 2
  const start = Math.floor(shifted / stepMs) * stepMs
  const before = valueOfTime(new Date(start))
  const after = valueOfTime(new Date(start + stepMs))
  return before + ((after - before) * (shifted - start)) / stepMs
}

// '2026-11-16' 모양의 날짜 글자
function dateKeyOf(date) {
  return toLocalDateTime(date).slice(0, 10)
}

// 날짜로 정하는 가짜 난수의 씨앗 (예: 20261116)
function daySeedOf(date) {
  return Number(dateKeyOf(date).replaceAll('-', ''))
}

function isPeakScenarioDay(date) {
  return scenario === 'peak' && dateKeyOf(date) === dateKeyOf(simNow())
}

// 날씨: 그날 태양광 발전량으로 추정. 그 달에 가장 많이 발전한 날과 비교해
// 75% 이상이면 맑음, 40% 이상이면 흐림, 그 아래는 비 (1년 중 맑음 212일 · 흐림 95일 · 비 58일)
let bestSolarByMonth = null // 달마다 가장 많이 발전한 날의 발전량 (처음 한 번만 계산)

function daySolarKwh(dayIndex) {
  let total = 0
  for (let hour = 0; hour < 24; hour++) total += solarKwOfHour(dayIndex * 24 + hour)
  return total
}

function weatherOf(date) {
  const day = isPeakScenarioDay(date) ? peakDayIndexOf() : solarDayIndexOf(date)
  const monthOf = (index) => new Date(SOLAR_YEAR, 0, 1 + index).getMonth()
  if (!bestSolarByMonth) {
    bestSolarByMonth = new Array(12).fill(0)
    for (let index = 0; index < 365; index++) {
      const month = monthOf(index)
      bestSolarByMonth[month] = Math.max(bestSolarByMonth[month], daySolarKwh(index))
    }
  }
  const ratio = daySolarKwh(day) / bestSolarByMonth[monthOf(day)]
  if (ratio >= 0.75) return '맑음'
  if (ratio >= 0.4) return '흐림'
  return '비'
}

// 그 시각 공장의 기준 전력 (데이터 값. 설비가 몇 분 단위로 출렁이는 부분은 뺀 값)
function baseLoadAt(time) {
  return smoothValueAt(time, loadKwOfTime, 15 * 60000)
}

// 그 시각 공장 전체가 쓰는 전력 (기준 전력 + 브레이징로·사출기의 출렁임)
function factoryLoadAt(time) {
  const seconds = time.getTime() / 1000
  return baseLoadAt(time) + furnaceSwingAt(seconds) + injectionSwingAt(seconds)
}

// 그 시각의 태양광 발전량 (데이터 값)
function solarKwAt(time) {
  return smoothValueAt(time, solarKwOfTime, 60 * 60000)
}

// ESS가 없을 때 한전에서 사야 하는 전력 = 공장 소비 − 태양광
function gridWithoutEssAt(time) {
  return factoryLoadAt(time) - solarKwAt(time)
}

// 최근 약 4분(10초 간격 25번)의 평균. 브레이징로 출렁임 한 주기(약 4분)를 덮어서 평균하면 출렁임이 지워짐
function recentAverageWithoutEss(now) {
  let total = 0
  for (let step = 0; step < 25; step++) {
    total += gridWithoutEssAt(new Date(now.getTime() - step * 10000))
  }
  return total / 25
}

// 바깥 기온 (오후 3시 무렵 가장 높고, 비 오는 날은 4℃ 낮음)
function outsideTempAt(time) {
  const hour = time.getHours() + time.getMinutes() / 60
  return 12 + 6 * Math.sin(((hour - 9) / 24) * 2 * Math.PI) - (weatherOf(time) === '비' ? 4 : 0)
}

function round1(value) {
  return Math.round(value * 10) / 10
}

// ---- 15분 구간(수요시한) 안에서 전력이 쌓이는 과정 ----
// demandKw = 구간 시작부터 지금까지 쓴 전력량을 "15분 평균 전력"으로 바꾼 값.
// 0에서 시작해 계속 올라가고, 15분이 끝났을 때의 값이 그 구간의 수요전력(피크)이 됩니다.
const WINDOW_MS = 15 * 60 * 1000
let windowState = null // { startMs, lastMs, demandKw, points: [{ minute, demandKw }] }

function windowStartOf(ms) {
  return Math.floor(ms / WINDOW_MS) * WINDOW_MS
}

// 지금까지의 누적값을 갱신. gridWithoutEss = ESS가 없을 때 지금 한전에서 사야 하는 전력
function updateWindow(nowMs, gridWithoutEss) {
  const startMs = windowStartOf(nowMs)

  if (!windowState || windowState.startMs !== startMs) {
    // 방금 끝난 구간이 있으면 결과를 이벤트와 그 구간의 추천에 남김
    if (windowState) {
      // 마지막 조회부터 구간이 끝날 때까지 빠진 몇십 초를 지금 전력으로 채워 구간을 닫음
      addWindowPoint(windowState.startMs + WINDOW_MS, gridWithoutEss - appliedEssKw)
      const endedKw = Math.round(windowState.demandKw)
      const endedStart = toLocalDateTime(new Date(windowState.startMs))
      const from = endedStart.slice(11, 16)
      const to = toLocalDateTime(new Date(startMs)).slice(11, 16)
      addEvent(
        endedKw > TARGET_PEAK_KW ? 'CRITICAL' : 'NORMAL',
        'WINDOW',
        `${from}–${to} 구간 ${endedKw.toLocaleString('ko-KR')} kW로 종료`,
        new Date(startMs),
      )
      // 이 구간이 들어 있는 추천에 결과를 남김. 계획 추천은 여러 구간에 걸치므로 그중 가장 높은 값
      for (const item of sessionDecisions) {
        if (endedStart < item.windowStart || endedStart >= item.windowEnd) continue
        item.resultPeakKw = Math.max(item.resultPeakKw ?? 0, endedKw)
        item.resultStatus = item.resultPeakKw > TARGET_PEAK_KW ? 'EXCEEDED' : 'DEFENDED'
      }
    }
    // 새 구간 시작. 화면을 구간 중간에 열었다면 그 전까지는 15초 간격으로 계산해 채움
    windowState = { startMs, lastMs: startMs, demandKw: 0, points: [{ minute: 0, demandKw: 0 }] }
    for (let ms = startMs + 15000; ms < nowMs; ms += 15000) {
      addWindowPoint(ms, gridWithoutEssAt(new Date(ms)))
    }
  }
  addWindowPoint(nowMs, gridWithoutEss - appliedEssKw)
}

function addWindowPoint(ms, gridKw) {
  windowState.demandKw += (gridKw * (ms - windowState.lastMs)) / WINDOW_MS
  windowState.lastMs = ms
  windowState.points.push({
    minute: Math.round(((ms - windowState.startMs) / 60000) * 100) / 100,
    demandKw: Math.round(windowState.demandKw),
  })
}

// ---- 요금 시간대 (계절별) ----
// 한전 「2026년도 8월 1일 시행 전기요금표(종합)」 p.2, 산업용전력(을) 고압A 선택Ⅱ (2026.4.16 시행 개편안)
//   - 시간대: 경부하는 모든 계절 22~08시. 봄·여름·가을 최대부하 15~21시, 겨울 최대부하 09~12시·16~19시
//   - 토요일: 최대부하 시간대 사용량을 중간부하로 계량 (periodsOf)
//   - 봄·가을 토·일요일 11~14시: 전력량요금 50% 할인 (priceAt)
//   - 공휴일 규칙(최대수요전력·사용량을 경부하로 계량)은 공휴일 목록이 필요해 아직 넣지 않음
// 환경 설정 화면의 단가 3칸은 "지금 계절"의 단가입니다. (applySettings 참고)
const TARIFF_LABELS = { LIGHT: '경부하', MID: '중간부하', PEAK: '최대부하' }
const SEASON_LABELS = { SUMMER: '여름', SPRING_FALL: '봄·가을', WINTER: '겨울' }
const SUMMER_PERIODS = [
  { from: '00:00', to: '08:00', period: 'LIGHT' },
  { from: '08:00', to: '15:00', period: 'MID' },
  { from: '15:00', to: '21:00', period: 'PEAK' },
  { from: '21:00', to: '22:00', period: 'MID' },
  { from: '22:00', to: '24:00', period: 'LIGHT' },
]
const PERIODS_BY_SEASON = {
  SUMMER: SUMMER_PERIODS,
  SPRING_FALL: SUMMER_PERIODS, // 봄·가을은 여름과 시간대 구분이 같고 단가만 다름
  WINTER: [
    { from: '00:00', to: '08:00', period: 'LIGHT' },
    { from: '08:00', to: '09:00', period: 'MID' },
    { from: '09:00', to: '12:00', period: 'PEAK' },
    { from: '12:00', to: '16:00', period: 'MID' },
    { from: '16:00', to: '19:00', period: 'PEAK' },
    { from: '19:00', to: '22:00', period: 'MID' },
    { from: '22:00', to: '24:00', period: 'LIGHT' },
  ],
}
// 전력량요금 단가 (원/kWh), 산업용전력(을) 고압A 선택Ⅱ
const PRICES_BY_SEASON = {
  SUMMER: { LIGHT: 116.0, MID: 163.8, PEAK: 229.0 },
  SPRING_FALL: { LIGHT: 116.0, MID: 133.4, PEAK: 150.9 },
  WINTER: { LIGHT: 123.0, MID: 164.0, PEAK: 204.6 },
}

// 여름 6~8월, 봄·가을 3~5월과 9~10월, 겨울 11~2월
function seasonOf(date) {
  const month = date.getMonth() + 1
  if (month >= 6 && month <= 8) return 'SUMMER'
  if (month >= 3 && month <= 10) return 'SPRING_FALL'
  return 'WINTER'
}

// 그날의 시간대 구분. 토요일은 최대부하 시간대를 중간부하로 계량함
function periodsOf(date) {
  const periods = PERIODS_BY_SEASON[seasonOf(date)]
  if (date.getDay() !== 6) return periods
  return periods.map((item) => (item.period === 'PEAK' ? { ...item, period: 'MID' } : item))
}

// 그 시각의 전력량요금 단가 (원/kWh). 봄·가을 토·일요일 11~14시는 50% 할인
function priceAt(date, period) {
  const price = PRICES_BY_SEASON[seasonOf(date)][period]
  const weekend = date.getDay() === 0 || date.getDay() === 6
  const hour = date.getHours()
  if (seasonOf(date) === 'SPRING_FALL' && weekend && hour >= 11 && hour < 14) return price * 0.5
  return price
}

// 그 시각의 요금 시간대 ('LIGHT' | 'MID' | 'PEAK')
function periodAt(date) {
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return periodsOf(date).find((item) => item.from <= time && time < item.to).period
}

function tariffAt(now) {
  const periods = periodsOf(now)
  const time = `${pad(now.getHours())}:${pad(now.getMinutes())}`
  const index = periods.findIndex((item) => item.from <= time && time < item.to)
  const current = periods[index]
  // 다음에 시간대 이름이 바뀌는 칸을 찾음 (23~24시 경부하 다음의 0시 경부하는 이어진 같은 시간대)
  let nextIndex = (index + 1) % periods.length
  while (periods[nextIndex].period === current.period && nextIndex !== index) {
    nextIndex = (nextIndex + 1) % periods.length
  }
  const next = periods[nextIndex]
  const season = seasonOf(now)
  return {
    season,
    seasonLabel: SEASON_LABELS[season],
    period: current.period,
    label: TARIFF_LABELS[current.period],
    until: next.from, // 이 시간대가 끝나는 시각
    nextLabel: TARIFF_LABELS[next.period],
    periods: periods.map((item) => ({ ...item, label: TARIFF_LABELS[item.period] })),
  }
}

// 명세 v0.2의 tariff 모양만 남김 (계절 정보는 가짜 백엔드 안에서만 씀)
function specTariffOf(tariff) {
  const { period, label, until, nextLabel, periods } = tariff
  return { period, label, until, nextLabel, periods }
}

// ---- 추천 ----
// 추천을 정하는 계산(하루 계획, 지금 무엇을 추천할지, 이유 문장)은 fakeRecommendation.js에 있습니다.
// 여기서는 그 결과로 "지금 추천"을 새로 낼지 유지할지 정하고, 적용 중인 출력과 이벤트를 관리합니다.
// 적용한 뒤에는 그 추천이 끝날 때까지 출력을 계획·필요한 양에 맞춰 조절합니다 (시뮬레이션).
const ACTION_TEXTS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }

// 추천 계산에 넘기는 환경 설정 값 (환경 설정 화면에서 바꾸면 applySettings가 고친 값)
function essLimits() {
  return {
    targetPeakKw: TARGET_PEAK_KW,
    warnKw: WARN_KW,
    maxPowerKw: MAX_DISCHARGE_KW,
    capacityKwh: ESS_CAPACITY_KWH,
    socLowPercent: SOC_LOW_LIMIT,
    socHighPercent: SOC_HIGH_LIMIT,
    efficiencyPercent: settings.efficiencyPercent,
  }
}

// 추천 이유 문장에 쓰는 지금 상황 (피크 위험, 요금)
function explainContextOf(now, limits) {
  const startSlot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15)
  const tariff = tariffAt(now)
  return {
    risk: peakRiskOf(planInputsOf(dayInputs(dateKeyOf(now)), startSlot), startSlot, limits),
    tariffLabel: tariff.label,
    price: priceAt(now, tariff.period),
    peakPrice: PRICES_BY_SEASON[seasonOf(now)].PEAK,
  }
}

// info: { soFarKw: 이번 구간에 지금까지 쌓인 평균, recentWithoutEss: 최근 4분 평균(ESS 없이),
//         remainingMinutes: 구간 남은 분, socPercent: 지금 잔량 }
function updateDecision(now, info) {
  const { socPercent } = info
  const limits = essLimits()
  const plan = livePlanOf(now, socPercent)
  const planKw = plan.essKw[0] // 계획의 지금 칸 출력
  const { kind, action, wantKw, predictedOf } = decideAdvice(plan, info, limits)

  // 이 추천이 끝나는 시각: 피크 방어·안전 제한은 이번 구간 끝, 나머지는 계획에서 같은 동작이 끝날 때
  const windowStartMs = windowStartOf(now.getTime())
  const endText = toLocalDateTime(
    kind === 'PEAK' || kind === 'SAFETY'
      ? new Date(windowStartMs + WINDOW_MS)
      : planBlockEndOf(plan),
  )
  // 적용 중이면 출력을 지금 필요한 양에 맞춤 (동작 방향은 추천과 같게, 넘김이면 0)
  const followKw = (decision) => {
    if (decision.status !== 'APPLIED' && decision.status !== 'AUTO') return 0
    if (decision.action === 'DISCHARGE') return Math.max(0, wantKw)
    if (decision.action === 'CHARGE') return Math.min(0, wantKw)
    return 0
  }

  const current = currentDecision
  const active = current && toLocalDateTime(now) < current.windowEnd
  // 새로 묻지 않고 유지하는 경우: 같은 종류가 이어질 때, 피크 방어가 아직 그 구간 안일 때,
  // 계획 충·방전 중간에 잠깐 쉬는 칸일 때 (그동안 출력은 0)
  const planKinds = ['PLAN_CHARGE', 'PLAN_DISCHARGE']
  const shortGap = kind === 'IDLE' && planKinds.includes(current?.kind)
  // 이유 문장과 판단 근거는 매번(5초마다) 새로 계산함. 유지 중인 추천도 지금 상황에 맞게 고쳐 둠
  // asIsKw는 지금 적용 중인 출력(appliedEssKw)을 반영한 값 = 예상 피크 카드와 같은 계산
  const explain = (shownKind, shownKw, applied) => {
    const sign = { PEAK: 1, PLAN_DISCHARGE: 1, PLAN_CHARGE: -1 }[shownKind] ?? 0
    const values = {
      asIsKw: predictedOf(appliedEssKw),
      afterKw: predictedOf(sign * shownKw),
      applied,
      socPercent,
    }
    return explainDecision(shownKind, values, explainContextOf(now, limits), limits)
  }

  if (active && (current.kind === kind || current.kind === 'PEAK' || shortGap)) {
    if (current.status === 'RECOMMENDED' && current.action === action && action !== 'IDLE') {
      current.powerKw = Math.abs(wantKw) // 아직 답하지 않았으면 지금 필요한 양으로 숫자를 고쳐 둠
    }
    if (current.kind === kind && kind !== 'PEAK' && kind !== 'SAFETY') current.windowEnd = endText
    appliedEssKw = followKw(current)
    // 적용 중이면 카드에 지금 실제로 내는 출력을 보여 줌 (적용한 순간의 숫자에 멈춰 있지 않게)
    if (current.status === 'APPLIED' || current.status === 'AUTO') {
      current.powerKw = Math.round(Math.abs(appliedEssKw))
    }
    const applied = current.status === 'APPLIED' || current.status === 'AUTO'
    Object.assign(current, explain(current.kind, current.powerKw, applied))
    return
  }

  // 새 추천
  const untilText = endText.slice(11, 16)
  const powerKw = Math.abs(wantKw)
  // 계획 방전을 적용해 둔 채 피크 방어가 새로 나오면, 답하기 전까지는 계획 방전만큼은 계속함
  const keepKw = current && action === 'DISCHARGE' && appliedEssKw > 0 ? Math.max(0, planKw) : 0
  appliedEssKw = keepKw

  currentDecision = {
    id: nextDecisionId++,
    decidedAt: toLocalDateTime(now),
    windowStart: toLocalDateTime(new Date(windowStartMs)),
    windowEnd: endText,
    kind,
    ...ADVICE_RULES[kind],
    predictedKw: Math.round(predictedOf(0)),
    targetPeakKw: TARGET_PEAK_KW,
    resultPeakKw: null, // 구간이 끝나야 알 수 있음
    resultStatus: null,
    action,
    powerKw: action === 'IDLE' ? 0 : powerKw,
    durationMinutes:
      action === 'IDLE' ? null : Math.ceil((new Date(endText) - now.getTime()) / 60000),
    ...explain(kind, powerKw, false),
    status: 'RECOMMENDED',
    appliedBy: null,
    appliedAt: null,
  }
  // 운영 이력 화면에 보이도록 기록 (대기 추천은 너무 많아져서 뺌)
  if (action === 'IDLE') return
  sessionDecisions.push(currentDecision)
  addEvent(
    kind === 'PEAK' ? 'CRITICAL' : 'INFO',
    'RECOMMEND',
    kind === 'PEAK'
      ? `방어선 초과 예상, 방전 ${powerKw} kW 추천`
      : `${ADVICE_RULES[kind].ruleLabel} ${powerKw} kW 추천 (${untilText}까지)`,
    now,
  )
  // 자동 모드면 사람이 누르지 않아도 바로 반영한 것으로 처리 (실제 제어가 아니라 시뮬레이션)
  if (settings.operationMode === 'AUTO') {
    currentDecision.status = 'AUTO'
    currentDecision.appliedBy = '자동 모드' // 명세 v0.2 값. 화면이 "(시뮬레이션)"을 붙여 보여 줌
    currentDecision.appliedAt = toLocalDateTime(now)
    appliedEssKw = followKw(currentDecision)
    addEvent('INFO', 'ACTION', `${ACTION_TEXTS[action]} ${powerKw} kW 자동 적용 (시뮬레이션)`, now)
  }
}

// ---- 운영 이력 ----
// 화면을 켠 뒤에 실제로 나온 추천들 (실시간 관제에서 적용/무시하면 여기에도 반영됨)
const sessionDecisions = []

const PEAK_REASON =
  '브레이징로 가열과 사출기 가동이 겹쳐, 이번 15분 구간의 예상 피크가 목표 피크를 넘을 것으로 보입니다.'

// 하루치 이력의 뼈대. 날짜마다 일부를 빼고 값을 조금씩 바꿔서 씁니다.
const HISTORY_TEMPLATES = [
  {
    time: '02:15',
    action: 'CHARGE',
    ruleNo: 4,
    ruleLabel: '계획 충전',
    powerKw: 300,
    status: 'AUTO',
    reason:
      '하루 계획에 따라 충전합니다. 지금(경부하) 채워 두었다가 뒤에 올 피크와 비싼 시간대에 씁니다.',
  },
  {
    time: '09:30',
    action: 'DISCHARGE',
    ruleNo: 2,
    ruleLabel: '피크 방어',
    powerKw: 500,
    status: 'APPLIED',
    reason: PEAK_REASON,
  },
  {
    time: '10:15',
    action: 'DISCHARGE',
    ruleNo: 2,
    ruleLabel: '피크 방어',
    powerKw: 400,
    status: 'IGNORED',
    reason: PEAK_REASON,
  },
  {
    time: '11:00',
    action: 'IDLE',
    ruleNo: 1,
    ruleLabel: '안전 제한',
    powerKw: 0,
    status: 'AUTO',
    reason: '배터리실 온도가 제한값에 가까워 방전을 멈추고 대기합니다.',
  },
  {
    time: '14:45',
    action: 'DISCHARGE',
    ruleNo: 2,
    ruleLabel: '피크 방어',
    powerKw: 350,
    status: 'APPLIED',
    reason: PEAK_REASON,
  },
  {
    time: '16:30',
    action: 'DISCHARGE',
    ruleNo: 2,
    ruleLabel: '피크 방어',
    powerKw: 500,
    status: 'APPLIED',
    reason: PEAK_REASON,
  },
  {
    time: '17:15',
    action: 'DISCHARGE',
    ruleNo: 5,
    ruleLabel: '계획 방전',
    powerKw: 200,
    status: 'IGNORED',
    reason: '하루 계획에 따라 방전합니다. 지금은 최대부하라 배터리 전기를 쓰는 편이 쌉니다.',
  },
  {
    time: '18:00',
    action: 'DISCHARGE',
    ruleNo: 2,
    ruleLabel: '피크 방어',
    powerKw: 450,
    status: 'AUTO',
    reason: PEAK_REASON,
  },
  {
    time: '23:00',
    action: 'CHARGE',
    ruleNo: 4,
    ruleLabel: '계획 충전',
    powerKw: 300,
    status: 'AUTO',
    reason:
      '하루 계획에 따라 충전합니다. 지금(경부하) 채워 두었다가 뒤에 올 피크와 비싼 시간대에 씁니다.',
  },
]

// 같은 숫자를 넣으면 항상 같은 값(0~1)이 나오는 가짜 난수. 새로고침해도 이력이 바뀌지 않게 하려고 씀
function fakeRandom(seed) {
  const x = Math.sin(seed * 12.9898) * 43758.5453
  return x - Math.floor(x)
}

// 하루치 이력 만들기 (date 예: '2026-11-14')
function buildHistoryOfDay(date, now) {
  const daySeed = Number(date.replaceAll('-', ''))
  const list = []

  HISTORY_TEMPLATES.forEach((template, index) => {
    const random = (n) => fakeRandom(daySeed + index * 17 + n)
    if (random(0) < 0.2) return // 날마다 일부 추천은 없는 것으로

    const decided = new Date(`${date}T${template.time}:00`)
    decided.setSeconds(Math.floor(random(1) * 600)) // 구간 안에서 조금 늦게 나온 것처럼
    if (decided > now) return // 아직 오지 않은 시각

    const windowStartMs = windowStartOf(decided.getTime())
    const windowEnd = new Date(windowStartMs + WINDOW_MS)
    const isPeak = template.ruleLabel === '피크 방어'
    const predictedKw = Math.round(
      isPeak ? TARGET_PEAK_KW + 30 + random(2) * 250 : 1480 + random(2) * 260,
    )

    // 결과: 구간이 끝났을 때의 실제 피크
    let resultPeakKw = null
    if (windowEnd <= now) {
      if (isPeak && template.status === 'IGNORED') {
        resultPeakKw = Math.round(predictedKw - random(3) * 25) // 무시하면 그대로 초과
      } else if (isPeak) {
        resultPeakKw = Math.round(TARGET_PEAK_KW - 15 - random(3) * 110) // 방전해서 목표 이내
      } else {
        resultPeakKw = Math.round(predictedKw - random(3) * 40)
      }
    }

    const soc = round1(35 + random(4) * 50)
    const loadKw = Math.round(predictedKw + solarKwAt(decided) + random(5) * 60)
    const answeredAt = new Date(decided.getTime() + (20 + Math.floor(random(6) * 70)) * 1000)

    list.push({
      id: daySeed * 100 + index,
      decidedAt: toLocalDateTime(decided),
      windowStart: toLocalDateTime(new Date(windowStartMs)),
      windowEnd: toLocalDateTime(windowEnd),
      ruleNo: template.ruleNo,
      ruleLabel: template.ruleLabel,
      action: template.action,
      powerKw: template.powerKw,
      reason: template.reason,
      predictedKw,
      targetPeakKw: TARGET_PEAK_KW,
      usedValues: [
        { label: '예상 피크', value: predictedKw, unit: 'kW' },
        { label: '목표 피크', value: TARGET_PEAK_KW, unit: 'kW' },
        { label: '공장 소비', value: loadKw, unit: 'kW' },
        { label: '태양광 발전', value: Math.round(solarKwAt(decided)), unit: 'kW' },
        { label: 'SOC', value: soc, unit: '%' },
        { label: '최대 출력', value: MAX_DISCHARGE_KW, unit: 'kW' },
        { label: '요금 시간대', value: TARIFF_LABELS[periodAt(decided)], unit: '' },
        { label: '배터리실 온도', value: round1(22 + random(7) * 9), unit: '℃' },
        { label: '날씨', value: random(8) < 0.6 ? '맑음' : '흐림', unit: '' },
        { label: '기온', value: Math.round(9 + random(9) * 11), unit: '℃' },
      ],
      status: template.status,
      appliedBy: template.status === 'AUTO' ? '자동 모드' : '운영자',
      appliedAt: toLocalDateTime(answeredAt),
      resultPeakKw,
      resultStatus:
        resultPeakKw === null ? null : resultPeakKw > TARGET_PEAK_KW ? 'EXCEEDED' : 'DEFENDED',
    })
  })
  return list
}

// GET /api/battery-decisions?from=&to= 흉내 (from, to 예: '2026-11-14'). 최신 순으로 돌려줌
export async function fakeGetDecisions(from, to) {
  failIfOffline()
  const now = simNow()
  let list = []

  const day = new Date(`${from}T00:00:00`)
  const lastDay = new Date(`${to}T00:00:00`)
  for (let count = 0; day <= lastDay && count < 121; count++) {
    list = list.concat(buildHistoryOfDay(toLocalDateTime(day).slice(0, 10), now))
    day.setDate(day.getDate() + 1)
  }

  // 화면을 켠 뒤에 나온 추천도 기간 안에 있으면 함께 보여 줌
  const live = sessionDecisions
    .filter((item) => from <= item.decidedAt.slice(0, 10) && item.decidedAt.slice(0, 10) <= to)
    .map((item) => ({ ...item }))

  return list.concat(live).sort((a, b) => (a.decidedAt < b.decidedAt ? 1 : -1))
}

// 설비별 전력: 기준 전력을 비중대로 나누고, 브레이징로와 사출기에는 변동분을 더함
// (설비 전력을 모두 더하면 그 시각 공장 총 소비전력과 같음)
function equipmentsAt(time) {
  const seconds = time.getTime() / 1000
  const baseKw = baseLoadAt(time)
  const extraKw = {
    'BRZ-01': furnaceSwingAt(seconds),
    'INJ-01': injectionSwingAt(seconds) * 0.6,
    'INJ-02': injectionSwingAt(seconds) * 0.4,
  }
  return EQUIPMENTS.map((item) => ({
    id: item.id,
    zoneId: item.zoneId,
    name: item.name,
    ratedKw: item.ratedKw,
    loadKw: round1(baseKw * item.share + (extraKw[item.id] ?? 0)),
    origin: item.id === 'UTL-01' ? 'MEASURED' : 'SIMULATED',
    // 설비 상태. 브레이징로는 전력이 크게 오를 때 "가열 중"으로 표시 (필드는 확인 필요)
    state: item.id === 'BRZ-01' && furnaceSwingAt(seconds) > 100 ? 'HEATING' : 'RUNNING',
  }))
}

// ---- 하루 시뮬레이션 (15분 칸 96개) ----
// 오늘 전력 추이와 추이 분석이 같은 하루를 보도록, 하루치를 한 번 계산해 두고 같이 씁니다.
// method: 'PLAN' = AI 부하 예측으로 세운 하루 계획 (기본) / 'RULE' = 2단계 규칙 (비교용)
// 'PLAN'은 실시간 화면과 같은 순서로 움직입니다.
//   1) 칸이 바뀔 때마다 지금 잔량에서 남은 하루를 예측값으로 다시 계획 (예측이 빗나간 만큼 보정)
//   2) 실제 전력으로 방어선을 넘으면 그 칸에서 더 방전 (실시간 피크 방어)
const dayCache = new Map() // 같은 날·같은 설정이면 다시 계산하지 않음

// 계획 결과를 바꾸는 설정값을 한 줄로 (이 값이 같으면 다시 계산하지 않음)
function planSettingsKey() {
  return [
    TARGET_PEAK_KW,
    MAX_DISCHARGE_KW,
    ESS_CAPACITY_KWH,
    SOC_LOW_LIMIT,
    SOC_HIGH_LIMIT,
    settings.efficiencyPercent,
    settings.priceLightWon,
    settings.priceMidWon,
    settings.pricePeakWon,
    scenario,
  ].join('|')
}

// dateKey 예: '2026-11-16'
function simulateDay(dateKey, method = 'PLAN') {
  const cacheKey = `${dateKey}|${method}|${planSettingsKey()}`
  if (dayCache.has(cacheKey)) return dayCache.get(cacheKey)

  // 1) 칸마다 ESS가 없을 때의 값 (실제 값과 AI 예측값)
  const inputs = dayInputs(dateKey)
  const limits = essLimits()
  const ruleKw = method === 'RULE' ? ruleDay(inputs, START_SOC_PERCENT, limits).essKw : null

  // 2) 칸마다 ESS 운영 정하고 결과 계산
  //    다시 계획하는 때: 하루 시작, 매시 정각, 직전 칸에서 실시간 보정이 일어난 뒤 (계산 시간을 줄이려고)
  let socPercent = START_SOC_PERCENT
  let plan = null
  let planInputs = null
  let planStart = 0
  let corrected = false
  const slots = inputs.map((item, slot) => {
    let essKw = 0
    let lineKw = TARGET_PEAK_KW
    if (method === 'RULE') {
      essKw = ruleKw[slot]
    } else {
      if (!plan || slot % 4 === 0 || corrected) {
        planInputs = planInputsOf(inputs, slot)
        plan = planDay(planInputs, socPercent, limits)
        planStart = slot
      }
      const planKw = plan.essKw[slot - planStart]
      const plannedPeakNetKw = planInputs[slot - planStart].peakNetKw
      lineKw = plan.lineKw
      essKw = correctedEssKw(planKw, item.netKw, plan, socPercent, plannedPeakNetKw, limits)
      corrected = Math.abs(essKw - planKw) >= 1
    }
    socPercent += socChangeOf(essKw, limits)
    return {
      recordedAt: toLocalDateTime(item.start),
      hour: item.start.getHours(),
      loadKw: item.loadKw,
      solarKw: item.solarKw,
      netKw: item.netKw,
      essKw,
      gridKw: item.netKw - essKw, // 방전하면 덜 사고, 충전하면 더 삼
      socPercent,
      lineKw, // 그 칸의 방어선
      price: item.price,
      roomTempC:
        23 +
        3 * Math.sin(((slot / 4 - 10) / 24) * 2 * Math.PI) +
        (essKw !== 0 ? 2.2 : 0) +
        fakeRandom(daySeedOf(item.middle)) * 3,
      outsideTempC: outsideTempAt(item.middle),
    }
  })
  dayCache.set(cacheKey, slots)
  return slots
}

// ---- 실시간 계획 ----
// 15분 구간이 바뀔 때마다 지금 잔량에서 남은 하루를 AI 예측값으로 다시 계획함.
// 실제 전력이 예측과 달라 잔량이 어긋나도 다음 구간에 바로잡음 (내비게이션의 경로 재탐색과 같음)
let livePlan = null // { key, dateKey, startSlot, lineKw, essKw: [지금 칸부터 하루 끝까지] }

function livePlanOf(now, socPercent) {
  const dateKey = dateKeyOf(now)
  const startSlot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15)
  const key = `${dateKey}|${startSlot}|${planSettingsKey()}`
  if (!livePlan || livePlan.key !== key) {
    const result = planDay(planInputsOf(dayInputs(dateKey), startSlot), socPercent, essLimits())
    livePlan = { key, dateKey, startSlot, ...result }
  }
  return livePlan
}

// 하루 96칸 각각의 ESS 없이의 값 (공장, 태양광, 한전 구매, 요금 시간대, 단가)
// 실제 값(netKw)은 결과 계산에, AI 예측값(planNetKw, planPeakNetKw)은 계획에 씀
// 태양광은 아직 예보(Open-Meteo)를 연결하지 않아서 실제 값을 계획에도 씀
function dayInputs(dateKey) {
  const inputs = []
  for (let slot = 0; slot < 96; slot++) {
    const start = new Date(`${dateKey}T00:00:00`)
    start.setMinutes(slot * 15)
    const middle = new Date(start.getTime() + 7.5 * 60000)
    // 칸 안을 2분 30초 간격으로 여섯 번 재서 평균 냄 (실시간 화면이 쌓는 15분 평균과 같아지게)
    // 설비의 빠른 출렁임은 15분 평균에서 거의 0이 되므로 기준 전력만 씀
    let loadKw = 0
    let solarKw = 0
    for (let step = 0; step < 6; step++) {
      const time = new Date(start.getTime() + (step + 0.5) * 150000)
      loadKw += baseLoadAt(time) / 6
      solarKw += solarKwAt(time) / 6
    }
    const period = periodAt(middle)
    inputs.push({
      start,
      middle,
      loadKw,
      solarKw,
      netKw: loadKw - solarKw, // ESS가 없을 때의 구매전력 (실제)
      planNetKw: forecastLoadKwOfTime(start, 'mean') - solarKw, // AI 예측으로 본 값 (요금 계산용)
      planPeakNetKw: forecastLoadKwOfTime(start, 'peak') - solarKw, // 상위 90% 예측 (방어선 판단용)
      period,
      price: priceAt(middle, period),
    })
  }
  return inputs
}

// 오늘 이미 끝난 15분 구간 중 가장 높았던 구매전력 (0시 첫 구간 중이면 null)
function todayPeakOf(now) {
  const slot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15)
  const ended = simulateDay(dateKeyOf(now)).slice(0, slot)
  return ended.length ? Math.round(Math.max(...ended.map((item) => item.gridKw))) : null
}

// ---- 실시간 잔량 ----
// 화면을 연 시각의 잔량은 하루 시뮬레이션에서 가져오고, 그 뒤로는 충·방전한 만큼 바뀜
let liveSocPercent = null
let liveSocAtMs = 0

function updateLiveSoc(now) {
  if (liveSocPercent === null) {
    const slots = simulateDay(dateKeyOf(now))
    const slot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15)
    liveSocPercent = slot > 0 ? slots[slot - 1].socPercent : START_SOC_PERCENT
    liveSocAtMs = now.getTime()
    return
  }
  // 지난번 조회부터 지금까지 적용 중이던 출력으로 바뀐 양 (충전은 손실만큼 덜 참)
  const hours = (now.getTime() - liveSocAtMs) / 3600000
  liveSocAtMs = now.getTime()
  liveSocPercent += socChangeOf(appliedEssKw, essLimits()) * (hours / 0.25)
  if (liveSocPercent <= SOC_LOW_LIMIT) {
    liveSocPercent = SOC_LOW_LIMIT
    if (appliedEssKw > 0) {
      // 하한에 닿으면 배터리를 보호하려고 방전을 멈춤
      appliedEssKw = 0
      addEvent('WARNING', 'BATTERY', `잔량 하한 ${SOC_LOW_LIMIT}% 도달, 방전 멈춤`, now)
    }
  }
  if (liveSocPercent >= SOC_HIGH_LIMIT) {
    liveSocPercent = SOC_HIGH_LIMIT
    if (appliedEssKw < 0) {
      appliedEssKw = 0
      addEvent('INFO', 'BATTERY', `잔량 상한 ${SOC_HIGH_LIMIT}% 도달, 충전 멈춤`, now)
    }
  }
}

// ---- 12개월 최고 피크 ----
// 기본요금은 지난 1년 중 가장 높았던 15분 피크로 정해짐 (Jang 외, 2022). 실제 산정 기준 달은 확인 필요.
// 성적표와 같은 숫자가 나오도록 지난 11개월과 이번 달의 성적표에서 가장 높은 달을 고름
let peakRecordCache = { key: '', value: null }

async function peakRecordOf(now) {
  const key = `${dateKeyOf(now)}|${TARGET_PEAK_KW}`
  if (peakRecordCache.key === key) return peakRecordCache.value

  let year = null
  let month = null
  const cursor = new Date(now.getFullYear(), now.getMonth() - 11, 1)
  for (let count = 0; count < 12; count++) {
    const report = await fakeGetReport(toLocalDateTime(cursor).slice(0, 7))
    if (report.hasData) {
      const record = { peakKw: report.summary.monthPeakKw, at: report.summary.monthPeakAt }
      if (!year || record.peakKw > year.peakKw) year = record
      month = record // 마지막으로 남는 것이 이번 달
    }
    cursor.setMonth(cursor.getMonth() + 1)
  }
  peakRecordCache = { key, value: { year, month } }
  return peakRecordCache.value
}

// GET /api/battery-statuses/latest 흉내
export async function fakeGetLatestStatus() {
  failIfOffline()
  const now = simNow()
  seedEventsOnce(now)
  const seconds = now.getTime() / 1000

  const solarKw = solarKwAt(now)
  const gridWithoutEss = gridWithoutEssAt(now)
  updateLiveSoc(now)
  const socPercent = liveSocPercent

  // 현재 15분 구간 (예: 14:00~14:15)
  const windowStart = new Date(windowStartOf(now.getTime()))
  const windowEnd = new Date(windowStart.getTime() + WINDOW_MS)
  const remainingMinutes = (windowEnd.getTime() - now.getTime()) / 60000

  // 지금까지 쌓인 값에 "남은 시간 동안 최근 전력이 계속된다"고 보고 더한 것이 예상 평균
  // (회의록의 예측 예시 식을 단순하게 흉내 낸 것. 실제 예측은 백엔드가 합니다)
  // 순간값 대신 최근 약 4분 평균을 씀: 브레이징로가 4분 주기로 ±330kW씩 출렁여서,
  // 순간값을 쓰면 필요한 방전량이 몇 초 사이에 수백 kW씩 바뀜
  updateWindow(now.getTime(), gridWithoutEss)
  const demandSoFar = windowState.demandKw
  const recentWithoutEss = recentAverageWithoutEss(now)
  updateDecision(now, {
    soFarKw: demandSoFar,
    recentWithoutEss,
    remainingMinutes,
    socPercent,
  })

  const dischargeKw = Math.max(0, appliedEssKw)
  const chargeKw = Math.max(0, -appliedEssKw)
  const gridKw = gridWithoutEss - appliedEssKw // 방전하면 덜 사고, 충전하면 더 삼
  const loadKw = gridWithoutEss + solarKw
  // 게이지의 예상 평균도 같은 방식 (지금 충·방전 중인 양을 반영한 값)
  const predictedKw = demandSoFar + ((recentWithoutEss - appliedEssKw) * remainingMinutes) / 15
  const lineKw = livePlan.lineKw // 오늘 방어선 (보통 목표 피크, 못 지키는 날만 더 높음)

  // 12개월 최고 피크는 성적표 계산을 기다려야 해서(await) 맨 뒤에 둠.
  // 이 앞에서 기다리면 같은 순간에 요청한 추천 조회가 먼저 끝나 한 번 전의 추천을 받게 됨
  const peakRecord = await peakRecordOf(now)

  let peakStatus = 'NORMAL'
  if (predictedKw > TARGET_PEAK_KW) peakStatus = 'CRITICAL'
  else if (predictedKw >= WARN_KW) peakStatus = 'WARNING'

  // 단계가 바뀌면 이벤트로 남김 (경고는 추천 이벤트와 겹치므로 주의·정상만)
  if (lastPeakStatus && lastPeakStatus !== peakStatus) {
    if (peakStatus === 'WARNING') addEvent('WARNING', 'STAGE', '주의 단계 진입', now)
    if (peakStatus === 'NORMAL') addEvent('NORMAL', 'STAGE', '정상 단계로 돌아옴', now)
  }
  lastPeakStatus = peakStatus

  const equipments = equipmentsAt(now)

  return {
    recordedAt: toLocalDateTime(now),
    loadKw: round1(loadKw),
    solarKw: round1(solarKw),
    chargeKw,
    dischargeKw,
    gridKw: round1(gridKw),
    socPercent: round1(socPercent),
    batteryTempC: round1(24 + Math.sin(seconds / 200)),
    // ESS 전력변환장치(PCS) 상태. 배터리 건강도(SOH)는 팀 결정으로 보내지 않음 (명세 확인 필요 11번)
    pcsStatus: dischargeKw > 0 ? 'DISCHARGING' : chargeKw > 0 ? 'CHARGING' : 'STANDBY',
    weather: { text: weatherOf(now), tempC: Math.round(outsideTempAt(now)) },
    // 시뮬레이션 배속 (1이면 실제 속도). 화면에 "10배속"처럼 알려 주기 위한 값. 필드는 확인 필요
    simulationSpeed: SIMULATION_SPEED,
    // 15분 피크
    windowStart: toLocalDateTime(windowStart),
    windowEnd: toLocalDateTime(windowEnd),
    remainingSeconds: Math.round((windowEnd.getTime() - now.getTime()) / 1000),
    predictedKw: round1(predictedKw),
    targetPeakKw: TARGET_PEAK_KW,
    warnKw: WARN_KW,
    peakStatus,
    // 방어 필요 전력: 방어선 안으로 들어오려면 지금보다 더 방전해야 하는 전력 (kW)
    defenseKw: Math.ceil(
      neededDischargeKw(predictedKw, remainingMinutes, lineKw, livePlan.marginKw),
    ),
    // 구간 시작부터 지금까지 쌓인 값 (명세 1번. 예상 피크 카드의 15분 흐름 그래프)
    windowPoints: windowState.points.slice(),
    // 오늘·이번 달 최대 피크 (명세 1번에 있음. 지금 화면은 안 씀)
    todayPeakKw: todayPeakOf(now),
    monthPeakKw: peakRecord.month.peakKw,
    // 요금 시간대 (명세 1번의 필드만 보냄: period, label, until, nextLabel, periods)
    tariff: specTariffOf(tariffAt(now)),
    // 공급원 비율 (공장 전체 기준, 계산 방식은 확인 필요)
    sourceRatio: {
      gridPercent: round1((gridKw / loadKw) * 100),
      solarPercent: round1((solarKw / loadKw) * 100),
      essPercent: round1((dischargeKw / loadKw) * 100),
    },
    equipments,
  }
}

// GET /api/battery-statuses?from=&to= 흉내: 오늘 00시부터 지금까지 15분 간격
export async function fakeGetTodayStatuses() {
  if (fakeMode === 'chart-error') {
    throw new Error('서버에 연결할 수 없습니다.')
  }
  const now = simNow()
  // 하루 시뮬레이션에서 지금까지의 칸만 보냄 (추이 분석과 같은 값)
  return simulateDay(dateKeyOf(now))
    .filter((item) => new Date(item.recordedAt) <= now)
    .map((item) => ({
      recordedAt: item.recordedAt,
      loadKw: Math.round(item.loadKw),
      solarKw: Math.round(item.solarKw),
      gridKw: Math.round(item.gridKw),
      essKw: Math.round(item.essKw), // ESS 충·방전 (방전 +, 충전 −). 명세에 없는 필드라 백엔드와 확정 필요
      socPercent: round1(item.socPercent),
    }))
}

// GET /api/battery-decisions 의 최신 1건 흉내
export async function fakeGetLatestDecision() {
  return currentDecision ? { ...currentDecision } : null
}

// PATCH /api/battery-decisions/{id} 흉내 (status: 'APPLIED' 또는 'IGNORED')
export async function fakeUpdateDecision(id, status) {
  failIfOffline()
  if (!currentDecision || currentDecision.id !== id) {
    throw new Error('이미 지난 추천입니다. 최신 추천을 확인해 주세요.')
  }
  currentDecision.status = status
  if (status === 'APPLIED') {
    currentDecision.appliedBy = '운영자'
    currentDecision.appliedAt = toLocalDateTime(simNow())
    // 방전은 +, 충전은 −. 그 뒤로는 추천이 끝날 때까지 updateDecision이 출력을 맞춰 감
    const sign = { DISCHARGE: 1, CHARGE: -1 }[currentDecision.action] ?? 0
    appliedEssKw = sign * currentDecision.powerKw
    addEvent(
      'INFO',
      'ACTION',
      `${ACTION_TEXTS[currentDecision.action]} ${currentDecision.powerKw} kW 적용 (운영자)`,
    )
  } else {
    appliedEssKw = 0
    addEvent('INFO', 'ACTION', `${currentDecision.ruleLabel} 추천을 넘김 (운영자)`)
  }
  return { ...currentDecision }
}

// GET /api/events?limit= 흉내: 최근 이벤트 (최신 순). 주소와 필드는 확인 필요
export async function fakeGetRecentEvents(limit = 10) {
  failIfOffline()
  return events.slice(0, limit).map((item) => ({ ...item }))
}

// 구역 한 곳의 최근 1시간 전력과 오늘 요약 (설비 모니터링 화면의 오른쪽 상세용). 주소와 필드는 확인 필요
export async function fakeGetZoneRecent(zoneId) {
  failIfOffline()
  const now = simNow()

  // 어떤 시각에 그 구역이 쓴 전력 (그 구역 설비들의 합)
  const zoneKwAt = (time) =>
    equipmentsAt(time)
      .filter((item) => item.zoneId === zoneId)
      .reduce((sum, item) => sum + item.loadKw, 0)

  const points = []
  for (let minutesAgo = 60; minutesAgo >= 0; minutesAgo--) {
    const time = new Date(now.getTime() - minutesAgo * 60000)
    points.push({
      recordedAt: toLocalDateTime(time),
      loadKw: Math.round(minuteAverage(zoneKwAt, time)),
    })
  }

  // 오늘 00시부터 지금까지 5분 간격으로 훑어 최대·평균·사용량을 구함
  const time = new Date(now)
  time.setHours(0, 0, 0, 0)
  const values = []
  while (time <= now) {
    values.push(zoneKwAt(time))
    time.setMinutes(time.getMinutes() + 5)
  }
  const total = values.reduce((sum, value) => sum + value, 0)

  return {
    zoneId,
    points,
    todayMaxKw: Math.round(Math.max(...values)),
    todayAvgKw: Math.round(total / values.length),
    todayKwh: Math.round((total * 5) / 60),
  }
}

// ---- 성적표 (GET /api/reports?month= 흉내) ----
// 단가와 배출계수는 화면 확인용으로 넣은 임시 값입니다. 실제 계산은 백엔드가 합니다.
// 전력량요금 단가는 위의 PRICES_BY_SEASON(계절별)을 씁니다.
let BASE_PRICE_PER_KW = 8320 // 기본요금 단가 (원/kW)
const EMISSION_FACTOR = 0.4594 // 탄소 배출계수 (kgCO2/kWh, 확인 필요)
// 피크가 잘 생기는 시각 (실제 데이터에서 공장 − 태양광이 1,800을 넘은 칸이 많은 시각: 아침 8시, 해 진 뒤 16~20시)
const BUSY_HOURS = [8, 16, 17, 18, 19, 20]
let ESS_CAPACITY_KWH = 1000

// month 예: '2026-11'. 이번 달이면 오늘까지, 지난 달이면 한 달 전체를 돌려줌
export async function fakeGetReport(month) {
  failIfOffline()
  const now = simNow()
  const thisMonth = toLocalDateTime(now).slice(0, 7)
  const [year, monthNumber] = month.split('-').map(Number)
  const daysInMonth = new Date(year, monthNumber, 0).getDate()

  let lastDay = daysInMonth
  if (month === thisMonth) lastDay = now.getDate()
  if (month > thisMonth) lastDay = 0 // 아직 오지 않은 달은 기록 없음

  // 아직 기록이 없는 달
  if (lastDay === 0) {
    return { month, hasData: false, targetPeakKw: TARGET_PEAK_KW }
  }

  const dailyPeaks = []
  const heatmap = []
  const exceeded = []
  const dailyDischarge = []
  const tariffKwh = { LIGHT: 0, MID: 0, PEAK: 0 }
  const prices = PRICES_BY_SEASON[seasonOf(new Date(year, monthNumber - 1, 1))] // 그 달 계절의 단가
  let defenseTotal = 0
  let solarKwh = 0

  for (let day = 1; day <= lastDay; day++) {
    const date = `${month}-${pad(day)}`
    const seed = year * 10000 + monthNumber * 100 + day
    const random = (n) => fakeRandom(seed + n * 7)
    const weekDay = new Date(year, monthNumber - 1, day).getDay()
    const weekend = weekDay === 0 || weekDay === 6
    const over = !weekend && random(1) < 0.12 // 가끔 목표를 넘는 날

    // 그날의 최대 피크와, 방전하지 않았을 때 예상했던 피크
    let peakKw = (weekend ? 1560 : 1690) + random(2) * 105
    if (over) peakKw = TARGET_PEAK_KW + 3 + random(2) * 40
    peakKw = Math.round(peakKw)
    let expectedKw = peakKw + 80 + random(3) * 240
    if (over) expectedKw = peakKw + 15 + random(3) * 60
    if (weekend) expectedKw = peakKw + random(3) * 40
    dailyPeaks.push({ date, peakKw, expectedKw: Math.round(expectedKw) })

    // 시각별 최대 15분 피크 (히트맵용) + 요금 시간대별 구매전력량
    const peakHour = BUSY_HOURS[Math.floor(random(4) * BUSY_HOURS.length)]
    for (let hour = 0; hour < 24; hour++) {
      let low = 0.82
      let high = 0.9
      if (hour < 7 || hour >= 21) {
        low = 0.7
        high = 0.78
      } else if (BUSY_HOURS.includes(hour)) {
        low = 0.9
        high = 0.985
      }
      const ratio = hour === peakHour ? 1 : low + random(10 + hour) * (high - low)
      const hourPeakKw = Math.round(peakKw * ratio)
      heatmap.push({ date, hour, peakKw: hourPeakKw })

      const period = periodAt(new Date(year, monthNumber - 1, day, hour))
      tariffKwh[period] += hourPeakKw * 0.93 // 1시간 평균은 최대값보다 조금 낮다고 가정
    }

    if (!weekend) defenseTotal += 2 + Math.floor(random(5) * 3)
    if (over) {
      const minute = [0, 15, 30, 45][Math.floor(random(6) * 4)]
      const start = new Date(year, monthNumber - 1, day, peakHour, minute)
      const lowBattery = random(7) < 0.25
      exceeded.push({
        windowStart: toLocalDateTime(start),
        windowEnd: toLocalDateTime(new Date(start.getTime() + WINDOW_MS)),
        resultPeakKw: peakKw,
        overKw: peakKw - TARGET_PEAK_KW,
        action: 'DISCHARGE',
        powerKw: [350, 400, 500][Math.floor(random(8) * 3)],
        status: lowBattery ? 'APPLIED' : 'IGNORED',
        cause: lowBattery ? '잔량 부족으로 방전 중단' : '추천을 넘김',
      })
    }

    dailyDischarge.push({
      date,
      kwh: Math.round(weekend ? 250 + random(9) * 200 : 550 + random(9) * 500),
    })
    solarKwh += 4800 + random(30) * 1500
  }

  const sum = (list) => list.reduce((total, value) => total + value, 0)
  const topDay = dailyPeaks.reduce((best, item) => (item.peakKw > best.peakKw ? item : best))
  const topHour = heatmap.find((item) => item.date === topDay.date && item.peakKw === topDay.peakKw)
  const reductions = dailyPeaks.map((item) => item.expectedKw - item.peakKw)

  // 전기요금 (원)
  const energyCharge = Math.round(
    sum(Object.keys(tariffKwh).map((key) => tariffKwh[key] * prices[key])),
  )
  const baseCharge = topDay.peakKw * BASE_PRICE_PER_KW
  const totalKwh = sum(Object.values(tariffKwh))
  const dischargeKwh = sum(dailyDischarge.map((item) => item.kwh))
  const selfUseKwh = Math.round(solarKwh * 0.995)

  return {
    month,
    hasData: true,
    periodStart: `${month}-01`,
    periodEnd: `${month}-${pad(lastDay)}`,
    targetPeakKw: TARGET_PEAK_KW,
    summary: {
      defenseTotal,
      defenseSuccess: defenseTotal - exceeded.length,
      defenseSuccessPercent: round1(((defenseTotal - exceeded.length) / defenseTotal) * 100),
      monthPeakKw: topDay.peakKw,
      // 목표를 넘은 날이면 그 15분 구간의 시작 시각을, 아니면 그 시각(정시)을 줌
      monthPeakAt:
        exceeded.find((item) => item.windowStart.slice(0, 10) === topDay.date)?.windowStart ??
        `${topDay.date}T${pad(topHour.hour)}:00:00`,
      avgReductionKw: Math.round(sum(reductions) / reductions.length),
      maxReductionKw: Math.max(...reductions),
    },
    dailyPeaks,
    heatmap,
    bill: {
      total: energyCharge + baseCharge,
      energyCharge,
      baseCharge,
      // 목표 피크를 넘어서 더 내게 된 기본요금 (넘지 않았으면 0)
      baseChargeOverTarget: Math.max(0, topDay.peakKw - TARGET_PEAK_KW) * BASE_PRICE_PER_KW,
      monthEndEstimate: Math.round((energyCharge / lastDay) * daysInMonth) + baseCharge,
      // 지난달 요금: 이번 달 예상의 94~106% 사이 (단가가 바뀌어도 비교가 터무니없어지지 않게)
      lastMonthTotal: Math.round(
        (Math.round((energyCharge / lastDay) * daysInMonth) + baseCharge) *
          (0.94 + fakeRandom(year * 100 + monthNumber) * 0.12),
      ),
      byTariff: Object.keys(tariffKwh).map((key) => ({
        period: key,
        label: TARIFF_LABELS[key],
        kwh: Math.round(tariffKwh[key]),
        percent: round1((tariffKwh[key] / totalKwh) * 100),
        charge: Math.round(tariffKwh[key] * prices[key]),
      })),
    },
    exceeded: exceeded.reverse(), // 최신 순
    ess: {
      dailyDischarge,
      dischargeKwh,
      chargeKwh: Math.round(dischargeKwh / 0.895),
      efficiencyPercent: 89.5,
      cycles: round1(dischargeKwh / ESS_CAPACITY_KWH),
      avgSocPercent: 58.3,
      minSocPercent: 22.4,
      blockedCount: exceeded.filter((item) => item.status === 'APPLIED').length,
    },
    solar: {
      generationKwh: Math.round(solarKwh),
      selfUseKwh,
      selfUsePercent: 99.5,
      gridReductionKwh: selfUseKwh,
      emissionFactor: EMISSION_FACTOR,
      carbonTon: round1((selfUseKwh * EMISSION_FACTOR) / 1000),
    },
  }
}

// ---- 분석 > 추이 분석 ----
let SOC_LOW_LIMIT = 20 // 잔량 하한 (%)
let SOC_HIGH_LIMIT = 90 // 잔량 상한 (%)
let ROOM_TEMP_LIMIT_C = 35 // 배터리실 온도 제한 (℃)
const SLOTS_PER_INTERVAL = { QUARTER: 1, HOUR: 4, DAY: 96 } // 15분 칸 몇 개를 하나로 묶는지

// GET /api/analyses/trend?from=&to=&interval= 흉내 (주소와 필드는 확인 필요)
// from, to 예: '2026-11-10' / interval: 'QUARTER'(15분) | 'HOUR'(1시간) | 'DAY'(1일)
// 요약·시간대별 평균·처리별 결과는 모두 여기(백엔드 역할)에서 계산해서 줍니다.
export async function fakeGetTrendAnalysis(from, to, interval) {
  failIfOffline()
  const now = simNow()
  const end = new Date(`${to}T00:00:00`)
  end.setDate(end.getDate() + 1)
  const last = end < now ? end : now // 아직 오지 않은 시간은 만들지 않음

  // 1) 15분 간격의 원본 값: 날마다 하루 시뮬레이션을 가져와 아직 오지 않은 칸은 뺌
  const raw = []
  const weather = []
  const day = new Date(`${from}T00:00:00`)
  for (let count = 0; day < last && count < 121; count++) {
    weather.push({ date: dateKeyOf(day), text: weatherOf(day) })
    for (const item of simulateDay(dateKeyOf(day))) {
      if (new Date(item.recordedAt) >= last) break
      raw.push(item)
    }
    day.setDate(day.getDate() + 1)
  }

  // 2) 고른 간격으로 묶어 평균 냄 (그래프용)
  const size = SLOTS_PER_INTERVAL[interval] ?? 4
  const average = (list, field) => list.reduce((sum, item) => sum + item[field], 0) / list.length
  const points = []
  for (let index = 0; index < raw.length; index += size) {
    const group = raw.slice(index, index + size)
    points.push({
      recordedAt: group[0].recordedAt,
      loadKw: Math.round(average(group, 'loadKw')),
      gridKw: Math.round(average(group, 'gridKw')),
      solarKw: Math.round(average(group, 'solarKw')),
      essKw: Math.round(average(group, 'essKw')),
      socPercent: round1(average(group, 'socPercent')),
      roomTempC: round1(average(group, 'roomTempC')),
      outsideTempC: round1(average(group, 'outsideTempC')),
    })
  }

  // 3) 기간 요약과 눈에 띄는 구간
  // field 값이 가장 큰(또는 작은) 기록 하나를 고름
  const pick = (list, field, isBetter) =>
    list.reduce(
      (best, item) => (best === null || isBetter(item[field], best[field]) ? item : best),
      null,
    )
  const bigger = (a, b) => a > b
  const smaller = (a, b) => a < b
  const maxGrid = pick(raw, 'gridKw', bigger)
  const minGrid = pick(raw, 'gridKw', smaller)
  const minSoc = pick(raw, 'socPercent', smaller)
  const maxSoc = pick(raw, 'socPercent', bigger)
  const maxRoom = pick(raw, 'roomTempC', bigger)
  // 한낮(12시대)에 태양광이 가장 적게 나온 때
  const minSolar = pick(
    raw.filter((item) => item.hour === 12),
    'solarKw',
    smaller,
  )
  // kW 값을 15분(0.25시간)씩 더해 kWh로 바꿈
  const sumKwh = (valueOf) => raw.reduce((sum, item) => sum + valueOf(item) * 0.25, 0)
  const loadKwh = sumKwh((item) => item.loadKw)
  const solarKwh = sumKwh((item) => item.solarKw)

  let summary = null
  const highlights = []
  if (raw.length) {
    summary = {
      maxPeakKw: Math.round(maxGrid.gridKw),
      maxPeakAt: maxGrid.recordedAt,
      avgGridKw: Math.round(average(raw, 'gridKw')),
      solarKwh: Math.round(solarKwh),
      solarPercentOfLoad: round1((solarKwh / loadKwh) * 100),
      dischargeKwh: Math.round(sumKwh((item) => Math.max(item.essKw, 0))),
      chargeKwh: Math.round(sumKwh((item) => Math.max(-item.essKw, 0))),
      efficiencyPercent: 89.5,
      socMinPercent: round1(minSoc.socPercent),
      socMinAt: minSoc.recordedAt,
      socMaxPercent: round1(maxSoc.socPercent),
      roomTempMaxC: round1(maxRoom.roomTempC),
      roomTempMaxAt: maxRoom.recordedAt,
    }
    highlights.push({
      at: maxGrid.recordedAt,
      label: '15분 피크 최대',
      value: Math.round(maxGrid.gridKw),
      unit: 'kW',
      level: maxGrid.gridKw > TARGET_PEAK_KW ? 'CRITICAL' : 'INFO',
    })
    highlights.push({
      at: minSoc.recordedAt,
      label: '배터리 잔량 최저',
      value: round1(minSoc.socPercent),
      unit: '%',
      level: 'INFO',
    })
    highlights.push({
      at: maxRoom.recordedAt,
      label: '배터리실 온도 최고',
      value: round1(maxRoom.roomTempC),
      unit: '℃',
      level: 'INFO',
    })
    if (minSolar) {
      highlights.push({
        at: minSolar.recordedAt,
        label: '한낮 태양광 발전 최저',
        value: Math.round(minSolar.solarKw),
        unit: 'kW',
        level: 'INFO',
      })
    }
    highlights.push({
      at: minGrid.recordedAt,
      label: '구매전력 최저',
      value: Math.round(minGrid.gridKw),
      unit: 'kW',
      level: 'INFO',
    })
  }

  // 4) 시각(0~23시)별 평균 구매전력과 그 시각의 요금 시간대
  const hourly = []
  for (let hour = 0; hour < 24; hour++) {
    const list = raw.filter((item) => item.hour === hour)
    const clock = `${pad(hour)}:00`
    hourly.push({
      hour,
      avgGridKw: list.length ? Math.round(average(list, 'gridKw')) : null,
      period: periodAt(new Date(`${to}T${clock}:00`)), // 기간 마지막 날 계절의 시간대
    })
  }

  // 5) 방전 추천을 처리(적용·자동·넘김)별로 묶은 결과. 추천 이력과 같은 데이터에서 셈
  const decisions = (await fakeGetDecisions(from, to)).filter(
    (item) => item.action === 'DISCHARGE' && item.resultStatus,
  )
  const byStatus = ['APPLIED', 'AUTO', 'IGNORED'].map((status) => {
    const list = decisions.filter((item) => item.status === status)
    const defended = list.filter((item) => item.resultStatus === 'DEFENDED').length
    return {
      status,
      count: list.length,
      defendedCount: defended,
      defendedPercent: list.length ? round1((defended / list.length) * 100) : null,
      avgResultPeakKw: list.length ? Math.round(average(list, 'resultPeakKw')) : null,
    }
  })

  return {
    from,
    to,
    interval,
    targetPeakKw: TARGET_PEAK_KW,
    socLowLimit: SOC_LOW_LIMIT,
    socHighLimit: SOC_HIGH_LIMIT,
    roomTempLimitC: ROOM_TEMP_LIMIT_C,
    summary,
    points,
    weather,
    hourly,
    byStatus,
    highlights,
  }
}

// ---- 환경 설정 ----
// 기준값 모음. 화면을 새로 고치면 처음 값으로 돌아갑니다 (가짜 서버라 저장해 둘 곳이 없음).
const settings = {
  // 피크 · 요금
  targetPeakKw: 1800,
  warnPercent: 97,
  // 전력량요금 단가 3칸은 지금 계절의 단가 (PRICES_BY_SEASON에서 가져옴)
  priceLightWon: PRICES_BY_SEASON[seasonOf(simNow())].LIGHT,
  priceMidWon: PRICES_BY_SEASON[seasonOf(simNow())].MID,
  pricePeakWon: PRICES_BY_SEASON[seasonOf(simNow())].PEAK,
  basePriceWon: 8320,
  // ESS
  capacityKwh: 1000,
  maxPowerKw: 500,
  socLowPercent: 20,
  socHighPercent: 90,
  efficiencyPercent: 89.5,
  roomTempLimitC: 35,
  // 운영
  operationMode: 'MANUAL', // MANUAL(운영자 승인) | AUTO(자동 모드, 시뮬레이션)
  pollSeconds: 5,
  decisionValidMinutes: 15,
}

const settingSavedAt = {} // 항목별 마지막 저장 시각
const settingHistory = [] // 변경 기록 (최신 순)
let nextSettingHistoryId = 1
let settingsSeeded = false

// 처음 부를 때 "예전에 저장해 둔 것처럼" 저장 시각과 변경 기록 몇 건을 만들어 둠
function seedSettingsOnce() {
  if (settingsSeeded) return
  settingsSeeded = true
  const now = simNow()
  const daysAgo = (days, hour, minute) => {
    const date = new Date(now)
    date.setDate(date.getDate() - days)
    date.setHours(hour, minute, 0, 0)
    return toLocalDateTime(date)
  }
  const savedAtByGroup = {
    peak: daysAgo(2, 16, 20),
    ess: daysAgo(6, 9, 5),
    operation: daysAgo(8, 11, 42),
  }
  const groupOf = (key) => {
    if (['operationMode', 'pollSeconds', 'decisionValidMinutes'].includes(key)) return 'operation'
    if (key.startsWith('price') || key === 'targetPeakKw' || key === 'warnPercent') return 'peak'
    if (key === 'basePriceWon') return 'peak'
    return 'ess'
  }
  for (const key of Object.keys(settings)) settingSavedAt[key] = savedAtByGroup[groupOf(key)]

  const past = [
    [daysAgo(2, 16, 20), 'peak.target_kw', '1850', '1800'],
    [daysAgo(2, 16, 20), 'peak.warn_ratio', '95', '97'],
    [daysAgo(6, 9, 5), 'ess.soc_low', '15', '20'],
    [daysAgo(8, 11, 42), 'operation.auto_mode', 'true', 'false'],
    [daysAgo(13, 14, 10), 'ess.room_temp_limit', '40', '35'],
  ]
  for (const [changedAt, key, oldValue, newValue] of past) {
    settingHistory.push({
      id: nextSettingHistoryId++,
      changedAt,
      key,
      label: SPEC_SETTINGS.find((item) => item.key === key).label,
      oldValue,
      newValue,
      changedBy: '운영자',
    })
  }
}

// 바뀐 기준값을 가짜 서버의 계산에 반영 (목표 피크를 바꾸면 통합 화면의 단계도 바뀜)
function applySettings() {
  TARGET_PEAK_KW = settings.targetPeakKw
  WARN_KW = Math.round((settings.targetPeakKw * settings.warnPercent) / 100)
  MAX_DISCHARGE_KW = settings.maxPowerKw
  // 단가 3칸은 지금 계절의 단가만 바꿈
  const prices = PRICES_BY_SEASON[seasonOf(simNow())]
  prices.LIGHT = settings.priceLightWon
  prices.MID = settings.priceMidWon
  prices.PEAK = settings.pricePeakWon
  BASE_PRICE_PER_KW = settings.basePriceWon
  ESS_CAPACITY_KWH = settings.capacityKwh
  SOC_LOW_LIMIT = settings.socLowPercent
  SOC_HIGH_LIMIT = settings.socHighPercent
  ROOM_TEMP_LIMIT_C = settings.roomTempLimitC
}

// GET /api/energy-settings 흉내: 기준값 목록
// ---- 명세 v0.2 모양으로 주고받기 (GET·PUT /api/settings, GET /api/setting-histories) ----
// 가짜 서버 안의 계산은 위 settings(camelCase)를 그대로 쓰고, 화면과는 명세의 키(점 이름)와 문자열 값으로 주고받음
// readOnly: 명세 "setting 키" 시트에서 "보기만"인 항목 (화면에서 고칠 수 없음)
const SPEC_SETTINGS = [
  { key: 'peak.target_kw', from: 'targetPeakKw', group: '피크', label: '목표 피크', unit: 'kW' },
  {
    key: 'peak.warn_ratio',
    from: 'warnPercent',
    group: '피크',
    label: '주의선 (목표 대비)',
    unit: '%',
  },
  {
    key: 'ess.capacity_kwh',
    from: 'capacityKwh',
    group: 'ESS',
    label: '배터리 용량',
    unit: 'kWh',
    readOnly: true,
  },
  {
    key: 'ess.max_power_kw',
    from: 'maxPowerKw',
    group: 'ESS',
    label: '배터리 최대 출력',
    unit: 'kW',
    readOnly: true,
  },
  { key: 'ess.soc_low', from: 'socLowPercent', group: 'ESS', label: '잔량 하한', unit: '%' },
  { key: 'ess.soc_high', from: 'socHighPercent', group: 'ESS', label: '잔량 상한', unit: '%' },
  {
    key: 'ess.room_temp_limit',
    from: 'roomTempLimitC',
    group: 'ESS',
    label: '배터리실 온도 한계',
    unit: '℃',
  },
  {
    key: 'solar.capacity_kw',
    fixed: 1000,
    group: '태양광',
    label: '태양광 용량',
    unit: 'kW',
    readOnly: true,
  },
  {
    key: 'tariff.base_charge',
    from: 'basePriceWon',
    group: '운영',
    label: '기본요금 단가',
    unit: '원/kW',
    readOnly: true,
  },
  {
    key: 'carbon.emission_factor',
    fixed: EMISSION_FACTOR,
    group: '운영',
    label: '탄소 배출계수',
    unit: 'kgCO2/kWh',
    readOnly: true,
  },
  {
    key: 'operation.auto_mode',
    from: 'operationMode',
    group: '운영',
    label: '자동 모드',
    unit: '',
  },
  // 아래 두 개는 명세에 없음 (화면에서 뺄지 팀 결정 대기, docs/api-spec-diff.md 2-10)
  {
    key: 'operation.poll_seconds',
    from: 'pollSeconds',
    group: '운영',
    label: '화면 갱신 주기',
    unit: '초',
  },
  {
    key: 'operation.decision_valid_minutes',
    from: 'decisionValidMinutes',
    group: '운영',
    label: '추천 유효 시간',
    unit: '분',
  },
]
const specSettingOf = (key) => SPEC_SETTINGS.find((item) => item.key === key)

// 가짜 서버 안의 값 → 명세의 문자열 값 (DB가 VARCHAR라 문자열로 보냄)
function specValueOf(item) {
  if (item.fixed !== undefined) return String(item.fixed)
  if (item.from === 'operationMode') return settings.operationMode === 'AUTO' ? 'true' : 'false'
  return String(settings[item.from])
}

// GET /api/settings 흉내 (명세 11번)
export async function fakeGetSettings() {
  failIfOffline()
  seedSettingsOnce()
  return SPEC_SETTINGS.map((item) => ({
    key: item.key,
    group: item.group,
    label: item.label,
    value: specValueOf(item),
    unit: item.unit,
    updatedAt: settingSavedAt[item.from] ?? settingSavedAt.targetPeakKw,
    updatedBy: '운영자',
  }))
}

// PUT /api/settings 흉내 (명세 12번): 여러 개를 한 번에 저장. changes 예: [{ key: 'peak.target_kw', value: '1750' }]
// 하나라도 잘못되면 아무것도 저장하지 않고 오류를 던짐 (실제 백엔드라면 400 응답)
export async function fakeUpdateSettings(changes) {
  failIfOffline()
  seedSettingsOnce()

  // 1) 먼저 모두 검사하고, 가짜 서버 안의 값으로 바꿔 둠
  const next = {}
  for (const change of changes) {
    const item = specSettingOf(change.key)
    if (!item) throw new Error('없는 설정 항목입니다.')
    if (item.readOnly) throw new Error(`${item.label}은(는) 화면에서 고칠 수 없는 항목입니다.`)
    if (typeof change.value !== 'string') throw new Error('설정 값은 문자열로 보내야 합니다.')
    if (item.from === 'operationMode') {
      if (!['true', 'false'].includes(change.value))
        throw new Error('자동 모드 값이 올바르지 않습니다.')
      next.operationMode = change.value === 'true' ? 'AUTO' : 'MANUAL'
    } else {
      const number = Number(change.value)
      if (change.value.trim() === '' || !Number.isFinite(number) || number <= 0) {
        throw new Error(`${item.label}에는 0보다 큰 숫자를 넣어야 합니다.`)
      }
      next[item.from] = number
    }
  }
  const low = next.socLowPercent ?? settings.socLowPercent
  const high = next.socHighPercent ?? settings.socHighPercent
  if (low >= high) throw new Error('잔량 하한은 잔량 상한보다 작아야 합니다.')

  // 2) 값이 바뀐 항목만 저장하고, 변경 기록과 이벤트를 남김
  const now = simNow()
  for (const change of changes) {
    const item = specSettingOf(change.key)
    const oldValue = specValueOf(item)
    if (oldValue === change.value) continue
    settings[item.from] = next[item.from]
    settingSavedAt[item.from] = toLocalDateTime(now)
    settingHistory.unshift({
      id: nextSettingHistoryId++,
      changedAt: toLocalDateTime(now),
      key: item.key,
      label: item.label,
      oldValue,
      newValue: change.value,
      changedBy: '운영자',
    })
    addEvent(
      'INFO',
      'SETTING',
      `설정 변경: ${item.label} ${oldValue} → ${change.value} (운영자)`,
      now,
    )
  }
  applySettings()

  // 저장된 전체 목록 (11번과 같은 모양)
  return fakeGetSettings()
}

// GET /api/setting-histories?from=&to= 흉내 (명세 13번): 기간 안의 변경 기록, 최신 순
export async function fakeGetSettingHistories(from, to) {
  failIfOffline()
  seedSettingsOnce()
  return settingHistory
    .filter((item) => from <= item.changedAt.slice(0, 10) && item.changedAt.slice(0, 10) <= to)
    .map((item) => ({ ...item }))
}

// ---- 분석 > 피크 분석 ----
// GET /api/analyses/peak?from=&to= 흉내 (주소와 필드는 확인 필요). from, to 예: '2026-11-01'
// 월간 성적표(fakeGetReport)와 같은 데이터에서 기간만 잘라 만듭니다. 그래서 같은 달이면 두 화면의 숫자가 같습니다.
// 피크 분석에 쓰는 값을 한 번에 만듦. 아래 두 API(피크 분석, 높았던 구간 순위)가 같이 씀
async function buildPeakAnalysis(from, to) {
  // 1) 기간에 걸친 달들의 성적표를 받아, 기간 안의 날짜만 모음
  const dailyPeaks = []
  const heatmap = []
  const exceeded = []
  const inRange = (date) => from <= date && date <= to
  const cursor = new Date(`${from.slice(0, 7)}-01T00:00:00`)
  for (let count = 0; count < 24; count++) {
    const month = toLocalDateTime(cursor).slice(0, 7)
    if (month > to.slice(0, 7)) break
    const report = await fakeGetReport(month)
    if (report.hasData) {
      dailyPeaks.push(...report.dailyPeaks.filter((item) => inRange(item.date)))
      heatmap.push(...report.heatmap.filter((item) => inRange(item.date)))
      exceeded.push(...report.exceeded.filter((item) => inRange(item.windowStart.slice(0, 10))))
    }
    cursor.setMonth(cursor.getMonth() + 1)
  }

  if (!dailyPeaks.length) {
    return { from, to, hasData: false, targetPeakKw: TARGET_PEAK_KW }
  }

  // 2) 날마다 피크가 가장 높았던 15분 구간과, 그때 추천을 어떻게 처리했는지
  const windows = dailyPeaks.map((day) => {
    const over = exceeded.find((item) => item.windowStart.slice(0, 10) === day.date)
    if (over) {
      return {
        windowStart: over.windowStart,
        windowEnd: over.windowEnd,
        peakKw: day.peakKw,
        noEssPeakKw: day.expectedKw,
        reducedKw: day.expectedKw - day.peakKw,
        action: over.action,
        status: over.status,
      }
    }
    const seed = Number(day.date.replaceAll('-', ''))
    const hour = heatmap.find((item) => item.date === day.date && item.peakKw === day.peakKw).hour
    const minute = [0, 15, 30, 45][Math.floor(fakeRandom(seed + 42) * 4)]
    const start = new Date(`${day.date}T${pad(hour)}:${pad(minute)}:00`)
    // 예상과 실제가 거의 같으면 방전 추천이 없었던 날로 봄
    const noAdvice = day.expectedKw - day.peakKw < 45
    const auto = fakeRandom(seed + 77) < 0.3
    return {
      windowStart: toLocalDateTime(start),
      windowEnd: toLocalDateTime(new Date(start.getTime() + WINDOW_MS)),
      peakKw: day.peakKw,
      noEssPeakKw: day.expectedKw,
      reducedKw: day.expectedKw - day.peakKw,
      // 그 구간에 추천이 없었으면 null (명세 16번)
      action: noAdvice ? null : 'DISCHARGE',
      status: noAdvice ? null : auto ? 'AUTO' : 'APPLIED',
    }
  })
  // 피크가 높은 순서
  const sortedWindows = [...windows].sort((a, b) => b.peakKw - a.peakKw)

  // 3) 피크가 잦은 시간대: 요금 시간대 묶음마다 평균을 내어 가장 높은 묶음을 고름
  let busiest = null
  for (const block of periodsOf(new Date(`${to}T00:00:00`))) {
    const list = heatmap.filter((item) => {
      const clock = `${pad(item.hour)}:00`
      return block.from <= clock && clock < block.to
    })
    if (!list.length) continue
    const avgKw = list.reduce((sum, item) => sum + item.peakKw, 0) / list.length
    if (!busiest || avgKw > busiest.avgKw) busiest = { ...block, avgKw }
  }

  // 4) 기간 안의 15분 구간 수 (오늘은 지금까지 지난 구간만 셈)
  const now = simNow()
  const today = toLocalDateTime(now).slice(0, 10)
  let windowCount = dailyPeaks.length * 96
  if (dailyPeaks[dailyPeaks.length - 1].date === today) {
    windowCount += Math.floor((now.getHours() * 60 + now.getMinutes()) / 15) - 96
  }

  const reductions = dailyPeaks.map((item) => item.expectedKw - item.peakKw)
  return {
    from,
    to,
    hasData: true,
    targetPeakKw: TARGET_PEAK_KW,
    summary: {
      maxPeakKw: sortedWindows[0].peakKw,
      maxPeakAt: sortedWindows[0].windowStart,
      exceededCount: exceeded.length,
      windowCount,
      avgReductionKw: Math.round(
        reductions.reduce((sum, value) => sum + value, 0) / reductions.length,
      ),
      maxReductionKw: Math.max(...reductions),
      busyFrom: busiest.from,
      busyTo: busiest.to,
      busyPeriodLabel: TARIFF_LABELS[busiest.period],
    },
    dailyPeaks,
    heatmap,
    sortedWindows,
  }
}

// 피크 분석: 요약 · 일별 최대 피크 · 히트맵
// 명세 v0.2에 없어서 백엔드에 추가 요청한 모양 (docs/api-spec-diff.md 2-9). 높았던 구간 순위는 아래 16번으로 따로 받음
export async function fakeGetPeakAnalysis(from, to) {
  failIfOffline()
  const result = await buildPeakAnalysis(from, to)
  if (!result.hasData) return result
  const analysis = { ...result }
  delete analysis.sortedWindows // 구간 순위는 이 API가 아니라 16번으로 보냄
  return analysis
}

// GET /api/demand-intervals?from=&to=&limit= 흉내 (명세 16번): 피크가 높았던 15분 구간 순위
export async function fakeGetDemandIntervals(from, to, limit = 5) {
  failIfOffline()
  const result = await buildPeakAnalysis(from, to)
  if (!result.hasData) return []
  return result.sortedWindows.slice(0, limit)
}
