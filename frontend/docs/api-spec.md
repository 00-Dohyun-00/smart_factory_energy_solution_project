# WATTever API 명세 (프론트엔드 기준)

프론트엔드 화면이 백엔드에 기대하는 **주소, 요청, 응답 모양**을 정리한 문서입니다.

- 지금은 백엔드 API가 아직 없어서, 프론트는 가짜 백엔드(`frontend/src/api/fakeBackend.js`)가 돌려주는 값으로 동작합니다.
- 이 문서의 응답 모양은 **가짜 백엔드가 실제로 돌려주는 모양과 같습니다.** 백엔드가 이대로 만들면 프론트는 `src/api/*Api.js`의 한 줄만 바꾸면 연결됩니다 ([9. 프론트 연결 방법](#9-프론트-연결-방법)).
- 표의 **상태** 칸에서 "제안"은 주소나 필드를 팀이 아직 확정하지 않았다는 뜻입니다. 바꿔도 되고, 바꾸면 이 문서와 `src/api/` 폴더만 고치면 됩니다.
- 추천·피크 계산 방법은 [ESS 추천 로직 설명](ess-recommendation.md)에 있습니다.

---

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| 기본 주소 | `/api` (개발 중에는 Vite가 `http://localhost:8080`으로 넘겨 줌) |
| 주소 모양 | `/api/{복수형 명사}` (주소에 동사를 넣지 않음) |
| 형식 | JSON, 필드 이름은 **camelCase** (Java `record` 필드 이름 그대로) |
| 시각 | `LocalDateTime` 글자 그대로: `"2026-11-16T14:07:32"` (시간대 표시 없음, 한국 시간) |
| 날짜 | `"2026-11-16"` / 달은 `"2026-11"` |
| 숫자 | 숫자 타입으로 보냄 (글자 아님). 단위는 필드 이름 끝에 붙임: `Kw`, `Kwh`, `Percent`, `C`(℃), `Won`(원) |
| 값 없음 | `null`. 화면은 `—`로 표시합니다. **0과 "값 없음"을 구분**해 주세요 (예: 아직 결과가 없는 피크는 0이 아니라 `null`) |
| 에러 | 공통 처리기 형식: `{ "status": 404, "message": "추천을 찾을 수 없습니다." }`. 화면은 `message`를 그대로 보여 줍니다 |
| 인증 | 세션(쿠키) 방식, 로그인 안 하면 401 (팀 결정, 아직 미구현) |
| 계산 | 피크 예측, 추천, 절감액, 탄소는 **백엔드가 계산**하고, 프론트는 표시만 합니다 (단위 변환·비율·합계 같은 표시용 계산만 프론트에서 함) |

### 화면 시각은 서버 값을 씁니다

시뮬레이터는 배속(예: 10배)으로 돌 수 있습니다. 그래서 화면의 "현재 시각", 15분 구간, 남은 시간은 모두 **응답의 시각 값**으로 계산합니다. 백엔드는 시뮬레이션 시각을 응답에 넣어 주세요 (`recordedAt`, `windowStart`, `remainingSeconds` 등).

---

## 2. API 목록

| # | 메서드 | 주소 | 쓰는 화면 | 부르는 때 | 상태 |
|---|---|---|---|---|---|
| 1 | GET | `/api/battery-statuses/latest` | 모든 화면 (왼쪽 아래 시각, 통합, 설비 모니터링) | **5초마다** (환경 설정 `pollSeconds`) | 제안 |
| 2 | GET | `/api/battery-statuses?from=&to=&interval=QUARTER` | 통합 › 오늘 전력 추이 | 화면 열 때, 15분 구간이 바뀔 때 | 제안 |
| 3 | GET | `/api/battery-statuses?from=&to=&interval=MINUTE` | (예비, 지금은 안 씀) | — | 제안 |
| 4 | GET | `/api/zone-statuses/{zoneId}` | 설비 모니터링 › 구역 상세 | 구역 고를 때, 15분 구간이 바뀔 때 | 제안 |
| 5 | GET | `/api/battery-decisions/latest` | 통합 › ESS 추천 | **5초마다** | 제안 |
| 6 | GET | `/api/battery-decisions?from=&to=` | 운영 로그 | 화면 열 때, 오늘이 기간에 들면 5초마다 | 제안 |
| 7 | PATCH | `/api/battery-decisions/{id}` | 통합 › [적용] [넘기기] | 버튼 누를 때 | 제안 |
| 8 | GET | `/api/events?limit=10` | 통합 › 이벤트 로그 | **5초마다** | 제안 |
| 9 | GET | `/api/reports?month=` | 보고서 | 화면 열 때, 달 바꿀 때 | 제안 |
| 10 | GET | `/api/analyses/trend?from=&to=&interval=` | 분석 › 추이 분석 | 조회할 때 | 제안 |
| 11 | GET | `/api/analyses/peak?from=&to=` | 분석 › 피크 분석 | 조회할 때 | 제안 |
| 12 | GET | `/api/energy-settings` | 환경 설정, 공통 틀(갱신 주기) | 화면 열 때 | 제안 |
| 13 | PUT | `/api/energy-settings/{key}` | 환경 설정 › 저장 | 저장 누를 때 (바뀐 항목마다 1번) | 제안 |
| 14 | GET | `/api/energy-settings/history?limit=20` | 환경 설정 › 변경 기록 | 화면 열 때, 저장한 뒤 | 제안 |

**5초마다 부르는 API는 1, 5, 8번 세 개**입니다. 이 셋은 가볍게 만들어 주세요.

---

## 3. 전력 상태

### 3-1. 최신 상태 1건 — `GET /api/battery-statuses/latest`

화면에서 가장 많이 쓰는 응답입니다. KPI 카드, 15분 피크 게이지, 요금 시간대, 전력 계통도, 설비 목록이 모두 이 한 건으로 그려집니다.

**요청**: 없음

**응답** (예시, 배열은 줄임)

```json
{
  "recordedAt": "2026-11-16T17:42:10",
  "loadKw": 1921.0,
  "solarKw": 85.4,
  "chargeKw": 0,
  "dischargeKw": 160,
  "gridKw": 1675.6,
  "socPercent": 86.7,
  "batteryTempC": 24.1,
  "pcsStatus": "DISCHARGING",
  "sohPercent": 98.2,
  "weather": { "text": "맑음", "tempC": 17 },
  "simulationSpeed": 10,

  "windowStart": "2026-11-16T17:30:00",
  "windowEnd": "2026-11-16T17:45:00",
  "remainingSeconds": 170,
  "predictedKw": 1702.3,
  "targetPeakKw": 1800,
  "warnKw": 1746,
  "peakStatus": "NORMAL",
  "defenseKw": 0,
  "defenseLineKw": 1800,
  "windowPoints": [
    { "minute": 0, "demandKw": 0 },
    { "minute": 0.83, "demandKw": 95 }
  ],

  "todayPeakKw": 1569,
  "monthPeakKw": 1780,
  "yearPeakKw": 1780,
  "yearPeakAt": "2026-01-05T20:30:00",

  "tariff": {
    "season": "WINTER",
    "seasonLabel": "겨울",
    "period": "PEAK",
    "label": "최대부하",
    "until": "20:00",
    "nextLabel": "중간부하",
    "periods": [
      { "from": "00:00", "to": "09:00", "period": "LIGHT", "label": "경부하" },
      { "from": "09:00", "to": "10:00", "period": "MID", "label": "중간부하" }
    ]
  },

  "sourceRatio": { "gridPercent": 87.2, "solarPercent": 4.4, "essPercent": 8.3 },

  "equipments": [
    { "id": "Z2-FURN", "name": "브레이징로", "loadKw": 712.4, "origin": "SIMULATED", "state": "HEATING" },
    { "id": "Z4-MOTOR1", "name": "1번 모터", "loadKw": 2.3, "origin": "MEASURED", "state": "RUNNING" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| recordedAt | 시각 | 이 값을 잰 시각 (시뮬레이션 시각) |
| loadKw | 숫자 | 공장 소비전력 |
| solarKw | 숫자 | 태양광 발전량 |
| chargeKw / dischargeKw | 숫자 | ESS 충전 / 방전 전력. 둘 중 하나만 0보다 큼 (동시 충·방전 없음) |
| gridKw | 숫자 | 한전 구매전력 = 공장 − 태양광 − 방전 + 충전 |
| socPercent | 숫자 | 배터리 잔량 (%) |
| batteryTempC | 숫자 | 배터리실 온도 (℃) |
| pcsStatus | 코드 | `CHARGING` / `DISCHARGING` / `STANDBY` |
| sohPercent | 숫자 | 배터리 건강 상태 (%) |
| weather | 객체 | `{ text: '맑음' \| '흐림' \| '비', tempC: 기온 }` |
| simulationSpeed | 숫자 | 시뮬레이션 배속 (1이면 실제 속도). 화면에 "10배속"처럼 표시 |
| windowStart / windowEnd | 시각 | 지금 15분 구간 (예: 17:30~17:45) |
| remainingSeconds | 숫자 | 구간이 끝날 때까지 남은 초 |
| predictedKw | 숫자 | 이번 구간이 끝날 때 예상되는 15분 평균 (지금 충·방전을 반영한 값) |
| targetPeakKw | 숫자 | 목표 피크 (환경 설정) |
| warnKw | 숫자 | 주의선 = 목표 × 주의선 % |
| peakStatus | 코드 | `NORMAL`(주의선 아래) / `WARNING`(주의선~목표) / `CRITICAL`(목표 초과 예상) |
| defenseKw | 숫자 | 방어 필요 전력: 방어선 안으로 들어오려면 **지금보다 더 방전해야 하는 kW** |
| defenseLineKw | 숫자 | 오늘 방어선. 보통 목표 피크와 같고, 목표를 다 지킬 수 없는 날만 더 높음 |
| windowPoints | 배열 | 구간 시작부터 지금까지 쌓인 평균의 변화 `{ minute: 구간 시작부터 지난 분, demandKw }` (게이지 그래프용) |
| todayPeakKw | 숫자 \| null | 오늘 끝난 구간 중 최고 (0시 첫 구간 중이면 null) |
| monthPeakKw / yearPeakKw | 숫자 | 이번 달 / 지난 12개월 최고 피크 |
| yearPeakAt | 시각 | 12개월 최고 피크가 나온 구간 시작 시각 |
| tariff | 객체 | 지금 요금 시간대. `until` = 이 시간대가 끝나는 시각, `periods` = 오늘의 시간대 구분 전체 (그래프 회색 띠용) |
| sourceRatio | 객체 | 공장 전체 기준 공급원 비율 (%). 설비별 비율도 이 값을 그대로 씀 |
| equipments | 배열 | 설비별 전력 (아래 표) |

`equipments` 한 줄:

| 필드 | 설명 |
|---|---|
| id | 설비 ID, 형식 `구역-코드` (예: `Z2-FURN`, 공용은 `U-COMP`). 구역은 `Z1`~`Z5`, `U` |
| name | 설비 이름 |
| loadKw | 설비 전력 |
| origin | 값의 출처: `MEASURED`(실측, 1번 모터) / `SIMULATED`(시뮬레이션) |
| state | `RUNNING` / `HEATING`(브레이징로 승온 중). 필드는 확인 필요 |

---

### 3-2. 기간 상태 목록 — `GET /api/battery-statuses?from=&to=&interval=`

| 쿼리 | 예 | 설명 |
|---|---|---|
| from, to | `2026-11-16T00:00:00`, `2026-11-16T17:45:00` | 기간 (to 포함) |
| interval | `QUARTER` / `MINUTE` | 15분 평균 / 1분 평균 |

**오늘 전력 추이** (`interval=QUARTER`, 오늘 0시부터 지금까지) 응답:

```json
[
  { "recordedAt": "2026-11-16T00:00:00", "loadKw": 1320, "solarKw": 0, "gridKw": 1570, "socPercent": 55.6 },
  { "recordedAt": "2026-11-16T00:15:00", "loadKw": 1318, "solarKw": 0, "gridKw": 1568, "socPercent": 61.2 }
]
```

- `recordedAt`은 15분 구간의 **시작** 시각, 값은 그 구간의 평균입니다.
- 시각 순서(오래된 것 먼저)로 보내 주세요.

**최근 1시간** (`interval=MINUTE`, 예비) 응답: `[{ "recordedAt": "...", "gridKw": 1702 }]` 61개 (1분 간격)

---

### 3-3. 구역 상세 — `GET /api/zone-statuses/{zoneId}`

설비 모니터링에서 구역을 누르면 오른쪽에 나오는 상세입니다. `zoneId` 예: `Z2`

```json
{
  "zoneId": "Z2",
  "points": [
    { "recordedAt": "2026-11-16T16:42:00", "loadKw": 812 },
    { "recordedAt": "2026-11-16T16:43:00", "loadKw": 795 }
  ],
  "todayMaxKw": 1043,
  "todayAvgKw": 702,
  "todayKwh": 12030
}
```

| 필드 | 설명 |
|---|---|
| points | 최근 1시간, 1분 간격 구역 전력 (61개, 오래된 것 먼저) |
| todayMaxKw / todayAvgKw | 오늘 0시부터 지금까지 구역 최대 / 평균 전력 |
| todayKwh | 오늘 구역 사용량 |

---

## 4. ESS 추천

### 4-1. 추천 한 건의 모양 (5·6·7번 공통)

```json
{
  "id": 42,
  "decidedAt": "2026-11-16T19:15:20",
  "windowStart": "2026-11-16T19:15:00",
  "windowEnd": "2026-11-16T20:00:00",
  "ruleNo": 5,
  "ruleLabel": "계획 방전",
  "action": "DISCHARGE",
  "powerKw": 160,
  "durationMinutes": 45,
  "predictedKw": 1517,
  "targetPeakKw": 1800,
  "reason": "하루 계획에 따라 20:00까지 방전합니다. 지금은 최대부하(106원/kWh)이라 배터리 전기를 쓰는 편이 싸고, 뒤에 올 피크에 쓸 잔량은 계획에 남겨 두었습니다.",
  "usedValues": [
    { "label": "예상 평균", "value": 1517, "unit": "kW" },
    { "label": "방어선", "value": 1800, "unit": "kW" },
    { "label": "SOC", "value": 90.0, "unit": "%" },
    { "label": "요금 시간대", "value": "최대부하", "unit": "" },
    { "label": "날씨", "value": "맑음", "unit": "" }
  ],
  "status": "RECOMMENDED",
  "appliedBy": null,
  "appliedAt": null,
  "resultPeakKw": null,
  "resultStatus": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| id | 숫자 | 추천 ID |
| decidedAt | 시각 | 추천을 만든 시각 |
| windowStart / windowEnd | 시각 | 이 추천이 유효한 기간. 피크 방어는 15분 구간 하나, 계획 충·방전은 계획에서 같은 동작이 이어지는 동안 |
| ruleNo / ruleLabel | 숫자 / 글자 | 추천 규칙 (아래 표). 운영 로그의 규칙 필터는 `ruleLabel` 목록으로 만듦 |
| action | 코드 | `CHARGE` / `DISCHARGE` / `IDLE` |
| powerKw | 숫자 | 추천 출력 (충전도 양수). 대기면 0 |
| durationMinutes | 숫자 \| null | 추천이 끝날 때까지 남은 분. 대기면 null |
| predictedKw | 숫자 | 추천할 때 본 이번 구간 예상 평균 (ESS 없이) |
| targetPeakKw | 숫자 | 그때의 목표 피크 |
| reason | 글자 | 화면에 그대로 보여 줄 이유 문장 |
| usedValues | 배열 | 판단 근거 `{ label, value, unit }`. 화면은 받은 순서대로 칸을 그림. 개수와 항목은 백엔드가 정해도 됨 |
| status | 코드 | `RECOMMENDED`(답 기다림) / `APPLIED`(운영자 적용) / `IGNORED`(넘김) / `AUTO`(자동 모드 적용, 시뮬레이션) |
| appliedBy / appliedAt | 글자 / 시각 | 적용·넘김한 사람과 시각. 자동 모드면 `"자동 모드 (시뮬레이션)"` |
| resultPeakKw | 숫자 \| null | 결과: 추천 기간 동안 끝난 구간 중 가장 높았던 15분 피크. 기간이 끝나기 전에는 null |
| resultStatus | 코드 \| null | `DEFENDED`(목표 이내) / `EXCEEDED`(목표 초과) / null |

규칙 번호:

| ruleNo | ruleLabel | action | 언제 |
|---|---|---|---|
| 1 | 안전 제한 | IDLE | 피크 방어가 필요한데 잔량 하한·온도 제한으로 못 할 때 |
| 2 | 피크 방어 | DISCHARGE | 이번 구간이 방어선을 넘을 것 같을 때 (넘는 만큼만 방전) |
| 3 | (비어 있음) | | 태양광 잉여 충전용으로 남겨 둠 |
| 4 | 계획 충전 | CHARGE | 하루 계획상 충전할 때 |
| 5 | 계획 방전 | DISCHARGE | 하루 계획상 방전할 때 |
| 6 | 조치 없음 | IDLE | 그 밖 |

> 지난 이력의 가짜 데이터에는 예전 이름인 "저요금 충전"(4), "고요금 방전"(5)이 섞여 있습니다. 백엔드는 위 표의 이름으로 통일해 주세요.

### 4-2. 최신 추천 — `GET /api/battery-decisions/latest`

- 응답: 추천 한 건 (4-1 모양). 아직 하나도 없으면 `null` (또는 204)
- 대기(`IDLE`) 추천도 최신 1건으로는 보내 주세요. 화면이 "대기"와 이유를 보여 줍니다.

### 4-3. 추천 이력 — `GET /api/battery-decisions?from=&to=`

| 쿼리 | 예 | 설명 |
|---|---|---|
| from, to | `2026-11-10`, `2026-11-16` | 날짜 기간 (양 끝 포함) |

- 응답: 추천 한 건의 배열, **최신 순**
- 대기(`IDLE`) 추천은 너무 많아지므로 이력에서는 빼도 됩니다 (가짜 백엔드는 "안전 제한"만 넣음).

### 4-4. 적용 / 넘기기 — `PATCH /api/battery-decisions/{id}`

**요청 본문**

```json
{ "status": "APPLIED" }
```

| status | 뜻 |
|---|---|
| `APPLIED` | 운영자가 적용 |
| `IGNORED` | 운영자가 넘김 |

- 응답: 바뀐 추천 한 건 (4-1 모양)
- 이미 지난 추천이면 400: `{ "status": 400, "message": "이미 지난 추천입니다. 최신 추천을 확인해 주세요." }`
- 적용자(`appliedBy`)는 로그인한 사용자로 채워 주세요 (로그인 전까지는 `"운영자"`).

---

## 5. 이벤트 — `GET /api/events?limit=10`

통합 화면 오른쪽 아래 "이벤트 로그"입니다.

```json
[
  { "id": 31, "occurredAt": "2026-11-16T19:15:20", "level": "INFO", "message": "계획 방전 160 kW 추천 (20:00까지)" },
  { "id": 30, "occurredAt": "2026-11-16T19:15:00", "level": "NORMAL", "message": "19:00–19:15 구간 1,690 kW로 종료" },
  { "id": 29, "occurredAt": "2026-11-16T18:31:10", "level": "CRITICAL", "message": "방어선 초과 예상, 방전 176 kW 추천" }
]
```

| 필드 | 설명 |
|---|---|
| level | `CRITICAL`(경고) / `WARNING`(주의) / `NORMAL`(정상) / `INFO`(그 밖의 기록) |
| message | 화면에 그대로 보여 줄 문장 |

- **최신 순**, 최대 `limit`개
- 이벤트로 남기는 일: 피크 단계 변화(주의 진입, 정상 복귀), 15분 구간 종료와 결과, 추천 생성, 적용·넘김, 자동 적용, 잔량 하한·상한 도달, 설정 변경

---

## 6. 보고서 — `GET /api/reports?month=2026-11`

월간 성적표입니다. 이번 달이면 오늘까지, 지난 달이면 한 달 전체를 보냅니다.

**아직 기록이 없는 달** (미래의 달 등):

```json
{ "month": "2026-12", "hasData": false, "targetPeakKw": 1800 }
```

**기록이 있는 달** (배열은 줄임):

```json
{
  "month": "2026-11",
  "hasData": true,
  "periodStart": "2026-11-01",
  "periodEnd": "2026-11-16",
  "targetPeakKw": 1800,
  "summary": {
    "defenseTotal": 36,
    "defenseSuccess": 33,
    "defenseSuccessPercent": 91.7,
    "monthPeakKw": 1841,
    "monthPeakAt": "2026-11-02T10:30:00",
    "avgReductionKw": 122,
    "maxReductionKw": 306
  },
  "dailyPeaks": [{ "date": "2026-11-01", "peakKw": 1612, "expectedKw": 1655 }],
  "heatmap": [{ "date": "2026-11-01", "hour": 0, "peakKw": 1190 }],
  "bill": {
    "total": 57771897,
    "energyCharge": 42454777,
    "baseCharge": 15317120,
    "baseChargeOverTarget": 341120,
    "monthEndEstimate": 95140000,
    "lastMonthTotal": 92850664,
    "byTariff": [
      { "period": "LIGHT", "label": "경부하", "kwh": 193339, "percent": 38.6, "charge": 11793679 }
    ]
  },
  "exceeded": [
    {
      "windowStart": "2026-11-11T10:30:00",
      "windowEnd": "2026-11-11T10:45:00",
      "resultPeakKw": 1817,
      "overKw": 17,
      "action": "DISCHARGE",
      "powerKw": 500,
      "status": "IGNORED",
      "cause": "추천을 넘김"
    }
  ],
  "ess": {
    "dailyDischarge": [{ "date": "2026-11-01", "kwh": 420 }],
    "dischargeKwh": 8920,
    "chargeKwh": 9966,
    "efficiencyPercent": 89.5,
    "cycles": 8.9,
    "avgSocPercent": 58.3,
    "minSocPercent": 22.4,
    "blockedCount": 1
  },
  "solar": {
    "generationKwh": 98420,
    "selfUseKwh": 97928,
    "selfUsePercent": 99.5,
    "gridReductionKwh": 97928,
    "emissionFactor": 0.4594,
    "carbonTon": 45.0
  }
}
```

| 묶음 | 필드 | 설명 |
|---|---|---|
| summary | defenseTotal / defenseSuccess / defenseSuccessPercent | 피크 방어를 시도한 구간 수 / 목표 이내로 막은 수 / 성공률 |
| | monthPeakKw / monthPeakAt | 이번 달 최고 15분 피크와 그 구간 시작 시각 |
| | avgReductionKw / maxReductionKw | 일별 (방전 안 했을 때 예상 − 실제) 피크의 평균 / 최대 |
| dailyPeaks | date, peakKw, expectedKw | 날마다 실제 최고 피크와, 방전하지 않았을 때 예상 최고 피크 |
| heatmap | date, hour, peakKw | 날짜 × 시각(0~23)마다 그 시간 안의 최고 15분 피크 |
| bill | total | 지금까지 요금 = 전력량요금 + 기본요금 (원, **추정**) |
| | energyCharge / baseCharge | 전력량요금 / 기본요금 |
| | baseChargeOverTarget | 목표 피크를 넘어서 더 내게 된 기본요금 (넘지 않았으면 0) |
| | monthEndEstimate | 이 속도면 월말 요금 예상 |
| | lastMonthTotal | 지난달 요금 |
| | byTariff | 요금 시간대별 구매 전력량·비중·요금 |
| exceeded | | 목표를 넘은 구간 목록 (**최신 순**). `status`는 추천 처리 상태, `cause`는 화면에 보여 줄 원인 문장 |
| ess | dailyDischarge | 날마다 방전량 |
| | dischargeKwh / chargeKwh / efficiencyPercent / cycles | 방전량 / 충전량 / 효율 / 사이클 수(방전량 ÷ 용량) |
| | avgSocPercent / minSocPercent | 평균 / 최저 잔량 |
| | blockedCount | 잔량 부족 등으로 방전을 못 해 넘은 구간 수 |
| solar | generationKwh / selfUseKwh / selfUsePercent | 발전량 / 자가소비량 / 자가소비율 |
| | gridReductionKwh | 태양광 덕분에 덜 산 전력량 |
| | emissionFactor / carbonTon | 탄소 배출계수(kgCO2/kWh, 확인 필요) / 탄소 절감 **추정**(t) |

- 금액과 탄소에는 화면이 항상 "추정"을 붙입니다.
- 운영 방식 비교(한전 100% / 태양광만 / ESS 운영)는 아직 응답에 없습니다. 추가하면 개수를 고정하지 말고 **배열**로 보내 주세요 (3가지 → 4가지로 늘 수 있음). 계산 방식은 [ESS 설명 7절](ess-recommendation.md#7-1년-예상-전기요금-운영-방식-4가지) 참고.

---

## 7. 분석

### 7-1. 추이 분석 — `GET /api/analyses/trend?from=&to=&interval=`

| 쿼리 | 예 | 설명 |
|---|---|---|
| from, to | `2026-11-10`, `2026-11-16` | 날짜 기간 (양 끝 포함, 최대 62일) |
| interval | `QUARTER` / `HOUR` / `DAY` | 15분 / 1시간 / 1일로 묶은 평균 |

```json
{
  "from": "2026-11-10",
  "to": "2026-11-16",
  "interval": "HOUR",
  "targetPeakKw": 1800,
  "socLowLimit": 20,
  "socHighLimit": 90,
  "roomTempLimitC": 35,
  "summary": {
    "maxPeakKw": 1797,
    "maxPeakAt": "2026-11-12T08:45:00",
    "avgGridKw": 1334,
    "solarKwh": 23087,
    "solarPercentOfLoad": 9.7,
    "dischargeKwh": 4230,
    "chargeKwh": 3408,
    "efficiencyPercent": 89.5,
    "socMinPercent": 20.0,
    "socMinAt": "2026-11-10T22:45:00",
    "socMaxPercent": 90.0,
    "roomTempMaxC": 29.7,
    "roomTempMaxAt": "2026-11-12T19:30:00"
  },
  "points": [
    {
      "recordedAt": "2026-11-10T00:00:00",
      "loadKw": 1320, "gridKw": 1570, "solarKw": 0, "essKw": -250,
      "socPercent": 61.2, "roomTempC": 24.5, "outsideTempC": 7.2
    }
  ],
  "weather": [{ "date": "2026-11-10", "text": "맑음" }],
  "hourly": [{ "hour": 0, "avgGridKw": 1402, "period": "LIGHT" }],
  "hourBlocks": [{ "fromHour": 0, "toHour": 4, "avgGridKw": 1398, "peak": false }],
  "daily": [{ "date": "2026-11-10", "avgGridKw": 1320 }],
  "byStatus": [
    { "status": "APPLIED", "count": 15, "defendedCount": 15, "defendedPercent": 100.0, "avgResultPeakKw": 1730 }
  ],
  "highlights": [
    { "at": "2026-11-12T08:45:00", "label": "15분 피크 최대", "value": 1797, "unit": "kW", "level": "INFO" }
  ]
}
```

| 필드 | 설명 |
|---|---|
| summary | 기간 요약. `maxPeakKw`는 기간 중 최고 15분 한전 구매, `solarPercentOfLoad`는 공장 소비 중 태양광 비율 |
| points | 묶은 간격의 평균. `essKw`는 **+방전 / −충전**, `recordedAt`은 묶음 시작 시각 |
| weather | 날마다 날씨 |
| hourly | 0~23시 각각의 평균 한전 구매와 요금 시간대 (사용 패턴: "오늘"일 때 1시간 단위 막대) |
| hourBlocks | 4시간 묶음(0~4, 4~8 … 20~24시)별 평균 한전 구매. `peak`는 묶음 안에 최대부하 시각이 있으면 true (사용 패턴: "최근 7일"일 때) — 새로 추가, 확인 필요 |
| daily | 날짜별 평균 한전 구매 (사용 패턴: "이번 달"처럼 긴 기간일 때) — 새로 추가, 확인 필요 |
| byStatus | 방전 추천을 처리(`APPLIED` / `AUTO` / `IGNORED`)별로 묶은 결과. 결과가 없으면 `defendedPercent`, `avgResultPeakKw`는 null |
| highlights | 눈에 띄는 구간 `{ at, label, value, unit, level }`. 화면은 받은 순서대로 보여 줌 |

### 7-2. 피크 분석 — `GET /api/analyses/peak?from=&to=`

| 쿼리 | 예 | 설명 |
|---|---|---|
| from, to | `2026-11-01`, `2026-11-16` | 날짜 기간 (이번 달, 지난 달, 최근 7일 등) |

기록이 없으면 `{ "from", "to", "hasData": false, "targetPeakKw" }`

```json
{
  "from": "2026-11-01",
  "to": "2026-11-16",
  "hasData": true,
  "targetPeakKw": 1800,
  "summary": {
    "maxPeakKw": 1841,
    "maxPeakAt": "2026-11-02T10:30:00",
    "exceededCount": 3,
    "windowCount": 1508,
    "avgReductionKw": 122,
    "maxReductionKw": 306,
    "busyFrom": "17:00",
    "busyTo": "20:00",
    "busyPeriodLabel": "최대부하"
  },
  "dailyPeaks": [{ "date": "2026-11-01", "peakKw": 1612, "expectedKw": 1655 }],
  "heatmap": [{ "date": "2026-11-01", "hour": 0, "peakKw": 1190 }],
  "topWindows": [
    {
      "windowStart": "2026-11-02T10:30:00",
      "windowEnd": "2026-11-02T10:45:00",
      "peakKw": 1841,
      "status": "IGNORED",
      "note": "추천을 넘김"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| summary.exceededCount / windowCount | 목표를 넘은 15분 구간 수 / 기간 안의 전체 구간 수 (오늘은 끝난 구간만) |
| summary.busyFrom / busyTo / busyPeriodLabel | 피크가 잦은 시간대 (요금 시간대 묶음 중 평균이 가장 높은 곳) |
| dailyPeaks, heatmap | 보고서와 같은 모양 |
| topWindows | 날마다 가장 높았던 구간 중 상위 8개 (높은 순). `status`는 그 구간 추천 처리, 추천이 없었으면 null |

보고서와 피크 분석은 **같은 데이터에서 계산**해 주세요. 같은 달이면 두 화면의 숫자가 같아야 합니다.

---

## 8. 환경 설정

### 8-1. 기준값 목록 — `GET /api/energy-settings`

```json
[
  { "key": "targetPeakKw", "value": 1800, "updatedAt": "2026-11-14T16:20:00", "updatedBy": "운영자" },
  { "key": "operationMode", "value": "MANUAL", "updatedAt": "2026-11-08T11:42:00", "updatedBy": "운영자" }
]
```

| key | 이름 | 단위 | 값 | 기본값 | 쓰는 곳 |
|---|---|---|---|---|---|
| targetPeakKw | 목표 피크 | kW | 0보다 큰 숫자 | 1800 | 피크 단계, 방어선 |
| warnPercent | 주의선 | % | 50~100 | 97 | 주의 단계 시작, 안전 여유 |
| priceLightWon | 경부하 단가 | 원/kWh | 0보다 큰 숫자 | 61 | 지금 계절의 단가 (임시값) |
| priceMidWon | 중간부하 단가 | 원/kWh | 〃 | 92 | 〃 |
| pricePeakWon | 최대부하 단가 | 원/kWh | 〃 | 106 | 〃 |
| basePriceWon | 기본요금 단가 | 원/kW | 〃 | 8320 | 기본요금 (임시값) |
| capacityKwh | 배터리 용량 | kWh | 〃 | 1000 | |
| maxPowerKw | 최대 충·방전 출력 | kW | 〃 | 500 | 추천 출력 상한 |
| socLowPercent | 잔량 하한 | % | 상한보다 작아야 함 | 20 | 이 아래로 방전 안 함 |
| socHighPercent | 잔량 상한 | % | 하한보다 커야 함 | 90 | 이 위로 충전 안 함 |
| efficiencyPercent | 충·방전 효율 | % | 소수 1자리 | 89.5 | |
| roomTempLimitC | 배터리실 온도 제한 | ℃ | | 35 | 넘으면 충·방전 멈춤 |
| operationMode | 운영 모드 | | `MANUAL` / `AUTO` | MANUAL | `AUTO`는 자동 모드 (시뮬레이션) |
| pollSeconds | 화면 갱신 주기 | 초 | 1~60 | 5 | 프론트 폴링 주기 |
| decisionValidMinutes | 추천 유효 시간 | 분 | 1~60 | 15 | 확인 필요 |

- 단가는 지금 계절(여름 / 봄·가을 / 겨울)의 값입니다. 계절별 단가를 모두 설정에 둘지는 확인 필요입니다.

### 8-2. 기준값 하나 바꾸기 — `PUT /api/energy-settings/{key}`

**요청 본문**

```json
{ "value": 1750 }
```

- 응답: 바뀐 한 항목 `{ key, value, updatedAt, updatedBy }`
- 잘못된 값이면 400과 이유 (예: `"잔량 하한은 잔량 상한보다 작아야 합니다."`, `"목표 피크에는 0보다 큰 숫자를 넣어야 합니다."`)
- 없는 key면 404 또는 400 (`"없는 설정 항목입니다."`)
- 바꾸면 변경 기록과 이벤트 로그(`설정 변경: 목표 피크 1,800 → 1,750 (운영자)`)에 남겨 주세요.
- 화면은 한 묶음(피크·요금 / ESS / 운영)의 바뀐 항목을 하나씩 차례로 보냅니다.

### 8-3. 변경 기록 — `GET /api/energy-settings/history?limit=20`

```json
[
  { "id": 6, "changedAt": "2026-11-16T10:02:11", "key": "targetPeakKw", "before": 1800, "after": 1750, "changedBy": "운영자" }
]
```

- **최신 순**, 최대 `limit`개. `before`, `after`는 숫자이거나 글자(`operationMode`)입니다.

---

## 9. 프론트 연결 방법

백엔드 API가 생기면 `src/api/*Api.js`에서 가짜 함수를 `api()` 호출로 바꾸면 됩니다. 화면과 스토어는 고치지 않습니다.

```js
// 지금 (가짜 백엔드)
export const getLatestBatteryStatus = () => fakeGetLatestStatus()

// 바꾼 뒤
import { api } from '@/api/client'
export const getLatestBatteryStatus = () => api('/battery-statuses/latest')
```

| 파일 | 함수 → 주소 |
|---|---|
| `batteryStatusApi.js` | `getLatestBatteryStatus` → 1번, `getTodayBatteryStatuses` → 2번, `getRecentBatteryStatuses` → 3번, `getZoneRecent` → 4번 |
| `batteryDecisionApi.js` | `getLatestBatteryDecision` → 5번, `getBatteryDecisions` → 6번, `updateBatteryDecision` → 7번 |
| `eventApi.js` | `getRecentEvents` → 8번 |
| `reportApi.js` | `getReport` → 9번 |
| `analysisApi.js` | `getTrendAnalysis` → 10번, `getPeakAnalysis` → 11번 |
| `settingsApi.js` | `getEnergySettings` → 12번, `updateEnergySetting` → 13번, `getEnergySettingHistory` → 14번 |

- 2번과 3번은 지금 함수에 인자가 없습니다. 연결할 때 `from`, `to`를 넘기도록 함께 고칩니다.
- 모든 API를 한 번에 바꿀 필요는 없습니다. 만들어진 것부터 하나씩 바꿔도 됩니다.
- 다 바꾸면 `fakeBackend.js`와 `fakeEnergyData.json`을 지웁니다.

---

## 10. 확인 필요

- [ ] 주소 확정: 특히 2·3번(기간 상태의 `interval`), 4번(`/zone-statuses/{zoneId}`), 14번(`/energy-settings/history`)
- [ ] 최신 추천이 없을 때 `null`로 줄지 204로 줄지
- [ ] 값의 출처 구분(`origin`: 실측 / 시뮬레이션 / 계산값)을 어떤 필드에 둘지. 지금은 설비 목록에만 있음
- [ ] 배터리실 온도, 날씨, SOH 필드 이름과 출처
- [ ] 추천 유효 시간과 만료 처리 (지금: 추천마다 `windowEnd`까지 유효)
- [ ] 계절별 단가를 설정에 모두 둘지 (지금: 지금 계절 단가 3칸만)
- [ ] 보고서에 운영 방식 비교(배열) 추가
- [ ] 로그인 붙인 뒤 `appliedBy`, `updatedBy`, `changedBy`에 넣을 사용자 이름
- [ ] 시뮬레이터 배속·시각 이동 같은 시연 제어 API를 둘지
