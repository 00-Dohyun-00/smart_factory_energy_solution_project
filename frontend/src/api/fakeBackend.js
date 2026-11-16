// 백엔드 API가 만들어지기 전까지 쓰는 "가짜 백엔드"입니다.
// TODO: 백엔드 연결 후 이 파일은 삭제합니다.
//
// - 여기 있는 피크 예측·추천 계산은 화면 확인용으로 단순하게 흉내 낸 것입니다. 실제 계산은 백엔드가 합니다.
// - 이 파일은 batteryStatusApi.js, batteryDecisionApi.js 에서만 불러옵니다. (Store, 화면에서 직접 쓰지 않음)
// - 이 파일은 서버 역할이라 "서버 시각"을 직접 만듭니다(아래 simNow). 화면 코드에서는 new Date()를 쓰지 않습니다.

const TARGET_PEAK_KW = 1800 // 목표 피크
const WARN_KW = 1746 // 주의선 (목표의 97%, 기준은 확인 필요)
const MAX_DISCHARGE_KW = 500 // ESS 최대 방전 출력

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

// 낮에만 발전하는 태양광 (06시~18시, 한낮에 최대 600kW)
function solarAt(hour) {
  return Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI)) * 600
}

// 브레이징로가 승온할 때 늘어나는 전력 (약 4분 주기로 반복되게 만든 값)
// 뒤쪽 항은 15분 구간마다 평균이 조금씩 달라지게 하는 느린 변화
function furnaceSwingAt(seconds) {
  return 330 * Math.sin(seconds / 40) + 110 * Math.sin(seconds / 700)
}

// 사출기가 한 사이클 돌 때마다 출렁이는 전력
function injectionSwingAt(seconds) {
  return 40 * Math.sin(seconds / 7)
}

