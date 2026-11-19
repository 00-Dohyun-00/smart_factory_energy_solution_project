// ESS 추천 로직 (가짜 백엔드용)
// TODO: 백엔드 연결 후 이 파일은 삭제합니다. 실제 계산은 백엔드가 맡고, 이 파일은 그때 넘길 설계 설명이기도 합니다.
//
// - 여기에는 "계산"만 있습니다. 상태(지금 추천, 적용 중인 출력, 이벤트)는 fakeBackend.js가 가지고 있습니다.
// - 함수는 넘겨받은 값만 보고 답을 냅니다. 같은 값을 넣으면 항상 같은 답이 나옵니다.
// - 이 파일은 fakeBackend.js 에서만 불러옵니다.
// - 설명 문서: frontend/docs/ess-recommendation.md
//
// limits: 환경 설정 값 묶음. fakeBackend.js의 essLimits()가 만들어 넘깁니다.
//   { targetPeakKw: 목표 피크, warnKw: 주의선, maxPowerKw: 최대 충·방전 출력, capacityKwh: 배터리 용량,
//     socLowPercent: 잔량 하한, socHighPercent: 잔량 상한, efficiencyPercent: 충·방전 효율 }
//
// 순서
//   1) 하루 계획 (planDay): 15분 칸 96개마다 충전·방전·대기를 정함
//   2) 계획에 넣을 값 (planInputsOf): AI 예측 부하 − 태양광, 빗나간 만큼 보정
//   3) 실시간 보정 (correctedEssKw): 하루 시뮬레이션에서 실제 값에 맞춰 출력을 고침
//   4) 실시간 추천 판단 (decideAdvice): 지금 무엇을 추천할지
//   5) 추천 설명 (explainDecision): 추천 카드에 보이는 이유 문장

const pad = (n) => String(n).padStart(2, '0')

export const START_SOC_PERCENT = 50 // 하루 시작(0시) 잔량

// ---- 1) 하루 계획 (Jang 외, Sustainability 2022 논문의 방식을 단순하게 옮긴 것) ----
// 하루의 15분 칸마다 충전·방전·대기를 정해서, 아래 비용의 합이 가장 작은 계획을 찾습니다.
//   비용 = 전력량요금 (한전에서 산 전기 × 그 시각 단가) + 배터리 마모 비용 (방전한 kWh × 마모 단가)
//   지킬 것 = 모든 칸에서 한전 구매 ≤ 방어선, 잔량은 하한~상한, 충·방전은 최대 출력까지
// 1) 피크 모드: 방어선은 목표 피크. 배터리로 그 선을 다 지킬 수 없는 날이면 지킬 수 있는 가장 낮은 선으로 올림
//    (못 지킬 선을 고집하다 배터리를 일찍 다 쓰면, 더 큰 피크를 그대로 맞기 때문)
// 2) 요금 모드: 방어선을 지키는 안에서, 남는 배터리로 싼 시간에 충전하고 비싼 시간에 방전
// 푸는 방법은 표 채우기(동적 계획법)입니다. 가로 = 칸(시각), 세로 = 잔량(1% 단위).
// 마지막 칸부터 거꾸로 "이 시각에 이 잔량이면 남은 하루를 가장 싸게 보내는 비용"을 채우고,
// 다 채우면 지금 잔량에서 시작해 가장 싼 길을 따라가기만 하면 됩니다.
const WEAR_COST_WON_PER_KWH = 20 // 1 kWh 방전할 때마다 배터리가 닳는 값 (임시값, 팀 확인 필요)

// 예비 잔량: 예측이 빗나가 실제 부하가 더 높게 나와도 막을 수 있게 남겨 두는 잔량
// 남은 구간마다 "예측보다 RESERVE_EXTRA_KW만큼 더 나오면 추가로 필요한 방전량"을 더해 예비 잔량으로 잡고,
// 잔량이 그보다 모자라면 모자란 kWh × RESERVE_PENALTY_WON 을 비용에 더함 (충전해 두고, 요금 차익용 방전은 줄이게 됨)
// RESERVE_EXTRA_KW: AI 예측(상위 90%)의 하루 피크 오차 절댓값 평균은 약 150 kW (ai/forecast 검증 결과).
//   2026 개편 요금표로 1년 시뮬레이션을 돌려, 방어선 초과가 0이 되는 가장 작은 값을 50 kW 단위로 찾아 200 kW로 정함
const RESERVE_EXTRA_KW = 200
const RESERVE_PENALTY_WON = 300 // 예비 잔량 1 kWh가 한 칸 동안 모자랄 때의 벌점 (원)

