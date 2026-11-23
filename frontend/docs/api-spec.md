# WATTever API 명세서

- **기준:** 2026-11-22, `dev` 브랜치(`408e24a`)의 백엔드 코드(Controller · 응답 DTO · Service의 검사 규칙)를 직접 읽고 정리함
- 서버를 켜면 Swagger(`http://localhost:8080/swagger-ui.html`)에서 직접 불러 볼 수 있음
- 이전 문서
  - v0.1: 가짜 백엔드 시절 제안 주소(`/api/battery-statuses` 등). 이 파일의 예전 판 (git 커밋 `9e6f767`)
  - v0.2: 팀 시트 `(api 명세서) 2조 에너지솔루션.xlsx` (2026-11-17)
  - 명세와 화면을 맞춘 기록: [api-spec-diff.md](api-spec-diff.md)
- 숫자 계산 방법(예상 피크, 추천 kW, 성적표 등): [frontend-qa.md](frontend-qa.md)

---

## 1. 공통 규칙

| 항목      | 규칙                                                                          |
| --------- | ----------------------------------------------------------------------------- |
| 기본 주소 | `/api` (개발 중에는 Vite가 `http://localhost:8080`으로 넘겨 줌)               |
| 주소 모양 | `/api/{복수형 명사}` (주소에 동사를 넣지 않음)                                |
| 형식      | JSON. 필드 이름은 camelCase (Java `record` 필드 이름 그대로)                  |
| 시각      | `"2026-11-22T14:07:00"` (한국 시간, 시간대 표시 없음)                         |
| 날짜 · 달 | `"2026-11-22"` / `"2026-11"`                                                  |
| 숫자      | 숫자 타입. kW · kWh · %는 대부분 소수 1자리로 반올림, 금액(원)은 정수         |
| 단위      | 필드 이름 끝에 붙임: `Kw`, `Kwh`, `Percent`, `C`(℃)                           |
| 값 없음   | `null` (0과 구분함). 화면은 `—`로 표시                                        |
| 본문 없음 | 204. 프론트 `api()`는 `null`을 돌려줌                                         |
| 로그인    | 없음. 적용 · 저장한 사람은 항상 `"운영자"`                                    |
| 공용 DB   | [적용] · [넘기기] · 설정 저장은 팀 공용 DB에 바로 기록돼 팀원 모두에게 반영됨 |

### 오류 응답

모든 오류는 같은 모양으로 옴.

```json
{ "status": 400, "message": "기간은 최대 121일까지 조회할 수 있습니다." }
```

| 상태 | 언제                                    | message                                                   |
| ---- | --------------------------------------- | --------------------------------------------------------- |
| 400  | 값이 잘못됨                             | API마다 다름 (각 API의 "오류" 표)                         |
| 400  | 쿼리 형식이 틀림 (예: `from=abc`)       | `from: 값의 형식이 올바르지 않습니다.`                    |
| 400  | 본문 JSON이 깨짐                        | `요청 형식이 올바르지 않습니다.`                          |
| 400  | 꼭 필요한 쿼리가 빠짐 (예: `from` 없음) | Spring 기본 문구 (영어로 옴)                              |
| 404  | 데이터가 없음                           | API마다 다름 (예: `없는 구역입니다: Z9`)                  |
| 404  | 없는 주소                               | `요청한 주소를 찾을 수 없습니다.`                         |
| 405  | 메서드가 틀림 (예: GET 자리에 POST)     | `지원하지 않는 요청 방식입니다.`                          |
| 500  | 예상하지 못한 서버 오류                 | `서버 오류가 발생했습니다.` (원인은 백엔드 로그에서 확인) |

프론트(`src/api/client.js`의 `api()`)는 이렇게 처리함.

- 실패하면 `message`로 오류를 던짐 → 화면이 그대로 보여 줌
- 서버에 닿지 못했거나 본문 없는 5xx가 오면 `서버에 연결할 수 없습니다.`

---

## 2. API 목록

### 화면에서 쓰는 API (15개)

| #   | 메서드 | 주소                                      | 하는 일             | 프론트 함수                | 쓰는 화면                                 | 부르는 때                                                         |
| --- | ------ | ----------------------------------------- | ------------------- | -------------------------- | ----------------------------------------- | ----------------------------------------------------------------- |
| 1   | GET    | `/api/site-powers/latest`                 | 지금 상태 1건       | `getLatestBatteryStatus`   | 모든 화면 (공통 틀)                       | **갱신 주기마다**                                                 |
| 2   | GET    | `/api/site-powers/today`                  | 오늘 15분 목록      | `getTodayBatteryStatuses`  | 통합 › 오늘 전력 추이                     | 화면 열 때, 15분 구간이 바뀔 때                                   |
| 3   | GET    | `/api/zones/{zoneCode}`                   | 구역 상세           | `getZoneRecent`            | 설비 모니터링                             | 구역 고를 때, 15분 구간이 바뀔 때                                 |
| 4   | GET    | `/api/recommendations/latest`             | 지금 추천 1건       | `getLatestBatteryDecision` | 통합 › ESS 추천                           | **갱신 주기마다**                                                 |
| 5   | PATCH  | `/api/recommendations/{id}`               | 추천 적용 · 넘기기  | `updateBatteryDecision`    | 통합 › [적용] [넘기기]                    | 버튼 누를 때                                                      |
| 6   | GET    | `/api/recommendations?from=&to=`          | 추천 이력           | `getBatteryDecisions`      | 운영 로그 › 추천 이력, 전력 이력          | 조회할 때 (추천 이력은 오늘이 든 기간이면 5초마다)                |
| 7   | GET    | `/api/events?limit=`                      | 최근 이벤트         | `getRecentEvents`          | 통합 › 이벤트 로그                        | **갱신 주기마다** (30건)                                          |
| 8   | GET    | `/api/events?from=&to=`                   | 기간 이벤트         | `getEventsInPeriod`        | 운영 로그 › 전력 이력                     | 조회할 때                                                         |
| 9   | GET    | `/api/equipment-powers?from=&to=`         | 15분 · 설비별 전력  | `getEquipmentPowers`       | 운영 로그 › 전력 이력                     | 조회할 때                                                         |
| 10  | GET    | `/api/analyses/trend?from=&to=&interval=` | 추이 분석           | `getTrendAnalysis`         | 분석 › 추이 분석, 전력 이력               | 조회할 때 (추이 분석은 오늘이 든 기간이면 15분 구간이 바뀔 때도) |
| 11  | GET    | `/api/analyses/peak?from=&to=`            | 피크 분석           | `getPeakAnalysis`          | 분석 › 추이 분석, 전력 이력               | 10번과 같음                                                       |
| 12  | GET    | `/api/reports?month=`                     | 월간 성적표         | `getReport`                | 보고서 › 월간 성적표                      | 화면 열 때, 달 바꿀 때                                            |
| 13  | GET    | `/api/settings`                           | 기준값 목록         | `getSettings`              | 공통 틀, 환경 설정                        | 앱 열 때, 환경 설정 열 때                                         |
| 14  | PUT    | `/api/settings`                           | 기준값 저장         | `updateSettings`           | 환경 설정 [저장], 통합 › 운영 모드 바꾸기 | 버튼 누를 때                                                      |
| 15  | GET    | `/api/setting-histories?from=&to=`        | 기준값 변경 기록    | `getSettingHistories`      | 환경 설정                                 | 화면 열 때, 저장한 뒤                                             |

