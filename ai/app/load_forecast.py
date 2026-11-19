"""
공장 부하 96구간 예측값 꺼내기.

예측값은 ai/forecast/train_load_forecast.py 가 학습한 모델로 미리 만든 1년치 값입니다 (data/load_forecast_2018.json).
모델은 "매일 0시에 그날 96구간을 예측"하도록 학습했으므로, 하루치 예측을 미리 계산해 두고 꺼내 쓰는 것과 같습니다.

- 데이터 해(2018년)와 지금 해가 달라서, 공장 부하는 같은 달·같은 요일인 2018년 날짜의 값을 씁니다.
  (프론트 가짜 백엔드 fakeBackend.js 의 loadDayIndexOf 와 같은 규칙)
- 원본 값은 15분 전력량(kWh)이라, 우리 공장 크기 kW로 바꿔서 돌려줍니다.
  공장 kW = 1,300 + 1.35 × (kWh × 4)   (frontend/docs/ess-recommendation.md 의 환산식)

이 파일은 파이썬 기본 라이브러리만 씁니다.
"""

import json
from datetime import date, datetime, timedelta
from pathlib import Path

DATA_FILE = Path(__file__).resolve().parent / "data" / "load_forecast_2018.json"
DATA_YEAR = 2018
SLOTS_PER_DAY = 96
BASE_LOAD_KW = 1300
LOAD_SCALE = 1.35
MODEL_NAME = "HistGradientBoostingRegressor (ai/forecast/train_load_forecast.py)"


def load_data(path: Path = DATA_FILE) -> dict:
    """예측값 파일을 읽음. loadKwh = 평균 예측, peakLoadKwh = 상위 90% 예측 (각 35,040칸)"""
    data = json.loads(path.read_text(encoding="utf-8"))
    return {"mean": data["loadKwh"], "peak": data["peakLoadKwh"]}


def js_weekday(d: date) -> int:
    """자바스크립트 getDay()와 같은 요일 번호 (일요일 0 ~ 토요일 6)"""
    return (d.weekday() + 1) % 7


def data_day_index(d: date) -> int:
    """지금 날짜에 쓸 2018년 날짜가 1월 1일부터 며칠째인지 (0부터). 같은 달·같은 요일로 맞춤"""
    day = min(d.day, 28) if d.month == 2 else d.day  # 2018년에는 2월 29일이 없음
    base = date(DATA_YEAR, d.month, day)
    shift = js_weekday(d) - js_weekday(base)
    if shift > 3:
        shift -= 7
    if shift < -3:
        shift += 7
    index = (base - date(DATA_YEAR, 1, 1)).days + shift
    if index < 0:
        return index + 7
    if index > 364:
        return index - 7
    return index


def to_factory_kw(kwh: float) -> float:
    """15분 전력량(kWh) → 우리 공장 크기 kW"""
    return BASE_LOAD_KW + LOAD_SCALE * kwh * 4


def quarter_start(time: datetime) -> datetime:
    """시각이 들어 있는 15분 칸의 시작 (예: 14:51 → 14:45)"""
    return time.replace(minute=time.minute // 15 * 15, second=0, microsecond=0)


def forecast(data: dict, start: datetime, slots: int) -> list[dict]:
    """start가 들어 있는 15분 칸부터 slots칸의 부하 예측 (자정을 넘어가면 다음 날 예측을 이어 붙임)"""
    first = quarter_start(start)
    result = []
    for k in range(slots):
        slot_start = first + timedelta(minutes=15 * k)
        index = data_day_index(slot_start.date()) * SLOTS_PER_DAY + slot_start.hour * 4 + slot_start.minute // 15
        result.append({
            "slot_start": slot_start.isoformat(),
            "load_kw": round(to_factory_kw(data["mean"][index]), 1),
            "peak_load_kw": round(to_factory_kw(data["peak"][index]), 1),
        })
    return result
