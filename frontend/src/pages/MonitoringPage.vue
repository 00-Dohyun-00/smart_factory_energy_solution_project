<!--
  모니터링 > 통합
  1줄: KPI 5개 (공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량)
  2줄: 예상 피크(15분 흐름 그래프) · ESS 추천
  3줄: 오늘 공장 소비 / 오늘 한전 구매 그래프 · 이벤트 로그(종류·단계 필터, 줄을 누르면 왼쪽 그래프에 표시)
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import FitChart from '@/components/FitChart.vue'
import EssAdviceCard from '@/components/EssAdviceCard.vue'
import KpiCards from '@/components/KpiCards.vue'
import PeakWindowCard from '@/components/PeakWindowCard.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { useRealtimeStore } from '@/stores/useRealtimeStore'
import { useSettingsStore } from '@/stores/useSettingsStore'
import { formatHourMinute, formatKw } from '@/utils/format'

const realtimeStore = useRealtimeStore()

// 요금 시간대 이름 → 배지 색 이름
const TARIFF_CLASS = { 최대부하: 'peak', 중간부하: 'mid', 경부하: 'light' }
const settingsStore = useSettingsStore() // 운영 모드를 알기 위해 (값은 공통 틀이 받아 둠)

const busy = ref(false) // 적용/넘기기 요청 중
const actionError = ref('') // 적용/넘기기 실패 메시지

const status = computed(() => realtimeStore.status)

// 최신 상태·추천은 공통 틀(MainLayout)이 5초마다 받아 오므로, 여기서는 그래프용 목록만 받음
onMounted(() => {
  realtimeStore.fetchToday()
})

// 15분 구간이 바뀌면 오늘 전력 추이에 점이 하나 늘어나므로 다시 받음
watch(
  () => status.value?.windowStart,
  (now, before) => {
    if (now && before) realtimeStore.fetchToday()
  },
)

// 운영 모드 바꾸기 (ESS 추천 카드의 버튼에서 확인을 마친 뒤): 환경 설정 operation.auto_mode 저장
// 공용 DB 설정이라 저장하면 팀원 모두의 화면에 적용됨. 실패하면 카드 아래에 이유를 보여 줌
const modeBusy = ref(false)
async function changeMode(wantAuto) {
  modeBusy.value = true
  actionError.value = ''
  try {
    await settingsStore.saveSettings([{ key: 'operation.auto_mode', value: wantAuto }])
  } catch (error) {
    actionError.value = `운영 모드를 바꾸지 못했습니다. (${error.message})`
  } finally {
    modeBusy.value = false
  }
}

async function answerDecision(newStatus) {
  busy.value = true
  actionError.value = ''
  try {
    await realtimeStore.changeDecisionStatus(newStatus)
  } catch (error) {
    actionError.value = error.message
  } finally {
    busy.value = false
  }
}

// ---- 이벤트 로그 ----
// 단계는 색만으로 구분하지 않도록 도형과 글자를 함께 표시
// color: 그래프에 표시할 때 쓰는 색 이름 (테마 스토어 colors의 키)
const EVENT_INFO = {
  CRITICAL: { shape: '■', label: '경고', className: 'crit', color: 'crit' },
  WARNING: { shape: '▲', label: '주의', className: 'warn', color: 'warn' },
  NORMAL: { shape: '●', label: '정상', className: 'ok', color: 'ok' },
  INFO: { shape: '○', label: '알림', className: 'info', color: 'ink2' },
}
// 이벤트 종류: 백엔드 eventType 값 그대로 (event_log.event_type). 예전 가짜 백엔드의 category를 대신함
const EVENT_CATEGORIES = {
  STAGE_CHANGE: '단계 변화',
  RECOMMEND: '추천',
  HANDLE: '적용·넘김',
  INTERVAL_END: '구간 종료',
  COMM: '통신',
  SETTING: '설정 변경',
}

// 필터: 받아 온 목록을 화면에서 거름 ('ALL'이면 거르지 않음)
const eventCategory = ref('ALL')
const eventLevel = ref('ALL')
const filteredEvents = computed(() =>
  realtimeStore.events.filter(
    (event) =>
      (eventCategory.value === 'ALL' || event.eventType === eventCategory.value) &&
      (eventLevel.value === 'ALL' || event.level === eventLevel.value),
  ),
)