**갱신 주기마다 부르는 API는 1 · 4 · 7번 3개**이고, 공통 틀(`MainLayout.vue`)이 한 번에 부름.

- 갱신 주기 = 설정 `operation.poll_seconds` (기본 5초)
- 브라우저 탭이 숨겨지면 멈추고, 앞 요청이 안 끝났으면 다음 요청은 건너뜀

### 화면에서 안 쓰는 API (6개)

| #   | 메서드 | 주소                            | 하는 일                          |
| --- | ------ | ------------------------------- | -------------------------------- |
| 16  | GET    | `/api/ess-plans/latest`         | 판단 엔진의 24시간 ESS 계획      |
| 17  | GET    | `/api/tariff-periods?season=`   | 계절별 요금 시간대표             |
| 18  | GET    | `/api/zones`                    | 구역 목록                        |
| 19  | GET    | `/api/equipment`                | 설비 목록                        |
| 20  | GET    | `/api/system/db-status`         | 공용 DB(Supabase) 상태           |
| 21  | POST   | `/api/system/db-target?target=` | 쓸 DB를 고정 · 해제 (관리용)     |

---

## 3. 지금 상태 (1 ~ 3번)

### 3-1. 지금 상태 1건 — `GET /api/site-powers/latest`

KPI 카드, 예상 피크, 요금 시간대, 전력 계통도, 설비 목록이 모두 이 응답 하나로 그려짐.

- **요청:** 없음
- **오류:** 1분 기록이 하나도 없으면 404 `아직 전력 기록이 없습니다.`
- 여러 탭이 동시에 불러도 DB는 1초에 한 번만 읽음 (응답을 1초 동안 캐시)

**응답 예시** (배열은 줄임)

```json
{
  "recordedAt": "2026-11-22T14:07:00",
  "loadKw": 1921.0,
  "liveLoadKw": 1934.2,
  "solarKw": 85.4,
  "chargeKw": 0.0,
  "dischargeKw": 160.0,
  "gridKw": 1675.6,
  "socPercent": 86.7,
  "batteryTempC": 24.1,
  "pcsStatus": "DISCHARGING",
  "sohPercent": 98.0,
  "weather": { "text": "맑음", "tempC": 7.0 },
  "simulationSpeed": 1,

  "windowStart": "2026-11-22T14:00:00",
  "windowEnd": "2026-11-22T14:15:00",
  "remainingSeconds": 470,
  "predictedKw": 1702.3,
  "targetPeakKw": 1800.0,
  "warnKw": 1746.0,
  "peakStatus": "NORMAL",
  "defenseKw": 0.0,
  "windowPoints": [
    { "minute": 1, "demandKw": 112.0 },
    { "minute": 2, "demandKw": 224.5 }
  ],
  "todayPeakKw": 1569.0,
  "monthPeakKw": 1780.0,

  "tariff": {
    "period": "MID",
    "label": "중간부하",
    "until": "16:00",
    "nextLabel": "최대부하",
    "periods": [{ "from": "00:00", "to": "08:00", "period": "LIGHT", "label": "경부하" }]
  },
  "sourceRatio": { "gridPercent": 87.3, "solarPercent": 4.4, "essPercent": 8.3 },
  "todayEnergy": { "gridKwh": 21034.5, "solarKwh": 410.2, "dischargeKwh": 820.0, "chargeKwh": 950.3 },

  "equipments": [
    {
      "id": "INJ-01",
      "zoneId": "Z3",
      "name": "사출기 1호",
      "ratedKw": 220.0,
      "loadKw": 186.3,
      "origin": "SIMULATED",
      "state": "RUNNING"
    }
  ]
}
```

**전력 · 배터리**

| 필드                   | 타입      | 설명                                                                                 |
| ---------------------- | --------- | ------------------------------------------------------------------------------------ |
| recordedAt             | 시각      | 마지막 1분 기록의 시각. 화면의 "지금" 기준. 시뮬레이터가 멈추면 이 시각도 멈춤       |
| loadKw                 | 숫자      | 공장 소비 (1분 평균)                                                                 |
| liveLoadKw             | 숫자      | 지금 이 순간의 공장 부하 (원본 부하 데이터를 바로 읽음, 매초 바뀜)                   |
| solarKw                | 숫자      | 태양광 발전                                                                          |
| chargeKw / dischargeKw | 숫자      | ESS 충전 / 방전. 쉬는 쪽은 0                                                         |
| gridKw                 | 숫자      | 한전 구매                                                                            |
| socPercent             | 숫자      | 배터리 잔량 (%)                                                                      |
| batteryTempC           | 숫자      | 배터리실 온도 (℃)                                                                    |
| pcsStatus              | 코드      | `CHARGING` / `DISCHARGING` / `STANDBY` (ESS 출력의 부호로 정함)                      |
| sohPercent             | 숫자      | 배터리 건강도 (%). 설정 `ess.soh_percent` 값                                         |
| weather                | 객체·null | `{ text, tempC }`. 날씨 기록이 없으면 null                                           |
| simulationSpeed        | 숫자      | 배속 표시용. 백엔드는 항상 1                                                         |

**15분 피크**

| 필드                      | 타입      | 설명                                                                                                  |
| ------------------------- | --------- | ----------------------------------------------------------------------------------------------------- |
| windowStart / windowEnd   | 시각      | 지금 15분 구간 (서버의 지금 시각 기준)                                                                |
| remainingSeconds          | 숫자      | 구간 끝까지 남은 초                                                                                   |
| predictedKw               | 숫자      | 예상 피크 = (이번 구간 1분 기록의 한전 구매 합 + 지금 한전 구매 × 남은 분) ÷ 15                       |
| targetPeakKw              | 숫자·null | 목표 피크 (설정 `peak.target_kw`)                                                                     |
| warnKw                    | 숫자·null | 주의선 = 목표 × `peak.warn_ratio` ÷ 100                                                               |
| peakStatus                | 코드      | 예상 > 목표 → `CRITICAL`, 예상 ≥ 주의선 → `WARNING`, 그 밖 → `NORMAL`                                 |
| defenseKw                 | 숫자      | 추가 방전 = (예상 − 목표) × 15 ÷ 남은 분, 올림. 예상이 목표 이하이거나 남은 분이 0이면 0              |
| windowPoints              | 배열      | `[{ minute, demandKw }]`. minute = 구간의 몇 번째 분(1~15), demandKw = 그 분까지 한전 구매 합 ÷ 15    |
| todayPeakKw / monthPeakKw | 숫자·null | 오늘 / 이번 달 최대 15분 피크 (15분이 다 찬 구간만). 없으면 null                                      |

**요금 · 비율 · 누적**

