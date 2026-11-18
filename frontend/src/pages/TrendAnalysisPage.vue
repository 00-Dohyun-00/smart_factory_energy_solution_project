<!--
  분석 > 추이 분석
  조회 조건 → 기간 요약 → 왼쪽에 같은 시간 축을 쓰는 그래프 세 개(전력 · 배터리 잔량 · 온도와 날씨)
  → 오른쪽에 시간대별 사용 패턴 · 추천 처리별 결과 · 눈에 띄는 구간

  요약 숫자, 시간대별 평균, 처리별 결과는 백엔드가 계산해 준 값을 그대로 보여 줍니다.
-->
<script setup>
import { computed, ref, watch } from 'vue'
import Chart from '@/components/Chart.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { useAnalysisStore } from '@/stores/useAnalysisStore'
import { useHistoryStore } from '@/stores/useHistoryStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import {
  formatHourMinute,
  formatInteger,
  formatKw,
  formatMonthDayTime,
  formatOneDecimal,
} from '@/utils/format'

const analysisStore = useAnalysisStore()
const historyStore = useHistoryStore() // 그래프 위 추천 핀용. 추천 이력 화면과 같은 API를 씀
const realtimeStore = useRealtimeStore()

const INTERVALS = [
  { value: 'QUARTER', label: '15분' },
  { value: 'HOUR', label: '1시간' },
  { value: 'DAY', label: '1일' },
]
// "보기"에서 켜고 끌 수 있는 항목
const ITEMS = [
  { key: 'load', label: '공장 소비' },
  { key: 'grid', label: '한전 구매' },
  { key: 'solar', label: '태양광' },
  { key: 'ess', label: 'ESS 충·방전' },
  { key: 'soc', label: '배터리 잔량' },
  { key: 'temp', label: '온도·날씨' },
]
// 처리 상태는 색만으로 구분하지 않도록 도형 + 글자로 표시 (운영 로그 화면과 같은 표시)
const STATUS_INFO = {
  APPLIED: { shape: '●', label: '적용' },
  AUTO: { shape: '◇', label: '자동' },
  IGNORED: { shape: '✕', label: '넘김' },
}
// 그래프 위 추천 핀: 위의 처리 상태에 "추천 중"(아직 처리 전)을 더함
const PIN_INFO = { ...STATUS_INFO, RECOMMENDED: { shape: '○', label: '추천 중' } }
const ACTION_LABELS = { CHARGE: '충전', DISCHARGE: '방전', IDLE: '대기' }
// 요금 시간대 이름 (순위 보기에서 시각 옆에 적음)
const PERIOD_LABELS = { PEAK: '최대부하', MID: '중간부하', LIGHT: '경부하' }

// ---- 조회 조건 ----
const preset = ref('week') // today(오늘) | week(최근 7일) | month(이번 달) | custom(직접 입력)
const fromDate = ref('') // 예: '2026-11-10'
const toDate = ref('')
const interval = ref('HOUR')
const shown = ref(ITEMS.map((item) => item.key)) // 켜져 있는 항목들
const focusAt = ref('') // "눈에 띄는 구간"에서 고른 시각 (그래프에 세로선으로 표시)
const hourlyView = ref('time') // 시간대별 사용 패턴 보기: time(시간 순 막대) | rank(많이 쓴 순위)
// 시간대별 사용 패턴의 막대 간격: AUTO(기간에 맞춰 자동) | HOUR(1시간) | HOUR4(4시간) | DAY(1일)
const patternStep = ref('AUTO')
const showPins = ref(true) // 전력 그래프 위에 추천 기호를 보일지

// "오늘"은 브라우저 시계가 아니라 서버가 보낸 시각에서 구함 (시뮬레이터가 배속으로 돌 수 있음)
const serverDate = computed(() => realtimeStore.status?.recordedAt.slice(0, 10) ?? '')

const dateError = computed(() =>
  fromDate.value && toDate.value && fromDate.value > toDate.value
    ? '시작일이 종료일보다 늦습니다.'
    : '',
)

// '2026-11-14'에서 며칠 전 날짜 구하기
function daysBefore(date, days) {
  const [year, month, day] = date.split('-').map(Number)
  const result = new Date(year, month - 1, day - days)
  const pad = (n) => String(n).padStart(2, '0')
  return `${result.getFullYear()}-${pad(result.getMonth() + 1)}-${pad(result.getDate())}`
}

function applyPreset(name) {
  preset.value = name
  if (!serverDate.value) return
  toDate.value = serverDate.value
  if (name === 'today') fromDate.value = serverDate.value
  if (name === 'week') fromDate.value = daysBefore(serverDate.value, 6)
  if (name === 'month') fromDate.value = `${serverDate.value.slice(0, 8)}01` // 이번 달 1일
}

function load(showLoading = true) {
  if (!fromDate.value || !toDate.value || dateError.value) return
  analysisStore.fetchTrend(fromDate.value, toDate.value, interval.value, showLoading)
  // 같은 기간의 추천 이력도 받아 그래프 위에 핀으로 꽂음
  historyStore.fetchDecisions(fromDate.value, toDate.value, showLoading)
}

// 기간이나 간격이 바뀌면 다시 조회
watch([fromDate, toDate, interval], () => {
  focusAt.value = ''
  load()
})

// 기간이 바뀌면 시간대별 사용 패턴 데이터(1시간 간격)도 다시 받음
// (1분마다 자동으로 다시 받지는 않음: 그래프를 좌우로 옮겨 보던 위치가 처음으로 돌아가지 않게)
watch([fromDate, toDate], () => {
  if (!fromDate.value || !toDate.value || dateError.value) return
  analysisStore.fetchPattern(fromDate.value, toDate.value)
})