// 고른 이벤트: 아래 오늘 그래프 두 개에 그 시각을 세로선으로 표시. 같은 줄을 다시 누르면 지움
const selectedEventId = ref(null)
const selectedEvent = computed(
  () => realtimeStore.events.find((event) => event.id === selectedEventId.value) ?? null,
)
function toggleEvent(event) {
  selectedEventId.value = selectedEventId.value === event.id ? null : event.id
}

// 이벤트 시각이 들어 있는 15분 칸 ('14:23' → '14:15'). 오늘이 아닌 이벤트는 그래프에 없으므로 null
const selectedSlot = computed(() => {
  const event = selectedEvent.value
  const today = status.value?.recordedAt?.slice(0, 10)
  if (!event || event.occurredAt.slice(0, 10) !== today) return null
  const minute = Math.floor(Number(event.occurredAt.slice(14, 16)) / 15) * 15
  return `${event.occurredAt.slice(11, 13)}:${String(minute).padStart(2, '0')}`
})

// ---- 오늘 전력 추이 ----
// 그래프(ECharts)는 CSS 변수를 못 읽으므로, 테마 스토어가 global.css에서 읽어 둔 지금 테마 색을 씀
// (라이트·다크를 바꾸면 이 색이 바뀌고 그래프도 다시 그려짐)
const themeStore = useThemeStore()
const COLOR = themeStore.colors
// 그래프 툴팁도 지금 테마 색으로
const tooltipColors = () => ({
  // 툴팁 창을 페이지 맨 위(body)에 붙여, 카드·그래프 칸 밖으로 나가도 잘리지 않게 함
  appendTo: 'body',
  backgroundColor: COLOR.card,
  borderColor: COLOR.line,
  textStyle: { color: COLOR.ink },
})

// 가로축: 하루를 15분으로 나눈 96칸 ('00:00', '00:15', … '23:45')
// 아직 오지 않은 시간은 값 없이 비워 두어, 하루 중 지금이 어디쯤인지 보이게 함
const SLOTS = Array.from({ length: 96 }, (_, index) => {
  const hour = String(Math.floor(index / 4)).padStart(2, '0')
  const minute = String((index % 4) * 15).padStart(2, '0')
  return `${hour}:${minute}`
})

// 누적 영역에서 ESS를 나눠 그릴 때의 이름 (툴팁에 이 이름으로 나옴)
const ESS_DISCHARGE = 'ESS 방전'
const ESS_CHARGE = 'ESS 충전'

