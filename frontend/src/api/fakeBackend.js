// 백엔드 API가 만들어지기 전까지 쓰는 "가짜 백엔드"입니다.
// TODO: 백엔드 연결 후 이 파일은 삭제합니다.
//
// - 여기 있는 피크 예측·추천 계산은 화면 확인용으로 단순하게 흉내 낸 것입니다. 실제 계산은 백엔드가 합니다.
// - 이 파일은 batteryStatusApi.js, batteryDecisionApi.js 에서만 불러옵니다. (Store, 화면에서 직접 쓰지 않음)
// - 이 파일은 서버 역할이라 new Date()로 "서버 시각"을 만듭니다. 화면 코드에서는 new Date()를 쓰지 않습니다.

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

let currentDecision = null // 가장 최근 추천 1건
let nextDecisionId = 1
let appliedDischargeKw = 0 // 운영자가 "적용"한 방전 전력

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
    ],
    status: 'RECOMMENDED',
    appliedBy: null,
    appliedAt: null,
  }
  // 운영 이력 화면에 보이도록 기록 (대기 추천은 너무 많아져서 뺌)
  if (action !== 'IDLE') {
    sessionDecisions.push(currentDecision)
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
  const now = new Date()
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

// GET /api/battery-statuses/latest 흉내
export async function fakeGetLatestStatus() {
  failIfOffline()
  const now = new Date()
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

  // 설비별 전력: 평소 전력을 비중대로 나누고, 브레이징로와 사출기에는 변동분을 더함
  // (태양광을 더하는 것은 어느 시간대에 열어도 피크 장면이 나오게 하려는 가짜 데이터용 처리)
  const baseKw = 1700 + solarKw
  const motorKw = 2.2 + 0.3 * Math.sin(seconds / 5) // 1번 모터만 실측이라고 가정
  const extraKw = {
    'Z2-FURN': furnaceSwingAt(seconds),
    'Z3-INJ1': injectionSwingAt(seconds) * 0.6,
    'Z3-INJ2': injectionSwingAt(seconds) * 0.4,
    'Z4-MOTOR1': motorKw,
    'U-HVAC': -motorKw,
  }
  const equipments = EQUIPMENTS.map((item) => ({
    id: item.id,
    name: item.name,
    loadKw: round1(baseKw * item.share + (extraKw[item.id] ?? 0)),
    origin: item.id === 'Z4-MOTOR1' ? 'MEASURED' : 'SIMULATED',
  }))

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
    equipments,
  }
}

// GET /api/battery-statuses?from=&to= 흉내: 오늘 00시부터 지금까지 15분 간격
export async function fakeGetTodayStatuses() {
  if (fakeMode === 'chart-error') {
    throw new Error('서버에 연결할 수 없습니다.')
  }
  const now = new Date()
  const time = new Date(now)
  time.setHours(0, 0, 0, 0)

  const list = []
  for (let i = 0; time <= now; i++) {
    const solarKw = solarAt(time.getHours() + time.getMinutes() / 60)
    const gridKw = 1650 + 220 * Math.sin(i / 5) + 60 * Math.sin(i / 2)
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
    currentDecision.appliedAt = toLocalDateTime(new Date())
    appliedDischargeKw = currentDecision.action === 'DISCHARGE' ? currentDecision.powerKw : 0
  }
  return { ...currentDecision }
}
