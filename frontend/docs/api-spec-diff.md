# API 명세와 프론트 화면 차이 정리

- **비교 기준 (2026-11-19 갱신):**
  - 백엔드: 실제 서버 (`dev` `80f7ac1`)의 Swagger(`http://localhost:8080/swagger-ui.html`)와 응답
  - 명세: 팀 구글 시트 "(api 명세서) 2조 에너지솔루션" v0.2
  - 프론트: `dev` `80f7ac1` + 설비 구역 정리(`zoneId`)
- **지금 상태: 프론트의 모든 화면이 실제 백엔드에 연결됐다.** 가짜 백엔드(`src/api/fakeBackend.js`)는 더 이상 어느 화면도 부르지 않는다.
- 필드 이름은 Swagger에 나온 그대로 쓴다. 이름을 바꾸는 변환 코드는 두지 않는다.
- 예전 내용(명세 v0.2와 가짜 데이터를 비교하고 백엔드에 요청할 목록을 정리한 판)은 git 기록(커밋 `3300045`, `d44833d`)에 있다.

---

## 0. 한눈에 보기

| 번호 | API | 프론트 함수 (`src/api/`) | 쓰는 화면 | 상태 |
|---|---|---|---|---|
| 1 | `GET /api/site-powers/latest` | `getLatestBatteryStatus` | 왼쪽 메뉴, 통합, 설비 모니터링 | 연결됨 (11-19) |
| 2 | `GET /api/recommendations/latest` (없으면 204) | `getLatestBatteryDecision` | 통합 ESS 추천 | 연결됨 (11-19) |
| 3 | `PATCH /api/recommendations/{id}` 본문 `{ status }` | `updateBatteryDecision` | 통합 [적용]·[넘기기] | 연결됨 (11-19), 실제 클릭은 아직 확인 못 함 |
| 4 | `GET /api/events?limit=30` | `getRecentEvents` | 통합 이벤트 로그 | 연결됨 (11-19) |
| 5 | `GET /api/site-powers/today` | `getTodayBatteryStatuses` | 통합 오늘 그래프 | 연결됨 (11-19) |
| 6 | `GET /api/site-powers/recent` | — | — | 화면에서 안 씀 (백엔드에도 없음) |
| 7 | `GET /api/zones/{zoneCode}` (`Z1`~`Z6`) | `getZoneRecent` | 설비 모니터링 구역 상세 | 연결됨 (11-19) |
| 8 | `GET /api/recommendations?from=&to=` (최대 121일) | `getBatteryDecisions` | 추천 이력, 추이 분석 | 연결됨 (11-17) |
| 9 | `GET /api/reports?month=` | `getReport` | 월간 성적표 | 연결됨 (11-18) |
| 10 | `GET /api/analyses/trend?from=&to=&interval=` (최대 121일) | `getTrendAnalysis` | 추이 분석 | 연결됨 (11-18) |
| 11 | `GET /api/settings` | `getSettings` | 왼쪽 메뉴, 환경 설정 | 연결됨 (11-18) |
| 12 | `PUT /api/settings` 본문 `[{ key, value }]` | `updateSettings` | 환경 설정 | 연결됨 (11-18) |
| 13 | `GET /api/setting-histories?from=&to=` | `getSettingHistories` | 환경 설정 | 연결됨 (11-18) |
| 16 | `GET /api/demand-intervals` | — | — | 안 씀: 피크 분석 응답의 `topWindows`로 대신함 |
| (추가) | `GET /api/analyses/peak?from=&to=` (최대 62일) | `getPeakAnalysis` | 피크 분석 | 연결됨 (11-18) |
| — | `GET /api/equipment`, `GET /api/tariff-periods`, `GET /api/zones`, `GET·POST /api/system/*` | — | — | 화면에서 안 씀 |

---

## 1. 연결하면서 맞춘 것

### 1-1. 구역 코드

- 백엔드 구역 코드는 `Z1`~`Z6`이다. **유틸리티는 `Z6`**이다(예전 프론트·명세 "확인 필요" 3번은 `U`).
- 프론트도 `Z6`으로 바꿨다: 구역 목록(`PowerTree.vue`, `PowerDiagramPage.vue`), 색 변수 `--zone-z6`, 테마 키 `zoneZ6`, 주소 `/monitoring/diagram?zone=Z6`.

### 1-2. 지금 상태의 설비 구역 (`equipments[].zoneId`)

