# 시간표 ESS(쌀 때 충전, 비쌀 때 방전) vs WATTever 판단 엔진, 목표 피크별 2025년 1년 비교
# 요금 계산은 frontend/docs/simulation/code/analyze.py와 같음 (기본요금 약관 제68조, 시간대별 전력량요금)
# 사용법: python -I tou_compare.py <폴더> <목표1,목표2,...>
import csv
import json
import sys
from collections import defaultdict

folder = sys.argv[1]
targets = [int(t) for t in sys.argv[2].split(",")]
BASE_PRICE = 8320
WEAR_WON = 20
CAP, EFF, LOW, HIGH, MAXKW, START = 1000.0, 0.895, 20.0, 90.0, 500.0, 50.0
RATCHET_MONTHS = {7, 8, 9, 12, 1, 2}

inputs = list(csv.DictReader(open(f"{folder}/inputs_2025.csv", encoding="utf-8")))
base_rows = []
for a in inputs:
    load, solar = float(a["loadKw"]), float(a["solarKw"])
    base_rows.append({"ts": a["ts"], "month": int(a["ts"][5:7]), "date": a["ts"][:10],
                      "price": float(a["price"]), "period": a["period"], "net": max(0.0, load - solar)})


def simulate_schedule(even, smooth_charge=False):
    """시간표 ESS: 경부하에 상한까지 충전, 최대부하에 방전, 중간부하는 대기.
    even=False: 최대부하가 시작되면 최대 출력으로 방전 (다 쓰면 멈춤)
    even=True : 그날 남은 최대부하 시간에 고르게 나눠 방전
    smooth_charge=True: 충전도 이어지는 경부하 시간에 고르게 나눠 함 (한꺼번에 500 kW로 채우지 않음)"""
    soc = START
    out = []
    # 지금 칸부터 이어지는 경부하 칸 수 (충전을 고르게 나눌 때 씀)
    remaining_light = [0] * len(base_rows)
    run = 0
    for i in range(len(base_rows) - 1, -1, -1):
        run = run + 1 if base_rows[i]["period"] == "LIGHT" else 0
        remaining_light[i] = run
    # 그날 남은 최대부하 칸 수 (고르게 나눌 때 씀)
    remaining_peak = [0] * len(base_rows)
    count = 0
    for i in range(len(base_rows) - 1, -1, -1):
        if i == len(base_rows) - 1 or base_rows[i]["date"] != base_rows[i + 1]["date"]:
            count = 0
        if base_rows[i]["period"] == "PEAK":
            count += 1
        remaining_peak[i] = count
    for i, r in enumerate(base_rows):
        ess = 0.0
        if r["period"] == "LIGHT" and soc < HIGH:
            need_kw = (HIGH - soc) / 100 * CAP / 0.25 / EFF
            charge = min(MAXKW, need_kw / remaining_light[i] if smooth_charge else need_kw)
            ess = -charge
            soc += charge * 0.25 * EFF / CAP * 100
        elif r["period"] == "PEAK" and soc > LOW:
            usable_kw = (soc - LOW) / 100 * CAP / 0.25
            want = usable_kw / remaining_peak[i] if even else MAXKW
            dis = min(MAXKW, want, usable_kw, r["net"])
            ess = dis
            soc -= dis * 0.25 / CAP * 100
        soc = min(HIGH, max(LOW, soc))
        out.append({"grid": max(0.0, r["net"] - ess), "ess": ess, "soc": soc})
    return out


def load_wattever(target):
    rows = list(csv.DictReader(open(f"{folder}/result_t{target}.csv", encoding="utf-8")))
    assert len(rows) == len(base_rows)
    return [{"grid": float(r["gridKw"]), "ess": float(r["essKw"]), "soc": float(r["socAfter"])} for r in rows]


def bill(series, target, with_ess=True):
    by_month = defaultdict(lambda: {"energy": 0.0, "peak": 0.0})
    for r, s in zip(base_rows, series):
        m = by_month[r["month"]]
        m["energy"] += s["grid"] * 0.25 * r["price"]
        m["peak"] = max(m["peak"], s["grid"])
    ratchet = max(by_month[m]["peak"] for m in RATCHET_MONTHS)
    base = sum(max(by_month[m]["peak"], ratchet) * BASE_PRICE for m in range(1, 13))
    energy = sum(by_month[m]["energy"] for m in range(1, 13))
    wear = sum(max(0.0, s["ess"]) * 0.25 for s in series) * WEAR_WON if with_ess else 0.0
    end_fix = (START - series[-1]["soc"]) / 100 * CAP / EFF * 123.0 if with_ess else 0.0
    over = [r for r, s in zip(base_rows, series) if s["grid"] > target]
    return {
        "yearPeak": max(s["grid"] for s in series),
        "overSlots": len(over), "overDays": len({r["date"] for r in over}),
        "base": base, "energy": energy, "wear": wear, "endFix": end_fix,
        "net": base + energy + wear + end_fix,
        "dischargeMwh": sum(max(0.0, s["ess"]) * 0.25 for s in series) / 1000,
    }


no_ess = [{"grid": r["net"], "ess": 0.0, "soc": START} for r in base_rows]
sched_max = simulate_schedule(even=False)
sched_even = simulate_schedule(even=True)
sched_smooth = simulate_schedule(even=True, smooth_charge=True)

report = {}
for t in targets:
    report[t] = {
        "noEss": bill(no_ess, t, with_ess=False),
        "schedMax": bill(sched_max, t),
        "schedEven": bill(sched_even, t),
        "schedSmooth": bill(sched_smooth, t),
        "wattever": bill(load_wattever(t), t),
    }
json.dump(report, open(f"{folder}/tou_summary.json", "w", encoding="utf-8"), indent=1)


def m(won):
    return f"{won / 1e4:,.0f}"


print("target | case | yearPeak | overSlots(days) | base | energy | wear | net (unit 10k KRW) | dischargeMWh")
for t in targets:
    for key in ("noEss", "schedMax", "schedEven", "schedSmooth", "wattever"):
        x = report[t][key]
        print(f"{t} | {key:9s} | {x['yearPeak']:,.0f} | {x['overSlots']}({x['overDays']}) | {m(x['base'])} | "
              f"{m(x['energy'])} | {m(x['wear'])} | {m(x['net'])} | {x['dischargeMwh']:.0f}")
    print()
