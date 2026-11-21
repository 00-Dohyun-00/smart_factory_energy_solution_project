// 개발용 스위치: 주소에 ?fake 가 있으면 실제 백엔드 대신 가짜 백엔드(fakeBackend.js)로 답함 (시연 영상 촬영 등)
//   http://localhost:5173/?fake                         → 가짜 데이터 (10배속)
//   http://localhost:5173/?fake&scenario=spike&speed=5  → 정상 → 피크 튐 → 정상 장면 (5배속)
// main.js가 개발 서버(npm run dev)에서만 불러오므로, 빌드한 결과물에는 들어가지 않음.
// 화면 코드와 공통 파일(client.js)은 그대로 두고, 브라우저의 fetch만 가로채서 /api/ 요청에 가짜 값으로 답함.
import {
  fakeGetDecisions,
  fakeGetLatestDecision,
  fakeGetLatestStatus,
  fakeGetPeakAnalysis,
  fakeGetRecentEvents,
  fakeGetReport,
  fakeGetSettingHistories,
  fakeGetSettings,
  fakeGetTodayStatuses,
  fakeGetTrendAnalysis,
  fakeGetZoneRecent,
  fakeUpdateDecision,
  fakeUpdateSettings,
} from '@/api/fakeBackend.js'

// 가짜 백엔드의 이벤트 종류(category) → 실제 백엔드의 eventType (화면은 eventType으로 거름)
const EVENT_TYPES = {
  STAGE: 'STAGE_CHANGE',
  RECOMMEND: 'RECOMMEND',
  ACTION: 'HANDLE',
  WINDOW: 'INTERVAL_END',
  BATTERY: 'COMM',
  SETTING: 'SETTING',
}
const withEventType = (events) =>
  events.map((event) => ({ ...event, eventType: EVENT_TYPES[event.category] ?? event.category }))

// [메서드, 주소 모양, 답하는 함수(주소에서 뽑은 값, 쿼리, 본문)]
const ROUTES = [
  ['GET', /^\/api\/site-powers\/latest$/, () => fakeGetLatestStatus()],
  ['GET', /^\/api\/site-powers\/today$/, () => fakeGetTodayStatuses()],
  ['GET', /^\/api\/zones\/([^/]+)$/, (match) => fakeGetZoneRecent(match[1])],
  ['GET', /^\/api\/recommendations\/latest$/, () => fakeGetLatestDecision()],
  [
    'GET',
    /^\/api\/recommendations$/,
    (_, query) => fakeGetDecisions(query.get('from'), query.get('to')),
  ],
  [
    'PATCH',
    /^\/api\/recommendations\/(\d+)$/,
    (match, _, body) => fakeUpdateDecision(Number(match[1]), body.status),
  ],
  [
    'GET',
    /^\/api\/events$/,
    // 가짜 백엔드에는 기간 조회가 없어서 기간으로 물으면 빈 목록
    async (_, query) =>
      query.has('limit')
        ? withEventType(await fakeGetRecentEvents(Number(query.get('limit'))))
        : [],
  ],
  ['GET', /^\/api\/settings$/, () => fakeGetSettings()],
  ['PUT', /^\/api\/settings$/, (_, __, body) => fakeUpdateSettings(body)],
  [
    'GET',
    /^\/api\/setting-histories$/,
    (_, query) => fakeGetSettingHistories(query.get('from'), query.get('to')),
  ],
  [
    'GET',
    /^\/api\/analyses\/trend$/,
    (_, query) => fakeGetTrendAnalysis(query.get('from'), query.get('to'), query.get('interval')),
  ],
  [
    'GET',
    /^\/api\/analyses\/peak$/,
    (_, query) => fakeGetPeakAnalysis(query.get('from'), query.get('to')),
  ],
  ['GET', /^\/api\/reports$/, (_, query) => fakeGetReport(query.get('month'))],
  ['GET', /^\/api\/equipment-powers$/, () => []], // 가짜 백엔드에 없는 기능 (전력 이력)
]

const json = (status, body) =>
  new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

export function installFakeFetch() {
  const realFetch = window.fetch.bind(window)
  window.fetch = async (input, options = {}) => {
    const url = new URL(typeof input === 'string' ? input : input.url, window.location.origin)
    if (!url.pathname.startsWith('/api/')) return realFetch(input, options)

    const method = (options.method ?? 'GET').toUpperCase()
    for (const [routeMethod, pattern, handler] of ROUTES) {
      const match = url.pathname.match(pattern)
      if (routeMethod !== method || !match) continue
      try {
        const body = options.body ? JSON.parse(options.body) : null
        const result = await handler(match, url.searchParams, body)
        // 실제 백엔드처럼: 값이 없으면 204(본문 없음)
        return result === null || result === undefined ? json(204, null) : json(200, result)
      } catch (error) {
        // 가짜 백엔드가 던진 오류 문구를 실제 백엔드의 400 응답처럼 돌려줌
        return json(400, { status: 400, message: error.message })
      }
    }
    return json(404, { status: 404, message: '가짜 백엔드에 없는 주소입니다.' })
  }
  console.info('[fake] 가짜 백엔드로 동작 중 (주소의 ?fake를 빼면 실제 백엔드)')
}