// 오늘 그래프 하나에 공장 소비 · 태양광 · 한전 구매 · ESS를 함께 그림 (2026-11-19, 두 그래프를 합침)
// 보고 싶은 선만 제목 줄 버튼으로 켜고 끄며, 선 / 영역 모양도 버튼으로 고름
function makeTrendOption(lines, withTarget) {
  const list = realtimeStore.todayStatuses
  const target = status.value?.targetPeakKw
  const lastTime = list.length ? formatHourMinute(list[list.length - 1].recordedAt) : null

  // 시각별 값을 96칸에 맞춰 넣음 (없는 칸은 null → 선을 그리지 않음)
  const byTime = {}
  for (const item of list) byTime[formatHourMinute(item.recordedAt)] = item
  const values = (field) => SLOTS.map((time) => (byTime[time] ? byTime[time][field] : null))

  // 요금이 비싼 시간대(최대부하)를 회색 띠로 표시. 시간대 구분은 백엔드가 준 값
  const tariffAreas = (status.value?.tariff.periods ?? [])
    .filter((item) => item.period === 'PEAK')
    .map((item) => [
      { xAxis: item.from, itemStyle: { color: COLOR.band } },
      { xAxis: item.to === '24:00' ? '23:45' : item.to },
    ])

  const stacked = chartStyle.value === 'stack'
  const series = lines.flatMap((line) => {
    // ESS 충·방전은 짧게 끊겨 나와서 선 모양일 때는 막대로 그림 (방전은 0 위, 충전은 0 아래).
    // 누적 영역일 때는 ESS도 공급원이라 한전 구매 · 태양광 위에 쌓음 (아래 영역 처리로 넘어감)
    if (line.bar && !stacked) {
      return {
        name: line.name,
        type: 'bar',
        barWidth: '70%',
        data: values(line.field),
        itemStyle: { color: line.color },
      }
    }
    // 누적 영역의 ESS: 방전(+)과 충전(−)을 두 영역으로 나눠 그림.
    // 한 선으로 쌓으면 방전 → 충전으로 바뀌는 순간 위(공급원 위)에서 0 아래까지 선이 이어져 그래프를 가로지름
    if (line.bar && stacked) {
      const raw = values(line.field)
      const area = (name, stack, pick) => ({
        name,
        type: 'line',
        showSymbol: false,
        connectNulls: false,
        stack,
        data: raw.map((value) => (value === null ? null : pick(value))),
        lineStyle: { color: line.color, width: 1.5 },
        itemStyle: { color: line.color },
        areaStyle: { color: line.color, opacity: 0.45 },
      })
      return [
        area(ESS_DISCHARGE, 'supply', (value) => Math.max(value, 0)), // 공급원 위에 쌓음
        area(ESS_CHARGE, 'charge', (value) => Math.min(value, 0)), // 0 아래에 따로
      ]
    }
    // 누적 영역: 공급원(한전 구매 · 태양광 · ESS 방전)을 아래부터 쌓아 칠함 → 쌓인 높이 = 공급원 합계.
    // 공장 소비는 쌓지 않고 선으로 두어, 쌓인 영역과 비교할 수 있게 함
    const inStack = stacked && line.stack
    return {
      name: line.name,
      type: 'line',
      showSymbol: false,
      connectNulls: false,
      data: values(line.field),
      stack: inStack ? 'supply' : undefined,
      lineStyle: {
        color: line.color,
        width: inStack ? 1.5 : 2.5,
        type: line.dashed && !inStack ? 'dashed' : 'solid',
      },
      itemStyle: { color: line.color },
      areaStyle: inStack ? { color: line.color, opacity: 0.45 } : undefined,
    }
  })

  // 누적 영역에는 빈 막대 계열(보이지 않음)을 하나 넣음.
  // ECharts는 막대 계열이 있으면 가로축 양 끝을 막대 반 칸씩 넓히는데, 선 모양에는 ESS 막대가 있고 누적 영역에는
  // 없어서 모양을 바꿀 때마다 가로축 시각 글자가 몇 px씩 움직였음 → 두 모양 모두 같은 여백이 되게 맞춤
  if (stacked) series.push({ type: 'bar', data: [], silent: true, tooltip: { show: false } })

  // 요금 띠, "지금" 세로선, 목표 피크선은 첫 번째 선에 붙임
  const first = series[0]
  first.markArea = { silent: true, data: tariffAreas }
  // 가로축 (y = 0): 데이터 선과 다른 색(먹색)으로 또렷하게. 글자 없음
  const markLines = [
    {
      yAxis: 0,
      lineStyle: { color: COLOR.ink, width: 1.5, type: 'solid' },
      label: { show: false },
    },
  ]
  if (withTarget && target) {
    markLines.push({
      yAxis: target,
      // 목표 피크선: 데이터 선과 섞이지 않게 전용 자주색 실선 (2026-11-18). 빨강은 상태 표시에만 씀
      lineStyle: { color: COLOR.target, width: 1.5, type: 'solid' },
      label: {
        formatter: `목표 피크 ${formatKw(target)}`,
        position: 'insideStartTop',
        color: COLOR.target,
        fontWeight: 'bold',
      },
    })
  }
  if (lastTime) {
    // 세로선은 지금 구간의 칸에 긋고, 글자에는 서버가 보낸 지금 시각을 적음
    markLines.push({
      xAxis: lastTime,
      lineStyle: { color: COLOR.ink2, width: 1, type: 'solid' },
      label: {
        formatter: `지금 ${formatHourMinute(status.value?.recordedAt)}`,
        // 선 위쪽 끝에 가로로 적음 (범례는 카드 제목 줄로 옮겨서 이 자리와 겹치지 않음, 2026-11-18)
        position: 'end',
        // 하루 끝 무렵(21시 이후)에는 오른쪽 끝을 넘어 잘리므로 선 왼쪽에, 하루 시작 무렵(3시 전)에는
        // 왼쪽 세로축 숫자에 걸리므로 선 오른쪽에 붙여 적음
        align:
          SLOTS.indexOf(lastTime) > 84 ? 'right' : SLOTS.indexOf(lastTime) < 12 ? 'left' : 'center',
        // 이벤트 로그에서 고른 시점이 바로 옆(2시간 이내)이면 두 글자가 겹치므로 지금 글자는 잠시 숨김 (선은 그대로)
        show: !(
          selectedSlot.value &&
          Math.abs(SLOTS.indexOf(selectedSlot.value) - SLOTS.indexOf(lastTime)) < 8
        ),
        backgroundColor: COLOR.card,
        padding: [2, 4],
        color: COLOR.ink,
      },
    })
  }
  // 이벤트 로그에서 고른 이벤트: 단계 색 점선 + 시각과 도형
  if (selectedSlot.value) {
    const info = EVENT_INFO[selectedEvent.value.level]
    markLines.push({
      xAxis: selectedSlot.value,
      lineStyle: { color: COLOR[info.color], width: 2, type: 'dashed' },
      label: {
        formatter: `${info.shape} ${formatHourMinute(selectedEvent.value.occurredAt)}`,
        // 선 위쪽 끝에 가로로 적음
        position: 'end',
        color: COLOR[info.color],
        fontWeight: 'bold',
        backgroundColor: COLOR.card,
        padding: [2, 4],
      },
    })
  }
  first.markLine = { silent: true, symbol: 'none', data: markLines }

  return {
    animation: false,
    // top: 범례 줄과 그래프 사이 공백 (28 → 22, 위쪽 "지금 HH:MM" 글자가 잘리지 않을 만큼만)
    // outerBoundsMode 'none': 세로축 글자 폭(예: -500 ↔ -1000)에 따라 ECharts가 여백을 저절로 늘리지 않게 함.
    // 선 ↔ 누적 영역을 바꿀 때 세로축 범위가 달라져도 그래프 칸(가로축 시각 위치)이 움직이지 않음
    grid: { left: 52, right: 20, top: 22, bottom: 24, outerBoundsMode: 'none' },
    // 범례는 카드 제목 줄의 버튼으로 보여 줌 (그래프 위쪽의 "지금" 글자와 겹쳐 눌리지 않던 문제, 2026-11-18)
    // 그래프 안 범례는 숨기고, 버튼으로 끄고 켠 상태만 넘김
    legend: {
      show: false,
      // ESS 버튼 하나로 누적 영역의 방전 · 충전 두 영역을 함께 끄고 켬
      selected: Object.fromEntries(
        lines.flatMap((line) => {
          const shown = !hiddenSeries.value[line.name]
          return line.bar
            ? [
                [line.name, shown],
                [ESS_DISCHARGE, shown],
                [ESS_CHARGE, shown],
              ]
            : [[line.name, shown]]
        }),
      ),
    },
    tooltip: {
      ...tooltipColors(),
      trigger: 'axis',
      valueFormatter: (value) => `${formatKw(value)} kW`,
    },
    xAxis: {
      type: 'category',
      data: SLOTS,
      boundaryGap: false,
      // 가로축 선(진한 선)은 y = 0 위치에 따로 그림 (아래 markLine). 시각 글자 · 눈금은 그래프 맨 아래에 두어
      // ESS 충전(0 아래) 막대와 겹치지 않게 함. 맨 아래 테두리 선은 연하게
      axisLine: { onZero: false, lineStyle: { color: COLOR.line } },
      axisTick: {
        show: true,
        interval: (index) => index % 12 === 0,
        length: 5,
        lineStyle: { color: COLOR.lineStrong },
      },
      // 3시간마다 눈금 글자 (15분 칸 12개 = 3시간)
      axisLabel: {
        color: COLOR.ink2,
        fontSize: 11,
        interval: (index) => index % 12 === 0,
      },
      splitLine: {
        show: true,
        interval: (index) => index % 12 === 0,
        lineStyle: { color: COLOR.line },
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: COLOR.mute },
      splitLine: { lineStyle: { color: COLOR.line } },
    },
    series,
  }
}

