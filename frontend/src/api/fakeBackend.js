// 백엔드 API가 만들어지기 전까지 쓰는 "가짜 백엔드"입니다.
// TODO: 백엔드 연결 후 이 파일은 삭제합니다.
//
// - 여기 있는 피크 예측·추천 계산은 화면 확인용으로 단순하게 흉내 낸 것입니다. 실제 계산은 백엔드가 합니다.
// - 이 파일은 batteryStatusApi.js, batteryDecisionApi.js 에서만 불러옵니다. (Store, 화면에서 직접 쓰지 않음)
// - 이 파일은 서버 역할이라 "서버 시각"을 직접 만듭니다(아래 simNow). 화면 코드에서는 new Date()를 쓰지 않습니다.

// 아래 기준값들은 환경 설정 화면에서 바꾸면 applySettings()가 고쳐 씁니다. 그래서 const가 아니라 let입니다.
let TARGET_PEAK_KW = 1800 // 목표 피크
let WARN_KW = 1746 // 주의선 (목표의 97%, 기준은 확인 필요)
let MAX_DISCHARGE_KW = 500 // ESS 최대 방전 출력

// 공장 컨셉: 전기차 열관리 부품 공장 (가상). 설비 구성과 전력 비중은 임시로 정한 값입니다.
// share = 평소 공장 전력에서 그 설비가 차지하는 비중 (합계 1.0)
const EQUIPMENTS = [
  { id: 'Z1-FIN', name: '핀 성형기', share: 0.05 },
  { id: 'Z1-TUBE', name: '튜브 가공기', share: 0.04 },
  { id: 'Z1-CORE', name: '코어 조립기', share: 0.03 },
  { id: 'Z2-FURN', name: '브레이징로', share: 0.3 },
  { id: 'Z2-DRY', name: '탈지·건조기', share: 0.06 },
  { id: 'Z3-INJ1', name: '사출기 1호', share: 0.1 },
  { id: 'Z3-INJ2', name: '사출기 2호', share: 0.1 },
  { id: 'Z3-TEMP', name: '금형 온조기', share: 0.04 },
  { id: 'Z4-MOTOR1', name: '1번 모터', share: 0 }, // 실측 설비 (조립 라인 컨베이어)
  { id: 'Z4-ROBOT', name: '체결 로봇', share: 0.02 },
  { id: 'Z5-LEAK', name: '기밀 검사기', share: 0.03 },
  { id: 'Z5-EOL', name: '성능 시험기', share: 0.05 },
  { id: 'U-COMP', name: '공기압축기', share: 0.08 },
  { id: 'U-CHIL', name: '냉각 칠러', share: 0.06 },
  { id: 'U-HVAC', name: '공조·조명', share: 0.04 },
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
let appliedDischargeKw = 0 // 운영자가 "적용"한 방전 전력

// ---- 최근 이벤트 (모니터링 화면의 "최근 이벤트" 목록용) ----
// level: CRITICAL(경고) | WARNING(주의) | NORMAL(정상) | INFO(그 밖의 기록)
const events = []
let nextEventId = 1
let lastPeakStatus = null // 단계가 바뀌었는지 알기 위해 직전 단계를 기억

function addEvent(level, message, date = simNow()) {
  events.unshift({ id: nextEventId++, occurredAt: toLocalDateTime(date), level, message })
  if (events.length > 50) events.pop()
}

// 화면을 처음 열었을 때 목록이 비어 있지 않도록, 조금 전에 있었던 일처럼 몇 건을 넣어 둠
let eventsSeeded = false
function seedEventsOnce(now) {
  if (eventsSeeded) return
  eventsSeeded = true
  const ago = (minutes) => new Date(now.getTime() - minutes * 60000)
  addEvent('INFO', '고요금 방전 추천을 넘김 (운영자)', ago(47))
  addEvent('WARNING', '주의 단계 진입', ago(31))
  addEvent('CRITICAL', '목표 초과 예상, 방전 500 kW 추천', ago(30))
  addEvent('INFO', '방전 500 kW 적용 (운영자)', ago(29))
  addEvent('NORMAL', '정상 단계로 돌아옴', ago(17))
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

// 낮에만 발전하는 태양광 (06시~18시, 맑은 날 한낮에 최대 600kW)
function solarAt(hour) {
  return Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI)) * 600
}

// 브레이징로가 승온할 때 늘어나는 전력 (약 4분 주기로 반복되게 만든 값)
// 15분 평균을 내면 거의 0이 되어, 순간값만 출렁이고 구간 평균은 크게 바꾸지 않음
function furnaceSwingAt(seconds) {
  return 330 * Math.sin(seconds / 40)
}

// 사출기가 한 사이클 돌 때마다 출렁이는 전력
function injectionSwingAt(seconds) {
  return 40 * Math.sin(seconds / 7)
}