// 한 칸에서 잔량을 바꿔 볼 후보 (%). 비용이 같으면 앞쪽(덜 움직이는 쪽)을 고르게 0부터 놓음
function socChangeCandidates(maxDown, maxUp) {
  const list = [0]
  for (let step = 1; step <= Math.max(maxDown, maxUp); step++) {
    if (step <= maxDown) list.push(-step)
    if (step <= maxUp) list.push(step)
  }
  return list
}

// inputs: [{ netKw, peakNetKw, price }] (칸마다 ESS 없이 사야 하는 전력과 단가), startSoc: 첫 칸 시작 잔량 (%)
//   netKw: 요금 계산에 쓰는 값 (평균 예측) / peakNetKw: 방어선을 지키는지 볼 때 쓰는 값 (상위 90% 예측)
// 돌려주는 값: { lineKw: 방어선, marginKw: 방어선 아래로 둔 여유, essKw: [칸마다 ESS 전력 (+방전, −충전)] }
export function planDay(inputs, startSoc, limits) {
  const efficiency = limits.efficiencyPercent / 100
  const kwhPerPercent = limits.capacityKwh / 100
  const low = Math.ceil(limits.socLowPercent)
  const states = Math.floor(limits.socHighPercent) - low + 1
  const start = Math.min(states - 1, Math.max(0, Math.round(startSoc) - low))
  // 한 칸(15분)에 잔량이 줄거나 늘 수 있는 최대 (%)
  const maxDown = Math.floor((limits.maxPowerKw * 0.25) / kwhPerPercent)
  const maxUp = Math.floor((limits.maxPowerKw * 0.25 * efficiency) / kwhPerPercent)
  const changes = socChangeCandidates(maxDown, maxUp)
  // 잔량 변화(%) → ESS 전력 (kW). 충전은 손실이 있어 잔량보다 더 많이 사야 함
  const essKwOf = (change) =>
    change < 0 ? (-change * kwhPerPercent) / 0.25 : -(change * kwhPerPercent) / efficiency / 0.25
  // 하루 끝 잔량의 값: 모자란 만큼은 내일 경부하에 다시 채워야 하므로 그 돈으로 셈
  const cheapest = Math.min(...inputs.map((item) => item.price))
  const endCost = (state) =>
    ((START_SOC_PERCENT - (low + state)) * kwhPerPercent * cheapest) / efficiency

  // 방어선 lineKw를 지키는 가장 싼 계획. 지킬 수 없으면 null
  const solve = (lineKw) => {
    // 칸마다 그 칸부터 하루 끝까지 남겨 둘 예비 잔량 (kWh). 뒤에서부터 더해 감
    const reserveKwh = new Array(inputs.length + 1).fill(0)
    for (let index = inputs.length - 1; index >= 0; index--) {
      const { peakNetKw } = inputs[index]
      const extraKw =
        Math.max(0, peakNetKw + RESERVE_EXTRA_KW - lineKw) - Math.max(0, peakNetKw - lineKw)
      reserveKwh[index] = reserveKwh[index + 1] + extraKw * 0.25
    }

    let after = Array.from({ length: states }, (_, state) => endCost(state))
    const picks = []
    for (let index = inputs.length - 1; index >= 0; index--) {
      const { netKw, peakNetKw, price } = inputs[index]
      const best = new Array(states).fill(Infinity)
      const pick = new Array(states).fill(0)
      for (let state = 0; state < states; state++) {
        // 이 칸을 시작할 때 쓸 수 있는 잔량이 예비 잔량보다 모자라면 벌점
        const shortKwh = Math.max(0, reserveKwh[index] - state * kwhPerPercent)
        const reservePenalty = shortKwh * RESERVE_PENALTY_WON
        for (const change of changes) {
          const next = state + change
          if (next < 0 || next >= states || after[next] === Infinity) continue
          const essKw = essKwOf(change)
          const gridKw = netKw - essKw
          if (peakNetKw - essKw > lineKw || gridKw < 0) continue
          const wear = essKw > 0 ? essKw * 0.25 * WEAR_COST_WON_PER_KWH : 0
          const cost = gridKw * 0.25 * price + wear + reservePenalty + after[next]
          if (cost < best[state]) {
            best[state] = cost
            pick[state] = change
          }
        }
      }
      picks[index] = pick
      after = best
    }
    if (after[start] === Infinity) return null
    // 앞에서부터 고른 길을 따라가며 칸마다 ESS 전력을 꺼냄
    const essKw = []
    let state = start
    for (let index = 0; index < inputs.length; index++) {
      essKw.push(essKwOf(picks[index][state]))
      state += picks[index][state]
    }
    return essKw
  }

  // 계획은 방어선보다 조금 낮게 잡음 (여유): 실제 전력은 계획과 조금씩 달라서, 방어선에 딱 맞춘 계획이면
  // 살짝 넘는 구간이 생김. 주의선만큼(54 kW) → 20 kW → 0 순서로, 배터리가 버티는 가장 큰 여유를 고름
  // (1년 중 가장 빠듯한 날도 20 kW 여유면 실시간에서 한 구간도 넘지 않았음)
  let lineKw = limits.targetPeakKw
  let usedMarginKw = 0
  let essKw = null
  for (const marginKw of [limits.targetPeakKw - limits.warnKw, 20, 0]) {
    essKw = solve(lineKw - marginKw)
    usedMarginKw = marginKw
    if (essKw) break
  }
  // 피크 모드: 목표 피크를 못 지키면, 지킬 수 있는 가장 낮은 선을 5 kW 차이까지 반씩 좁혀 찾음
  if (!essKw) {
    let canKeep = Math.ceil(Math.max(...inputs.map((item) => item.peakNetKw))) // ESS 없이도 지켜지는 선
    let cannotKeep = limits.targetPeakKw
    while (canKeep - cannotKeep > 5) {
      const middle = Math.round((canKeep + cannotKeep) / 2)
      if (solve(middle)) canKeep = middle
      else cannotKeep = middle
    }
    lineKw = canKeep
    essKw = solve(lineKw)
  }
  return { lineKw, marginKw: usedMarginKw, essKw }
}