// 범례 버튼으로 끈 선 (선 이름 → true). 다시 누르면 켜짐
const hiddenSeries = ref({})
function toggleSeries(name) {
  hiddenSeries.value = { ...hiddenSeries.value, [name]: !hiddenSeries.value[name] }
}

// 그래프 모양: 'line'(선) / 'stack'(누적 영역: 한전 구매 · 태양광을 쌓아 칠함)
const CHART_STYLES = [
  { value: 'line', label: '선' },
  { value: 'stack', label: '누적 영역' },
]
const chartStyle = ref('line')

// 오늘 그래프 (공장 소비 · 태양광 · 한전 구매 · ESS 충·방전을 한 그래프에)
// stack: 누적 영역에서 쌓는 선 (쌓는 순서 = 목록 순서, 한전 구매가 맨 아래) / bar: 선 모양일 때 막대로 그림
const TREND_LINES = computed(() => [
  // 공장 소비는 회색, 한전 구매는 하늘색 (가로축 먹색과도 구분되게, 2026-11-19)
  { name: '공장 소비', field: 'loadKw', color: COLOR.load },
  { name: '한전 구매', field: 'gridKw', color: COLOR.gridLine, stack: true },
  { name: '태양광', field: 'solarKw', color: COLOR.solar, dashed: true, stack: true },
  // ESS는 막대. 방전 +, 충전 − (충전하면 0 아래로 내려감)
  { name: 'ESS 충·방전', field: 'essKw', color: COLOR.ess, bar: true, stack: true },
])
const trendOption = computed(() => makeTrendOption(TREND_LINES.value, true))
</script>

