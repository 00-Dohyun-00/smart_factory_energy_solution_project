# 공용 DB 값으로 1년(15분 칸) 시뮬레이션 입력을 만든다. (DB에서 받은 CSV를 읽음, DB에 직접 붙지 않음)
#   - 태양광: DB weather(Open-Meteo)의 그해 일사량 → 백엔드 SolarForecastService와 같은 식
#       발전량 kW = 일사량 W/m2 x solar.capacity_kw x solar.performance_ratio / 1000 (0 ~ 용량)
#       Open-Meteo 1시간 값은 "그 시각 직전 1시간 평균"이라, H시 칸에는 (H+1)시 값을 씀
#   - 요금: DB tariff_period 그대로 (백엔드와 같음. 토요일·주말 할인 규칙은 DB 표에 없어서 넣지 않음)
#   - 공장 부하 · AI 예측: 그해에 맞는 실측이 없어서 prep_inputs.py와 같게 UCI 2018년 같은 달·같은 요일 값
# 사용법: python -I prep_inputs_db.py <프로젝트 루트> <weather csv> <tariff csv> <setting csv> <연도> <출력 csv>
import csv
import datetime as dt
import json
import sys

root, weather_path, tariff_path, setting_path, year, out_path = sys.argv[1:7]
YEAR = int(year)
energy = json.load(open(f"{root}/frontend/src/api/fakeEnergyData.json", encoding="utf-8"))
forecast = json.load(open(f"{root}/ai/app/data/load_forecast_2018.json", encoding="utf-8"))
LOAD_YEAR, BASE_LOAD_KW, LOAD_SCALE = 2018, 1300, 1.35

radiation = {r["ts"]: float(r["radiation_wm2"]) for r in csv.DictReader(open(weather_path, encoding="utf-8"))}
settings = {r["setting_key"]: r["value"] for r in csv.DictReader(open(setting_path, encoding="utf-8"))}
capacity = float(settings["solar.capacity_kw"])
ratio = float(settings["solar.performance_ratio"])
ratio = ratio / 100 if ratio > 1 else ratio
tariff = list(csv.DictReader(open(tariff_path, encoding="utf-8")))
CODE = {"OFF_PEAK": "LIGHT", "MID_PEAK": "MID", "ON_PEAK": "PEAK"}


def season_of(d):  # 백엔드 seasonOf와 같음
    if 6 <= d.month <= 8:
        return "SUMMER"
    if 3 <= d.month <= 10:
        return "SPRING_FALL"
    return "WINTER"


def js_day(d):
    return (d.weekday() + 1) % 7


def day_of_year(y, month, day):
    return (dt.date(y, month, day) - dt.date(y, 1, 1)).days


def load_day_index(d):  # AI 서버 data_day_index · 가짜 백엔드 loadDayIndexOf와 같음
    day = min(d.day, 28) if d.month == 2 else d.day
    shift = js_day(d) - js_day(dt.date(LOAD_YEAR, d.month, day))
    if shift > 3:
        shift -= 7
    if shift < -3:
        shift += 7
    index = day_of_year(LOAD_YEAR, d.month, day) + shift
    return index + 7 if index < 0 else index - 7 if index > 364 else index


def tariff_at(d, minute_of_day):  # 칸 가운데 시각의 요금 (백엔드 periodAt과 같게)
    season = season_of(d)
    hhmm = f"{minute_of_day // 60:02d}:{minute_of_day % 60:02d}"
    for t in tariff:
        end = "24:00" if t["e"] in ("00:00", "23:59") else t["e"]
        if t["season"] == season and t["s"] <= hhmm < end:
            return float(t["unit_price"]), CODE[t["period_type"]]
    raise ValueError(f"요금표 빈 시각: {d} {hhmm}")


missing = 0
with open(out_path, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["ts", "loadKw", "meanKw", "p90Kw", "solarKw", "price", "period"])
    d = dt.date(YEAR, 1, 1)
    while d.year == YEAR:
        load_day = load_day_index(d)
        for slot in range(96):
            hour, minute = slot // 4, slot % 4 * 15
            i = load_day * 96 + slot
            end_key = (dt.datetime(d.year, d.month, d.day, hour) + dt.timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%S")
            if end_key not in radiation:
                missing += 1
            rad = radiation.get(end_key, 0.0)
            solar = max(0.0, min(capacity, rad * capacity * ratio / 1000))
            price, period = tariff_at(d, hour * 60 + minute + 7)
            w.writerow([f"{d.isoformat()}T{hour:02d}:{minute:02d}:00",
                        round(BASE_LOAD_KW + LOAD_SCALE * energy["loadKwh"][i] * 4, 3),
                        round(BASE_LOAD_KW + LOAD_SCALE * forecast["loadKwh"][i] * 4, 3),
                        round(BASE_LOAD_KW + LOAD_SCALE * forecast["peakLoadKwh"][i] * 4, 3),
                        round(solar, 3), price, period])
        d += dt.timedelta(days=1)
print(f"done (일사량 빠진 칸 {missing}, 태양광 용량 {capacity} kW, 성능비 {ratio})")
