# API 명세 v0.2와 프론트 화면 차이 정리

- **비교 기준:**
  - 명세: 팀 구글 시트 "(api 명세서) 2조 에너지솔루션" v0.2. "확인 필요" 시트의 결정(2026-11-17 기준)까지 반영했다.
  - 프론트: `dev` 브랜치 (`c939a53`, 2026-11-17)
- **방향: 프론트가 명세에 맞춘다.** 명세를 바꿔 달라는 요청은 화면이 꼭 필요한 것만 적었다.
- 명세는 예전 브랜치(`feat/monitoring-menu`)의 가짜 데이터를 보고 썼다. 그래서 그 뒤에 바뀐 화면(피크 분석, 환경 설정 등)과 차이가 생겼다.
- 예전에 프론트가 따로 쓴 `docs/api-spec.md`는 v0.1 주소(`battery-statuses` 등)를 쓴다. **이 문서와 팀 명세가 기준**이다.

---

## 0. 한눈에 보기

| 번호 | API (v0.2) | 결과 | 할 일 |
|---|---|---|---|
| 1 | `GET /api/site-powers/latest` | 거의 맞음 | `defenseKw` 뜻 맞추기 (프론트). 화면이 안 쓰는 필드는 빼도 됨 |
| 2 | `GET /api/recommendations/latest` | 맞음 | 판단 근거(`usedValues`)가 없어도 화면이 동작하게 (프론트) |
| 3 | `PATCH /api/recommendations/{id}` | 맞음 | 주소만 바꿈 |
| 4 | `GET /api/events?limit=` | 맞음 | 화면은 `limit=10`으로 부름. INFO 단계 결정 필요 |
| 5 | `GET /api/site-powers/today` | 맞음 | 주소만 바꿈 |
| 6 | `GET /api/site-powers/recent` | **화면에서 안 씀** | 명세에서 빼도 됨 |
| 7 | `GET /api/zones/{zoneName}` | 맞음 | 주소만 바꿈 (이미 `Z2`처럼 이름으로 부름) |
| 8 | `GET /api/recommendations?from=&to=` | 맞음 | 주소만 바꿈 |
| 9 | `GET /api/reports?month=` | 맞음 | 작년 같은 달도 같은 API로 한 번 더 부름 (명세 변경 없음) |
| 10 | `GET /api/analyses/trend` | 맞음 | 없음 |
| 11~13 | `GET·PUT /api/settings`, `GET /api/setting-histories` | 프론트가 바꿈 | 키 이름·문자열 값·묶음 저장·기록 필드를 명세대로. 명세의 "화면 없음"은 정정 필요 (화면 있음) |
| 16 | `GET /api/demand-intervals` | 피크 분석이 씀 | 피크 분석의 "피크가 높았던 구간"을 이 API로 바꿈 (프론트) |
| **없음** | 피크 분석 요약·일별·히트맵 | **명세에 없음** | **명세에 1개 추가 요청** (아래 2-9) |
| 14, 15, 17, 18, 19 | 에어컨, 이벤트 조회, 시연, MQTT, 요금표 | 화면 없음 | 지금 할 일 없음 |

---

## 1. 주소: 프론트가 v0.2 주소로 바꾼다

함수 이름은 그대로 두고, 각 `src/api/*Api.js` 안의 주소만 바꾼다(명세 "확인 필요" 13번).

| 파일 | 프론트 함수 | v0.2 주소 |
|---|---|---|
| `batteryStatusApi.js` | `getLatestBatteryStatus()` | `GET /api/site-powers/latest` |
| | `getTodayBatteryStatuses()` | `GET /api/site-powers/today` |
| | `getRecentBatteryStatuses()` | `GET /api/site-powers/recent` → **화면에서 안 써서 지움** |
| | `getZoneRecent(zoneName)` | `GET /api/zones/{zoneName}` (예: `Z2`) |
| `batteryDecisionApi.js` | `getLatestBatteryDecision()` | `GET /api/recommendations/latest` (없으면 204 → `null`) |
| | `getBatteryDecisions(from, to)` | `GET /api/recommendations?from=&to=` |
| | `updateBatteryDecision(id, status)` | `PATCH /api/recommendations/{id}` 본문 `{ "status": "APPLIED" \| "IGNORED" }` |
| `eventApi.js` | `getRecentEvents(10)` | `GET /api/events?limit=10` |
| `reportApi.js` | `getReport(month)` | `GET /api/reports?month=2026-11` |
| `analysisApi.js` | `getTrendAnalysis(from, to, interval)` | `GET /api/analyses/trend?from=&to=&interval=` |
| | `getPeakAnalysis(from, to)` | **명세에 없음** → 2-9 참고 |
| | (새로) `getDemandIntervals(from, to, limit)` | `GET /api/demand-intervals?from=&to=&limit=8` |
| `settingsApi.js` | `getEnergySettings()` → `getSettings()` | `GET /api/settings` |
| | `updateEnergySetting(key, value)` → `updateSettings(changes)` | `PUT /api/settings` 본문 `[{ key, value }]` (한 번에 여러 개) |
| | `getEnergySettingHistory()` → `getSettingHistories(from, to)` | `GET /api/setting-histories?from=&to=` |