- `site-powers/latest`의 `equipments[]`는 `{ id, zoneId, name, ratedKw, loadKw, origin, state }`이다. `zoneId`는 구역 코드(`Z1`~`Z6`), 한전·태양광·ESS는 `null`이다.
- 설비 모니터링은 이 `zoneId`로 구역별 설비를 묶는다.
- 처음에는 구역이 없어서 `GET /api/equipment`로 붙이는 임시 코드를 썼다. #29 요청으로 백엔드가 `zoneId`를 넣어 줘서(2026-11-19) 임시 코드는 지웠다.
- 이름 주의: 설비 목록 API(`/api/equipment`)와 구역 목록 API(`/api/zones`)는 같은 값을 `zoneCode`라고 부르고, 지금 상태는 `zoneId`라고 부른다.

### 1-3. 이벤트 종류

- 백엔드는 `eventType`을 준다: `STAGE_CHANGE`(단계 변화), `RECOMMEND`(추천), `HANDLE`(적용·넘김), `INTERVAL_END`(구간 종료), `COMM`(통신), `SETTING`(설정 변경).
- 통합 이벤트 로그의 종류 칸과 필터가 이 값을 쓴다.
- 백엔드는 화면용 코드 `category`(`STAGE`·`RECOMMEND`·`ACTION`·`WINDOW`·`SETTING`·`COMM`, 2026-11-19 추가)도 함께 준다. 프론트는 쓰지 않는다(`eventType`과 뜻이 같음).
- `level`은 `CRITICAL` / `WARNING` / `NORMAL` / `INFO` 4가지이고 화면은 모두 그린다.

### 1-4. 시각과 "오늘"