// 서버 날짜를 처음 알게 되면 "최근 7일"로 조회 시작
watch(
  serverDate,
  (date) => {
    if (date && !fromDate.value) applyPreset('week')
  },
  { immediate: true },
)

// 조회 기간에 오늘이 들어 있으면, 15분 구간이 바뀔 때마다 조용히 다시 받음
watch(
  () => realtimeStore.status?.windowStart,
  (now, before) => {
    if (now && before && toDate.value >= serverDate.value) load(false)
  },
)

// ---- 받은 결과 ----
const trend = computed(() => analysisStore.trend)
const points = computed(() => trend.value?.points ?? [])
const summary = computed(() => trend.value?.summary ?? null)

// 목표 피크. 설정 값을 못 찾으면 백엔드가 null로 줌 → 그때는 목표선과 "목표 초과/달성"을 그리지 않음
const targetKw = computed(() => trend.value?.targetPeakKw ?? null)

// 목표 피크를 넘었는지 (목표 값이 없으면 넘었다고 하지 않음)
const isOverTarget = (kw) => targetKw.value !== null && kw > targetKw.value

// 최대 피크 아래 줄의 판정. 목표 값이 없으면 판정 없이 시각만 보여 줌
const maxPeakVerdict = computed(() => {
  if (targetKw.value === null || !summary.value) return ''
  return isOverTarget(summary.value.maxPeakKw) ? ', 목표 초과' : ', 목표 달성'
})

// 한전 평균 구매전력이 목표 피크의 몇 %인지 (표시용 비율)
const avgGridPercent = computed(() =>
  summary.value && targetKw.value ? (summary.value.avgGridKw / targetKw.value) * 100 : null,
)

// '2026-11-11T09:30:00' → '11-11 09:30'
const shortTime = (dateTime) => formatMonthDayTime(dateTime).slice(0, 11)

// 눈에 띄는 구간의 값: 단위에 맞는 자릿수로
const highlightValue = (item) =>
  item.unit === 'kW' ? formatKw(item.value) : formatOneDecimal(item.value)

function toggleFocus(at) {
  focusAt.value = focusAt.value === at ? '' : at
}

// ---- 그래프 공통 ----
// 그래프(ECharts)는 CSS 변수를 못 읽으므로, 테마 스토어가 global.css에서 읽어 둔 지금 테마 색을 씀
// (라이트·다크를 바꾸면 이 색이 바뀌고 그래프도 다시 그려짐)
const themeStore = useThemeStore()
const COLOR = themeStore.colors
// 그래프 툴팁도 지금 테마 색으로
const tooltipColors = () => ({
  backgroundColor: COLOR.card,
  borderColor: COLOR.line,
  textStyle: { color: COLOR.ink },
})

const isDaily = computed(() => trend.value?.interval === 'DAY') // 하루에 점 하나
const isSingleDay = computed(() => trend.value?.from === trend.value?.to) // 하루만 조회

// 가로축 글자: 1일 간격이면 '11-10', 그 밖에는 '11-10 09:00'
const labels = computed(() =>
  points.value.map((item) =>
    isDaily.value ? item.recordedAt.slice(5, 10) : shortTime(item.recordedAt),
  ),
)

// 날짜('11-10') → 날씨, 날짜 → 몇 번째 날인지
const weatherByDate = computed(() => {
  const result = {}
  for (const item of trend.value?.weather ?? []) result[item.date.slice(5)] = item.text
  return result
})
const dayNumbers = computed(() => {
  const result = {}
  ;(trend.value?.weather ?? []).forEach((item, index) => {
    result[item.date.slice(5)] = index
  })
  return result
})

// 날짜가 많으면 가로축 글자가 겹치므로 며칠에 하나씩만 적음
const dayStep = computed(() => Math.ceil((trend.value?.weather.length ?? 1) / 10))

// 세 그래프가 같이 쓰는 가로축. withWeather = true면 날짜 아래에 날씨도 적음
function timeAxis(withWeather) {
  return {
    type: 'category',
    data: labels.value,
    axisLine: { lineStyle: { color: COLOR.line } },
    axisTick: { show: false },
    axisLabel: {
      color: COLOR.mute,
      // 어느 칸에 글자를 적을지
      interval: (index, value) => {
        const date = value.slice(0, 5)
        if (isSingleDay.value && !isDaily.value) {
          return value.endsWith(':00') && Number(value.slice(6, 8)) % 3 === 0 // 3시간마다
        }
        const startOfDay = isDaily.value || value.endsWith(' 00:00')
        return startOfDay && dayNumbers.value[date] % dayStep.value === 0
      },
      formatter: (value) => {
        if (isSingleDay.value && !isDaily.value) return value.slice(6) // 하루만 보면 시각
        const date = value.slice(0, 5)
        const weather = weatherByDate.value[date]
        return withWeather && weather ? `${date}\n${weather}` : date
      },
    },
    // 날짜가 바뀌는 곳에 세로선
    splitLine: {
      show: !isDaily.value && !isSingleDay.value,
      interval: (index, value) => value.endsWith(' 00:00'),
      lineStyle: { color: COLOR.line },
    },
  }
}

const valueAxis = {
  type: 'value',
  axisLabel: { color: COLOR.mute },
  splitLine: { lineStyle: { color: COLOR.line } },
}