공통으로 맞는 것:
- **시각:** `"2026-11-16T14:05:00"`(LocalDateTime)
- **날짜 파라미터:** `2026-11-14`
- **내용 없음(204):** `api()`가 `null`로 받는다(`client.js`).
- **오류:** 명세에는 `{ "message" }`로 적혀 있지만, 실제 공통 처리기는 `{ "status", "message" }`를 보낸다. 프론트는 `message`만 읽으므로 그대로 동작한다. 명세 표기만 고치면 된다.

---

## 2. API별 차이

### 2-1. `GET /api/site-powers/latest` (지금 상태)

**화면이 쓰는 필드 (명세에 모두 있음):**
- 기본 값: `recordedAt`, `loadKw`, `solarKw`, `chargeKw`, `dischargeKw`, `gridKw`, `socPercent`, `batteryTempC`, `pcsStatus`, `weather.text`, `weather.tempC`, `simulationSpeed`
- 15분 피크: `windowStart`, `windowEnd`, `remainingSeconds`, `predictedKw`, `targetPeakKw`, `warnKw`, `peakStatus`, `defenseKw`, `windowPoints[]`(예상 피크 카드의 15분 흐름 그래프, 2026-11-18부터)
- 요금 시간대: `tariff.label`, `tariff.until`, `tariff.nextLabel`, `tariff.periods[]`
- 공급원 비율: `sourceRatio.gridPercent`, `sourceRatio.solarPercent`, `sourceRatio.essPercent`
- 설비: `equipments[].id`, `equipments[].name`, `equipments[].loadKw`, `equipments[].state`

**뜻이 다른 필드: `defenseKw` → 프론트가 명세 뜻에 맞춘다**

| | 뜻 | 1,830 kW 예상, 목표 1,800일 때 |
|---|---|---|
| 명세 | 더 줄여야 하는 kW = `predictedKw − targetPeakKw` (음수면 0) | 30 |
| 지금 화면 | "추가 방전": 남은 시간 동안 더 방전해야 하는 kW | 126 |

- 프론트 할 일: 15분 피크 카드의 칸 이름을 **"목표 초과 예상"**으로 바꾸고, 가짜 백엔드도 명세 계산식으로 바꾼다. 용어표의 "추가 방전"도 같이 고친다.
- 다른 방법: "추가 방전" 뜻을 꼭 남기고 싶다면 명세를 바꿔 달라고 해야 한다. 그러면 판단 엔진이 남은 시간을 넣어 계산해야 한다. **명세에 맞추는 쪽을 추천한다.**

**명세에 있지만 지금 화면이 안 쓰는 필드 (백엔드 일을 줄이려면 나중으로 미루거나 빼도 됨):**

| 필드 | 메모 |
|---|---|
| `sohPercent` | 팀 결정 "배터리 건강도 X"(확인 필요 11번). 명세에서 빼기 |
| `todayPeakKw`, `monthPeakKw` | 통합 화면에서 뺀 지 오래됨. 성적표는 9번 `summary.monthPeakKw`를 씀 |
| `todayEnergy.*` | 쓰는 화면 없음 |
| `equipments[].origin` | 실측·시뮬레이션 출처 표시는 보류 중(프론트 문서 6장) |

**설비 상태:** 명세는 `RUNNING` / `HEATING`이다. 화면은 `STOPPED`(정지)도 그릴 수 있다. 정지를 보낼 일이 있으면 값 목록에 `STOPPED`를 더한다. 없으면 그대로 두면 된다.

**가짜 백엔드에만 있고 화면도 안 쓰는 필드:** `defenseLineKw`, `yearPeakKw`, `yearPeakAt`, `tariff.season`, `tariff.seasonLabel`. 명세에 넣을 필요 없이 가짜 백엔드에서 정리한다.

