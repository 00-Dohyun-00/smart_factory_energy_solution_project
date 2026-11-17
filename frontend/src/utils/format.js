// 숫자·시각 표시 함수. 값이 없으면(null, undefined) '—'를 돌려줍니다. (0과 "값 없음"을 구분)

const EMPTY = '—'

function formatNumber(value, digits) {
  if (value === null || value === undefined) return EMPTY
  return value.toLocaleString('ko-KR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

// kW: 100 미만은 소수 1자리, 그 이상은 정수 (예: 45.2 / 2,570)
export function formatKw(value) {
  if (value === null || value === undefined) return EMPTY
  return formatNumber(value, Math.abs(value) < 100 ? 1 : 0)
}

// %와 ℃: 소수 1자리 (예: 60.0)
export function formatOneDecimal(value) {
  return formatNumber(value, 1)
}

// 정수로 표시 (예: 212,000). 큰 전력량(kWh)이나 횟수에 씀
export function formatInteger(value) {
  return formatNumber(value, 0)
}

// 원 → 만원 단위 정수 (예: 84,280,000 → '8,428'). 큰 금액을 읽기 쉽게 하려는 단위 변환
export function formatManwon(won) {
  if (won === null || won === undefined) return EMPTY
  return formatNumber(Math.round(won / 10000), 0)
}

// '2026-11-14T14:07:32' → '14:07:32'
export function formatClock(dateTime) {
  return dateTime ? dateTime.slice(11, 19) : EMPTY
}

// '2026-11-14T14:00:00' → '14:00'
export function formatHourMinute(dateTime) {
  return dateTime ? dateTime.slice(11, 16) : EMPTY
}

// '2026-11-14T14:07:32' → '11-14 14:07:32'
export function formatMonthDayTime(dateTime) {
  return dateTime ? `${dateTime.slice(5, 10)} ${dateTime.slice(11, 19)}` : EMPTY
}

// 448 → '7분 28초'
export function formatRemaining(seconds) {
  if (seconds === null || seconds === undefined) return EMPTY
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`
}