// ESS가 없을 때 한전에서 사야 하는 전력.
// 브레이징로 승온과 사출기 가동이 겹치면 목표 피크(1,800kW)를 넘도록 만든 값
function gridWithoutEssAt(seconds) {
  return 1700 + furnaceSwingAt(seconds) + injectionSwingAt(seconds)
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

// 지금까지의 누적값을 갱신. gridKw = 지금 한전에서 사고 있는 전력
function updateWindow(nowMs, gridKw) {
  const startMs = windowStartOf(nowMs)

  if (!windowState || windowState.startMs !== startMs) {
    // 방금 끝난 구간이 있으면 결과를 이벤트로 남김
    if (windowState) {
      const endedKw = Math.round(windowState.demandKw)
      const from = toLocalDateTime(new Date(windowState.startMs)).slice(11, 16)
      const to = toLocalDateTime(new Date(startMs)).slice(11, 16)
      addEvent(
        endedKw > TARGET_PEAK_KW ? 'CRITICAL' : 'NORMAL',
        `${from}–${to} 구간 ${endedKw.toLocaleString('ko-KR')} kW로 종료`,
        new Date(startMs),
      )
    }
    // 새 구간 시작. 화면을 구간 중간에 열었다면 그 전까지는 15초 간격으로 계산해 채움
    windowState = { startMs, lastMs: startMs, demandKw: 0, points: [{ minute: 0, demandKw: 0 }] }
    for (let ms = startMs + 15000; ms < nowMs; ms += 15000) {
      addWindowPoint(ms, gridWithoutEssAt(ms / 1000))
    }
  }
  addWindowPoint(nowMs, gridKw)
}

function addWindowPoint(ms, gridKw) {
  windowState.demandKw += (gridKw * (ms - windowState.lastMs)) / WINDOW_MS
  windowState.lastMs = ms
  windowState.points.push({
    minute: Math.round(((ms - windowState.startMs) / 60000) * 100) / 100,
    demandKw: Math.round(windowState.demandKw),
  })
}

// ---- 요금 시간대 ----
// 산업용 계절별·시간대별 요금제의 겨울철 시간대를 흉내 낸 임시 값입니다. (실제 구분과 단가는 확인 필요)
const TARIFF_LABELS = { LIGHT: '경부하', MID: '중간부하', PEAK: '최대부하' }
const TARIFF_PERIODS = [
  { from: '00:00', to: '08:00', period: 'LIGHT' },
  { from: '08:00', to: '09:00', period: 'MID' },
  { from: '09:00', to: '12:00', period: 'PEAK' },
  { from: '12:00', to: '16:00', period: 'MID' },
  { from: '16:00', to: '19:00', period: 'PEAK' },
  { from: '19:00', to: '22:00', period: 'MID' },
  { from: '22:00', to: '24:00', period: 'LIGHT' },
]

function tariffAt(now) {
  const time = `${pad(now.getHours())}:${pad(now.getMinutes())}`
  const index = TARIFF_PERIODS.findIndex((item) => item.from <= time && time < item.to)
  const current = TARIFF_PERIODS[index]
  const next = TARIFF_PERIODS[(index + 1) % TARIFF_PERIODS.length]
  return {
    period: current.period,
    label: TARIFF_LABELS[current.period],
    until: current.to === '24:00' ? '00:00' : current.to, // 이 시간대가 끝나는 시각
    nextLabel: TARIFF_LABELS[next.period],
    periods: TARIFF_PERIODS.map((item) => ({ ...item, label: TARIFF_LABELS[item.period] })),
  }
}

// 추천 판단 흉내: 피크 초과가 예상되면 방전, 아니면 대기
function updateDecision(now, predictedWithoutEss, socPercent, remainingMinutes) {
  const overKw = predictedWithoutEss - TARGET_PEAK_KW
  const action = overKw > 0 ? 'DISCHARGE' : 'IDLE'

  if (currentDecision && currentDecision.action === action) return

  // 추천 내용이 바뀌면 새 추천을 만들고, 이전에 적용한 방전은 끝냄
  appliedDischargeKw = 0
  const powerKw = action === 'DISCHARGE' ? MAX_DISCHARGE_KW : 0
  const windowStartMs = windowStartOf(now.getTime())
  currentDecision = {
    id: nextDecisionId++,
    decidedAt: toLocalDateTime(now),
    windowStart: toLocalDateTime(new Date(windowStartMs)),
    windowEnd: toLocalDateTime(new Date(windowStartMs + WINDOW_MS)),
    ruleNo: action === 'DISCHARGE' ? 2 : 6,
    ruleLabel: action === 'DISCHARGE' ? '피크 방어' : '조치 없음',
    predictedKw: Math.round(predictedWithoutEss),
    targetPeakKw: TARGET_PEAK_KW,
    resultPeakKw: null, // 구간이 끝나야 알 수 있음
    resultStatus: null,
    action,
    powerKw,
    durationMinutes: action === 'DISCHARGE' ? Math.max(1, Math.ceil(remainingMinutes)) : null,
    reason:
      action === 'DISCHARGE'
        ? '브레이징로 승온과 사출기 가동이 겹쳐, 이번 15분 구간의 예상 평균이 목표 피크를 넘을 것으로 보입니다.'
        : '피크 여유가 있고, 충전에 쓸 남는 태양광이 없습니다.',
    usedValues: [
      { label: '예상 평균', value: Math.round(predictedWithoutEss), unit: 'kW' },
      { label: '목표 피크', value: TARGET_PEAK_KW, unit: 'kW' },
      { label: 'SOC', value: round1(socPercent), unit: '%' },
      { label: '최대 출력', value: MAX_DISCHARGE_KW, unit: 'kW' },
      {
        label: '배터리실 온도',
        value: round1(24 + Math.sin(now.getTime() / 1000 / 200)),
        unit: '℃',
      },
      { label: '날씨', value: '맑음', unit: '' },
      { label: '기온', value: 18, unit: '℃' },
    ],
    status: 'RECOMMENDED',
    appliedBy: null,
    appliedAt: null,
  }
  // 운영 이력 화면에 보이도록 기록 (대기 추천은 너무 많아져서 뺌)
  if (action !== 'IDLE') {
    sessionDecisions.push(currentDecision)
    addEvent('CRITICAL', `목표 초과 예상, 방전 ${powerKw} kW 추천`, now)
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

function tariffLabelAt(time) {
  const found = TARIFF_PERIODS.find((item) => item.from <= time && time < item.to)
  return TARIFF_LABELS[found.period]
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
        { label: '요금 시간대', value: tariffLabelAt(template.time), unit: '' },
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

// 설비별 전력: 평소 전력을 비중대로 나누고, 브레이징로와 사출기에는 변동분을 더함
// (태양광을 더하는 것은 어느 시간대에 열어도 피크 장면이 나오게 하려는 가짜 데이터용 처리)
function equipmentsAt(seconds, solarKw) {
  const baseKw = 1700 + solarKw
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

// 오늘 0시부터 지금까지 쌓인 양 흉내: 15분마다의 값을 더해 kWh로 바꿈 (15분 = 0.25시간)
// ESS 충전·방전량은 고정된 예시 값
function todayEnergyAt(hour) {
  let gridKwh = 0
  let solarKwh = 0
  for (let i = 0; i * 0.25 < hour; i++) {
    gridKwh += (1570 + 165 * Math.sin(i / 5) + 45 * Math.sin(i / 2)) * 0.25
    solarKwh += solarAt(i * 0.25) * 0.25
  }
  return {
    gridKwh: Math.round(gridKwh),
    solarKwh: Math.round(solarKwh),
    dischargeKwh: 250,
    chargeKwh: 300,
  }
}

// GET /api/battery-statuses/latest 흉내
export async function fakeGetLatestStatus() {
  failIfOffline()
  const now = simNow()
  seedEventsOnce(now)
  const seconds = now.getTime() / 1000
  const hour = now.getHours() + now.getMinutes() / 60

  const solarKw = solarAt(hour)
  const gridWithoutEss = gridWithoutEssAt(seconds)
  const socPercent = 60 + 15 * Math.sin(seconds / 300)

  // 현재 15분 구간 (예: 14:00~14:15)
  const windowStart = new Date(windowStartOf(now.getTime()))
  const windowEnd = new Date(windowStart.getTime() + WINDOW_MS)
  const remainingMinutes = (windowEnd.getTime() - now.getTime()) / 60000

  // 지금까지 쌓인 값에 "남은 시간 동안 지금 전력이 계속된다"고 보고 더한 것이 예상 평균
  // (회의록의 예측 예시 식을 단순하게 흉내 낸 것. 실제 예측은 백엔드가 합니다)
  updateWindow(now.getTime(), gridWithoutEss - appliedDischargeKw)
  const demandSoFar = windowState.demandKw
  const predictedWithoutEss = demandSoFar + (gridWithoutEss * remainingMinutes) / 15

  updateDecision(now, predictedWithoutEss, socPercent, remainingMinutes)

  const dischargeKw = appliedDischargeKw
  const gridKw = gridWithoutEss - dischargeKw
  const loadKw = gridWithoutEss + solarKw
  const predictedKw = demandSoFar + (gridKw * remainingMinutes) / 15

  let peakStatus = 'NORMAL'
  if (predictedKw > TARGET_PEAK_KW) peakStatus = 'CRITICAL'
  else if (predictedKw >= WARN_KW) peakStatus = 'WARNING'

  // 단계가 바뀌면 이벤트로 남김 (경고는 추천 이벤트와 겹치므로 주의·정상만)
  if (lastPeakStatus && lastPeakStatus !== peakStatus) {
    if (peakStatus === 'WARNING') addEvent('WARNING', '주의 단계 진입', now)
    if (peakStatus === 'NORMAL') addEvent('NORMAL', '정상 단계로 돌아옴', now)
  }
  lastPeakStatus = peakStatus

  const equipments = equipmentsAt(seconds, solarKw)

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
    weather: { text: '맑음', tempC: 18 },
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
    defenseKw: Math.max(0, Math.ceil(predictedKw - TARGET_PEAK_KW)),
    // 구간 시작부터 지금까지 쌓인 값 (그래프용). 필드는 확인 필요
    windowPoints: windowState.points.slice(),
    // 지금까지의 최대 피크 (고정된 예시 값). 필드는 확인 필요
    todayPeakKw: 1768,
    monthPeakKw: 1792,
    // 요금 시간대
    tariff: tariffAt(now),
    // 공급원 비율 (공장 전체 기준, 계산 방식은 확인 필요)
    sourceRatio: {
      gridPercent: round1((gridKw / loadKw) * 100),
      solarPercent: round1((solarKw / loadKw) * 100),
      essPercent: round1((dischargeKw / loadKw) * 100),
    },
    // 오늘 0시부터 지금까지 쌓인 양 (kWh). 필드는 확인 필요
    todayEnergy: todayEnergyAt(hour),
    equipments,
  }
}

// GET /api/battery-statuses?from=&to= 흉내: 오늘 00시부터 지금까지 15분 간격
export async function fakeGetTodayStatuses() {
  if (fakeMode === 'chart-error') {
    throw new Error('서버에 연결할 수 없습니다.')
  }
  const now = simNow()
  const time = new Date(now)
  time.setHours(0, 0, 0, 0)

  const list = []
  for (let i = 0; time <= now; i++) {
    const solarKw = solarAt(time.getHours() + time.getMinutes() / 60)
    // 지난 구간의 15분 평균. 대부분 목표(1,800kW) 아래이고 가끔 가까이 올라가게 만든 값
    const gridKw = 1570 + 165 * Math.sin(i / 5) + 45 * Math.sin(i / 2)
    list.push({
      recordedAt: toLocalDateTime(time),
      loadKw: Math.round(gridKw + solarKw),
      solarKw: Math.round(solarKw),
      gridKw: Math.round(gridKw),
      socPercent: round1(60 + 18 * Math.sin(i / 9)),
    })
    time.setMinutes(time.getMinutes() + 15)
  }
  return list
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
      gridKw: Math.round(minuteAverage((at) => gridWithoutEssAt(at.getTime() / 1000), time)),
    })
  }
  return list
}

// GET /api/events?limit= 흉내: 최근 이벤트 (최신 순). 주소와 필드는 확인 필요
export async function fakeGetRecentEvents(limit = 6) {
  failIfOffline()
  return events.slice(0, limit).map((item) => ({ ...item }))
}

// 구역 한 곳의 최근 1시간 전력과 오늘 요약 (설비 모니터링 화면의 오른쪽 상세용). 주소와 필드는 확인 필요
export async function fakeGetZoneRecent(zoneId) {
  failIfOffline()
  const now = simNow()

  // 어떤 시각에 그 구역이 쓴 전력 (그 구역 설비들의 합)
  const zoneKwAt = (time) => {
    const solarKw = solarAt(time.getHours() + time.getMinutes() / 60)
    return equipmentsAt(time.getTime() / 1000, solarKw)
      .filter((item) => item.id.startsWith(`${zoneId}-`))
      .reduce((sum, item) => sum + item.loadKw, 0)
  }

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
const BASE_PRICE_PER_KW = 8320 // 기본요금 단가 (원/kW)
const ENERGY_PRICES = { LIGHT: 87, MID: 110, PEAK: 172 } // 전력량요금 단가 (원/kWh)
const EMISSION_FACTOR = 0.4594 // 탄소 배출계수 (kgCO2/kWh, 확인 필요)
const BUSY_HOURS = [9, 10, 11, 16, 17, 18] // 피크가 잘 생기는 시각
const ESS_CAPACITY_KWH = 1000

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

      const time = `${pad(hour)}:00`
      const period = TARIFF_PERIODS.find((item) => item.from <= time && time < item.to).period
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
    sum(Object.keys(tariffKwh).map((key) => tariffKwh[key] * ENERGY_PRICES[key])),
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
      monthPeakAt: `${topDay.date}T${pad(topHour.hour)}:00:00`,
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
      lastMonthTotal: Math.round(150000000 + fakeRandom(year * 100 + monthNumber) * 12000000),
      byTariff: Object.keys(tariffKwh).map((key) => ({
        period: key,
        label: TARIFF_LABELS[key],
        kwh: Math.round(tariffKwh[key]),
        percent: round1((tariffKwh[key] / totalKwh) * 100),
        charge: Math.round(tariffKwh[key] * ENERGY_PRICES[key]),
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
// 날씨에 따라 태양광이 덜 나오게 하는 비율 (가짜 데이터용)
const WEATHER_TEXTS = ['맑음', '맑음', '흐림', '맑음', '비', '흐림', '맑음']
const SOLAR_BY_WEATHER = { 맑음: 1, 흐림: 0.55, 비: 0.2 }
const SOC_LOW_LIMIT = 20 // 잔량 하한 (%)
const SOC_HIGH_LIMIT = 90 // 잔량 상한 (%)
const ROOM_TEMP_LIMIT_C = 35 // 배터리실 온도 제한 (℃)
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

  // 1) 15분 간격의 원본 값 만들기
  const raw = []
  const weather = []
  let socPercent = 62
  const time = new Date(`${from}T00:00:00`)
  for (let count = 0; time < last && count < 96 * 62; count++) {
    const date = toLocalDateTime(time).slice(0, 10)
    const daySeed = Number(date.replaceAll('-', ''))
    const hour = time.getHours() + time.getMinutes() / 60
    const slot = time.getHours() * 4 + time.getMinutes() / 15
    const weatherText = WEATHER_TEXTS[daySeed % 7]
    if (!weather.length || weather[weather.length - 1].date !== date) {
      weather.push({ date, text: weatherText })
    }

    const solarKw = solarAt(hour) * SOLAR_BY_WEATHER[weatherText]
    // ESS가 없을 때 한전에서 사야 하는 전력: 하루 주기 + 피크가 잘 생기는 시각에 더 올라감
    const busy = BUSY_HOURS.includes(time.getHours())
    const gridBase =
      1500 +
      190 * Math.sin(((hour - 3) / 24) * 2 * Math.PI) +
      (busy ? 200 : 0) +
      (fakeRandom(daySeed * 100 + slot) - 0.5) * 120

    // ESS: 새벽(경부하)에 충전(-), 피크가 잘 생기는 시각에 방전(+). 잔량이 범위를 벗어나면 멈춤
    let essKw = 0
    if (hour >= 1 && hour < 4) essKw = -300
    if (busy && fakeRandom(daySeed * 7 + time.getHours()) > 0.25) essKw = 300
    // 15분 동안 충전·방전하면 잔량이 얼마나 바뀌는지 보고, 하한·상한을 넘게 되면 하지 않음
    const socChange = -((essKw * 0.25) / ESS_CAPACITY_KWH) * 100
    if (socPercent + socChange < SOC_LOW_LIMIT || socPercent + socChange > SOC_HIGH_LIMIT) {
      essKw = 0
    } else {
      socPercent += socChange
    }

    raw.push({
      recordedAt: toLocalDateTime(time),
      hour: time.getHours(),
      loadKw: gridBase + solarKw,
      gridKw: gridBase - essKw, // 방전하면 덜 사고, 충전하면 더 삼
      solarKw,
      essKw,
      socPercent,
      roomTempC:
        23 +
        3 * Math.sin(((hour - 10) / 24) * 2 * Math.PI) +
        (essKw !== 0 ? 2.2 : 0) +
        fakeRandom(daySeed) * 3,
      outsideTempC:
        12 + 6 * Math.sin(((hour - 9) / 24) * 2 * Math.PI) - (weatherText === '비' ? 4 : 0),
    })
    time.setMinutes(time.getMinutes() + 15)
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
      period: TARIFF_PERIODS.find((item) => item.from <= clock && clock < item.to).period,
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