### 2-2. 추천 (`GET /api/recommendations/latest`, `PATCH /{id}`, `GET ?from=&to=`)

**맞는 것:**
- 처리 상태 `RECOMMENDED` / `APPLIED` / `IGNORED` / `AUTO`
- 결과 `DEFENDED` / `EXCEEDED`
- 행동 `CHARGE` / `DISCHARGE` / `IDLE`
- PATCH 본문, "이미 지난 추천입니다…" 400 문구, 추천이 없을 때 204

**화면이 쓰는 필드:** `id`, `decidedAt`, `windowStart`, `windowEnd`, `action`, `powerKw`, `predictedKw`, `reason`, `usedValues`, `status`, `appliedBy`, `appliedAt`, `resultPeakKw`, `resultStatus`, `ruleLabel`

**보류된 결정에 맞춘 프론트 처리:**

| 항목 | 명세 상태 | 프론트 |
|---|---|---|
| `ruleNo` → `reasonType` + `ruleLabel` (확인 필요 4번) | 보류 | 화면은 `ruleLabel`만 쓴다. `ruleNo`나 `reasonType`이 어떻게 정해져도 화면은 안 바뀐다 |
| `usedValues[]` (확인 필요 5번) | 보류 ("안 더하면 화면에서 빼야 함") | 없거나 빈 배열이면 판단 근거 영역을 숨긴다. 통합 추천 카드는 이미 숨기고, **추천 이력 펼친 줄의 "판단 근거" 제목은 숨기도록 고친다** |
| `durationMinutes` (확인 필요 5번) | 보류 | 화면이 안 쓴다 → 명세에서 빼도 됨 |
| 대기(IDLE) 추천 저장 (확인 필요 15번) | 미정 | 최신 1건(2번)에 대기가 오면 카드에 "대기"로 보인다. 이력(8번)에는 없어도 된다 |

**프론트가 맞출 것:**
- `appliedBy`: 명세는 자동일 때 `"자동 모드"`다. 화면에는 실제 제어가 아니라는 뜻으로 **"자동 모드 (시뮬레이션)"**으로 붙여 보여 준다(프론트 규칙).

**명세에 고쳐 달라고 할 것 (예시 값만):**
- `ruleLabel` 예시가 옛 규칙("저요금 충전", "고요금 방전")이다. 지금 판단 규칙 이름은 **안전 제한 / 피크 방어 / 계획 충전 / 계획 방전 / 조치 없음**이다.
- `usedValues[].label` 예시 "예상 평균"은 프론트 용어표에 맞춰 **"예상 피크"**로 바꾼다.

### 2-3. `GET /api/events?limit=` (최근 이벤트)

- 화면이 쓰는 필드: `id`, `occurredAt`, `level`, `message`. `eventType`과 `recommendationId`는 안 쓰지만 있어도 괜찮다.
- 개수: 명세 기본값은 6이지만 화면은 10건을 보여 준다. 프론트가 `?limit=10`으로 부르므로 명세는 그대로 둬도 된다.
- `level`의 `INFO`(확인 필요 7번, 미정): 화면은 `CRITICAL` / `WARNING` / `NORMAL` / `INFO` 4가지를 다 그린다. 추천 발생, 적용·넘김은 지금 `INFO`로 보인다. INFO를 안 넣기로 하면 그 이벤트들을 `NORMAL`로 보내면 된다(화면 변경 없음).

### 2-4. `GET /api/site-powers/today` (오늘 15분 목록)

`recordedAt`, `loadKw`, `solarKw`, `gridKw`, `socPercent`이 명세와 같다. 화면은 이 중 공장 소비·태양광·한전 구매를 그래프 두 개로 그린다.

### 2-5. `GET /api/site-powers/recent` (최근 1시간)

통합 화면의 "실시간 전력 추세"를 뺀 뒤로 **쓰는 화면이 없다.** 명세에서 빼도 된다. 프론트는 Store의 `fetchRecent`, `recentStatuses`와 API 함수를 지운다.

### 2-6. `GET /api/zones/{zoneName}` (구역 상세)

`zoneId`(값은 이름), `points[].recordedAt`, `points[].loadKw`, `todayMaxKw`, `todayAvgKw`, `todayKwh`이 명세와 같다. 화면은 이미 `Z2`처럼 이름으로 부른다.

### 2-7. `GET /api/reports?month=` (월간 성적표)