// ---- 하루 전력 모양 ----
// 공장 소비전력의 시각별 기준값 (평일, kW). 0시부터 23시까지이고, 시각 사이는 앞뒤 값을 이어서 씀.
// 밤에도 브레이징로·칠러가 돌아 1,300kW 안팎을 쓰고, 오전·오후 생산 시간에 2,000kW 넘게 올라가는 모양.
// 한낮에는 태양광이 이만큼을 덜어 주므로, 해가 진 뒤인 17~18시나 흐리거나 비 오는 날에 피크 위험이 커짐
const LOAD_BY_HOUR = [
  1300, 1280, 1270, 1270, 1280, 1300, 1350, 1500, 1800, 1960, 2010, 2030, 1760, 1920, 1980, 1990,
  1960, 1880, 1820, 1700, 1600, 1520, 1440, 1360,
]

// 날짜마다 정해 둔 가짜 날씨. 흐리거나 비가 오면 태양광이 그 비율만큼만 나옴
const WEATHER_TEXTS = ['맑음', '맑음', '흐림', '맑음', '비', '흐림', '맑음']
const SOLAR_BY_WEATHER = { 맑음: 1, 흐림: 0.55, 비: 0.2 }

// 주소 뒤에 ?scenario=peak 를 붙이면 오늘을 "피크 위험일"(비 + 바쁜 날)로 만듦.
// 평범한 날에는 피크 장면이 드물어서, 시연에서 피크 방어를 보여 줄 때 씀
//   http://localhost:5173/?scenario=peak
const scenario = new URLSearchParams(window.location.search).get('scenario')

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

function weatherOf(date) {
  if (isPeakScenarioDay(date)) return '비'
  return WEATHER_TEXTS[daySeedOf(date) % 7]
}

// 그날 공장이 얼마나 바쁜지 (평일은 0.95~1.05배, 주말은 0.72배)
function dayFactorOf(date) {
  if (isPeakScenarioDay(date)) return 1.05
  const weekDay = date.getDay()
  if (weekDay === 0 || weekDay === 6) return 0.72
  return 0.95 + fakeRandom(daySeedOf(date) * 3) * 0.1
}

// 그 시각 공장의 기준 전력 (설비가 몇 분 단위로 출렁이는 부분은 뺀 값)
function baseLoadAt(time) {
  const hour = time.getHours() + time.getMinutes() / 60 + time.getSeconds() / 3600
  const index = Math.floor(hour)
  const now = LOAD_BY_HOUR[index]
  const next = LOAD_BY_HOUR[(index + 1) % 24]
  const seconds = time.getTime() / 1000
  // 15분 구간마다 평균이 조금씩 달라지게 하는 느린 변화
  const drift = 50 * Math.sin(seconds / 530) + 30 * Math.sin(seconds / 1700)
  return (now + (next - now) * (hour - index)) * dayFactorOf(time) + drift
}

// 그 시각 공장 전체가 쓰는 전력 (기준 전력 + 브레이징로·사출기의 출렁임)
function factoryLoadAt(time) {
  const seconds = time.getTime() / 1000
  return baseLoadAt(time) + furnaceSwingAt(seconds) + injectionSwingAt(seconds)
}