const legend = {
  right: 0,
  top: 0,
  icon: 'rect',
  itemWidth: 10,
  itemHeight: 10,
  textStyle: { color: COLOR.ink2 },
}

// 어떤 시각이 그래프의 어느 칸인지 (그 시각이 들어 있는 칸의 가로축 글자)
function slotLabelOf(at) {
  let found = null
  points.value.forEach((item, index) => {
    if (item.recordedAt <= at) found = labels.value[index]
  })
  return found
}

// "눈에 띄는 구간"에서 고른 시각이 그래프의 어느 칸인지
const focusLabel = computed(() => (focusAt.value ? slotLabelOf(focusAt.value) : null))

// ---- 추천 핀 ----
// 조회 기간 안의 추천을 그래프 칸별로 묶음. 예: { '11-13 10:00': [추천, 추천] }
const pinsByLabel = computed(() => {
  const result = {}
  if (!trend.value) return result
  const from = `${trend.value.from}T00:00:00`
  const to = `${trend.value.to}T23:59:59`
  for (const item of historyStore.decisions) {
    // 다른 화면에서 받아 둔 다른 기간의 이력이 섞이지 않게 기간 안의 것만 씀
    if (!PIN_INFO[item.status] || item.decidedAt < from || item.decidedAt > to) continue
    const label = slotLabelOf(item.decidedAt)
    if (!label) continue
    if (!result[label]) result[label] = []
    result[label].push(item)
  }
  return result
})

// 핀에 적는 기호: 그 칸에 나온 추천들의 처리 상태 (같은 기호는 한 번만)
function pinShapes(list) {
  return [...new Set(list.map((item) => PIN_INFO[item.status].shape))].join('')
}

// 전력 그래프 툴팁 아래에 붙이는 그 칸의 추천 목록
function pinTooltip(label) {
  if (!showPins.value) return ''
  const list = pinsByLabel.value[label] ?? []
  if (!list.length) return ''
  const rows = list.slice(0, 4).map((item) => {
    const power = item.action === 'IDLE' ? '' : ` ${formatKw(item.powerKw)} kW`
    const info = PIN_INFO[item.status]
    return `${info.shape} ${formatHourMinute(item.decidedAt)} ${ACTION_LABELS[item.action]}${power} · ${info.label}`
  })
  if (list.length > 4) rows.push(`외 ${list.length - 4}건`)
  return `<br/><strong>추천</strong><br/>${rows.join('<br/>')}`
}

// 기준선(가로), 추천 기호, 고른 시각(세로)을 그리는 설정
// lines 예: [{ value: 1800, text: '목표 피크 1,800', color: COLOR.ink, dashed: false }]
// withPins = true면 그래프 위쪽 띠에 추천 기호를 적음 (전력 그래프만)
function markLines(lines, withPins = false) {
  // 기준값이 null이면(백엔드가 설정 값을 못 찾은 경우) 그 선은 빼고 그림. 넣으면 ECharts 오류가 남
  const data = lines
    .filter((item) => item.value !== null && item.value !== undefined)
    .map((item) => ({
      yAxis: item.value,
      lineStyle: {
        color: item.color,
        width: 1.5,
        type: item.dashed ? 'dashed' : 'solid',
      },
      label: {
        formatter: item.text,
        position: 'insideEndTop',
        color: item.color,
        fontWeight: 'bold',
      },
    }))
  // 추천은 세로선을 긋지 않고(선이 많으면 그래프가 지저분해짐) 그래프 위쪽 띠에 기호만 적음.
  // 선 색을 투명하게 두고 선 끝의 글자(기호)만 보이게 하는 방식. 자세한 내용은 툴팁으로 봄
  // (opacity: 0을 쓰면 글자까지 같이 사라져서 색을 투명하게 함)
  if (withPins && showPins.value) {
    for (const [label, list] of Object.entries(pinsByLabel.value)) {
      data.push({
        xAxis: label,
        lineStyle: { color: 'rgba(0, 0, 0, 0)' },
        label: { formatter: pinShapes(list), position: 'end', color: COLOR.ink2, fontSize: 11 },
      })
    }
  }
  if (focusLabel.value) {
    data.push({
      xAxis: focusLabel.value,
      lineStyle: { color: COLOR.ink, width: 2, type: 'solid' },
      label: { formatter: '선택', position: 'end', color: COLOR.ink, fontWeight: 'bold' },
    })
  }
  return { silent: true, symbol: 'none', data }
}

function lineSeries(name, field, color, width = 2) {
  return {
    name,
    type: 'line',
    showSymbol: false,
    data: points.value.map((item) => item[field]),
    lineStyle: { color, width },
    itemStyle: { color },
  }
}

// ---- 전력 (kW) ----
const powerItems = computed(() => shown.value.filter((key) => key !== 'soc' && key !== 'temp'))

