<!--
  모니터링 > 통합
  1줄: KPI 5개 (공장 소비전력 · 한전 구매전력 · 태양광 발전량 · ESS 충·방전 · 배터리 잔량)
  2줄: 예상 피크(15분 흐름 그래프) · ESS 추천 (화면에서 가장 크게)
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
// 이벤트 종류 (category는 가짜 백엔드에 임시로 넣은 값, 백엔드와 확정 필요)
const EVENT_CATEGORIES = {
  STAGE: '단계 변화',
  RECOMMEND: '추천',
  ACTION: '적용·넘김',
  WINDOW: '구간 종료',
  BATTERY: '배터리',
  SETTING: '설정 변경',
}

// 필터: 받아 온 목록을 화면에서 거름 ('ALL'이면 거르지 않음)
const eventCategory = ref('ALL')
const eventLevel = ref('ALL')
const filteredEvents = computed(() =>
  realtimeStore.events.filter(
    (event) =>
      (eventCategory.value === 'ALL' || event.category === eventCategory.value) &&
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

// 공장 소비와 한전 구매는 그래프를 나눠 그림 (한 그래프에 선이 많으면 비교가 어려움)
// 두 그래프는 같은 가로축, 같은 요금 띠, 같은 "지금" 세로선을 씀
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

  const series = lines.map((line) => ({
    name: line.name,
    type: 'line',
    showSymbol: false,
    connectNulls: false,
    data: values(line.field),
    lineStyle: { color: line.color, width: 2.5, type: line.dashed ? 'dashed' : 'solid' },
    itemStyle: { color: line.color },
  }))

  // 요금 띠와 "지금" 세로선은 첫 번째 선에, 목표 피크선은 한전 구매 그래프에만 붙임
  const first = series[0]
  first.markArea = { silent: true, data: tariffAreas }
  const markLines = []
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
    grid: { left: 52, right: 20, top: 28, bottom: 24 },
    // 범례는 카드 제목 줄의 버튼으로 보여 줌 (그래프 위쪽의 "지금" 글자와 겹쳐 눌리지 않던 문제, 2026-11-18)
    // 그래프 안 범례는 숨기고, 버튼으로 끄고 켠 상태만 넘김
    legend: {
      show: false,
      selected: Object.fromEntries(
        lines.map((line) => [line.name, !hiddenSeries.value[line.name]]),
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
      axisLine: { lineStyle: { color: COLOR.line } },
      axisTick: { show: false },
      // 3시간마다 눈금 글자 (15분 칸 12개 = 3시간)
      axisLabel: { color: COLOR.mute, interval: (index) => index % 12 === 0 },
    },
    yAxis: {
      type: 'value',
      // 그래프가 낮아(약 110~150px) 눈금 글자가 겹치지 않게 눈금을 3칸 정도로
      splitNumber: 3,
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

// 오늘 그래프 두 개 (위: 공장 소비 / 아래: 한전 구매)
const trendCards = computed(() => {
  const loadLines = [
    { name: '공장 소비', field: 'loadKw', color: COLOR.ink },
    { name: '태양광', field: 'solarKw', color: COLOR.solar, dashed: true },
  ]
  const gridLines = [{ name: '한전 구매', field: 'gridKw', color: COLOR.grid }]
  return [
    {
      key: 'load',
      title: '오늘 공장 소비',
      note: '15분 평균 (kW), 회색 띠는 요금 비싼 시간',
      lines: loadLines,
      option: makeTrendOption(loadLines, false),
    },
    {
      key: 'grid',
      title: '오늘 한전 구매',
      note: '15분 평균 (kW), 피크는 이 값으로 정해짐',
      lines: gridLines,
      option: makeTrendOption(gridLines, true),
    },
  ]
})
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

    <!-- 2줄: 예상 피크 + ESS 추천 (화면에서 가장 눈에 띄게 가장 크게) -->
    <div class="row middle">
      <PeakWindowCard class="peak" :status="status" :stale="!realtimeStore.connected" />

      <div class="advice-wrap">
        <EssAdviceCard
          class="advice"
          :decision="realtimeStore.decision"
          :busy="busy"
          :offline="!realtimeStore.connected"
          :auto="settingsStore.values['operation.auto_mode'] === true"
          @apply="answerDecision('APPLIED')"
          @ignore="answerDecision('IGNORED')"
        />
        <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
      </div>
    </div>

    <!-- 3줄: 오늘 그래프 두 개(공장 소비 / 한전 구매) + 이벤트 로그(두 그래프를 합친 높이) -->
    <div class="row bottom">
      <div class="trends">
        <section v-for="card in trendCards" :key="card.key" class="card trend">
          <div class="card-head">
            <h2 class="card-title">{{ card.title }}</h2>
            <!-- 범례: 누르면 그 선을 끄고 켬 (끈 선은 흐리게) -->
            <div class="legend-buttons">
              <button
                v-for="line in card.lines"
                :key="line.name"
                class="legend-button"
                :class="{ off: hiddenSeries[line.name] }"
                :aria-pressed="!hiddenSeries[line.name]"
                @click="toggleSeries(line.name)"
              >
                <span
                  class="legend-swatch"
                  :class="{ dashed: line.dashed }"
                  :style="{ '--swatch': line.color }"
                  aria-hidden="true"
                ></span>
                {{ line.name }}
              </button>
            </div>
            <span class="note">{{ card.note }}</span>
          </div>
          <FitChart
            v-if="realtimeStore.todayStatuses.length"
            :option="card.option"
            :min-height="70"
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
                <td class="event-category">{{ EVENT_CATEGORIES[event.category] ?? '—' }}</td>
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
            {{ selectedSlot ? '시점을 아래 그래프에 표시 중' : '이벤트는 오늘 그래프 밖입니다' }}
            <button class="clear" @click="selectedEventId = null">지우기</button>
          </p>
          <p v-else class="select-hint">줄을 누르면 아래 그래프에 그 시점을 표시합니다.</p>
          <RouterLink to="/logs" class="more-link">추천 이력 보기</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 화면 높이에 맞춤: 남은 높이를 아래 오늘 그래프 두 개가 나눠 채움 (설비 모니터링과 같은 방식, 2026-11-18)
   화면이 아주 낮아 그래프 최소 높이도 안 나오면 그때만 스크롤 */
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 20px;
}
.tariff {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  font-size: 14px;
  color: var(--ink-2);
}
.tariff-caption {
  font-size: 13px;
  color: var(--mute);
}
.tariff-next {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
/* 요금 시간대 배지: 시간대 색을 옅게 깐 바탕 + 같은 색 테두리·글자 */
.tariff-badge {
  --tariff: var(--ink-2);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 12px;
  /* 배지가 제목 글자(30px)보다 높아 제목 줄이 커지지 않게 줄 간격을 줄임 → 카드 시작 위치가 설비 모니터링과 같음 */
  line-height: 1.25;
  background: color-mix(in srgb, var(--tariff) 14%, var(--card));
  border: 1.5px solid var(--tariff);
  border-radius: 999px;
  font-size: 16px;
  font-weight: 700;
  color: var(--tariff);
}
.tariff-badge.small {
  padding: 1px 8px;
  background: none;
  border-width: 1px;
  font-size: 13px;
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
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--tariff);
}
/* 자동 모드 띠: 상태색이 아니라 먹색 바탕으로 눈에 띄게 함 */
.mode-band {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-left: 16px;
  padding: 5px 12px;
  background: var(--ink);
  color: var(--card);
  font-size: 14px;
}
.mode-sub {
  font-size: 12px;
  color: var(--line);
}

.row {
  display: flex;
  gap: 16px;
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
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 330px;
  color: var(--mute);
}

/* 2줄(예상 피크 · ESS 추천)이 가장 눈에 띄도록, 화면의 남은 높이를 3줄보다 조금 더 크게 나눠 가짐 (2026-11-18, UI 피드백)
   flex-basis가 0이라 ESS 추천 내용이 바뀌어도 줄 높이는 변하지 않음. 최소 높이는 ESS 추천 버튼까지 들어오는 높이 */
.middle {
  flex: 1.15 1 0;
  min-height: 320px;
}
/* 예상 피크와 ESS 추천이 2줄을 반씩 나눠 씀 */
.middle > .peak {
  flex: 1;
  min-width: 0;
}
.advice-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
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
  flex: 1 1 0;
  min-height: 0;
}
.trends {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
/* 제목 줄의 범례 버튼 */
.legend-buttons {
  display: flex;
  gap: 4px;
}
.legend-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 6px;
  background: none;
  border: 1px solid transparent;
  border-radius: 3px;
  font-size: 12px;
  color: var(--ink-2);
}
.legend-button:hover {
  border-color: var(--line-strong);
}
/* 끈 선: 흐리게 + 취소선 */
.legend-button.off {
  color: var(--faint);
  text-decoration: line-through;
}
.legend-button.off .legend-swatch {
  opacity: 0.35;
}
/* 선 모양 견본: 실선은 굵은 줄, 점선은 끊긴 줄 (색은 그래프 선 색) */
.legend-swatch {
  width: 16px;
  height: 0;
  border-top: 3px solid var(--swatch);
}
.legend-swatch.dashed {
  border-top-style: dashed;
}
/* 그래프 카드 두 개가 남은 높이를 반씩 씀 */
.trend {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.chart-empty {
  flex: 1;
  height: auto;
}
/* 이벤트 로그: 오늘 그래프 두 개의 오른쪽, 두 그래프를 합친 높이. 넘치는 줄은 표 안에서 스크롤 */
.events-card {
  display: flex;
  flex: none;
  flex-direction: column;
  width: 520px;
  min-height: 0;
  overflow: hidden;
}
.event-filters {
  display: flex;
  gap: 6px;
  margin-left: auto;
}
.filter {
  padding: 2px 6px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 13px;
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
  font-size: 14px;
}
.events th {
  position: sticky;
  top: 0;
  padding: 4px 8px;
  background: var(--card);
  border-bottom: 1px solid var(--line-strong);
  font-size: 12px;
  font-weight: 400;
  color: var(--mute);
  text-align: left;
  white-space: nowrap;
}
.events td {
  padding: 6px 8px;
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
  height: auto;
}
.events-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
}
.select-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: var(--mute);
}
.clear {
  padding: 1px 8px;
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 12px;
  color: var(--ink);
}
.events-foot .more-link {
  margin-left: auto;
  white-space: nowrap;
}

/* 화면이 좁으면 이벤트 로그 폭을 줄여 왼쪽 그래프 자리를 남김 */
@media (max-width: 1600px) {
  .events-card {
    width: 420px;
  }
}

/* 화면이 낮으면(노트북, 예: 1707×890) 간격과 2줄 높이를 줄여
   남은 높이를 오늘 그래프 두 개가 나눠 쓰게 함 (2026-11-18, UI 피드백) */
@media (max-height: 950px) {
  /* 바깥 여백(20px)과 제목 줄 아래 간격(16px)은 설비 모니터링과 같게 두어 카드의 바깥 꼭짓점 위치를 맞추고,
     카드 사이 간격만 줄임 (2026-11-18, UI 피드백) */
  .page {
    gap: 10px;
    padding: 20px;
  }
  .page > .crumb {
    margin-bottom: 6px;
  }
  .row,
  .trends {
    gap: 10px;
  }
  .middle {
    min-height: 270px;
  }
  .trend {
    padding-top: 8px;
    padding-bottom: 6px;
  }
  .trend .card-head {
    margin-bottom: 0;
  }
}
</style>