// 그 시각의 태양광 발전량 (날씨 반영)
function solarKwAt(time) {
  const hour = time.getHours() + time.getMinutes() / 60
  return solarAt(hour) * SOLAR_BY_WEATHER[weatherOf(time)]
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
      addWindowPoint(windowState.startMs + WINDOW_MS, gridWithoutEss - appliedDischargeKw)
      const endedKw = Math.round(windowState.demandKw)
      const endedStart = toLocalDateTime(new Date(windowState.startMs))
      const from = endedStart.slice(11, 16)
      const to = toLocalDateTime(new Date(startMs)).slice(11, 16)
      addEvent(
        endedKw > TARGET_PEAK_KW ? 'CRITICAL' : 'NORMAL',
        `${from}–${to} 구간 ${endedKw.toLocaleString('ko-KR')} kW로 종료`,
        new Date(startMs),
      )
      for (const item of sessionDecisions) {
        if (item.windowStart !== endedStart) continue
        item.resultPeakKw = endedKw
        item.resultStatus = endedKw > TARGET_PEAK_KW ? 'EXCEEDED' : 'DEFENDED'
      }
      // 방전은 그 구간을 지키려고 한 것이므로 구간이 끝나면 멈춤
      appliedDischargeKw = 0
    }
    // 새 구간 시작. 화면을 구간 중간에 열었다면 그 전까지는 15초 간격으로 계산해 채움
    windowState = { startMs, lastMs: startMs, demandKw: 0, points: [{ minute: 0, demandKw: 0 }] }
    for (let ms = startMs + 15000; ms < nowMs; ms += 15000) {
      addWindowPoint(ms, gridWithoutEssAt(new Date(ms)))
    }
  }
  addWindowPoint(nowMs, gridWithoutEss - appliedDischargeKw)
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
// 시간대 구분: 한전 계절별·시간대별 요금제의 구분 (Jang 외, Sustainability 2022, 표 5)
// 단가: 같은 논문 표 2의 고압A 선택II 값을 반올림한 임시 값. 2021년 일반용 기준이라 현재 산업용 단가는 확인 필요
// 환경 설정 화면의 단가 3칸은 "지금 계절"의 단가입니다. (applySettings 참고)
const TARIFF_LABELS = { LIGHT: '경부하', MID: '중간부하', PEAK: '최대부하' }
const SEASON_LABELS = { SUMMER: '여름', SPRING_FALL: '봄·가을', WINTER: '겨울' }
const SUMMER_PERIODS = [
  { from: '00:00', to: '09:00', period: 'LIGHT' },
  { from: '09:00', to: '10:00', period: 'MID' },
  { from: '10:00', to: '12:00', period: 'PEAK' },
  { from: '12:00', to: '13:00', period: 'MID' },
  { from: '13:00', to: '17:00', period: 'PEAK' },
  { from: '17:00', to: '23:00', period: 'MID' },
  { from: '23:00', to: '24:00', period: 'LIGHT' },
]
const PERIODS_BY_SEASON = {
  SUMMER: SUMMER_PERIODS,
  SPRING_FALL: SUMMER_PERIODS, // 봄·가을은 여름과 시간대 구분이 같고 단가만 다름
  WINTER: [
    { from: '00:00', to: '09:00', period: 'LIGHT' },
    { from: '09:00', to: '10:00', period: 'MID' },
    { from: '10:00', to: '12:00', period: 'PEAK' },
    { from: '12:00', to: '17:00', period: 'MID' },
    { from: '17:00', to: '20:00', period: 'PEAK' },
    { from: '20:00', to: '22:00', period: 'MID' },
    { from: '22:00', to: '23:00', period: 'PEAK' },
    { from: '23:00', to: '24:00', period: 'LIGHT' },
  ],
}
// 전력량요금 단가 (원/kWh)
const PRICES_BY_SEASON = {
  SUMMER: { LIGHT: 52, MID: 104, PEAK: 126 },
  SPRING_FALL: { LIGHT: 52, MID: 60, PEAK: 71 },
  WINTER: { LIGHT: 61, MID: 92, PEAK: 106 },
}

// 여름 6~8월, 봄·가을 3~5월과 9~10월, 겨울 11~2월
function seasonOf(date) {
  const month = date.getMonth() + 1
  if (month >= 6 && month <= 8) return 'SUMMER'
  if (month >= 3 && month <= 10) return 'SPRING_FALL'
  return 'WINTER'
}