- 화면의 "지금"과 "오늘"은 `site-powers/latest`의 `recordedAt`에서 구한다.
- `recordedAt`은 **마지막 1분 기록(`site_power_minute`)의 시각**이다(2026-11-19 백엔드 변경, 그 전에는 서버의 지금 시각). 화면의 숫자와 시각이 같은 순간의 값이 된다.
- 시뮬레이터가 멈추면 `recordedAt`도 마지막 기록에 멈춘다. 그러면 왼쪽 메뉴 시계와 분석 화면의 "오늘"이 그 날짜로 보인다.
- 15분 구간(`windowStart`·`windowEnd`·`remainingSeconds`)은 서버의 지금 시각 기준이다. 시뮬레이터가 돌면 둘의 차이는 1분 안쪽이다.
- 백엔드는 배속이 없어 `simulationSpeed`가 1이고, 왼쪽 메뉴의 "N배속" 표시는 숨겨진다.
- 예전에는 가짜 백엔드가 10배속이라 실제 API 날짜와 어긋났다(#25-1, 해결).

### 1-5. 값이 없을 때 (`null`)

| 응답 | 백엔드가 `null`을 주는 경우 | 화면 |
|---|---|---|
| `site-powers/latest`의 `weather` | 날씨 기록이 없을 때 (2026년 날씨 데이터 없음, #24-3) | 왼쪽 메뉴의 날씨 칸을 숨김 |
| `recommendations/latest` | 추천이 없을 때 (204) | `api()`가 `null` → "아직 받은 추천이 없습니다" |
| 분석·성적표의 `targetPeakKw` 등 | 설정 값을 못 찾을 때 | 목표선, 목표 초과/달성, 히트맵 색 단계를 그리지 않음 |
| 성적표 `dailyPeaks`·`heatmap` | 기록이 없는 달 (`hasData: false`) | "기록 없음" |
| 피크 분석 `summary`·`topWindows` 등 | `hasData: false` | "이 기간에는 기록이 없습니다" |

### 1-6. `defenseKw` (추가 방전)

- 백엔드 계산: `(predictedKw − targetPeakKw) × 15 ÷ 남은 분` (예상이 목표 이하면 0, `SitePowerService.java`).
- "남은 시간 동안 더 방전해야 하는 kW"라서 화면의 **"추가 방전"과 뜻이 같다.** 예전 문서의 "명세 뜻(`예상 − 목표`)에 맞출지" 결정은 필요 없어졌다.

### 1-7. 환경 설정

- 키는 명세 형식(`peak.target_kw`, `operation.auto_mode` 등)이고 값은 문자열이다. DB 키가 대문자였던 문제는 #17·#18로 해결됐다.
- 백엔드가 저장을 허용하는 키는 화면에서 고칠 수 있는 항목과 같다: `peak.target_kw`, `peak.warn_ratio`, `ess.soc_low`, `ess.soc_high`, `ess.room_temp_limit`, `operation.auto_mode`, `operation.poll_seconds`, `operation.decision_valid_minutes`(1~60), 설비 임계치 `equipment.{설비 id}.max_kw`.
- 배터리 용량(`ess.capacity_kwh`)과 최대 출력(`ess.max_power_kw`)은 백엔드에서도 보기 전용이다.
- 설정은 공용 DB 값이라 화면에서 저장하면 팀원 모두에게 바로 바뀐다.

### 1-8. 공통

- **기간 제한:** 기간 조회 API는 8, 10번이 최대 121일(동절기를 한 번에 보려고 2026-11-20에 늘림), 피크 분석이 최대 62일이다. 화면이 요청 전에 막고 "기간은 최대 N일까지 조회할 수 있습니다."를 보여 준다.
- **오류 응답:** `{ "status", "message" }`. 화면은 `message`를 그대로 보여 준다.
- **폴링:** 갱신 주기는 설정 `operation.poll_seconds`(공용 DB 값)를 쓴다. 숫자가 아니거나 1보다 작으면 5초를 쓰고, 앞 요청이 끝나기 전에는 다음 요청을 건너뛴다.
- **[적용]·[넘기기]:** 공용 DB에 바로 기록되고, 적용하면 시뮬레이터에 ESS 명령이 내려간다.

---

## 2. 화면이 안 쓰는 응답 필드 (참고)

받아도 문제는 없고, 백엔드 일을 줄이려면 나중으로 미뤄도 되는 것들이다.

| API | 필드 |
|---|---|
| `site-powers/latest` | `sohPercent`, `todayPeakKw`, `monthPeakKw`, `todayEnergy.*`, `equipments[].origin` |
| `recommendations` | `durationMinutes`, `reasonType`(화면은 `ruleLabel`만 씀) |
| `events` | `recommendationId` |
| `site-powers/latest` 설비 | `ratedKw` |

---

## 3. 남은 차이와 백엔드에 요청한 것

| 항목 | 상태 | 프론트 영향 |
|---|---|---|
| `site-powers/latest`의 설비에 구역 추가 (#29) | 반영됨 (`zoneId`, 11-19) | 임시 코드 지움 |
| 2026년 날씨 데이터 없음 (#24-3) | 답변 대기 | 날씨 칸·온도 그래프 바깥 기온이 비어 있음 (화면은 오류 없이 숨김) |
| 공용 DB에 한전 구매전력 음수 데이터 (#24-6) | 답변 대기 | 추이 분석 "눈에 띄는 구간"에 음수가 보일 수 있음 |
| 팀 결정 4건: 추천 저장 빈도, 최신값 출처, 판단 방식(method) 칸 (#27) | 결정 대기 | method 칸이 생기면 추천 이력·ESS 카드에 "규칙 / 모델" 표시 |
| 월간 성적표 엑셀 저장 (예: `GET /api/reports/{month}/excel`) | 계획 | 백엔드가 .xlsx를 만들어 주면 버튼만 연결 (프론트 라이브러리 추가 없음) |
| 피크 분석 "시간대별 위험 · 피크 원인 구역" | 시안 단계 | 정해지면 피크 분석 응답에 필드 추가 요청 |

---

## 4. 프론트 할 일

- [x] 모든 화면을 실제 API에 연결 (8번 11-17 / 9·10·11~13·피크 분석 11-18 / 1~5·7 11-19)
- [x] 구역 코드 `Z6`, 이벤트 `eventType`, 날씨 `null`, 기간 62일 제한 맞춤
- [ ] 시뮬레이터가 도는 상태에서 실데이터 확인: 예상 피크 15분 흐름 그래프(`windowPoints`), 오늘 그래프, 이벤트 로그, **[적용]·[넘기기]**
- [x] #29 반영: 설비 구역을 `equipments[].zoneId`로 바꾸고 임시 코드(`GET /api/equipment`) 지움
- [ ] 데이터가 멈췄을 때 경고: `recordedAt`이 서버 지금 시각(`windowEnd − remainingSeconds`)보다 몇 분 이상 늦으면 화면 위에 알림 (제안 단계)
- [ ] 안 쓰게 된 가짜 데이터 파일(`fakeBackend.js`, `fakeRecommendation.js`, `fakeEnergyData.json`, `fakeLoadForecast.json`) 정리 — 팀원 작업이 들어 있어 지우기 전에 팀에 확인