<template>
  <div class="page">
    <div class="crumb">
      <span class="crumb-parent">모니터링</span>
      <span class="crumb-parent" aria-hidden="true">›</span>
      <h1 class="crumb-title">통합</h1>
      <!-- 요금 시간대: 지금 시간대를 색 배지로 크게, 다음 시간대는 옅은 배지로 (최대부하 빨강 · 중간부하 주황 · 경부하 초록) -->
      <p v-if="status" class="tariff">
        <span class="tariff-caption">요금 시간대</span>
        <strong class="tariff-badge" :class="TARIFF_CLASS[status.tariff.label]">
          <span class="tariff-dot" aria-hidden="true"></span>{{ status.tariff.label }}
        </strong>
        <span class="tariff-next">
          <span class="num">{{ status.tariff.until }}</span>부터
          <span class="tariff-badge small" :class="TARIFF_CLASS[status.tariff.nextLabel]">
            {{ status.tariff.nextLabel }}
          </span>
        </span>
      </p>
      <!-- 자동 모드일 때 멀리서도 보이게 제목 줄에 크게 표시 (실제 제어가 아니라 시뮬레이션) -->
      <p v-if="settingsStore.values['operation.auto_mode'] === true" class="mode-band">
        <strong>자동 모드 (시뮬레이션)</strong>
        <span class="mode-sub">추천을 자동으로 적용 중</span>
      </p>
    </div>

    <!-- 서버 연결이 끊겼다는 알림은 공통 틀(MainLayout)이 모든 화면에 띄움 -->

    <!-- 1줄: KPI 5개 -->
    <KpiCards :status="status" :stale="!realtimeStore.connected" />

    <!-- 2줄: 15분 피크 + ESS 추천 -->
    <div class="row middle">
      <PeakWindowCard class="peak" :status="status" :stale="!realtimeStore.connected" />

      <div class="advice-wrap">
        <EssAdviceCard
          class="advice"
          :decision="realtimeStore.decision"
          :busy="busy"
          :offline="!realtimeStore.connected"
          :auto="settingsStore.values['operation.auto_mode'] === true"
          :mode-busy="modeBusy"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
          @change-mode="changeMode"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄: 오늘 그래프(공장 소비 · 태양광 · 한전 구매 · ESS를 한 그래프에) + 이벤트 로그 -->
    <div class="row bottom">
      <div class="trends">
        <section class="card trend">
          <div class="card-head trend-head">
            <h2 class="card-title">오늘의 전력 추이</h2>
            <!-- 그래프 모양: 선 / 누적 영역 (제목 줄 오른쪽 끝) -->
            <div class="presets style-switch" role="group" aria-label="그래프 모양">
              <button
                v-for="item in CHART_STYLES"
                :key="item.value"
                :class="{ active: chartStyle === item.value }"
                :aria-pressed="chartStyle === item.value"
                @click="chartStyle = item.value"
              >
                {{ item.label }}
              </button>
            </div>
            <!-- 안내 글은 다음 줄로 (제목 줄에 버튼이 많아 한 줄에 두면 글자가 끼임) -->
            <span class="note trend-note"
              >15분 평균 (kW) · 회색 띠는 요금 비싼 시간 · ESS는 방전 +, 충전 −</span
            >
            <!-- 범례: 그래프 바로 위 줄. 누르면 그 선을 끄고 켬 (끈 선은 흐리게) -->
            <div class="legend-buttons">
              <button
                v-for="line in TREND_LINES"
                :key="line.name"
                class="legend-button"
                :class="{ off: hiddenSeries[line.name] }"
                :aria-pressed="!hiddenSeries[line.name]"
                @click="toggleSeries(line.name)"
              >
                <span
                  class="legend-swatch"
                  :class="{ dashed: line.dashed, bar: line.bar }"
                  :style="{ '--swatch': line.color }"
                  aria-hidden="true"
                ></span>
                {{ line.name }}
              </button>
            </div>
          </div>
          <!-- key: 선 ↔ 누적 영역을 바꾸면 새로 그림 (공통 차트는 이전 설정에 덮어써서, 영역을 끄면 칠한 색이 남음) -->
          <!-- 카드의 남은 높이를 채움 (화면 높이에 맞춰 스크롤 없이 들어가게) -->
          <FitChart
            v-if="realtimeStore.todayStatuses.length"
            :key="chartStyle"
            :option="trendOption"
            :min-height="80"
          />
          <p v-else-if="realtimeStore.todayLoading" class="empty chart-empty">불러오는 중입니다.</p>
          <p v-else-if="realtimeStore.todayError" class="empty chart-empty" role="alert">
            그래프를 불러오지 못했습니다. ({{ realtimeStore.todayError }})
            <button class="retry" @click="realtimeStore.fetchToday()">다시 시도</button>
          </p>
          <p v-else class="empty chart-empty">오늘 기록이 아직 없습니다.</p>
        </section>
      </div>

      <section class="card events-card">
        <div class="card-head">
          <h2 class="card-title">이벤트 로그</h2>
          <div class="event-filters">
            <select v-model="eventCategory" class="filter" aria-label="종류">
              <option value="ALL">종류 전체</option>
              <option v-for="(label, key) in EVENT_CATEGORIES" :key="key" :value="key">
                {{ label }}
              </option>
            </select>
            <select v-model="eventLevel" class="filter" aria-label="단계">
              <option value="ALL">단계 전체</option>
              <option v-for="(info, key) in EVENT_INFO" :key="key" :value="key">
                {{ info.shape }} {{ info.label }}
              </option>
            </select>
          </div>
        </div>

        <div v-if="filteredEvents.length" class="events-scroll">
          <table class="events">
            <thead>
              <tr>
                <th class="col-time">시각</th>
                <th class="col-level">단계</th>
                <th class="col-category">종류</th>
                <th>내용</th>
              </tr>
            </thead>
            <tbody>
              <!-- 줄을 누르면 아래 오늘 그래프에 그 시각을 표시 (다시 누르면 지움) -->
              <tr
                v-for="event in filteredEvents"
                :key="event.id"
                :class="{ selected: event.id === selectedEventId }"
                tabindex="0"
                :aria-pressed="event.id === selectedEventId"
                @click="toggleEvent(event)"
                @keydown.enter.prevent="toggleEvent(event)"
                @keydown.space.prevent="toggleEvent(event)"
              >
                <td class="num event-time">{{ formatHourMinute(event.occurredAt) }}</td>
                <td class="event-level" :class="EVENT_INFO[event.level].className">
                  <span aria-hidden="true">{{ EVENT_INFO[event.level].shape }}</span>
                  {{ EVENT_INFO[event.level].label }}
                </td>
                <td class="event-category">{{ EVENT_CATEGORIES[event.eventType] ?? '—' }}</td>
                <td class="event-text">{{ event.message }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="empty events-empty">
          {{
            realtimeStore.events.length
              ? '조건에 맞는 이벤트가 없습니다.'
              : '아직 기록된 이벤트가 없습니다.'
          }}
        </p>

        <div class="events-foot">
          <p v-if="selectedEvent" class="select-hint">
            <span class="num">{{ formatHourMinute(selectedEvent.occurredAt) }}</span>
            {{ selectedSlot ? '시점을 그래프에 표시 중' : '이벤트는 오늘 그래프 밖입니다' }}
            <button class="clear" @click="selectedEventId = null">지우기</button>
          </p>
          <p v-else class="select-hint">줄을 누르면 왼쪽 그래프에 그 시점을 표시합니다.</p>
          <RouterLink to="/logs" class="more-link">추천 이력 보기</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 제목 줄 높이를 다른 화면(30px)과 같게 고정 (요금 시간대 배지 때문에 높아지면 카드 시작 위치가 어긋남) */
.page > .crumb {
  height: 30px;
}
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  height: 100%;
  min-height: 100%;
  box-sizing: border-box;
  padding: 20px;
  overflow: hidden;
}
.tariff {
  display: flex;
  align-items: center;
  gap: 6.4px;
  margin-left: auto;
  font-size: 11.2px;
  color: var(--ink-2);
}
.tariff-caption {
  font-size: 10.4px;
  color: var(--mute);
}
.tariff-next {
  display: inline-flex;
  align-items: center;
  gap: 4.8px;
}
/* 요금 시간대 배지: 시간대 색을 옅게 깐 바탕 + 같은 색 테두리·글자 */
.tariff-badge {
  --tariff: var(--ink-2);
  display: inline-flex;
  align-items: center;
  gap: 4.8px;
  padding: 2.4px 9.6px;
  background: color-mix(in srgb, var(--tariff) 14%, var(--card));
  border: 1.5px solid var(--tariff);
  border-radius: 999px;
  font-size: 12.8px;
  font-weight: 700;
  color: var(--tariff);
}
.tariff-badge.small {
  padding: 0.8px 6.4px;
  background: none;
  border-width: 1px;
  font-size: 10.4px;
  font-weight: 400;
}
.tariff-badge.peak {
  --tariff: var(--crit);
}
.tariff-badge.mid {
  --tariff: var(--warn);
}
.tariff-badge.light {
  --tariff: var(--ok);
}
.tariff-dot {
  width: 7.2px;
  height: 7.2px;
  border-radius: 50%;
  background: var(--tariff);
}
/* 자동 모드 띠: 상태색이 아니라 먹색 바탕으로 눈에 띄게 함 */
.mode-band {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-left: 12.8px;
  padding: 4px 9.6px;
  background: var(--ink);
  color: var(--card);
  font-size: 11.2px;
}
.mode-sub {
  font-size: 9.6px;
  color: var(--line);
}