const powerOption = computed(() => {
  const series = []
  if (shown.value.includes('ess')) {
    series.push({
      name: 'ESS 방전(+) · 충전(−)',
      type: 'bar',
      barMaxWidth: 14,
      data: points.value.map((item) => item.essKw),
      itemStyle: { color: COLOR.ess },
    })
  }
  if (shown.value.includes('solar')) series.push(lineSeries('태양광', 'solarKw', COLOR.solar))
  if (shown.value.includes('grid')) series.push(lineSeries('한전 구매', 'gridKw', COLOR.grid))
  if (shown.value.includes('load')) series.push(lineSeries('공장 소비', 'loadKw', COLOR.ink))

  // 목표 피크선은 한전 구매전력과 비교하는 선이라, 한전 구매를 켰을 때만 그림
  const target = trend.value.targetPeakKw
  const lines = shown.value.includes('grid')
    ? [{ value: target, text: `목표 피크 ${formatKw(target)}`, color: COLOR.ink }]
    : []
  if (series.length) series[0].markLine = markLines(lines, true)

  return {
    animation: false,
    // 위쪽에 범례와 추천 핀 기호가 들어가므로 여백을 넉넉히 둠
    grid: { left: 52, right: 20, top: 44, bottom: 24 },
    legend,
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      // 값 목록 아래에 그 칸에 나온 추천을 함께 보여 줌
      formatter: (params) => {
        const label = params[0].axisValue
        const rows = params.map(
          (item) => `${item.marker}${item.seriesName} <strong>${formatKw(item.value)} kW</strong>`,
        )
        return `${label}<br/>${rows.join('<br/>')}${pinTooltip(label)}`
      },
    },
    xAxis: timeAxis(false),
    yAxis: valueAxis,
    series,
  }
})

// ---- 배터리 잔량 (%) ----
const socOption = computed(() => {
  const series = lineSeries('배터리 잔량', 'socPercent', COLOR.ess, 2.5)
  series.markLine = markLines([
    {
      value: trend.value.socLowLimit,
      text: `하한 ${trend.value.socLowLimit}%`,
      color: COLOR.mute,
      dashed: true,
    },
    {
      value: trend.value.socHighLimit,
      text: `상한 ${trend.value.socHighLimit}%`,
      color: COLOR.mute,
      dashed: true,
    },
  ])
  return {
    animation: false,
    grid: { left: 52, right: 20, top: 12, bottom: 22 },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      valueFormatter: (value) => `${formatOneDecimal(value)} %`,
    },
    xAxis: timeAxis(false),
    yAxis: { ...valueAxis, min: 0, max: 100, interval: 50 },
    series: [series],
  }
})

// ---- 온도 (℃) · 날씨 ----
const tempOption = computed(() => {
  const room = lineSeries('배터리실 온도', 'roomTempC', COLOR.ink, 2.5)
  const outside = lineSeries('바깥 기온', 'outsideTempC', COLOR.faint)
  const limit = trend.value.roomTempLimitC
  room.markLine = markLines([{ value: limit, text: `온도 제한 ${limit}℃`, color: COLOR.crit }])
  return {
    animation: false,
    // 가로축에 날짜와 날씨를 두 줄로 적으므로 아래 여백을 넉넉히 둠
    grid: { left: 52, right: 20, top: 24, bottom: 40 },
    // 그래프가 낮아 오른쪽 위의 "온도 제한" 글자와 겹치므로 범례는 왼쪽 위에 둠
    legend: { ...legend, right: 'auto', left: 52 },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      valueFormatter: (value) => `${formatOneDecimal(value)} ℃`,
    },
    xAxis: timeAxis(true),
    // 높이가 낮아 눈금은 20℃ 간격으로만
    yAxis: { ...valueAxis, min: 0, max: 40, interval: 20 },
    series: [outside, room],
  }
})

// ---- 시간대별 사용 패턴 ----
// 순위 보기: 백엔드가 준 시각별 평균을 큰 순서로 정렬만 함. 막대 길이는 1위를 100%로 본 비율(표시용)
const RANK_COUNT = 10 // 순위 보기에서 보여 줄 개수
const hourlyRanks = computed(() => {
  const sorted = [...(trend.value?.hourly ?? [])].sort((a, b) => b.avgGridKw - a.avgGridKw)
  const top = sorted[0]?.avgGridKw || 1
  return sorted.slice(0, RANK_COUNT).map((item, index) => ({
    rank: index + 1,
    hour: item.hour,
    avgGridKw: item.avgGridKw,
    period: PERIOD_LABELS[item.period] ?? '',
    isPeak: item.period === 'PEAK',
    width: (item.avgGridKw / top) * 100,
  }))
})

// ---- 시간대별 사용 패턴: 기간 동안의 막대 (1시간 · 4시간 · 1일 묶음) ----
const PATTERN_STEPS = [
  { value: 'AUTO', label: '자동' },
  { value: 'HOUR', label: '1시간' },
  { value: 'HOUR4', label: '4시간' },
  { value: 'DAY', label: '1일' },
]
// 한 화면에 보여 줄 막대 수. 이보다 많으면 아래 스크롤 막대로 좌우로 옮겨 봄
const PATTERN_WINDOW = { HOUR: 24, HOUR4: 42, DAY: 31 }

// 고른 기간이 며칠인지 (시작일과 종료일 포함)
const periodDays = computed(() => {
  if (!fromDate.value || !toDate.value) return 0
  const start = new Date(`${fromDate.value}T00:00:00`)
  const end = new Date(`${toDate.value}T00:00:00`)
  return Math.round((end - start) / 86400000) + 1
})
// "자동"일 때의 간격: 하루면 1시간, 7일 이하면 4시간, 그보다 길면(한 달 등) 1일
const autoStep = computed(() => {
  if (periodDays.value <= 1) return 'HOUR'
  if (periodDays.value <= 7) return 'HOUR4'
  return 'DAY'
})
const activeStep = computed(() =>
  patternStep.value === 'AUTO' ? autoStep.value : patternStep.value,
)
const stepLabel = (value) => PATTERN_STEPS.find((item) => item.value === value)?.label ?? ''