// 2단계 규칙 (비교용): 목표를 넘으면 넘는 만큼만 방전(최대 출력까지), 경부하에는 충전
const NIGHT_CHARGE_KW = 250 // 경부하 시간대 충전 전력

// inputs: [{ netKw, period }]
export function ruleDay(inputs, startSoc, limits) {
  const efficiency = limits.efficiencyPercent / 100
  let socPercent = startSoc
  const essKw = inputs.map((item) => {
    let kw = 0
    if (item.netKw > limits.targetPeakKw) {
      const usableKwh = ((socPercent - limits.socLowPercent) / 100) * limits.capacityKwh
      kw = Math.max(
        0,
        Math.min(limits.maxPowerKw, item.netKw - limits.targetPeakKw, usableKwh / 0.25),
      )
    } else if (item.period === 'LIGHT') {
      const emptyKwh = ((limits.socHighPercent - socPercent) / 100) * limits.capacityKwh
      // 충전하면 한전에서 더 사므로, 충전 때문에 목표 피크를 넘지 않게 함
      const headroomKw = limits.targetPeakKw - item.netKw
      kw = -Math.max(0, Math.min(NIGHT_CHARGE_KW, headroomKw, emptyKwh / 0.25 / efficiency))
    }
    socPercent += socChangeOf(kw, limits)
    return kw
  })
  return { lineKw: limits.targetPeakKw, essKw }
}