화면이 쓰는 필드가 모두 명세에 있다.
- `month`, `hasData`, `periodStart`, `periodEnd`, `targetPeakKw`
- `summary.*`, `dailyPeaks[]`, `heatmap[]`
- `bill.*`(`lastMonthTotal`, `byTariff[]` 포함)
- `exceeded[]`(`windowStart`, `windowEnd`, `resultPeakKw`, `overKw`, `powerKw`, `status`, `cause`)
- `ess.*`, `solar.*`

**작년 같은 달 비교:** 같은 API를 `month=2025-11`처럼 1년 전 달로 한 번 더 불러 `bill.total`을 쓴다. 명세 변경은 없다. 백엔드는 1년 전 달도 같은 방법으로 계산만 해 주면 된다. 기록이 없으면 `hasData: false`이고, 화면은 "기록 없음"으로 보인다.

### 2-8. `GET /api/analyses/trend` (추이 분석)

화면이 쓰는 필드가 모두 명세에 있다: `from`, `to`, `interval`, 기준선 4개, `summary.*`, `points[]`, `weather[]`, `hourly[]`, `byStatus[]`, `highlights[]`.

화면의 추천 표시는 같은 기간의 8번(추천 이력)을 한 번 더 불러 그린다. 명세 변경은 없다.

### 2-9. 피크 분석: 명세에 없음 → 1개 추가 요청

피크 분석 화면(`/analysis/peak`)은 명세를 쓴 뒤에 생겨서 명세에 없다. 명세에 맞추려고 **이미 있는 16번을 최대한 재사용**하고, 나머지만 새 API로 요청한다.

**(1) "피크가 높았던 구간" 표 → 16번 `GET /api/demand-intervals?from=&to=&limit=8`을 그대로 쓴다 (프론트가 맞춤)**
- 쓰는 필드: `windowStart`, `windowEnd`, `peakKw`, `action`, `status`
- 지금 가짜 데이터의 `note`(예: "추천을 넘김")는 받지 않는다. 화면이 `action`과 `status`로 문구를 만든다.
  - IGNORED → "추천을 넘김"
  - APPLIED → "방전 적용"
  - AUTO → "자동 방전 (시뮬레이션)"
  - 추천 없음 → "방전 추천 없음"

**(2) 요약 4개 · 일별 피크 · 히트맵 → 새 API 요청: `GET /api/analyses/peak?from=&to=`**

응답 모양 제안 (`dailyPeaks`·`heatmap`은 9번 성적표와 같은 모양):

| 필드 | 타입 | 예시 | 설명 / DB 출처 |
|---|---|---|---|
| `from`, `to` | string | 2026-11-01 | 요청값 |
| `hasData` | boolean | true | 기간에 기록이 없으면 false |
| `targetPeakKw` | number | 1800 | setting `peak.target_kw` |
| `summary.maxPeakKw` / `maxPeakAt` | number / datetime | 1841 / 2026-11-02T08:30:00 | MAX(`demand_interval_15m.actual_avg_kw`)와 그 `interval_start` |
| `summary.exceededCount` | number | 3 | 목표를 넘은 끝난 구간 수 |
| `summary.windowCount` | number | 1597 | 기간 안의 끝난 15분 구간 수 (화면은 둘을 나눠 % 표시) |
| `summary.avgReductionKw` / `maxReductionKw` | number | 126 / 306 | 날짜별 최대 피크 기준 AVG / MAX(`reduced_kw`) |
| `summary.busyFrom` / `busyTo` / `busyPeriodLabel` | string | 17:00 / 20:00 / 최대부하 | 평균 피크가 가장 높은 요금 시간대 묶음 |
| `dailyPeaks[]` | array | `{ date, peakKw, expectedKw }` | 9번과 같음 |
| `heatmap[]` | array | `{ date, hour, peakKw }` | 9번과 같음 |

- 최대 기간은 다른 분석 API처럼 62일이다. `from`이 `to`보다 늦으면 400이다.
- 검토에서 나온 이름 문제: "피크가 잦은 시간"인데 실제로는 평균이 가장 높은 시간대입니다. 그래서 화면 이름을 "평균 피크가 가장 높은 시간대"로 바꾸거나, 백엔드가 목표 초과가 가장 많은 시각을 주는 방안을 같이 정한다.

### 2-10. 환경 설정 (`GET·PUT /api/settings`, `GET /api/setting-histories`)

명세에는 "화면 없음"이라고 되어 있지만 **환경 설정 화면이 있다** → 상태 정정 요청. 화면은 명세 모양에 맞춘다.