// 1시간 간격 값을 고른 간격으로 묶어 평균 냄 (그래프에 그리기 위한 표시용 평균)
const patternBars = computed(() => {
  const list = analysisStore.pattern?.points ?? []
  const groups = []
  for (const item of list) {
    const date = item.recordedAt.slice(0, 10) // '2026-11-18'
    const hour = Number(item.recordedAt.slice(11, 13))
    let key = item.recordedAt.slice(0, 13) // 1시간: 날짜 + 시
    if (activeStep.value === 'HOUR4') key = `${date} ${Math.floor(hour / 4)}`
    if (activeStep.value === 'DAY') key = date
    const last = groups[groups.length - 1]
    if (last && last.key === key) {
      last.values.push(item.gridKw)
    } else {
      groups.push({
        key,
        date,
        hour: activeStep.value === 'HOUR4' ? Math.floor(hour / 4) * 4 : hour,
        values: [item.gridKw],
      })
    }
  }
  const oneDay = periodDays.value <= 1
  return groups.map((group) => {
    const md = group.date.slice(5) // '11-18'
    let label = md
    if (activeStep.value === 'HOUR') label = oneDay ? `${group.hour}시` : `${md} ${group.hour}시`
    if (activeStep.value === 'HOUR4') label = `${md} ${group.hour}~${group.hour + 4}시`
    return {
      label,
      hour: group.hour,
      avgGridKw: group.values.reduce((sum, value) => sum + value, 0) / group.values.length,
    }
  })
})