// ESS 전력(+방전, −충전)으로 15분 동안 바뀌는 잔량 (%). 충전할 때 손실(효율)을 반영
export function socChangeOf(essKw, limits) {
  if (essKw > 0) return -((essKw * 0.25) / limits.capacityKwh) * 100
  return ((-essKw * 0.25 * limits.efficiencyPercent) / 100 / limits.capacityKwh) * 100
}

// ---- 2) 계획에 넣을 값 ----
// AI 예측 부하 − 태양광. 지난 1시간(4칸) 동안 예측이 빗나간 평균만큼 남은 칸을 보정함
// (예: 실제가 예측보다 평균 80 kW 높았으면 남은 칸도 80 kW 올려서 계획)
// inputs: 하루 96칸 [{ netKw: 실제, planNetKw: 평균 예측, planPeakNetKw: 상위 90% 예측, price }]
// 돌려주는 값: startSlot 칸부터 하루 끝까지 [{ netKw, peakNetKw, price }] (planDay에 그대로 넣음)
const BIAS_SLOTS = 4

export function planInputsOf(inputs, startSlot) {
  const recent = inputs.slice(Math.max(0, startSlot - BIAS_SLOTS), startSlot)
  const biasKw = recent.length
    ? recent.reduce((sum, item) => sum + item.netKw - item.planNetKw, 0) / recent.length
    : 0
  return inputs.slice(startSlot).map((item) => ({
    netKw: item.planNetKw + biasKw,
    peakNetKw: item.planPeakNetKw + biasKw,
    price: item.price,
  }))
}

// ---- 3) 실시간 보정 (15분 칸 단위로 단순화) ----
// 실시간 화면의 피크 방어(decideAdvice)와 같은 일을 하루 시뮬레이션에서 칸 단위로 함
//   1) 실제 전력이 목표 피크를 넘으면 넘는 만큼 더 방전 (계획이 방어선을 올린 날도 목표 피크를 먼저 지키려 함)
//   2) 피크에 대비해 계획한 방전인데 실제가 예측보다 낮으면, 필요한 만큼만 방전 (배터리를 아껴 둠)
//   3) 충전 때문에 주의선을 넘으면 충전을 줄임
// plannedPeakNetKw: 계획할 때 본 이 칸의 예측값 (상위 90%)
export function correctedEssKw(planKw, netKw, plan, socPercent, plannedPeakNetKw, limits) {
  const aimKw = Math.min(plan.lineKw, limits.targetPeakKw) - plan.marginKw // 방어선보다 여유만큼 낮게 맞춤
  let essKw = planKw
  if (netKw - essKw > limits.targetPeakKw) essKw = netKw - aimKw
  else if (essKw > 0 && plannedPeakNetKw > aimKw)
    essKw = Math.max(0, Math.min(essKw, netKw - aimKw))
  else if (essKw < 0 && netKw - essKw > aimKw) essKw = Math.min(0, netKw - aimKw)
  // 잔량(하한~상한)과 최대 출력 안으로 자름
  const efficiency = limits.efficiencyPercent / 100
  const usableKwh = Math.max(0, ((socPercent - limits.socLowPercent) / 100) * limits.capacityKwh)
  const emptyKwh = Math.max(0, ((limits.socHighPercent - socPercent) / 100) * limits.capacityKwh)
  const dischargeLimitKw = Math.min(limits.maxPowerKw, usableKwh / 0.25)
  const chargeLimitKw = Math.min(limits.maxPowerKw, emptyKwh / efficiency / 0.25)
  return Math.max(-chargeLimitKw, Math.min(dischargeLimitKw, essKw))
}

// ---- 4) 실시간 추천 판단 ----
// 하루 계획(planDay)을 따르되, 실제 전력이 계획과 달라 방어선을 넘을 것 같으면 그 구간만 피크 방어를 추천합니다.
//   피크 방어 · 안전 제한 : 그 15분 구간이 끝날 때까지 유지 (방전 → 대기 → 방전 깜빡임 방지)
//   계획 방전 · 계획 충전 · 조치 없음 : 계획에서 같은 동작이 이어지는 동안 유지 (그동안은 다시 묻지 않음)
// 추천을 유지할지, 새로 낼지, 적용한 출력을 얼마로 맞출지는 fakeBackend.js의 updateDecision이 정합니다.
export const MIN_ADVICE_KW = 10 // 이보다 작은 충·방전은 추천하지 않음 (의미 없이 버튼만 늘어남)
export const ADVICE_RULES = {
  SAFETY: { ruleNo: 1, ruleLabel: '안전 제한' },
  PEAK: { ruleNo: 2, ruleLabel: '피크 방어' },
  PLAN_CHARGE: { ruleNo: 4, ruleLabel: '계획 충전' },
  PLAN_DISCHARGE: { ruleNo: 5, ruleLabel: '계획 방전' },
  IDLE: { ruleNo: 6, ruleLabel: '조치 없음' },
}