| 필드                   | 타입      | 설명                                                                     |
| ---------------------- | --------- | ------------------------------------------------------------------------ |
| tariff                 | 객체·null | 지금 요금 시간대. 요금표에 빈 시각이 있으면 null                         |
| tariff.period / label  | 코드/글자 | `LIGHT` 경부하 / `MID` 중간부하 / `PEAK` 최대부하                        |
| tariff.until           | 글자      | 지금 시간대가 끝나는 시각 (`HH:mm`)                                      |
| tariff.nextLabel       | 글자      | 다음 시간대 이름                                                         |
| tariff.periods         | 배열      | 오늘 시간대표 `[{ from, to, period, label }]`. 하루 끝은 `"24:00"`       |
| sourceRatio            | 객체      | `{ gridPercent, solarPercent, essPercent }` 공장 소비를 무엇으로 채웠는지 |
| todayEnergy            | 객체      | `{ gridKwh, solarKwh, dischargeKwh, chargeKwh }` 오늘 0시부터 누적       |
| equipments             | 배열      | 설비별 최신 1분 전력 (아래 표)                                           |

`sourceRatio` 계산 규칙

- 태양광 → ESS 방전 → 한전 순으로 채움. 한전 = 100 − 태양광 − ESS (세 칸 합이 100)
- 공장 소비가 0이면 모두 0
- **충전 중이면 `essPercent`가 음수**(예: −4)라서 `gridPercent`가 100을 넘음(예: 104). 화면은 ESS 0%, 한전 = 100 − 태양광으로 보여 줌 (이슈 #35-1)

`equipments` 한 줄

| 필드    | 설명                                                                         |
| ------- | ---------------------------------------------------------------------------- |
| id      | 설비 id (예: `INJ-01`)                                                       |
| zoneId  | 구역 코드 `Z1` ~ `Z6` (아래 표)                                              |
| name    | 설비 이름                                                                    |
| ratedKw | 정격 전력                                                                    |
| loadKw  | 최신 1분 전력. 그 분의 기록이 없으면 null                                    |
| origin  | `MEASURED`(실측, 설비 id가 `Z4-MOTOR1`일 때만) / `SIMULATED`(시뮬레이션)     |
| state   | `RUNNING`(가동) / `STOPPED`(정지). 기록이 없으면 null                        |

- 공장 부하 설비(`LOAD`)만, 설비 id 순. 한전 · 태양광 · ESS는 들어 있지 않음
- 분이 바뀌는 순간 1~2초 동안 `loadKw`가 비어서 올 수 있음 (이슈 #35-3, 화면은 구역 합계를 `—`로 표시)

구역 코드 (이름은 화면에서 쓰는 이름, `PowerTree.vue`)

| 코드 | 구역        | 코드 | 구역      |
| ---- | ----------- | ---- | --------- |
| Z1   | 열교환기 가공 | Z4   | 모듈 조립 |
| Z2   | 브레이징    | Z5   | 검사      |
| Z3   | 사출        | Z6   | 유틸리티  |

---

### 3-2. 오늘 15분 목록 — `GET /api/site-powers/today`

- **요청:** 없음
- **응답:** 오늘(서버 날짜) 0시부터 지금까지 15분 구간마다 1줄, 시간 순. 진행 중인 구간도 그때까지의 평균으로 들어감

```json
[
  { "recordedAt": "2026-11-22T00:00:00", "loadKw": 1320.0, "solarKw": 0.0, "gridKw": 1570.0, "essKw": -250.0, "socPercent": 55.6 },
  { "recordedAt": "2026-11-22T00:15:00", "loadKw": 1318.0, "solarKw": 0.0, "gridKw": 1568.0, "essKw": -250.0, "socPercent": 61.2 }
]
```

| 필드                     | 설명                                                     |
| ------------------------ | -------------------------------------------------------- |
| recordedAt               | 15분 구간 시작 시각                                      |
| loadKw / solarKw / gridKw | 구간 평균 공장 소비 / 태양광 / 한전 구매                |
| essKw                    | 구간 평균 ESS 출력 (**+ 방전 / − 충전**)                 |
| socPercent               | 구간 마지막 1분의 배터리 잔량. 기록이 없으면 null        |

---

### 3-3. 구역 상세 — `GET /api/zones/{zoneCode}`

설비 모니터링에서 구역을 고르면 나오는 상세.

- **요청:** `zoneCode` = `Z1` ~ `Z6` (대소문자 상관없음)
- **오류:** 없는 구역이면 404 `없는 구역입니다: Z9`

```json
{
  "zoneId": "Z3",
  "points": [
    { "recordedAt": "2026-11-22T13:07:00", "loadKw": 612.4 },
    { "recordedAt": "2026-11-22T13:08:00", "loadKw": 598.0 }
  ],
  "todayMaxKw": 702.1,
  "todayAvgKw": 541.3,
  "todayKwh": 7578.2
}
```

| 필드                    | 설명                                                                         |
| ----------------------- | ---------------------------------------------------------------------------- |
| zoneId                  | 구역 코드                                                                    |
| points                  | 최근 1시간, 1분마다 구역 설비 전력의 합. 기록이 있는 분만 (최대 61개), 시간 순 |
| todayMaxKw / todayAvgKw | 오늘 구역 최대 / 평균 전력. 기록이 없으면 null                               |
| todayKwh                | 오늘 구역 사용량                                                             |

---

## 4. ESS 추천 (4 ~ 6번, 16번)

### 4-1. 추천 1건의 모양 (4 · 5 · 6번 공통)

```json
{
  "id": 1234,
  "decidedAt": "2026-11-22T17:05:20",
  "windowStart": "2026-11-22T17:00:00",
  "windowEnd": "2026-11-22T17:15:00",
  "reasonType": "PEAK_DEFENSE",
  "ruleLabel": "피크 방어",
  "action": "DISCHARGE",
  "powerKw": 155,
  "durationMinutes": null,
  "predictedKw": 1862.4,
  "targetPeakKw": 1800,
  "reason": "적용하면 이번 15분 평균이 1,862 → 1,790 kW로 목표(1,800 kW) 안에 들어옵니다. 넘기면 피크가 목표를 넘어 기본요금이 오를 수 있습니다.",
  "usedValues": [],
  "status": "RECOMMENDED",
  "appliedBy": null,
  "appliedAt": null,
  "resultPeakKw": null,
  "resultStatus": null
}
```

| 필드                    | 설명                                                                       |
| ----------------------- | -------------------------------------------------------------------------- |
| id                      | 추천 번호                                                                  |
| decidedAt               | 추천을 만든 시각                                                           |
| windowStart / windowEnd | 대상 15분 구간 (끝 = 시작 + 15분)                                          |
| reasonType / ruleLabel  | 이유 코드 / 화면 이름 (아래 표). 화면은 `ruleLabel`만 씀                   |
| action                  | `CHARGE` / `DISCHARGE` / `IDLE`                                            |
| powerKw                 | 추천 출력 kW (충전도 양수)                                                 |
| durationMinutes         | **항상 null** (DB에 칸이 없음)                                             |
| predictedKw             | 추천할 때 본 이번 구간 예상 피크                                           |
| targetPeakKw            | 그때의 목표 피크                                                           |
| reason                  | 화면에 그대로 보여 줄 이유 문장 (판단 엔진이 만듦)                          |
| usedValues              | **항상 빈 배열** (DB에 칸이 없음)                                          |
| status                  | 처리 상태 (아래 표)                                                        |
| appliedBy / appliedAt   | 처리한 사람 · 시각. 사람이 처리하면 `"운영자"`. 처리 전에는 null           |
| resultPeakKw            | 그 15분 구간의 실제 피크. 구간이 안 끝났으면 null                          |
| resultStatus            | 실제 ≤ 목표면 `DEFENDED`, 넘으면 `EXCEEDED`, 아직 모르면 null              |

이유 코드 (판단 엔진이 위에서부터 차례로 확인)

| reasonType             | ruleLabel   | action    | 언제                                                         |
| ---------------------- | ----------- | --------- | ------------------------------------------------------------ |
| `PEAK_DEFENSE`         | 피크 방어   | DISCHARGE | 이번 15분이 방어선을 넘을 것 같을 때                         |
| `SAFETY_LIMIT`         | 안전 제한   | IDLE      | 피크 방어가 필요한데 잔량이 하한이라 방전할 수 없을 때       |
| `ON_PEAK_DISCHARGE`    | 고요금 방전 | DISCHARGE | 24시간 계획상 요금이 비싼 때 방전                            |
| `SOLAR_SURPLUS_CHARGE` | 잉여 충전   | CHARGE    | 태양광이 공장 소비보다 많아 남을 때                          |
| `OFF_PEAK_CHARGE`      | 저요금 충전 | CHARGE    | 24시간 계획상 요금이 싼 때 충전                              |
| `IDLE`                 | 조치 없음   | IDLE      | 그 밖 (10 kW 미만 충 · 방전도 여기)                          |

처리 상태

| status (화면 코드) | DB 값               | 뜻                                |
| ------------------ | ------------------- | --------------------------------- |
| `RECOMMENDED`      | `PENDING`           | 답 기다림                         |
| `APPLIED`          | `APPLIED`           | 운영자가 적용                     |
| `AUTO`             | `AUTO`              | 자동 모드가 적용 (시뮬레이션)     |
| `IGNORED`          | `SKIPPED`, `EXPIRED` | 운영자가 넘김, 또는 시간이 지나 만료 |

### 4-2. 지금 추천 — `GET /api/recommendations/latest`

- **요청:** 없음
- **응답:** 가장 최근 추천 1건 (4-1 모양). 대기(`IDLE`) 추천도 나옴
- 추천이 하나도 없으면 **204** → 프론트는 `null`을 받고 "아직 받은 추천이 없습니다" 표시

### 4-3. 추천 이력 — `GET /api/recommendations?from=&to=`

| 쿼리     | 예                         | 설명                    |
| -------- | -------------------------- | ----------------------- |
| from, to | `2026-11-14`, `2026-11-22` | 날짜 기간 (양 끝 포함)  |

- **응답:** 추천 배열, **최신 순**. 대기(`IDLE`) 추천은 빠짐 (너무 많아서)
- **오류**

| 상태 | 언제            | message                                    |
| ---- | --------------- | ------------------------------------------ |
| 400  | from이 to보다 늦음 | `시작 날짜가 끝 날짜보다 늦습니다.`      |
| 400  | 121일 넘음      | `기간은 최대 121일까지 조회할 수 있습니다.` |

### 4-4. 적용 / 넘기기 — `PATCH /api/recommendations/{id}`

**요청 본문**

```json
{ "status": "APPLIED" }
```

| status    | 뜻            |
| --------- | ------------- |
| `APPLIED` | 운영자가 적용 |
| `IGNORED` | 운영자가 넘김 |

- **응답:** 바뀐 추천 1건 (4-1 모양). `appliedBy`는 `"운영자"`, `appliedAt`은 지금
- 이벤트 로그에 `HANDLE` 1줄이 남음 (예: `방전 500 kW 적용 (운영자)`)
- 적용하면 시뮬레이터가 ESS를 그 추천대로 움직임. "적용 시각 + `operation.decision_valid_minutes`분"과 "그 15분 구간 끝" 중 빠른 때까지 유지
- **오류**

| 상태 | 언제                                         | message                                            |
| ---- | -------------------------------------------- | -------------------------------------------------- |
| 400  | status가 `APPLIED` · `IGNORED`가 아님        | `status는 APPLIED 또는 IGNORED여야 합니다.`         |
| 400  | status가 비어 있음                           | `status: ` + 검증 기본 문구                         |
| 404  | 없는 id                                      | `추천을 찾을 수 없습니다. id=12`                    |
| 400  | 가장 최근 추천이 아니거나, 그 15분 구간이 끝남 | `이미 지난 추천입니다. 최신 추천을 확인해 주세요.` |
| 400  | 이미 처리한 추천                             | `이미 처리한 추천입니다.`                           |

### 4-5. 24시간 ESS 계획 — `GET /api/ess-plans/latest` (화면에서 안 씀)

판단 엔진이 1분마다 짜는 24시간(15분 96칸) 충 · 방전 계획.

- **오류:** 계획은 시뮬레이터를 켠 서버의 메모리에만 있음. 다른 서버에서 부르면 404 `아직 ESS 계획이 없습니다. 판단 엔진은 시뮬레이터를 켠 서버에서만 돕니다.`

| 필드               | 설명                                                                     |
| ------------------ | ------------------------------------------------------------------------ |
| plannedAt          | 계획을 짠 시각                                                           |
| startSocPercent    | 계획을 시작할 때 잔량 %                                                  |
| lineKw             | 방어선 kW (보통 목표 피크. 배터리로 다 못 막는 날은 막을 수 있는 가장 낮은 선) |
| marginKw           | 방어선 아래로 둔 여유 kW                                                 |
| loadForecastMethod | 부하 예측 방법 (예: `최근 1시간 평균`)                                   |
| slots              | 96칸 배열 (아래 표)                                                      |

`slots` 한 칸

| 필드       | 설명                                                                                  |
| ---------- | ------------------------------------------------------------------------------------- |
| slotStart  | 칸 시작 시각                                                                          |
| loadKw     | 예상 부하                                                                             |
| solarKw    | 예상 태양광                                                                           |
| netKw      | ESS 없이 한전 구매 예상 (음수면 태양광이 남음)                                        |
| price      | 전력량 단가 (원/kWh)                                                                  |
| periodType | 요금 시간대. **DB 코드 그대로** `OFF_PEAK` / `MID_PEAK` / `ON_PEAK` (다른 API의 `LIGHT` · `MID` · `PEAK`와 다름) |
| essKw      | 계획한 ESS 출력 (+ 방전 / − 충전)                                                     |
| gridKw     | 계획대로 하면 한전 구매 (0 미만은 0)                                                  |
| socPercent | 이 칸이 끝날 때 예상 잔량 %                                                           |

---

## 5. 이벤트 (7 · 8번)

### 이벤트 1건의 모양

```json
{
  "id": 31,
  "occurredAt": "2026-11-22T17:05:20",
  "level": "CRITICAL",
  "message": "방어선 초과 예상, 방전 155 kW 추천",
  "eventType": "RECOMMEND",
  "category": "RECOMMEND",
  "recommendationId": 1234
}
```

| 필드             | 설명                                                    |
| ---------------- | ------------------------------------------------------- |
| level            | `CRITICAL`(경고) / `WARNING`(주의) / `NORMAL`(정상) / `INFO`(단계 없음) |
| message          | 화면에 그대로 보여 줄 문장                              |
| eventType        | 이벤트 종류 (아래 표). 화면의 종류 칸 · 필터가 씀       |
| category         | `eventType`을 화면 코드로 바꾼 값 (프론트는 안 씀)      |
| recommendationId | 관련 추천 번호. 없으면 null                             |

| eventType      | category    | 뜻                      | 지금 만드는 곳        |
| -------------- | ----------- | ----------------------- | --------------------- |
| `STAGE_CHANGE` | `STAGE`     | 피크 단계 변화          | 있음                  |
| `RECOMMEND`    | `RECOMMEND` | 추천 생성               | 있음                  |
| `HANDLE`       | `ACTION`    | 적용 · 넘김 · 자동 적용 | 있음                  |
| `INTERVAL_END` | `WINDOW`    | 15분 구간 종료와 결과   | 있음                  |
| `SETTING`      | `SETTING`   | 설정 변경               | **없음** (코드값만 있음) |
| `COMM`         | `COMM`      | 통신                    | **없음** (코드값만 있음) |

`level`은 DB 단계를 바꾼 값: `NORMAL` → `NORMAL`, `CAUTION`(주의) → `WARNING`, `WARNING`(경고) → `CRITICAL`, 단계 없음 → `INFO`

### 5-1. 최근 이벤트 — `GET /api/events?limit=`

| 쿼리  | 설명                             |
| ----- | -------------------------------- |
| limit | 몇 건 (기본 6, 1 ~ 50). 통합 화면은 30 |

- **응답:** 최신 순 (같은 시각이면 번호 큰 순)
- **오류:** 400 `limit은 1 ~ 50 사이여야 합니다.`

### 5-2. 기간 이벤트 — `GET /api/events?from=&to=`

| 쿼리     | 예                                           | 설명                       |
| -------- | -------------------------------------------- | -------------------------- |
| from, to | `2026-11-22T00:00:00`, `2026-11-23T00:00:00` | 시각. from 이상 ~ to 미만  |

- `from`과 `to`가 둘 다 있으면 5-1 대신 이 조회가 됨
- **응답:** **오래된 순**
- **오류**

| 상태 | 언제                    | message                                    |
| ---- | ----------------------- | ------------------------------------------ |
| 400  | from이 to보다 늦거나 같음 | `시작 시각이 끝 시각보다 빨라야 합니다.`  |
| 400  | 7일(168시간) 넘음       | `기간은 최대 168시간까지 조회할 수 있습니다.` |

---

## 6. 설비별 전력 — `GET /api/equipment-powers?from=&to=` (9번)

운영 로그 › 전력 이력의 설비별 표.

| 쿼리     | 예                                           | 설명                      |
| -------- | -------------------------------------------- | ------------------------- |
| from, to | `2026-11-22T14:00:00`, `2026-11-22T15:00:00` | 시각. from 이상 ~ to 미만 |

```json
[
  {
    "windowStart": "2026-11-22T14:00:00",
    "equipmentId": "BRZ-01",
    "name": "브레이징로",
    "zoneCode": "Z2",
    "zoneName": "브레이징",
    "avgKw": 512.3,
    "runningPercent": 100.0
  }
]
```

| 필드           | 설명                             |
| -------------- | -------------------------------- |
| windowStart    | 15분 구간 시작                   |
| avgKw          | 그 15분 평균 kW                  |
| runningPercent | 그 15분 중 가동한 분의 비율 %    |

- 공장 부하 설비만. 구간 시간 순, 같은 구간 안에서는 많이 쓴 순
- **오류:** 5-2와 같음 (from < to, 최대 168시간)

---

## 7. 분석 (10 · 11번)

### 7-1. 추이 분석 — `GET /api/analyses/trend?from=&to=&interval=`

| 쿼리     | 예                         | 설명                                        |
| -------- | -------------------------- | ------------------------------------------- |
| from, to | `2026-11-16`, `2026-11-22` | 날짜 기간 (양 끝 포함, 최대 121일)          |
| interval | `QUARTER` / `HOUR` / `DAY` | 15분 / 1시간 / 1일로 묶은 평균. 없으면 `HOUR` |

**오류**

| 상태 | 언제            | message                                         |
| ---- | --------------- | ----------------------------------------------- |
| 400  | interval이 틀림 | `interval은 QUARTER, HOUR, DAY 중 하나여야 합니다.` |
| 400  | from이 to보다 늦음 | `시작 날짜가 끝 날짜보다 늦습니다.`          |
| 400  | 121일 넘음      | `기간은 최대 121일까지 조회할 수 있습니다.`     |

**응답 예시** (배열은 줄임)

```json
{
  "from": "2026-11-16",
  "to": "2026-11-22",
  "interval": "HOUR",
  "targetPeakKw": 1800.0,
  "socLowLimit": 20.0,
  "socHighLimit": 90.0,
  "roomTempLimitC": 35.0,
  "summary": {
    "maxPeakKw": 1797.0,
    "maxPeakAt": "2026-11-18T08:45:00",
    "avgGridKw": 1334.0,
    "solarKwh": 23087.0,
    "solarPercentOfLoad": 9.7,
    "dischargeKwh": 4230.0,
    "chargeKwh": 3408.0,
    "efficiencyPercent": 89.5,
    "socMinPercent": 20.0,
    "socMinAt": "2026-11-16T22:45:00",
    "socMaxPercent": 90.0,
    "roomTempMaxC": 29.7,
    "roomTempMaxAt": "2026-11-18T19:30:00"
  },
  "points": [
    {
      "recordedAt": "2026-11-16T00:00:00",
      "loadKw": 1320.0,
      "gridKw": 1570.0,
      "solarKw": 0.0,
      "essKw": -250.0,
      "socPercent": 61.2,
      "roomTempC": 24.5,
      "outsideTempC": null
    }
  ],
  "weather": [{ "date": "2026-11-16", "text": "맑음" }],
  "hourly": [{ "hour": 0, "avgGridKw": 1402.0, "period": "LIGHT" }],
  "byStatus": [
    { "status": "APPLIED", "count": 15, "defendedCount": 15, "defendedPercent": 100.0, "avgResultPeakKw": 1730.0 }
  ],
  "highlights": [
    { "at": "2026-11-18T08:45:00", "label": "15분 피크 최대", "value": 1797.0, "unit": "kW", "level": "INFO" }
  ]
}
```

| 필드                                    | 설명                                                                                               |
| --------------------------------------- | -------------------------------------------------------------------------------------------------- |
| targetPeakKw / socLowLimit / socHighLimit / roomTempLimitC | 그래프 기준선. 설정 `peak.target_kw` / `ess.soc_low` / `ess.soc_high` / `ess.room_temp_limit` |
| summary                                 | 위쪽 요약 숫자. 기록이 하나도 없으면 null. 값을 못 구한 칸도 null                                   |
| summary.maxPeakKw / maxPeakAt           | 기간 중 최고 15분 평균 한전 구매와 그 구간 시작 시각                                               |
| summary.solarPercentOfLoad              | 공장 소비 중 태양광 비율 %                                                                         |
| summary.efficiencyPercent               | 충 · 방전 효율 = (방전량 + 기간 중 저장량 변화) ÷ 충전량                                           |
| points                                  | interval로 묶은 평균. `recordedAt`은 묶음 시작 시각, `essKw`는 **+ 방전 / − 충전**, 기록 없는 값은 null |
| weather                                 | 날짜별 날씨 (그날 가장 많이 나온 하늘 상태)                                                        |
| hourly                                  | 0 ~ 23시 각각의 평균 한전 구매와 요금 시간대(`LIGHT` / `MID` / `PEAK`, 요금표 없으면 null)          |
| byStatus                                | 방전 추천을 처리별(`APPLIED` / `AUTO` / `IGNORED`)로 묶은 결과. 0건이면 `defendedPercent`, `avgResultPeakKw`는 null |
| highlights                              | 눈에 띄는 순간 `{ at, label, value, unit, level }`. level은 `CRITICAL`(목표 초과) / `INFO`         |

### 7-2. 피크 분석 — `GET /api/analyses/peak?from=&to=`

| 쿼리     | 예                         | 설명                               |
| -------- | -------------------------- | ---------------------------------- |
| from, to | `2026-11-01`, `2026-11-22` | 날짜 기간 (양 끝 포함, 최대 121일) |

- **오류:** 7-1의 from · to 오류와 같음
- 기록이 없으면 `{ "from", "to", "hasData": false, "targetPeakKw" }`이고 나머지는 null

```json
{
  "from": "2026-11-01",
  "to": "2026-11-22",
  "hasData": true,
  "targetPeakKw": 1800.0,
  "summary": {
    "maxPeakKw": 1841.0,
    "maxPeakAt": "2026-11-02T10:30:00",
    "exceededCount": 3,
    "windowCount": 2016,
    "avgReductionKw": 122.0,
    "maxReductionKw": 306.0,
    "busyFrom": "16:00",
    "busyTo": "19:00",
    "busyPeriodLabel": "최대부하"
  },
  "dailyPeaks": [{ "date": "2026-11-01", "peakKw": 1612.0, "expectedKw": 1655.0 }],
  "heatmap": [{ "date": "2026-11-01", "hour": 0, "peakKw": 1190.0 }],
  "topWindows": [
    {
      "windowStart": "2026-11-02T10:30:00",
      "windowEnd": "2026-11-02T10:45:00",
      "peakKw": 1841.0,
      "status": null,
      "note": "방전 추천 없음"
    }
  ],
  "exceededWindows": []
}
```

| 필드                                        | 설명                                                                                    |
| ------------------------------------------- | --------------------------------------------------------------------------------------- |
| summary.exceededCount / windowCount         | 목표를 넘은 15분 구간 수 / 기간 안의 다 찬 15분 구간 수                                 |
| summary.avgReductionKw / maxReductionKw     | 날마다 (배터리가 없었다면 피크 − 실제 피크)의 평균 / 최대                               |
| summary.busyFrom / busyTo / busyPeriodLabel | 피크가 잦은 시간대. 요금표가 없으면 null                                                |
| dailyPeaks                                  | 날마다 실제 최대 피크(`peakKw`)와 배터리가 없었다면 예상 최대 피크(`expectedKw`)        |
| heatmap                                     | 날짜 × 시각(0 ~ 23)마다 그 시간 안 15분 구간 중 최대                                    |
| topWindows                                  | 피크가 높았던 구간 8개 (높은 순)                                                        |
| exceededWindows                             | 목표를 넘은 15분 구간 전부 (시간 순). 추이 그래프의 점(●)용                             |

`topWindows` · `exceededWindows` 한 줄: `{ windowStart, windowEnd, peakKw, status, note }`

- `status`: 그 구간 방전 추천의 처리 (`APPLIED` / `AUTO` / `IGNORED` / `RECOMMENDED`). 방전 추천이 없었으면 null
- `note`: 추천 설명 문장. 추천이 없었으면 `방전 추천 없음`

---

## 8. 월간 성적표 — `GET /api/reports?month=` (12번)

| 쿼리  | 예        | 설명               |
| ----- | --------- | ------------------ |
| month | `2026-11` | 달 (`YYYY-MM`)     |

- 이번 달이면 오늘까지, 지난 달이면 한 달 전체
- **오류:** 400 `month 형식이 올바르지 않습니다. (예: 2026-11)`
- 기록이 없는 달: `{ "month": "2026-12", "hasData": false, "targetPeakKw": 1800.0 }` (나머지는 null)

**응답 예시** (배열은 줄임)

```json
{
  "month": "2026-11",
  "hasData": true,
  "periodStart": "2026-11-01",
  "periodEnd": "2026-11-22",
  "targetPeakKw": 1800.0,
  "summary": {
    "defenseTotal": 36,
    "defenseSuccess": 33,
    "defenseSuccessPercent": 91.7,
    "monthPeakKw": 1841.0,
    "monthPeakAt": "2026-11-02T10:30:00",
    "avgReductionKw": 122.0,
    "maxReductionKw": 306.0
  },
  "dailyPeaks": [{ "date": "2026-11-01", "peakKw": 1612.0, "expectedKw": 1655.0 }],
  "heatmap": [{ "date": "2026-11-01", "hour": 0, "peakKw": 1190.0 }],
  "bill": {
    "total": 57771897,
    "energyCharge": 42454777,
    "baseCharge": 15317120,
    "baseChargeOverTarget": 341120,
    "monthEndEstimate": 95140000,
    "lastMonthTotal": null,
    "byTariff": [{ "period": "LIGHT", "label": "경부하", "kwh": 193339.0, "percent": 38.6, "charge": 11793679 }]
  },
  "exceeded": [
    {
      "windowStart": "2026-11-11T10:30:00",
      "windowEnd": "2026-11-11T10:45:00",
      "resultPeakKw": 1817.0,
      "overKw": 17.0,
      "action": "DISCHARGE",
      "powerKw": 500,
      "status": "IGNORED",
      "cause": "적용하면 이번 15분 평균이 ..."
    }
  ],
  "ess": {
    "dailyDischarge": [{ "date": "2026-11-01", "kwh": 420.0 }],
    "dischargeKwh": 8920.0,
    "chargeKwh": 9966.0,
    "efficiencyPercent": 89.5,
    "cycles": 8.9,
    "avgSocPercent": 58.3,
    "minSocPercent": 22.4,
    "blockedCount": 1
  },
  "solar": {
    "generationKwh": 98420.0,
    "selfUseKwh": 97928.0,
    "selfUsePercent": 99.5,
    "gridReductionKwh": 97928.0,
    "emissionFactor": 0.4594,
    "carbonTon": 45.0
  }
}
```

| 묶음       | 필드                                                  | 설명                                                                         |
| ---------- | ----------------------------------------------------- | ---------------------------------------------------------------------------- |
| (맨 위)    | periodStart / periodEnd                               | 집계 기간 (그 달 1일 ~ 이번 달이면 오늘)                                     |
| summary    | defenseTotal / defenseSuccess / defenseSuccessPercent | 피크 방어 추천 수 / 그중 목표 이하로 끝난 수 / 성공률 %                      |
|            | monthPeakKw / monthPeakAt                             | 이번 달 최대 15분 피크와 그 구간 시작 시각                                   |
|            | avgReductionKw / maxReductionKw                       | 날마다 (배터리가 없었다면 피크 − 실제 피크)의 평균 / 최대                    |
| dailyPeaks |                                                       | 피크 분석(7-2)과 같은 모양                                                   |
| heatmap    |                                                       | 피크 분석(7-2)과 같은 모양                                                   |
| bill       | total                                                 | 합계 = 전력량요금 + 기본요금 (원, **추정**)                                  |
|            | energyCharge                                          | 전력량요금 = 시간대별 kWh × 단가                                             |
|            | baseCharge                                            | 기본요금 = 이번 달 최대 피크 × 기본요금 단가                                 |
|            | baseChargeOverTarget                                  | 목표 피크를 넘어서 더 낸 기본요금                                            |
|            | monthEndEstimate                                      | 월말 예상 요금                                                               |
|            | lastMonthTotal                                        | 전달 요금. 기록이 없으면 null                                                |
|            | byTariff                                              | 요금 시간대별 `{ period, label, kwh, percent, charge }`                      |
| exceeded   |                                                       | 목표를 넘은 15분 구간 (**최신 순**). 그 구간 추천이 없으면 `action`, `powerKw`, `status`는 null |
| ess        | dailyDischarge                                        | 날짜별 방전량 `{ date, kwh }`                                                |
|            | dischargeKwh / chargeKwh                              | 방전 / 충전 합계                                                             |
|            | efficiencyPercent                                     | 효율 = (방전 + 기간 중 저장량 변화) ÷ 충전 × 100                             |
|            | cycles                                                | 충 · 방전 횟수 = 방전량 ÷ 배터리 용량                                        |
|            | avgSocPercent / minSocPercent                         | 평균 / 최저 잔량                                                             |
|            | blockedCount                                          | 적용했는데도 목표를 넘은 횟수                                                |
| solar      | generationKwh / selfUseKwh / selfUsePercent           | 발전량 / 공장이 직접 쓴 양 / 그 비율 %                                       |
|            | gridReductionKwh                                      | 한전 구매를 줄인 양 (= selfUseKwh)                                           |
|            | emissionFactor / carbonTon                            | 탄소 배출계수 kgCO2/kWh (설정 `carbon.emission_factor`) / 탄소 감축 **추정** t |

- 금액과 탄소는 화면이 항상 "추정"을 붙임

---

## 9. 환경 설정 (13 ~ 15번)

### 9-1. 기준값 목록 — `GET /api/settings`

- **요청:** 없음
- **응답:** 기준값 배열, 키 이름 순

```json
[
  {
    "key": "peak.target_kw",
    "group": "피크",
    "label": "목표 피크",
    "value": "1800",
    "unit": "kW",
    "updatedAt": "2026-11-20T16:20:00",
    "updatedBy": "운영자"
  }
]
```

| 필드                 | 설명                                                                 |
| -------------------- | -------------------------------------------------------------------- |
| key                  | 설정 키 (아래 표)                                                    |
| group                | 묶음 이름: `피크` / `ESS` / `운영` / `태양광` / `환경`               |
| label / unit         | 화면 이름 / 단위 (DB 값 그대로)                                      |
| value                | **글자**로 옴 (DB가 VARCHAR). 숫자 · true/false 변환은 프론트 Store가 함 |
| updatedAt / updatedBy | 마지막으로 바꾼 시각 / 사람                                         |

설정 키 (DB에 있는 16개)

| key                                | 뜻                        | 화면에서 바꾸기              |
| ---------------------------------- | ------------------------- | ---------------------------- |
| `peak.target_kw`                   | 목표 피크                 | 1 ~ 100,000                  |
| `peak.warn_ratio`                  | 주의선 (목표의 %)         | 50 ~ 100                     |
| `tariff.base_charge`               | 기본요금 단가             | 보기만                       |
| `ess.capacity_kwh`                 | 배터리 용량               | 보기만                       |
| `ess.max_power_kw`                 | 최대 충 · 방전 출력       | 보기만                       |
| `ess.efficiency_percent`           | 충 · 방전 효율            | 보기만                       |
| `ess.soh_percent`                  | 배터리 건강도             | 보기만                       |
| `ess.soc_low`                      | 잔량 하한 (%)             | 0 ~ 100, 상한보다 작아야 함  |
| `ess.soc_high`                     | 잔량 상한 (%)             | 0 ~ 100, 하한보다 커야 함    |
| `ess.room_temp_limit`              | 배터리실 온도 한계 (℃)    | 0 ~ 80                       |
| `solar.capacity_kw`                | 태양광 설비 용량          | 보기만                       |
| `solar.performance_ratio`          | 태양광 성능비             | 보기만                       |
| `carbon.emission_factor`           | 탄소 배출계수             | 보기만                       |
| `operation.auto_mode`              | 자동 모드                 | `true` / `false`             |
| `operation.poll_seconds`           | 화면 갱신 주기 (초)       | 1 ~ 60                       |
| `operation.decision_valid_minutes` | 추천 적용 유지 시간 (분)  | 1 ~ 60                       |

- 설비 임계치 `equipment.{설비 id}.max_kw`(예: `equipment.INJ-01.max_kw`)는 백엔드가 1 ~ 100,000으로 저장을 허용하지만 **DB에 행이 없어서** 지금은 목록에 안 오고 저장하면 400 `없는 설정입니다: ...`
- 그래서 프론트는 설비 정격 전력을 기본값으로 씀 (`src/stores/useSettingsStore.js`, 이슈 #35-2). DB에 행이 생기면 서버 값이 우선

### 9-2. 기준값 저장 — `PUT /api/settings`

**요청 본문** (여러 개를 한 번에, value는 글자)

```json
[
  { "key": "peak.target_kw", "value": "1750" },
  { "key": "peak.warn_ratio", "value": "95" }
]
```

- **응답:** 저장된 뒤의 전체 목록 (9-1과 같은 모양)
- 값이 그대로인 키는 건너뜀 (보기만 하는 키가 같은 값으로 같이 와도 괜찮음)
- 바뀐 키마다 변경 기록 1줄이 남음 (처리자 `"운영자"`). 이벤트 로그에는 안 남음
- **하나라도 틀리면 아무것도 저장하지 않음**
- 저장하면 백엔드 설정 캐시를 다시 읽어서 판단 엔진 · 시뮬레이터에 바로 반영됨
- **오류**

| 상태 | 언제                              | message                                                    |
| ---- | --------------------------------- | ---------------------------------------------------------- |
| 400  | 빈 배열                           | `저장할 값이 없습니다.`                                    |
| 400  | key나 value가 비어 있음           | `key와 value는 비워 둘 수 없습니다.`                       |
| 400  | 없는 키                           | `없는 설정입니다: equipment.INJ-01.max_kw`                 |
| 400  | 보기만 하는 키의 값을 바꿈        | `배터리 용량은(는) 화면에서 바꿀 수 없는 값입니다.`        |
| 400  | 숫자가 아님 (`1e3`, `NaN` 등도 막음) | `목표 피크 값은 숫자여야 합니다.`                        |
| 400  | 범위를 벗어남                     | `주의선 값은 50 ~ 100 사이여야 합니다.`                    |
| 400  | 자동 모드가 true/false가 아님     | `자동 모드 값은 true 또는 false여야 합니다.`               |
| 400  | 잔량 하한 ≥ 상한                  | `잔량 하한은 잔량 상한보다 작아야 합니다.`                 |

(message 앞의 이름은 DB의 `label` 값이 들어감. 위 예의 이름은 예시)

### 9-3. 변경 기록 — `GET /api/setting-histories?from=&to=`

| 쿼리     | 예                         | 설명                                 |
| -------- | -------------------------- | ------------------------------------ |
| from, to | `2026-10-24`, `2026-11-22` | 날짜 기간 (양 끝 포함, 최대 기간 없음) |

- **응답:** 최신 순
- **오류:** 400 `시작 날짜가 끝 날짜보다 늦습니다.`

```json
[
  {
    "id": 6,
    "changedAt": "2026-11-20T16:20:00",
    "key": "peak.target_kw",
    "label": "목표 피크",
    "oldValue": "1750",
    "newValue": "1800",
    "changedBy": "운영자"
  }
]
```

- `oldValue`, `newValue`는 글자. `label`은 지금 setting 표에서 찾아 붙인 이름

---

## 10. 화면에서 안 쓰는 API (17 ~ 21번)

### 10-1. 요금 시간대표 — `GET /api/tariff-periods?season=`

- `season`: `SUMMER` / `SPRING_FALL` / `WINTER`. 없으면 전체
- **오류:** 400 `season은 SUMMER, SPRING_FALL, WINTER 중 하나여야 합니다.`
- **응답:** `[{ id, season, period, label, from, to, unitPrice }]`
  - period: `LIGHT` / `MID` / `PEAK`, from · to: `HH:mm` (하루 끝은 `"24:00"`), unitPrice: 원/kWh
- 계절 기준 (한전): 6 ~ 8월 여름, 3 ~ 5월 · 9 ~ 10월 봄가을, 나머지 겨울

### 10-2. 구역 목록 — `GET /api/zones`

- **응답:** `[{ zoneCode, name }]`, 구역 코드 순

### 10-3. 설비 목록 — `GET /api/equipment`

- **응답:** `[{ equipmentId, zoneCode, name, equipmentRole, ratedKw }]`
- equipmentRole: `GRID`(한전) / `SOLAR` / `ESS` / `LOAD`(공장 부하 설비)
- 이름 주의: 같은 구역 값을 여기와 `/api/zones`는 `zoneCode`, 지금 상태(3-1)는 `zoneId`라고 부름

### 10-4. DB 상태 — `GET /api/system/db-status`

- **응답:** `{ supabase, target, since }`
  - supabase: `UP` / `DOWN` (공용 DB 상태)
  - target: `primary`(Supabase) / `backup`(백업 DB)
  - since: 마지막으로 바뀐 시각
- Supabase가 계속 응답하지 않으면 백업 DB로 자동 전환되고, 살아나면 되돌아감

### 10-5. 쓸 DB 고정 — `POST /api/system/db-target?target=`

- target: `primary` / `backup` (고정) / `auto` (자동 전환으로 되돌림)
- **응답:** 10-4와 같은 모양
- **오류:** 400 `target은 primary, backup, auto 중 하나여야 합니다.`
- 서버 동작이 바뀌는 관리용 API라 화면에서 부르지 않음

---

## 11. 코드 값 모음

| 어디                             | 필드              | 값                                                                  |
| -------------------------------- | ----------------- | ------------------------------------------------------------------- |
| 지금 상태                        | peakStatus        | `NORMAL` / `WARNING` / `CRITICAL`                                   |
| 지금 상태                        | pcsStatus         | `CHARGING` / `DISCHARGING` / `STANDBY`                              |
| 지금 상태 설비                   | origin            | `MEASURED` / `SIMULATED`                                            |
| 지금 상태 설비                   | state             | `RUNNING` / `STOPPED` / null                                        |
| 요금 시간대 (ESS 계획 빼고 모두) | period            | `LIGHT` 경부하 / `MID` 중간부하 / `PEAK` 최대부하                   |
| ESS 계획                         | periodType        | `OFF_PEAK` / `MID_PEAK` / `ON_PEAK`                                 |
| 요금 시간대표                    | season            | `SUMMER` / `SPRING_FALL` / `WINTER`                                 |
| 추천                             | action            | `CHARGE` / `DISCHARGE` / `IDLE`                                     |
| 추천                             | reasonType        | `PEAK_DEFENSE` / `SAFETY_LIMIT` / `ON_PEAK_DISCHARGE` / `SOLAR_SURPLUS_CHARGE` / `OFF_PEAK_CHARGE` / `IDLE` |
| 추천 · 분석 · 성적표             | status            | `RECOMMENDED` / `APPLIED` / `AUTO` / `IGNORED`                      |
| 추천                             | resultStatus      | `DEFENDED` / `EXCEEDED` / null                                      |
| 이벤트                           | level             | `CRITICAL` / `WARNING` / `NORMAL` / `INFO`                          |
| 이벤트                           | eventType         | `STAGE_CHANGE` / `RECOMMEND` / `HANDLE` / `INTERVAL_END` / `SETTING` / `COMM` |
| 추이 분석                        | interval          | `QUARTER` / `HOUR` / `DAY`                                          |
| 추이 분석                        | highlights.level  | `CRITICAL` / `INFO`                                                 |
| 설비 목록                        | equipmentRole     | `GRID` / `SOLAR` / `ESS` / `LOAD`                                   |

---

## 12. 알아 둘 점

### 팀 시트(v0.2)와 달라진 것

- **시트에 있었지만 만들지 않은 API**
  - `GET /api/site-powers/recent` (6번): 화면에서 안 쓰게 돼서 뺌
  - `PATCH /api/aircons` (14번), `POST /api/demo-scenarios` (17번): 없음
  - `GET /api/events?stage=&type=` (15번): 기간 조회(5-2)만 있고 단계 · 종류 거르기는 없음
  - `GET /api/demand-intervals` (16번): 피크 분석 응답의 `topWindows`로 대신함
- **시트에 없던 API:** 설비별 전력(9번), 기간 이벤트(8번), 피크 분석(11번), ESS 계획(16번), 구역 · 설비 목록, DB 상태
- **기간 제한:** 추천 이력 · 추이 분석 · 피크 분석 모두 최대 121일 (시트는 62일)
- **추천 응답:** `durationMinutes`는 null, `usedValues`는 빈 배열 (DB에 칸이 없음)

### 응답을 쓸 때 주의할 것

- 충전 중에는 `sourceRatio.essPercent`가 음수, `gridPercent`가 100을 넘음 (3-1, 이슈 #35-1)
- 분이 바뀌는 순간 설비 `loadKw`가 비어서 올 수 있음 (3-1, 이슈 #35-3)
- 설비 임계치 설정 행이 DB에 없음 (9-1, 이슈 #35-2)
- 요금 시간대 코드는 ESS 계획(16번)만 DB 코드(`OFF_PEAK` 등)를 그대로 줌
- 이벤트 `SETTING` · `COMM` 종류는 코드값만 있고 지금 만드는 곳이 없음. 설정을 바꿔도 이벤트 로그에는 안 남음

### 프론트 코드 주석과 다른 곳 (동작에는 문제 없음)

- `src/api/equipmentPowerApi.js` 주석 "최대 24시간" → 백엔드는 7일(168시간)
- `src/api/eventApi.js` 주석 "최대 48시간" → 백엔드는 7일(168시간)
- `src/pages/HistoryPage.vue`(추천 이력)는 최대 62일로 막음 → 백엔드는 121일까지 됨