const patternOption = computed(() => {
  const bars = patternBars.value
  const names = bars.map((item) => item.label)
  const target = analysisStore.pattern?.targetPeakKw

  // 1시간 간격일 때만 최대부하 시각의 막대 뒤에 회색 띠 (4시간 · 1일은 여러 요금 시간대가 섞여 표시하지 않음)
  const peakHours = new Set(
    (trend.value?.hourly ?? []).filter((item) => item.period === 'PEAK').map((item) => item.hour),
  )
  const areas =
    activeStep.value === 'HOUR'
      ? bars
          .filter((item) => peakHours.has(item.hour))
          .map((item) => [
            { xAxis: item.label, itemStyle: { color: COLOR.band } },
            { xAxis: item.label },
          ])
      : []

  // 처음에는 가장 최근 막대들을 보여 줌 (한 화면 막대 수만큼)
  const windowSize = PATTERN_WINDOW[activeStep.value]
  const startValue = Math.max(0, bars.length - windowSize)
  const endValue = Math.max(0, bars.length - 1)
  // 좌우 스크롤: 그래프 위에서 드래그 · 휠, 아래 막대로 옮김.
  // filterMode 'none' → 옮겨도 세로축 범위가 바뀌지 않음 (세로축은 왼쪽에 항상 그대로 보임)
  const zoomBase = { xAxisIndex: 0, filterMode: 'none', startValue, endValue }

  return {
    animation: false,
    grid: { left: 48, right: 12, top: 22, bottom: 48 },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    dataZoom: [
      { ...zoomBase, type: 'inside', zoomOnMouseWheel: false, moveOnMouseWheel: true },
      {
        ...zoomBase,
        type: 'slider',
        height: 14,
        bottom: 6,
        showDetail: false,
        brushSelect: false,
        zoomLock: true, // 막대 폭은 그대로, 위치만 옮김
        borderColor: COLOR.line,
        fillerColor: COLOR.band,
        handleStyle: { color: COLOR.card, borderColor: COLOR.mute },
        dataBackground: { lineStyle: { color: COLOR.faint }, areaStyle: { color: COLOR.band } },
      },
    ],
    xAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      axisLabel: { color: COLOR.mute, hideOverlap: true },
    },
    yAxis: { ...valueAxis, min: 0 },
    series: [
      {
        name: `평균 한전 구매 (${stepLabel(activeStep.value)})`,
        type: 'bar',
        barCategoryGap: '25%',
        data: bars.map((item) => Math.round(item.avgGridKw)),
        itemStyle: { color: COLOR.grid },
        markArea: { silent: true, data: areas },
        markLine: target
          ? {
              silent: true,
              symbol: 'none',
              lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
              label: {
                formatter: `목표 피크 ${formatKw(target)}`,
                position: 'insideEndTop',
                color: COLOR.ink,
                fontWeight: 'bold',
              },
              data: [{ yAxis: target }],
            }
          : undefined,
      },
    ],
  }
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">분석</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">추이 분석</h1>
      <p class="crumb-note">전력, 배터리, 온도·날씨를 같은 시간 축에 놓고 봅니다</p>
    </div>

    <!-- 조회 조건 -->
    <section class="card filters" aria-label="조회 조건">
      <div class="field">
        <span class="field-label">기간</span>
        <div class="presets" role="group" aria-label="기간 빠른 선택">
          <button
            :class="{ active: preset === 'today' }"
            :aria-pressed="preset === 'today'"
            @click="applyPreset('today')"
          >
            오늘
          </button>
          <button
            :class="{ active: preset === 'week' }"
            :aria-pressed="preset === 'week'"
            @click="applyPreset('week')"
          >
            최근 7일
          </button>
          <button
            :class="{ active: preset === 'month' }"
            :aria-pressed="preset === 'month'"
            @click="applyPreset('month')"
          >
            이번 달
          </button>
        </div>
        <input
          v-model="fromDate"
          type="date"
          class="input num"
          aria-label="시작일"
          @change="preset = 'custom'"
        />
        <span aria-hidden="true">~</span>
        <input
          v-model="toDate"
          type="date"
          class="input num"
          aria-label="종료일"
          @change="preset = 'custom'"
        />
      </div>

      <div class="field">
        <span class="field-label">간격</span>
        <div class="presets" role="group" aria-label="간격">
          <button
            v-for="item in INTERVALS"
            :key="item.value"
            :class="{ active: interval === item.value }"
            :aria-pressed="interval === item.value"
            @click="interval = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="field">
        <span class="field-label">보기</span>
        <label v-for="item in ITEMS" :key="item.key" class="check">
          <input v-model="shown" type="checkbox" :value="item.key" />
          {{ item.label }}
        </label>
      </div>

      <p v-if="analysisStore.loading" class="loading-note">불러오는 중입니다.</p>
      <p v-if="dateError" class="date-error" role="alert">{{ dateError }}</p>
    </section>

    <!-- 받아 둔 결과가 있으면 지우지 않고 위에 실패 띠만 띄움 (마지막으로 받은 값 유지) -->
    <p v-if="analysisStore.errorMessage && trend" class="error-band" role="alert">
      <span aria-hidden="true">◆</span>
      새 결과를 불러오지 못해 이전에 받은 결과를 보여 줍니다. ({{ analysisStore.errorMessage }})
      <button class="error-band-button" @click="load()">다시 시도</button>
    </p>
    <p v-if="analysisStore.errorMessage && !trend" class="state error" role="alert">
      분석 결과를 불러오지 못했습니다. ({{ analysisStore.errorMessage }})
      <button class="retry" @click="load()">다시 시도</button>
    </p>
    <!-- 조회 기간은 서버 시각으로 정하므로, 서버 시각을 받기 전에는 조회하지 않음 -->
    <p v-else-if="!trend && !serverDate" class="state">
      서버에서 현재 시각을 받으면 자동으로 조회합니다.
    </p>
    <p v-else-if="!trend" class="state">불러오는 중입니다.</p>
    <p v-else-if="!points.length" class="state">이 기간에는 기록이 없습니다. 기간을 바꿔 보세요.</p>

    <template v-else>
      <!-- 기간 요약 -->
      <dl class="card summary">
        <div>
          <dt>기간 중 최대 피크</dt>
          <dd :class="{ over: isOverTarget(summary.maxPeakKw) }">
            <span class="num">{{ formatKw(summary.maxPeakKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">{{ shortTime(summary.maxPeakAt) }}{{ maxPeakVerdict }}</p>
        </div>
        <div>
          <dt>평균 한전 구매전력</dt>
          <dd>
            <span class="num">{{ formatKw(summary.avgGridKw) }}</span> <small>kW</small>
          </dd>
          <p class="num">목표 피크의 {{ formatOneDecimal(avgGridPercent) }}%</p>
        </div>
        <div>
          <dt>태양광 발전</dt>
          <dd>
            <span class="num">{{ formatInteger(summary.solarKwh) }}</span> <small>kWh</small>
          </dd>
          <p class="num">공장 소비의 {{ formatOneDecimal(summary.solarPercentOfLoad) }}%</p>
        </div>
        <div>
          <dt>ESS 방전 / 충전</dt>
          <dd>
            <span class="num">
              {{ formatInteger(summary.dischargeKwh) }} / {{ formatInteger(summary.chargeKwh) }}
            </span>
            <small>kWh</small>
          </dd>
          <p class="num">충·방전 효율 {{ formatOneDecimal(summary.efficiencyPercent) }}%</p>
        </div>
        <div>
          <dt>배터리 잔량 범위</dt>
          <dd>
            <span class="num">
              {{ formatOneDecimal(summary.socMinPercent) }} ~
              {{ formatOneDecimal(summary.socMaxPercent) }}
            </span>
            <small>%</small>
          </dd>
          <p class="num">최저 {{ shortTime(summary.socMinAt) }}</p>
        </div>
        <div>
          <dt>배터리실 최고 온도</dt>
          <dd>
            <span class="num">{{ formatOneDecimal(summary.roomTempMaxC) }}</span> <small>℃</small>
          </dd>
          <p class="num">
            {{ shortTime(summary.roomTempMaxAt) }}, 제한
            {{ formatOneDecimal(trend.roomTempLimitC) }}℃
          </p>
        </div>
      </dl>

      <div class="main">
        <!-- 왼쪽: 같은 시간 축을 쓰는 그래프 세 개 -->
        <section class="card charts">
          <!--
            전력이 이 화면의 주인공이라 크게, 배터리 잔량·온도는 보조라 작게 그림 (2026-11-18 UI 피드백)
            세 그래프는 그대로 위아래로 두어 가로축(시각)을 맞춤
          -->
          <template v-if="powerItems.length">
            <h2 class="chart-title main-chart">
              전력 <small>kW</small>
              <span v-if="showPins" class="chart-note">
                위쪽 기호 = 추천 (● 적용 · ◇ 자동 · ✕ 넘김 · ○ 추천 중), 올리면 자세히
              </span>
              <label class="check pin-toggle">
                <input v-model="showPins" type="checkbox" />
                추천 표시
              </label>
            </h2>
            <p v-if="historyStore.errorMessage" class="pin-error" role="alert">
              추천 기록을 불러오지 못해 핀을 표시하지 못했습니다. ({{ historyStore.errorMessage }})
            </p>
            <!--
              key: 켜 둔 항목이 바뀌면 그래프를 새로 만듦.
              Chart.vue는 이전 설정에 새 설정을 덮어쓰는 방식이라, 선의 개수가 줄면 꺼 둔 선이 남기 때문
            -->
            <Chart :key="`${powerItems.join()}-${showPins}`" :option="powerOption" :height="460" />
          </template>

          <template v-if="shown.includes('soc')">
            <h2 class="chart-title sub-chart">
              배터리 잔량 <small>%</small>
              <span class="chart-note">점선은 잔량 하한과 상한</span>
            </h2>
            <Chart :option="socOption" :height="100" />
          </template>

          <template v-if="shown.includes('temp')">
            <h2 class="chart-title sub-chart">
              온도 <small>℃</small> · 날씨
              <span class="chart-note">날짜 아래에 그날 날씨</span>
            </h2>
            <Chart :option="tempOption" :height="120" />
          </template>

          <p v-if="!shown.length" class="state">"보기"에서 볼 항목을 하나 이상 골라 주세요.</p>
        </section>

        <!-- 오른쪽 -->
        <div class="right">
          <section class="card">
            <div class="card-head">
              <h2 class="card-title">시간대별 사용 패턴</h2>
              <div class="presets view-switch" role="group" aria-label="보기 방식">
                <button
                  :class="{ active: hourlyView === 'time' }"
                  :aria-pressed="hourlyView === 'time'"
                  @click="hourlyView = 'time'"
                >
                  시간 순
                </button>
                <button
                  :class="{ active: hourlyView === 'rank' }"
                  :aria-pressed="hourlyView === 'rank'"
                  @click="hourlyView = 'rank'"
                >
                  순위
                </button>
              </div>
            </div>
            <!-- Chart.vue는 크기가 0이면 오류가 나므로 v-show 대신 v-if로 바꿔 끼움 -->
            <template v-if="hourlyView === 'time'">
              <!-- 막대 간격 고르기: 자동이면 기간에 맞춰 (하루 1시간 · 7일 4시간 · 한 달 1일) -->
              <div class="step-row">
                <span class="step-label">간격</span>
                <div class="presets step-switch" role="group" aria-label="막대 간격">
                  <button
                    v-for="item in PATTERN_STEPS"
                    :key="item.value"
                    :class="{ active: patternStep === item.value }"
                    :aria-pressed="patternStep === item.value"
                    @click="patternStep = item.value"
                  >
                    {{ item.label }}
                  </button>
                </div>
                <span v-if="patternStep === 'AUTO'" class="step-auto">
                  지금 {{ stepLabel(autoStep) }}
                </span>
              </div>
              <Chart v-if="patternBars.length" :option="patternOption" :height="210" />
              <p v-else-if="analysisStore.patternLoading" class="state">불러오는 중입니다.</p>
              <p v-else-if="analysisStore.patternError" class="state" role="alert">
                불러오지 못했습니다. ({{ analysisStore.patternError }})
                <button class="retry" @click="analysisStore.fetchPattern(fromDate, toDate)">
                  다시 시도
                </button>
              </p>
              <p v-else class="state">이 기간의 기록이 없습니다.</p>
              <p class="foot-note">
                기간 동안의 평균 한전 구매 (kW). 그래프를 끌거나 아래 막대로 좌우로 옮겨 봅니다.
                <template v-if="activeStep === 'HOUR'">회색 띠는 최대부하 시간대입니다.</template>
              </p>
            </template>
            <template v-else>
              <ol class="ranks">
                <li v-for="item in hourlyRanks" :key="item.hour">
                  <span class="rank num">{{ item.rank }}</span>
                  <strong class="rank-hour num">{{ item.hour }}시</strong>
                  <span class="rank-track" aria-hidden="true">
                    <span class="rank-bar" :style="{ width: `${item.width}%` }"></span>
                  </span>
                  <span class="rank-kw num">{{ formatKw(item.avgGridKw) }} kW</span>
                  <span class="rank-period" :class="{ peak: item.isPeak }">{{ item.period }}</span>
                </li>
              </ol>
              <p class="foot-note">한전 구매가 많은 시간부터 {{ RANK_COUNT }}개 (시각별 평균)</p>
            </template>
          </section>

          <section class="card">
            <div class="card-head">
              <h2 class="card-title">추천 처리별 결과</h2>
              <span class="note">결과가 나온 방전 추천</span>
            </div>
            <table class="table">
              <thead>
                <tr>
                  <th>처리</th>
                  <th class="right-text">건수</th>
                  <th>목표 달성</th>
                  <th class="right-text">평균 실제 피크</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in trend.byStatus" :key="item.status">
                  <td>
                    <strong>
                      <span aria-hidden="true">{{ STATUS_INFO[item.status].shape }}</span>
                      {{ STATUS_INFO[item.status].label }}
                    </strong>
                  </td>
                  <td class="right-text num">{{ item.count }}</td>
                  <td class="num">
                    <template v-if="item.count">
                      <span class="track" aria-hidden="true">
                        <span class="bar" :style="{ width: `${item.defendedPercent}%` }"></span>
                      </span>
                      {{ item.defendedCount }}건 ({{ formatOneDecimal(item.defendedPercent) }}%)
                    </template>
                    <template v-else>—</template>
                  </td>
                  <td class="right-text num">
                    <strong :class="{ over: isOverTarget(item.avgResultPeakKw) }">
                      {{ formatKw(item.avgResultPeakKw) }}
                      <template v-if="item.count">kW</template>
                    </strong>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="card highlights-card">
            <div class="card-head">
              <h2 class="card-title">눈에 띄는 구간</h2>
              <span class="note">누르면 그래프에 그 시각을 표시</span>
            </div>
            <ul class="highlights">
              <li v-for="item in trend.highlights" :key="item.label">
                <button
                  class="highlight"
                  :class="{ active: focusAt === item.at }"
                  :aria-pressed="focusAt === item.at"
                  @click="toggleFocus(item.at)"
                >
                  <span class="highlight-time num">{{ shortTime(item.at) }}</span>
                  <span class="highlight-label">
                    <span v-if="item.level === 'CRITICAL'" class="over" aria-hidden="true">■</span>
                    {{ item.label }}
                  </span>
                  <strong class="num" :class="{ over: item.level === 'CRITICAL' }">
                    {{ highlightValue(item) }} {{ item.unit }}
                  </strong>
                </button>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 20px;
}
.crumb-note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}

/* 조회 조건 */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 24px;
}
.field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.field-label {
  font-weight: 700;
  color: var(--ink-2);
}
.input {
  height: 34px;
  padding: 0 10px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  color: var(--ink);
}
.check {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
}
.check input {
  width: 15px;
  height: 15px;
  accent-color: var(--ink);
}
.loading-note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.date-error {
  width: 100%;
  color: var(--crit);
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-height: 200px;
  color: var(--mute);
}
.state.error {
  color: var(--crit);
}
.over {
  color: var(--crit);
}

/* 기간 요약 */
.summary {
  display: flex;
  padding: 12px 16px;
}
.summary > div {
  flex: 1;
  min-width: 0;
  margin-right: 16px;
  padding-right: 16px;
  border-right: 1px solid var(--line);
}
.summary > div:last-child {
  margin-right: 0;
  padding-right: 0;
  border-right: 0;
}
.summary dt {
  font-size: 12px;
  color: var(--mute);
}
.summary dd {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
}
.summary small {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--ink-2);
}
.summary p {
  overflow: hidden;
  font-size: 12px;
  color: var(--mute);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 그래프와 오른쪽 열 */
.main {
  display: flex;
  flex: 1;
  gap: 16px;
}
.charts {
  flex: 1;
  min-width: 0;
}
.chart-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 15px;
  font-weight: 700;
}
/* 주 그래프(전력)는 제목을 크게, 보조 그래프(배터리 잔량·온도)는 작고 옅게 */
.chart-title.main-chart {
  font-size: 18px;
}
.chart-title.sub-chart {
  font-size: 13px;
  color: var(--ink-2);
}
.chart-title:first-child {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}
.chart-title small {
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
}
.chart-note {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
}