.row {
  display: flex;
  gap: 16px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 9.6px;
  margin-bottom: 6.4px;
}
.note {
  margin-left: auto;
  font-size: 10.4px;
  color: var(--mute);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 264px;
  color: var(--mute);
}

/* 2줄: 높이 고정 (ESS 추천이 와서 버튼·판단 근거가 생겨도 줄 높이가 변하지 않아 아래 화면이 움직이지 않음)
   내용이 아주 길면(판단 근거 "모두 보기") ESS 추천 카드 안에서만 스크롤 */
/* 2줄 높이: 340 → 306(10% 축소) → 245px(통합 화면 전체 20% 축소, 2026-11-19) */
.middle {
  height: 245px;
}
.peak {
  flex: 1;
  min-width: 0;
}
.advice-wrap {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 8px;
  width: 580px; /* 600 → 480(20% 축소) → 580px(+100, 2026-11-19). 왼쪽 카드가 그만큼 줄어듦 */
}
.advice {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.action-error {
  color: var(--crit);
}

/* 3줄 */
.bottom {
  flex: 1;
  min-height: 0;
  align-items: stretch;
}
.trends {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}
/* 제목 줄의 범례 버튼 */
.legend-buttons {
  display: flex;
  gap: 4px;
}
/* 범례 버튼: 누를 수 있게 보이도록 테두리 · 흰 바탕 · 마우스 올림 표시 */
.legend-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
  padding: 0 7px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 4px;
  font-size: 9.6px;
  color: var(--ink-2);
  cursor: pointer;
}
.legend-button:hover {
  background: var(--bg);
  border-color: var(--ink-2);
  color: var(--ink);
}
/* 끈 선: 점선 테두리 + 흐리게 + 취소선 */
.legend-button.off {
  background: var(--bg);
  border-style: dashed;
  color: var(--faint);
  text-decoration: line-through;
}
.legend-button.off .legend-swatch {
  opacity: 0.35;
}
/* 선 모양 견본: 실선은 굵은 줄, 점선은 끊긴 줄 (색은 그래프 선 색) */
.legend-swatch {
  width: 12.8px;
  height: 0;
  border-top: 3px solid var(--swatch);
}
.legend-swatch.dashed {
  border-top-style: dashed;
}
/* 막대로 그리는 선(ESS)의 견본: 작은 네모 */
.legend-swatch.bar {
  width: 8px;
  height: 8px;
  border-top: 0;
  background: var(--swatch);
}
/* 오늘 전력 제목 줄: 제목 · 선 버튼 · 모양 버튼이 넘치면 줄을 바꾸고, 안내 글은 항상 다음 줄 */
.trend-head {
  flex-wrap: wrap;
  align-items: center;
  row-gap: 3.2px;
}
/* 오늘 그래프 제목 묶음 아래 여백을 줄여 그래프를 조금 더 크게 */
.trend-head {
  margin-bottom: 2px;
}
/* 선 / 누적 영역 버튼은 제목 줄 오른쪽 끝 */
.trend-head .style-switch {
  margin-left: auto;
}
/* 선 이름 버튼(범례)은 맨 아래 줄 = 그래프 바로 위 */
.trend-head .legend-buttons {
  flex-basis: 100%;
  justify-content: flex-end; /* 오른쪽 정렬 */
}
.trend-head .trend-note {
  flex-basis: 100%;
  margin-left: 0;
  font-size: 9.6px;
}
/* 그래프 모양(선 / 영역) 버튼: 제목 줄에 들어가게 작게 */
.style-switch button {
  height: 19.2px;
  padding: 0 8px;
  font-size: 9.6px;
}
.chart-empty {
  flex: 1;
  height: auto;
}
/* 오늘 전력 카드: 3줄의 남은 높이를 다 쓰고, 그래프가 카드 안 남은 높이를 채움 */
.trend {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
/* 이벤트 로그: 폭 고정(위 ESS 추천 카드와 같은 폭), 높이는 3줄 높이에 맞춤 */
.events-card {
  display: flex;
  flex: none;
  flex-direction: column;
  contain: size; /* 이벤트가 늘어나도 줄 높이를 밀어내지 않음 (넘치는 이벤트는 표 안에서 스크롤) */
  width: 580px; /* 600 → 480(20% 축소) → 580px(+100, 2026-11-19). 왼쪽 카드가 그만큼 줄어듦 */
  min-height: 0;
}
.event-filters {
  display: flex;
  gap: 4.8px;
  margin-left: auto;
}
.filter {
  padding: 1.6px 4.8px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 10.4px;
  color: var(--ink);
}
/* 표: 칸 높이가 고정이라 넘치는 줄은 표 안에서 스크롤 */
.events-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.events {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px; /* 이벤트 로그 글자 (본문 기본 14px보다 작게) */
}
.events th {
  position: sticky;
  top: 0;
  padding: 3.2px 6.4px;
  background: var(--card);
  border-bottom: 1px solid var(--line-strong);
  font-size: 9.6px;
  font-weight: 400;
  color: var(--mute);
  text-align: left;
  white-space: nowrap;
}
.events td {
  padding: 4.8px 6.4px;
  border-bottom: 1px solid var(--line);
  vertical-align: top;
}
.events tbody tr {
  cursor: pointer;
}
.events tbody tr:hover {
  background: var(--bg);
}
/* 고른 줄: 왼쪽 굵은 줄 + 옅은 바탕 */
.events tbody tr.selected {
  background: var(--bg);
  box-shadow: inset 3px 0 0 var(--ink);
}
.col-time,
.col-level,
.col-category {
  width: 1%;
}
.event-time {
  font-weight: 700;
  white-space: nowrap;
}
.event-level,
.event-category {
  white-space: nowrap;
}
.event-category {
  color: var(--ink-2);
}
.event-level.crit {
  color: var(--crit);
}
.event-level.warn {
  color: var(--warn);
}
.event-level.ok {
  color: var(--ok);
}
.event-level.info {
  color: var(--ink-2);
}
.events-empty {
  flex: 1;
}
.events-foot {
  display: flex;
  align-items: center;
  gap: 9.6px;
  padding-top: 6.4px;
}
.select-hint {
  display: flex;
  align-items: center;
  gap: 4.8px;
  min-width: 0;
  font-size: 10.4px;
  color: var(--mute);
}
.clear {
  padding: 0.8px 6.4px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 9.6px;
  color: var(--ink);
}
.events-foot .more-link {
  margin-left: auto;
  white-space: nowrap;
}

/* 화면이 좁으면 오른쪽 카드 폭을 줄여 왼쪽 그래프와 게이지 자리를 남김 */
@media (max-width: 1600px) {
  .events-card {
    width: 484px; /* 384 + 100 */
  }
  .advice-wrap {
    width: 484px;
  }
}
</style>