**프론트가 바꿀 것:**

| 항목 | 지금 화면 | 명세 (프론트가 맞춤) |
|---|---|---|
| 키 이름 | `targetPeakKw` 같은 camelCase | `peak.target_kw` 같은 점 이름 (아래 표) |
| 값 | 숫자 | **문자열**. 화면에서 숫자로 바꿔 쓰고, 저장할 때 문자열로 보냄 |
| 저장 | 항목마다 `PUT /{key}` | 묶음의 바뀐 값을 **`PUT /api/settings` 한 번**에 `[{ key, value }]`로. 화면의 "묶음마다 저장" 버튼과 잘 맞음 |
| 받는 필드 | `key`, `value`, `updatedAt`, `updatedBy` | 여기에 `group`, `label`, `unit`이 더해짐. 화면은 그대로 써도 되고 서버 이름을 그대로 보여 줘도 됨 |
| 변경 기록 | 개수 20건, `before` / `after` | 기간 `?from=&to=`(서버 날짜 기준 최근 30일), **`oldValue` / `newValue` / `label`** |
| 다른 화면에서 읽는 이름 | `settingsStore.values.maxPowerKw` 등 | `settingsStore.values['ess.max_power_kw']` 등. 프론트 규칙상 이름을 바꾸는 변환 코드를 두지 않고 명세 키를 그대로 씀 |

**키 대응표:**

| 지금 화면 키 | 명세 키 | 명세의 "화면에서 수정" | 프론트 |
|---|---|---|---|
| `targetPeakKw` | `peak.target_kw` | 가능 | 그대로 입력 |
| `warnPercent` | `peak.warn_ratio` | 가능 | 그대로 입력 |
| `capacityKwh` | `ess.capacity_kwh` | **보기만** | 입력칸 → **읽기 전용** |
| `maxPowerKw` | `ess.max_power_kw` | **보기만** | 입력칸 → **읽기 전용** |
| `socLowPercent` | `ess.soc_low` | 가능 | 그대로 입력 |
| `socHighPercent` | `ess.soc_high` | 가능 | 그대로 입력 |
| `roomTempLimitC` | `ess.room_temp_limit` | 가능 | 그대로 입력 |
| `operationMode` (`MANUAL`/`AUTO`) | `operation.auto_mode` (`"false"`/`"true"`) | 가능 | 값만 바꿔 읽고 씀 |
| `pollSeconds` | **없음** | - | 아래 "결정 필요" |
| `decisionValidMinutes` | **없음** | - | 아래 "결정 필요" |
| (화면에 없음) | `solar.capacity_kw`, `tariff.base_charge`, `carbon.emission_factor` | 보기만 | 아래 "결정 필요" |
| (화면에 없음) | `ess.soh_percent` | 보기만 | 팀 결정 "배터리 건강도 X" → 명세에서 빼기 |

**결정 필요 (명세에 맞추는 안을 먼저 적음):**

1. **`pollSeconds`(화면 갱신 주기):** 명세에 키가 없다. 멘토 피드백도 "페이지마다 바꾸게"였다. → **설정 화면에서 빼고**, 화면별 주기는 브라우저에 저장한다(명세 변경 없음).
2. **`decisionValidMinutes`(추천 유효 시간):** 명세에 키가 없고 지금 계산에도 안 쓰인다. → **설정 화면에서 뺀다.** 추천 만료 규칙이 정해지면 그때 키를 요청한다.
3. **보기만 값(태양광 용량, 기본요금 단가, 탄소 배출계수):** 지금 화면에는 없다(단가는 2026-11-17에 뺐다). → 보여 줄지 정한다. 보여 준다면 "참고 값(수정 불가)"으로 읽기 전용 줄을 둔다. 받는 API는 11번 그대로라 명세 변경은 없다.

---

## 3. "확인 필요" 시트 결정이 프론트에 주는 영향