// 이번 구간 평균이 방어선을 넘을 것 같을 때, 남은 시간 동안 얼마나 방전해야 하는지 (kW)
// 15분 평균을 X kW 낮추려면 X × 15분만큼의 전력량을 빼야 하고, 그걸 남은 시간에 나눠 냄
// 방어선에 딱 맞추면 예측이 조금만 틀려도 살짝 넘어서, 주의선만큼(목표 − 주의선 = 54 kW) 더 낮춤 (안전 여유)
// 예: 방어선 1,800, 예상 1,830, 10분 남음 → (1,830 − 1,746) × 15 ÷ 10 = 126 kW
// marginKw: 방어선보다 얼마나 낮게 맞출지. 계획이 여유를 못 둔 날은 0 (여유까지 맞추려다 배터리가 모자라지 않게)
export function neededDischargeKw(predictedKw, remainingMinutes, lineKw, marginKw) {
  if (predictedKw <= lineKw) return 0
  return ((predictedKw - (lineKw - marginKw)) * 15) / Math.max(remainingMinutes, 0.5)
}

// 숫자의 부호: 충·방전이라고 볼 만큼 크면 +1(방전) / −1(충전), 아니면 0(대기)
function directionOf(essKw) {
  if (essKw >= MIN_ADVICE_KW) return 1
  if (essKw <= -MIN_ADVICE_KW) return -1
  return 0
}

// 계획에서 지금 칸과 같은 동작(방전·충전·대기)이 이어지다 끝나는 시각
// 방전·충전 사이에 잠깐(1시간 미만) 쉬는 칸은 같은 동작으로 봄 (계획이 "충전 → 쉼 → 충전"으로
// 잘게 오가도 운영자에게 매번 다시 묻지 않으려는 것). 반대 동작이 나오거나 1시간 넘게 쉬면 끝
// plan: { dateKey, startSlot, essKw: [지금 칸부터 하루 끝까지] }
const PLAN_GAP_SLOTS = 4

export function planBlockEndOf(plan) {
  const direction = directionOf(plan.essKw[0])
  let count = 1
  let idleRun = 0
  while (count < plan.essKw.length) {
    const next = directionOf(plan.essKw[count])
    if (direction === 0 && next !== 0) break
    if (direction !== 0 && next === -direction) break
    idleRun = next === 0 ? idleRun + 1 : 0
    if (direction !== 0 && idleRun >= PLAN_GAP_SLOTS) {
      count -= idleRun - 1 // 쉬는 칸이 시작된 곳에서 끝냄
      break
    }
    count++
  }
  const end = new Date(`${plan.dateKey}T00:00:00`)
  end.setMinutes((plan.startSlot + count) * 15)
  return end
}

