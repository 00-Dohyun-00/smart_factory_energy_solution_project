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
function furnaceSwingAt(seconds) {
  return 330 * Math.sin(seconds / 40)
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

// 추천 판단 흉내: 피크 초과가 예상되면 방전, 아니면 대기
function updateDecision(now, predictedWithoutEss, socPercent) {
  const overKw = predictedWithoutEss - TARGET_PEAK_KW
  const action = overKw > 0 ? 'DISCHARGE' : 'IDLE'

  if (currentDecision && currentDecision.action === action) return

  // 추천 내용이 바뀌면 새 추천을 만들고, 이전에 적용한 방전은 끝냄
  appliedDischargeKw = 0
  const powerKw = action === 'DISCHARGE' ? MAX_DISCHARGE_KW : 0
  currentDecision = {
    id: nextDecisionId++,
    decidedAt: toLocalDateTime(now),
    ruleNo: action === 'DISCHARGE' ? 2 : 6,
    action,
    powerKw,
    durationMinutes: action === 'DISCHARGE' ? 10 : null,
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
}

// GET /api/battery-statuses/latest 흉내
export async function fakeGetLatestStatus() {
  const now = new Date()
  const seconds = now.getTime() / 1000
  const hour = now.getHours() + now.getMinutes() / 60

  const solarKw = solarAt(hour)
  const gridWithoutEss = gridWithoutEssAt(seconds)
  const predictedWithoutEss = gridWithoutEssAt(seconds - 10)
  const socPercent = 60 + 15 * Math.sin(seconds / 300)

  updateDecision(now, predictedWithoutEss, socPercent)

  const dischargeKw = appliedDischargeKw
  const gridKw = gridWithoutEss - dischargeKw
  const loadKw = gridWithoutEss + solarKw
  const predictedKw = predictedWithoutEss - dischargeKw

  let peakStatus = 'NORMAL'
  if (predictedKw > TARGET_PEAK_KW) peakStatus = 'CRITICAL'
  else if (predictedKw >= WARN_KW) peakStatus = 'WARNING'

  // 현재 15분 구간 (예: 14:00~14:15)
  const windowStart = new Date(now)
  windowStart.setMinutes(Math.floor(now.getMinutes() / 15) * 15, 0, 0)
  const windowEnd = new Date(windowStart.getTime() + 15 * 60 * 1000)

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