| 번호 | 항목 | 시트 상태 | 프론트 영향 |
|---|---|---|---|
| 2 | 시각 칸 타입 | 수정완료 | 없음 (응답은 LocalDateTime 문자열) |
| 3 | 구역 주소 이름 | 조치완료 (Z1~Z5, U) | 없음 (이미 이름으로 부름) |
| 4 | 규칙 번호 → `reasonType` | 보류 | 없음 (`ruleLabel`만 씀) |
| 5 | `durationMinutes`, `usedValues` | 보류 | 없을 때 판단 근거 숨김 (추천 이력 펼친 줄 고침) |
| 6 | 영어 코드 저장 | 영어로 합의 | 없음 (화면은 이미 코드로 비교) |
| 7 | 이벤트 INFO | 미정 | 정해지면 그대로 (화면은 4가지 다 그림) |
| 8 | 5초 화면 vs 1분 기록 | 현업자와 상의 | 1분 기록이면 화면 숫자가 1분 동안 같음. 화면 변경은 없음 |
| 9 | 설비 상태 칸 | 수정완료 (가동 칼럼 추가) | `RUNNING` / `HEATING`(필요하면 `STOPPED`) 그대로 표시 |
| 10 | 에어컨 | 검토 후 결정 | 화면 없음. 정해지면 화면 시안부터 |
| 11 | 고정값 | SOH X, 요금 단가는 요금표, 탄소는 백엔드 계산 | SOH 필드 정리, 단가 입력칸은 이미 뺌 |
| 14 | 요금 단가 실제 값 | - | 없음 (금액은 백엔드가 계산) |
| 15 | 대기 추천 이력 | - | 없음 (최신 1건만 대기를 보여 줘도 됨) |
| 16 | `simulationSpeed` | - | 백엔드가 1로 보내거나 빼면 왼쪽 메뉴의 "N배속"이 안 보임 (정상) |

---

## 4. 백엔드에 요청할 것 (명세 수정 목록)

꼭 필요한 것부터 적었다.

1. **피크 분석 API 추가:** `GET /api/analyses/peak?from=&to=` (2-9의 응답 모양)
2. **환경 설정 상태 정정:** 11~13번 "화면 없음" → 화면 있음(환경 설정 > 기준값). 우선순위를 "나중"보다 올리기
3. **예시 값 갱신:** `ruleLabel`(안전 제한 / 피크 방어 / 계획 충전 / 계획 방전 / 조치 없음), `usedValues[].label`의 "예상 평균" → "예상 피크"
4. **빼도 되는 것 표시:** 6번 API, `sohPercent`, `todayPeakKw`, `monthPeakKw`, `todayEnergy.*`, `durationMinutes`, `equipments[].origin` (화면이 안 씀)
5. **표기 정정:** 오류 응답 `{ "status", "message" }`
6. **(선택)** 설비 상태에 `STOPPED`를 보낼 일이 있으면 값 목록에 추가

---

## 5. 프론트 할 일 (순서)

백엔드의 작업 순서("역할 나누기" 시트: 2단계 API 1·4·5·6 → 3단계 2·3·7·8 → 4단계 9·10)에 맞춰, 먼저 열리는 API부터 바로 붙일 수 있게 준비한다.

- [x] **1. 주소 정리:** `src/api/*Api.js` 주석의 교체 예시를 v0.2 주소로 바꾼다. 백엔드가 열면 가짜 함수 호출 한 줄만 `api(...)`로 바꾸면 되게 한다
- [x] **2. 안 쓰는 것 지우기:** `getRecentBatteryStatuses`, Store의 `fetchRecent` / `recentStatuses`
- [ ] **3. `defenseKw`를 명세 뜻으로:** 칸 이름 "목표 초과 예상"으로 바꾸고, 가짜 백엔드 계산식과 용어표도 고친다 — **팀 결정 대기**
- [x] **4. 판단 근거 없을 때:** 추천 이력 펼친 줄의 "판단 근거" 제목 숨김
- [x] **5. 처리자 표시:** `appliedBy`가 "자동 모드"면 "자동 모드 (시뮬레이션)"으로 표시
- [x] **6. 환경 설정을 명세 모양으로:** 키 이름, 문자열 값, 묶음 PUT, 기록 `oldValue` / `newValue` / `label`, 용량·최대 출력 읽기 전용. 갱신 주기·유효 시간은 결정 후 처리 (지금은 명세에 없는 키 `operation.poll_seconds`, `operation.decision_valid_minutes`로 임시 유지)
- [x] **7. 피크 분석:** "피크가 높았던 구간"을 16번 모양으로 바꾸고, 문구를 `action` + `status`로 만들기
- [x] **8. 가짜 백엔드를 명세 모양으로:** 위 1~7과 같은 필드 이름·값으로 맞춘다. 화면이 안 쓰는 가짜 필드(`defenseLineKw`, `yearPeakKw` 등)는 정리한다 (`defenseKw`는 3번 결정 후)
- [ ] **9. 연결:** Swagger에 API가 보이는 대로 하나씩 실제 호출로 바꾸고, 정상·값 없음·연결 끊김을 확인한다