// 지금 무엇을 추천할지
// plan: 지금 칸부터의 하루 계획 { lineKw, marginKw, essKw: [...] }
// info: { soFarKw: 이번 구간에 지금까지 쌓인 평균, recentWithoutEss: 최근 4분 평균(ESS 없이),
//         remainingMinutes: 구간 남은 분, socPercent: 지금 잔량 }
// 돌려주는 값: { kind: 'PEAK' | 'SAFETY' | 'PLAN_CHARGE' | 'PLAN_DISCHARGE' | 'IDLE',
//               action: 'DISCHARGE' | 'CHARGE' | 'IDLE', wantKw: 낼 출력 (+방전, −충전),
//               predictedOf(essKw): ESS를 essKw로 돌리면 이번 구간 평균이 얼마가 될지 }
export function decideAdvice(plan, info, limits) {
  const { soFarKw, recentWithoutEss, remainingMinutes, socPercent } = info
  const planKw = plan.essKw[0] // 계획의 지금 칸 출력
  const lineKw = plan.lineKw
  const efficiency = limits.efficiencyPercent / 100

  // 남은 구간 시간 동안 잔량으로 낼 수 있는 한계
  const restHours = Math.max(remainingMinutes, 0.5) / 60
  const usableKwh = Math.max(0, ((socPercent - limits.socLowPercent) / 100) * limits.capacityKwh)
  const emptyKwh = Math.max(0, ((limits.socHighPercent - socPercent) / 100) * limits.capacityKwh)
  const dischargeLimitKw = Math.min(limits.maxPowerKw, usableKwh / restHours)
  const chargeLimitKw = Math.min(limits.maxPowerKw, emptyKwh / efficiency / restHours)

  // ESS를 essKw로 돌리면 이번 구간 평균이 얼마가 될지
  const predictedOf = (essKw) => soFarKw + ((recentWithoutEss - essKw) * remainingMinutes) / 15
  // 계획대로(충전은 빼고) 해도 방어선을 넘으면, 넘는 만큼 더 방전
  const baseKw = Math.max(planKw, 0)
  const needKw = neededDischargeKw(predictedOf(baseKw), remainingMinutes, lineKw, plan.marginKw)
  let wantKw = needKw > 0 ? baseKw + needKw : planKw
  if (wantKw < 0) {
    // 충전 때문에 방어선 쪽으로 너무 올라가지 않게 충전을 줄임
    const aimKw = lineKw - plan.marginKw
    const roomKw = ((aimKw - predictedOf(0)) * 15) / Math.max(remainingMinutes, 0.5)
    wantKw = -Math.min(-wantKw, Math.max(0, roomKw))
  }
  wantKw = Math.round(Math.max(-chargeLimitKw, Math.min(dischargeLimitKw, wantKw)))

  let kind = 'IDLE'
  if (needKw >= MIN_ADVICE_KW) kind = dischargeLimitKw >= MIN_ADVICE_KW ? 'PEAK' : 'SAFETY'
  else if (directionOf(wantKw) === 1) kind = 'PLAN_DISCHARGE'
  else if (directionOf(wantKw) === -1) kind = 'PLAN_CHARGE'
  const action =
    { PEAK: 'DISCHARGE', PLAN_DISCHARGE: 'DISCHARGE', PLAN_CHARGE: 'CHARGE' }[kind] ?? 'IDLE'

  return { kind, action, wantKw, predictedOf }
}

// ---- 5) 추천 설명 ----
// 피크 위험 (지금부터 오늘 끝까지): 계획에 넣는 예측(상위 90%)이 주의선을 넘는 칸을 피크 위험 칸으로 봄
// inputs: planInputsOf가 돌려준 값 (startSlot 칸부터 하루 끝까지)
//   startAt: 첫 위험 칸 시각 / peakKw: 가장 높은 예측 / nowInRisk: 지금 칸이 위험 칸인지
//   needPercent: 위험 칸에서 주의선 위로 올라오는 전력량을 모두 막는 데 필요한 잔량 (하한 포함)
export function peakRiskOf(inputs, startSlot, limits) {
  let first = null
  let peakKw = 0
  let needKwh = 0
  inputs.forEach((item, index) => {
    const overKw = item.peakNetKw - limits.warnKw
    if (overKw <= 0) return
    needKwh += overKw * 0.25
    if (first === null) first = index
    peakKw = Math.max(peakKw, item.peakNetKw)
  })
  const slot = startSlot + (first ?? 0)
  return {
    startAt: first === null ? null : `${pad(Math.floor(slot / 4))}:${pad((slot % 4) * 15)}`,
    peakKw,
    nowInRisk: first === 0,
    needPercent: Math.min(
      limits.socHighPercent,
      limits.socLowPercent + (needKwh / limits.capacityKwh) * 100,
    ),
  }
}