function periodsOf(date) {
  return PERIODS_BY_SEASON[seasonOf(date)]
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

// ---- 추천 판단 흉내 ----
// 1) 이번 15분 구간이 목표 피크를 넘을 것 같으면 "필요한 만큼만" 방전을 추천 (최대 출력까지)
// 2) 한 구간에는 방전 추천을 한 번만 내고 구간이 끝날 때까지 유지 (방전 → 대기 → 방전 깜빡임 방지)
// 3) 적용한 뒤에는 구간이 끝날 때까지 필요한 양에 맞춰 출력을 조절 (시뮬레이션)
const MIN_ADVICE_KW = 10 // 이보다 작은 방전은 추천하지 않음 (의미 없이 버튼만 늘어남)

// 이번 구간 평균이 목표 피크를 넘을 것 같을 때, 남은 시간 동안 얼마나 방전해야 하는지 (kW)
// 15분 평균을 X kW 낮추려면 X × 15분만큼의 전력량을 빼야 하고, 그걸 남은 시간에 나눠 냄
// 목표(1,800)에 딱 맞추면 예측이 조금만 틀려도 1,801~1,820으로 넘어서, 주의선(1,746)까지 낮춤 (안전 여유)
// 예: 예상 1,830, 10분 남음 → (1,830 − 1,746) × 15 ÷ 10 = 126 kW
function neededDischargeKw(predictedKw, remainingMinutes) {
  if (predictedKw <= TARGET_PEAK_KW) return 0
  return ((predictedKw - WARN_KW) * 15) / Math.max(remainingMinutes, 0.5)
}

// predictedWithoutEss = 지금부터 방전을 하지 않으면 이번 구간이 끝날 때 될 평균
function updateDecision(now, predictedWithoutEss, socPercent, remainingMinutes) {
  const windowStartMs = windowStartOf(now.getTime())
  const windowStart = toLocalDateTime(new Date(windowStartMs))

  // 방전할 수 있는 최대: 최대 출력, 그리고 잔량 하한까지 남은 양을 남은 시간에 나눈 값
  const usableKwh = Math.max(0, ((socPercent - SOC_LOW_LIMIT) / 100) * ESS_CAPACITY_KWH)
  const batteryLimitKw = usableKwh / (Math.max(remainingMinutes, 0.5) / 60)
  const needKw = neededDischargeKw(predictedWithoutEss, remainingMinutes)
  const powerKw = Math.round(Math.min(MAX_DISCHARGE_KW, needKw, batteryLimitKw))

  const sameWindow = currentDecision?.windowStart === windowStart
  if (sameWindow && currentDecision.action === 'DISCHARGE') {
    if (currentDecision.status === 'RECOMMENDED' && powerKw >= MIN_ADVICE_KW) {
      // 아직 답하지 않은 추천이면 지금 필요한 양으로 숫자를 고쳐 둠
      currentDecision.powerKw = powerKw
      currentDecision.predictedKw = Math.round(predictedWithoutEss)
    }
    if (currentDecision.status === 'APPLIED' || currentDecision.status === 'AUTO') {
      appliedDischargeKw = powerKw // 넘김(IGNORED)이면 이 구간에는 방전하지 않음
    }
    return
  }

  let advice = null
  if (powerKw >= MIN_ADVICE_KW) {
    advice = {
      action: 'DISCHARGE',
      ruleNo: 2,
      ruleLabel: '피크 방어',
      powerKw,
      reason: `이번 15분 구간 예상 평균이 목표 피크를 넘을 것으로 보여, 남은 ${Math.ceil(remainingMinutes)}분 동안 ${powerKw.toLocaleString('ko-KR')} kW만 방전하면 목표 안으로 들어옵니다.`,
    }
  } else if (needKw >= MIN_ADVICE_KW) {
    // 방전이 필요하지만 잔량이 하한에 닿아 쓸 수 없음
    advice = {
      action: 'IDLE',
      ruleNo: 1,
      ruleLabel: '안전 제한',
      powerKw: 0,
      reason: `목표 피크를 넘을 것으로 보이지만, 배터리 잔량이 하한(${SOC_LOW_LIMIT}%)에 닿아 방전할 수 없습니다.`,
    }
  } else if (!sameWindow) {
    advice = {
      action: 'IDLE',
      ruleNo: 6,
      ruleLabel: '조치 없음',
      powerKw: 0,
      reason: '이번 구간은 목표 피크 안으로 들어올 것으로 보입니다.',
    }
  }
  // 같은 구간에서 같은 대기 추천이면 새로 만들지 않음
  if (!advice || (sameWindow && currentDecision.ruleNo === advice.ruleNo)) return

  currentDecision = {
    id: nextDecisionId++,
    decidedAt: toLocalDateTime(now),
    windowStart,
    windowEnd: toLocalDateTime(new Date(windowStartMs + WINDOW_MS)),
    ruleNo: advice.ruleNo,
    ruleLabel: advice.ruleLabel,
    predictedKw: Math.round(predictedWithoutEss),
    targetPeakKw: TARGET_PEAK_KW,
    resultPeakKw: null, // 구간이 끝나야 알 수 있음
    resultStatus: null,
    action: advice.action,
    powerKw: advice.powerKw,
    durationMinutes: advice.action === 'DISCHARGE' ? Math.ceil(remainingMinutes) : null,
    reason: advice.reason,
    usedValues: [
      { label: '예상 평균', value: Math.round(predictedWithoutEss), unit: 'kW' },
      { label: '목표 피크', value: TARGET_PEAK_KW, unit: 'kW' },
      { label: '필요 방전', value: Math.round(needKw), unit: 'kW' },
      { label: '남은 시간', value: Math.ceil(remainingMinutes), unit: '분' },
      { label: 'SOC', value: round1(socPercent), unit: '%' },
      { label: '최대 출력', value: MAX_DISCHARGE_KW, unit: 'kW' },
      {
        label: '배터리실 온도',
        value: round1(24 + Math.sin(now.getTime() / 1000 / 200)),
        unit: '℃',
      },
      { label: '날씨', value: weatherOf(now), unit: '' },
      { label: '기온', value: Math.round(outsideTempAt(now)), unit: '℃' },
    ],
    status: 'RECOMMENDED',
    appliedBy: null,
    appliedAt: null,
  }
  // 운영 이력 화면에 보이도록 기록 (대기 추천은 너무 많아져서 뺌)
  if (advice.action === 'IDLE') return
  sessionDecisions.push(currentDecision)
  addEvent('CRITICAL', `목표 초과 예상, 방전 ${advice.powerKw} kW 추천`, now)
  // 자동 모드면 사람이 누르지 않아도 바로 반영한 것으로 처리 (실제 제어가 아니라 시뮬레이션)
  if (settings.operationMode === 'AUTO') {
    currentDecision.status = 'AUTO'
    currentDecision.appliedBy = '자동 모드 (시뮬레이션)'
    currentDecision.appliedAt = toLocalDateTime(now)
    appliedDischargeKw = advice.powerKw
    addEvent('INFO', `방전 ${advice.powerKw} kW 자동 적용 (시뮬레이션)`, now)
  }
}

// ---- 운영 이력 ----
// 화면을 켠 뒤에 실제로 나온 추천들 (실시간 관제에서 적용/무시하면 여기에도 반영됨)
const sessionDecisions = []

const PEAK_REASON =
  '브레이징로 승온과 사출기 가동이 겹쳐, 이번 15분 구간의 예상 평균이 목표 피크를 넘을 것으로 보입니다.'

// 하루치 이력의 뼈대. 날짜마다 일부를 빼고 값을 조금씩 바꿔서 씁니다.
const HISTORY_TEMPLATES = [
  {
    time: '02:15',
    action: 'CHARGE',
    ruleNo: 4,
    ruleLabel: '저요금 충전',
    powerKw: 300,
    status: 'AUTO',
    reason: '경부하 시간대이고 배터리 잔량이 낮아 충전을 추천합니다.',
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
    ruleLabel: '고요금 방전',
    powerKw: 200,
    status: 'IGNORED',
    reason: '최대부하 시간대라 방전하면 전력량 요금을 줄일 수 있습니다.',
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
    ruleLabel: '저요금 충전',
    powerKw: 300,
    status: 'AUTO',
    reason: '경부하 시간대이고 배터리 잔량이 낮아 충전을 추천합니다.',
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
    const loadKw = Math.round(predictedKw + solarAt(decided.getHours()) + random(5) * 60)
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
        { label: '예상 평균', value: predictedKw, unit: 'kW' },
        { label: '목표 피크', value: TARGET_PEAK_KW, unit: 'kW' },
        { label: '공장 소비', value: loadKw, unit: 'kW' },
        { label: '태양광 발전', value: Math.round(solarAt(decided.getHours())), unit: 'kW' },
        { label: 'SOC', value: soc, unit: '%' },
        { label: '최대 출력', value: MAX_DISCHARGE_KW, unit: 'kW' },
        { label: '요금 시간대', value: TARIFF_LABELS[periodAt(decided)], unit: '' },
        { label: '배터리실 온도', value: round1(22 + random(7) * 9), unit: '℃' },
        { label: '날씨', value: random(8) < 0.6 ? '맑음' : '흐림', unit: '' },
        { label: '기온', value: Math.round(9 + random(9) * 11), unit: '℃' },
      ],
      status: template.status,
      appliedBy: template.status === 'AUTO' ? '자동 모드 (시뮬레이션)' : '운영자',
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
  for (let count = 0; day <= lastDay && count < 62; count++) {
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
// (설비 전력을 모두 더하면 그 시각 공장 소비전력과 같음)
function equipmentsAt(time) {
  const seconds = time.getTime() / 1000
  const baseKw = baseLoadAt(time)
  const motorKw = 2.2 + 0.3 * Math.sin(seconds / 5) // 1번 모터만 실측이라고 가정
  const extraKw = {
    'Z2-FURN': furnaceSwingAt(seconds),
    'Z3-INJ1': injectionSwingAt(seconds) * 0.6,
    'Z3-INJ2': injectionSwingAt(seconds) * 0.4,
    'Z4-MOTOR1': motorKw,
    'U-HVAC': -motorKw,
  }
  return EQUIPMENTS.map((item) => ({
    id: item.id,
    name: item.name,
    loadKw: round1(baseKw * item.share + (extraKw[item.id] ?? 0)),
    origin: item.id === 'Z4-MOTOR1' ? 'MEASURED' : 'SIMULATED',
    // 설비 상태. 브레이징로는 전력이 크게 오를 때 "승온 중"으로 표시 (필드는 확인 필요)
    state: item.id === 'Z2-FURN' && furnaceSwingAt(seconds) > 100 ? 'HEATING' : 'RUNNING',
  }))
}

// ---- 하루 시뮬레이션 (15분 칸 96개) ----
// 오늘 전력 추이와 추이 분석이 같은 하루를 보도록, 하루치를 한 번 계산해 두고 같이 씁니다.
// ESS는 실시간 추천과 같은 규칙을 흉내 냄: 15분 평균이 목표 피크를 넘으면 넘는 만큼만 방전(최대 출력까지),
// 경부하에는 충전. (다음 단계에서 하루를 미리 보는 계획 방식을 넣어 두 방식을 비교합니다)
const START_SOC_PERCENT = 50 // 하루 시작(0시) 잔량
const NIGHT_CHARGE_KW = 250 // 경부하 시간대 충전 전력
const dayCache = new Map() // 같은 날·같은 설정이면 다시 계산하지 않음

// dateKey 예: '2026-11-16'
function simulateDay(dateKey) {
  const efficiency = settings.efficiencyPercent / 100
  const cacheKey = [
    dateKey,
    TARGET_PEAK_KW,
    MAX_DISCHARGE_KW,
    ESS_CAPACITY_KWH,
    SOC_LOW_LIMIT,
    SOC_HIGH_LIMIT,
    efficiency,
    scenario,
  ].join('|')
  if (dayCache.has(cacheKey)) return dayCache.get(cacheKey)

  const slots = []
  let socPercent = START_SOC_PERCENT
  for (let slot = 0; slot < 96; slot++) {
    const start = new Date(`${dateKey}T00:00:00`)
    start.setMinutes(slot * 15)
    const middle = new Date(start.getTime() + 7.5 * 60000)
    // 설비의 빠른 출렁임은 15분 평균에서 거의 0이 되므로 기준 전력만 씀
    const loadKw = baseLoadAt(middle)
    const solarKw = solarKwAt(middle)
    const netKw = loadKw - solarKw // ESS가 없을 때의 한전 구매전력

    // ESS 전력: +는 방전, −는 충전. 잔량이 하한·상한을 넘지 않는 만큼만
    let essKw = 0
    if (netKw > TARGET_PEAK_KW) {
      const usableKwh = ((socPercent - SOC_LOW_LIMIT) / 100) * ESS_CAPACITY_KWH
      essKw = Math.max(0, Math.min(MAX_DISCHARGE_KW, netKw - TARGET_PEAK_KW, usableKwh / 0.25))
    } else if (periodAt(middle) === 'LIGHT') {
      const emptyKwh = ((SOC_HIGH_LIMIT - socPercent) / 100) * ESS_CAPACITY_KWH
      // 충전하면 한전에서 더 사므로, 충전 때문에 목표 피크를 넘지 않게 함
      const headroomKw = TARGET_PEAK_KW - netKw
      const chargeKw = Math.min(NIGHT_CHARGE_KW, headroomKw, emptyKwh / 0.25 / efficiency)
      essKw = -Math.max(0, chargeKw)
    }
    // 15분(0.25시간) 동안 바뀐 잔량. 충전할 때 손실(효율)을 반영
    if (essKw > 0) socPercent -= ((essKw * 0.25) / ESS_CAPACITY_KWH) * 100
    if (essKw < 0) socPercent += ((-essKw * 0.25 * efficiency) / ESS_CAPACITY_KWH) * 100

    slots.push({
      recordedAt: toLocalDateTime(start),
      hour: start.getHours(),
      loadKw,
      solarKw,
      netKw,
      essKw,
      gridKw: netKw - essKw, // 방전하면 덜 사고, 충전하면 더 삼
      socPercent,
      roomTempC:
        23 +
        3 * Math.sin(((slot / 4 - 10) / 24) * 2 * Math.PI) +
        (essKw !== 0 ? 2.2 : 0) +
        fakeRandom(daySeedOf(middle)) * 3,
      outsideTempC: outsideTempAt(middle),
    })
  }
  dayCache.set(cacheKey, slots)
  return slots
}

// 오늘 이미 끝난 15분 구간 중 가장 높았던 한전 구매전력 (0시 첫 구간 중이면 null)
function todayPeakOf(now) {
  const slot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15)
  const ended = simulateDay(dateKeyOf(now)).slice(0, slot)
  return ended.length ? Math.round(Math.max(...ended.map((item) => item.gridKw))) : null
}

// ---- 실시간 잔량 ----
// 화면을 연 시각의 잔량은 하루 시뮬레이션에서 가져오고, 그 뒤로는 방전한 만큼 줄어듦
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
  // 지난번 조회부터 지금까지 적용 중이던 방전 전력으로 줄어든 양
  const hours = (now.getTime() - liveSocAtMs) / 3600000
  liveSocAtMs = now.getTime()
  liveSocPercent -= ((appliedDischargeKw * hours) / ESS_CAPACITY_KWH) * 100
  if (liveSocPercent <= SOC_LOW_LIMIT) {
    liveSocPercent = SOC_LOW_LIMIT
    if (appliedDischargeKw > 0) {
      // 하한에 닿으면 배터리를 보호하려고 방전을 멈춤
      appliedDischargeKw = 0
      addEvent('WARNING', `잔량 하한 ${SOC_LOW_LIMIT}% 도달, 방전 멈춤`, now)
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
  const peakRecord = await peakRecordOf(now)

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
  const predictedWithoutEss = demandSoFar + (recentWithoutEss * remainingMinutes) / 15

  updateDecision(now, predictedWithoutEss, socPercent, remainingMinutes)

  const dischargeKw = appliedDischargeKw
  const gridKw = gridWithoutEss - dischargeKw
  const loadKw = gridWithoutEss + solarKw
  // 게이지의 예상 평균도 같은 방식 (지금 방전 중인 양을 뺀 값)
  const predictedKw = demandSoFar + ((recentWithoutEss - dischargeKw) * remainingMinutes) / 15

  let peakStatus = 'NORMAL'
  if (predictedKw > TARGET_PEAK_KW) peakStatus = 'CRITICAL'
  else if (predictedKw >= WARN_KW) peakStatus = 'WARNING'

  // 단계가 바뀌면 이벤트로 남김 (경고는 추천 이벤트와 겹치므로 주의·정상만)
  if (lastPeakStatus && lastPeakStatus !== peakStatus) {
    if (peakStatus === 'WARNING') addEvent('WARNING', '주의 단계 진입', now)
    if (peakStatus === 'NORMAL') addEvent('NORMAL', '정상 단계로 돌아옴', now)
  }
  lastPeakStatus = peakStatus

  const equipments = equipmentsAt(now)

  return {
    recordedAt: toLocalDateTime(now),
    loadKw: round1(loadKw),
    solarKw: round1(solarKw),
    chargeKw: 0,
    dischargeKw,
    gridKw: round1(gridKw),
    socPercent: round1(socPercent),
    batteryTempC: round1(24 + Math.sin(seconds / 200)),
    // ESS 전력변환장치(PCS) 상태와 배터리 건강 상태(SOH). 필드는 확인 필요
    pcsStatus: dischargeKw > 0 ? 'DISCHARGING' : 'STANDBY',
    sohPercent: 98.2,
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
    // 방어 필요 전력: 목표 안으로 들어오려면 지금보다 더 방전해야 하는 전력 (kW)
    defenseKw: Math.ceil(neededDischargeKw(predictedKw, remainingMinutes)),
    // 구간 시작부터 지금까지 쌓인 값 (그래프용). 필드는 확인 필요
    windowPoints: windowState.points.slice(),
    // 지금까지의 최대 피크. 필드는 확인 필요
    todayPeakKw: todayPeakOf(now),
    monthPeakKw: peakRecord.month.peakKw,
    // 12개월 최고 피크: 기본요금을 정하는 값. 이보다 낮은 피크는 막아도 기본요금이 줄지 않음
    yearPeakKw: peakRecord.year.peakKw,
    yearPeakAt: peakRecord.year.at,
    // 요금 시간대
    tariff: tariffAt(now),
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
    appliedDischargeKw = currentDecision.action === 'DISCHARGE' ? currentDecision.powerKw : 0
    addEvent('INFO', `방전 ${currentDecision.powerKw} kW 적용 (운영자)`)
  } else {
    addEvent('INFO', '방전 추천을 넘김 (운영자)')
  }
  return { ...currentDecision }
}

// GET /api/battery-statuses?from=&to= 흉내: 최근 1시간, 1분 간격 (모니터링의 전력 추세 그래프용)
export async function fakeGetRecentStatuses() {
  failIfOffline()
  const now = simNow()
  const list = []
  for (let minutesAgo = 60; minutesAgo >= 0; minutesAgo--) {
    const time = new Date(now.getTime() - minutesAgo * 60000)
    list.push({
      recordedAt: toLocalDateTime(time),
      gridKw: Math.round(minuteAverage(gridWithoutEssAt, time)),
    })
  }
  return list
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
      .filter((item) => item.id.startsWith(`${zoneId}-`))
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
const BUSY_HOURS = [10, 11, 14, 15, 17, 18] // 피크가 잘 생기는 시각 (하루 전력 모양에서 태양광을 뺀 값이 높은 때)
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
  for (let count = 0; day < last && count < 62; count++) {
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
      label: '한전 구매전력 최저',
      value: Math.round(minGrid.gridKw),
      unit: 'kW',
      level: 'INFO',
    })
  }

  // 4) 시각(0~23시)별 평균 한전 구매전력과 그 시각의 요금 시간대
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
// 이벤트 로그에 남길 때 쓰는 항목 이름
const SETTING_LABELS = {
  targetPeakKw: '목표 피크',
  warnPercent: '주의선',
  priceLightWon: '경부하 단가',
  priceMidWon: '중간부하 단가',
  pricePeakWon: '최대부하 단가',
  basePriceWon: '기본요금 단가',
  capacityKwh: '배터리 용량',
  maxPowerKw: '최대 충·방전 출력',
  socLowPercent: '잔량 하한',
  socHighPercent: '잔량 상한',
  efficiencyPercent: '충·방전 효율',
  roomTempLimitC: '배터리실 온도 제한',
  operationMode: '운영 모드',
  pollSeconds: '화면 갱신 주기',
  decisionValidMinutes: '추천 유효 시간',
}
const MODE_LABELS = { MANUAL: '운영자 승인', AUTO: '자동 모드 (시뮬레이션)' }

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
    [daysAgo(2, 16, 20), 'targetPeakKw', 1850, 1800],
    [daysAgo(2, 16, 20), 'warnPercent', 95, 97],
    [daysAgo(6, 9, 5), 'socLowPercent', 15, 20],
    [daysAgo(8, 11, 42), 'operationMode', 'AUTO', 'MANUAL'],
    [daysAgo(13, 14, 10), 'roomTempLimitC', 40, 35],
  ]
  for (const [changedAt, key, before, after] of past) {
    settingHistory.push({
      id: nextSettingHistoryId++,
      changedAt,
      key,
      before,
      after,
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
export async function fakeGetSettings() {
  failIfOffline()
  seedSettingsOnce()
  return Object.keys(settings).map((key) => ({
    key,
    value: settings[key],
    updatedAt: settingSavedAt[key],
    updatedBy: '운영자',
  }))
}

// PUT /api/energy-settings/{key} 흉내: 기준값 하나 바꾸기
// 잘못된 값이면 오류를 던짐 (실제 백엔드라면 400 응답)
export async function fakeUpdateSetting(key, value) {
  failIfOffline()
  seedSettingsOnce()
  if (!(key in settings)) {
    throw new Error('없는 설정 항목입니다.')
  }
  if (key === 'operationMode') {
    if (!MODE_LABELS[value]) throw new Error('운영 모드 값이 올바르지 않습니다.')
  } else {
    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
      throw new Error(`${SETTING_LABELS[key]}에는 0보다 큰 숫자를 넣어야 합니다.`)
    }
    const low = key === 'socLowPercent' ? value : settings.socLowPercent
    const high = key === 'socHighPercent' ? value : settings.socHighPercent
    if (low >= high) throw new Error('잔량 하한은 잔량 상한보다 작아야 합니다.')
  }

  const before = settings[key]
  const now = simNow()
  settings[key] = value
  settingSavedAt[key] = toLocalDateTime(now)
  settingHistory.unshift({
    id: nextSettingHistoryId++,
    changedAt: toLocalDateTime(now),
    key,
    before,
    after: value,
    changedBy: '운영자',
  })
  applySettings()

  // 이벤트 로그에도 남김
  const text = (item) =>
    key === 'operationMode' ? MODE_LABELS[item] : item.toLocaleString('ko-KR')
  addEvent(
    'INFO',
    `설정 변경: ${SETTING_LABELS[key]} ${text(before)} → ${text(value)} (운영자)`,
    now,
  )

  return { key, value, updatedAt: settingSavedAt[key], updatedBy: '운영자' }
}

// GET /api/energy-settings/history 흉내: 변경 기록 (최신 순). 주소와 필드는 확인 필요
export async function fakeGetSettingHistory(limit = 20) {
  failIfOffline()
  seedSettingsOnce()
  return settingHistory.slice(0, limit).map((item) => ({ ...item }))
}

// ---- 분석 > 피크 분석 ----
// GET /api/analyses/peak?from=&to= 흉내 (주소와 필드는 확인 필요). from, to 예: '2026-11-01'
// 월간 성적표(fakeGetReport)와 같은 데이터에서 기간만 잘라 만듭니다. 그래서 같은 달이면 두 화면의 숫자가 같습니다.
export async function fakeGetPeakAnalysis(from, to) {
  failIfOffline()

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
        status: over.status,
        note: over.cause,
      }
    }
    const seed = Number(day.date.replaceAll('-', ''))
    const hour = heatmap.find((item) => item.date === day.date && item.peakKw === day.peakKw).hour
    const minute = [0, 15, 30, 45][Math.floor(fakeRandom(seed + 42) * 4)]
    const start = new Date(`${day.date}T${pad(hour)}:${pad(minute)}:00`)
    // 예상과 실제가 거의 같으면 방전 추천이 없었던 날로 봄
    const noAdvice = day.expectedKw - day.peakKw < 45
    const auto = fakeRandom(seed + 77) < 0.3
    let status = auto ? 'AUTO' : 'APPLIED'
    let note = auto ? '자동 방전 (시뮬레이션)' : '방전 적용'
    if (noAdvice) {
      status = null
      note = '방전 추천 없음'
    }
    return {
      windowStart: toLocalDateTime(start),
      windowEnd: toLocalDateTime(new Date(start.getTime() + WINDOW_MS)),
      peakKw: day.peakKw,
      status,
      note,
    }
  })
  const topWindows = [...windows].sort((a, b) => b.peakKw - a.peakKw).slice(0, 8)

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
      maxPeakKw: topWindows[0].peakKw,
      maxPeakAt: topWindows[0].windowStart,
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
    topWindows,
  }
}