.right {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 16px;
  width: 480px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.note {
  margin-left: auto;
  font-size: 13px;
  color: var(--mute);
}
.foot-note {
  font-size: 12px;
  color: var(--mute);
}

/* 추천 처리별 결과 표 */
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th {
  padding: 6px 12px 6px 0;
}
.table td {
  padding: 9px 12px 9px 0;
  border-bottom: 1px solid var(--line);
  white-space: nowrap;
}
.table tr:last-child td {
  border-bottom: 0;
}
.table th:last-child,
.table td:last-child {
  padding-right: 0;
}
.table .right-text {
  text-align: right;
}
.pin-toggle {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
}
.pin-error {
  font-size: 12px;
  color: var(--crit);
}

/* 시간대별 사용 패턴: 보기 방식 전환 */
.view-switch {
  margin-left: auto;
}
.view-switch button {
  height: 28px;
  padding: 0 10px;
  font-size: 13px;
}
/* 시간대별 사용 패턴: 막대 간격 고르기 */
.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.step-label {
  font-size: 13px;
  color: var(--mute);
}
.step-switch button {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
}
.step-auto {
  font-size: 12px;
  color: var(--mute);
}
/* 순위 보기 */
.ranks {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ranks li {
  display: grid;
  grid-template-columns: 22px 40px minmax(0, 1fr) 76px 60px;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.rank {
  color: var(--mute);
  text-align: right;
}
.rank-track {
  height: 10px;
  background: var(--line);
}
.rank-bar {
  display: block;
  height: 100%;
  background: var(--src-grid);
}
.rank-kw {
  text-align: right;
}
.rank-period {
  font-size: 12px;
  color: var(--mute);
}
.rank-period.peak {
  font-weight: 700;
  color: var(--ink-2);
}

.track {
  display: inline-block;
  width: 80px;
  height: 8px;
  margin-right: 8px;
  background: var(--line);
  vertical-align: middle;
}
.bar {
  display: block;
  height: 100%;
  background: var(--ok);
}

/* 눈에 띄는 구간 */
.highlights-card {
  flex: 1;
}
.highlight {
  display: flex;
  align-items: baseline;
  gap: 12px;
  width: 100%;
  padding: 8px 8px 8px 4px;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-left: 4px solid transparent;
  color: var(--ink);
  text-align: left;
}
.highlights li:last-child .highlight {
  border-bottom: 0;
}
.highlight:hover {
  background: var(--bg);
}
/* 그래프에 표시 중인 구간 */
.highlight.active {
  background: var(--bg);
  border-left-color: var(--ink);
}
.highlight-time {
  flex: none;
  width: 92px;
  font-weight: 700;
}
.highlight-label {
  color: var(--ink-2);
}
.highlight strong {
  margin-left: auto;
  white-space: nowrap;
}

/* 화면이 좁으면 오른쪽 열을 줄여 그래프 자리를 남김 */
@media (max-width: 1600px) {
  .right {
    width: 400px;
  }
  .track {
    width: 48px;
  }
}
</style>