// 추천 카드는 "무엇을 / 하면 무엇이 좋은지(한두 문장) / 수락·넘기기"만 보여 줌.
// 이번 15분 상황은 예상 피크 카드, 잔량은 KPI 카드가 이미 보여 주므로 카드에 다시 그리지 않음.
// values: asIsKw(지금대로면 이번 15분 평균 = 예상 피크 카드 값),
//         afterKw(추천대로 하면 이번 15분 평균), applied(이미 적용 중인지), socPercent
// context: risk(peakRiskOf 결과), tariffLabel(지금 요금 시간대 이름), price(지금 단가), peakPrice(최대부하 단가)
// usedValues(판단 근거 4개)는 추천 이력 화면용으로 계속 보냄
export function explainDecision(kind, values, context, limits) {
  const { asIsKw, afterKw, applied, socPercent } = values
  const { risk, tariffLabel, price, peakPrice } = context
  const text = (value) => Math.round(value).toLocaleString('ko-KR')
  const target = text(limits.targetPeakKw)
  const lead = applied ? '' : '적용하면 '

  let reason = risk.startAt
    ? `${risk.startAt} 피크(예상 ${text(risk.peakKw)} kW)에 대비해 잔량을 아껴 둡니다.`
    : '지금은 충·방전해도 이득이 없어 대기합니다.'
  if (kind === 'PEAK') {
    if (applied) {
      reason =
        asIsKw <= limits.targetPeakKw
          ? `방전 중이라 이번 15분 평균이 ${text(asIsKw)} kW로 목표(${target} kW) 안입니다.`
          : `방전 중이지만 이번 15분 평균이 ${text(asIsKw)} kW로 목표(${target} kW)를 넘을 수 있습니다.`
    } else {
      reason =
        afterKw <= limits.targetPeakKw
          ? `적용하면 이번 15분 평균이 ${text(asIsKw)} → ${text(afterKw)} kW로 목표(${target} kW) 안에 들어옵니다. 넘기면 피크가 목표를 넘어 기본요금이 오를 수 있습니다.`
          : `적용하면 이번 15분 평균이 ${text(asIsKw)} → ${text(afterKw)} kW로 목표(${target} kW)에 가까워집니다.`
    }
  } else if (kind === 'SAFETY') {
    reason = `잔량이 하한(${limits.socLowPercent}%)이라 방전할 수 없어, 이번 15분 평균이 ${text(asIsKw)} kW로 목표를 넘을 수 있습니다. 설비 가동 조정이 필요합니다.`
  } else if (kind === 'PLAN_CHARGE') {
    reason =
      risk.startAt && socPercent < risk.needPercent
        ? `${lead}${risk.startAt} 피크(예상 ${text(risk.peakKw)} kW) 때 쓸 전기를 미리 채워 두어, 피크를 목표(${target} kW) 안으로 막을 수 있습니다.`
        : `${lead}싼 ${tariffLabel} 요금(${price}원/kWh)으로 채워 두고, 비싼 시간(최대 ${peakPrice}원/kWh)에 씁니다.`
  } else if (kind === 'PLAN_DISCHARGE') {
    reason = risk.nowInRisk
      ? `${lead}피크 시간 한전 구매가 ${applied ? '' : `${text(asIsKw)} → `}${text(afterKw)} kW로 목표(${target} kW) 안에 머뭅니다.`
      : `${lead}비싼 ${tariffLabel} 요금(${price}원/kWh) 대신 배터리 전기를 써서 전기요금을 줄입니다. 피크에 쓸 잔량은 남겨 둡니다.`
  }

  return {
    reason,
    usedValues: [
      { label: '이번 15분 예상', value: Math.round(asIsKw), unit: 'kW' },
      {
        label: '다음 피크 위험',
        value: risk.startAt ? `${risk.startAt} · ${text(risk.peakKw)}` : '없음',
        unit: risk.startAt ? 'kW' : '',
      },
      {
        label: '잔량 지금 / 필요',
        value: `${Math.round(socPercent)} / ${Math.round(risk.needPercent)}`,
        unit: '%',
      },
      { label: '요금 (원/kWh)', value: `${tariffLabel} ${price}`, unit: '' },
    ],
  }
}
