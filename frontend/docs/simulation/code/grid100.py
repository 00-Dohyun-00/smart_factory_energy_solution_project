# 2025년 1년 전기요금: 한전 100% / 태양광만 / 태양광 + ESS(WATTever 추천) 세 경우 비교
# 요금 계산은 frontend/docs/simulation/code/analyze.py와 같은 방식 (기본요금 약관 제68조, 시간대별 전력량요금)
# 사용법: python -I grid100.py <inputs·result 폴더> <출력 폴더>
import csv
import json
import sys
from collections import defaultdict

folder, out = sys.argv[1], sys.argv[2]
BASE_PRICE = 8320
WEAR_WON = 20
TARGET = 1800
CAPACITY, EFFICIENCY, START_SOC = 1000, 0.895, 50
RATCHET_MONTHS = {7, 8, 9, 12, 1, 2}
EMISSION = 0.4594  # kgCO2/kWh, 공용 DB 설정 carbon.emission_factor

inputs = list(csv.DictReader(open(f"{folder}/inputs_2025.csv", encoding="utf-8")))


def load_result(name):
    result = list(csv.DictReader(open(f"{folder}/result_{name}.csv", encoding="utf-8")))
    assert len(result) == len(inputs) == 35040
    return result


def summarize(rows, key):
    by_month = defaultdict(lambda: {"kwh": 0.0, "energy": 0.0, "peak": 0.0})
    for r in rows:
        m = by_month[r["month"]]
        m["kwh"] += r[key] * 0.25
        m["energy"] += r[key] * 0.25 * r["price"]
        m["peak"] = max(m["peak"], r[key])
    ratchet = max(by_month[m]["peak"] for m in RATCHET_MONTHS)
    base = sum(max(by_month[m]["peak"], ratchet) * BASE_PRICE for m in range(1, 13))
    energy = sum(by_month[m]["energy"] for m in range(1, 13))
    over = [r for r in rows if r[key] > TARGET]
    return {
        "kwh": sum(by_month[m]["kwh"] for m in range(1, 13)),
        "base": base, "energy": energy, "total": base + energy,
        "yearPeak": max(by_month[m]["peak"] for m in range(1, 13)),
        "overSlots": len(over), "overDays": len({r["date"] for r in over}),
    }


def build(name):
    rows = []
    for a, b in zip(inputs, load_result(name)):
        assert a["ts"] == b["ts"]
        load, solar = float(a["loadKw"]), float(a["solarKw"])
        rows.append({
            "month": int(a["ts"][5:7]), "date": a["ts"][:10], "price": float(a["price"]),
            "load": load, "solar": solar,
            "grid100": load,                       # 태양광·ESS 없음
            "solarOnly": max(0.0, load - solar),   # 태양광만 (남는 태양광은 버림, 역송 없음)
            "wattever": float(b["gridKw"]),        # 태양광 + ESS 추천을 모두 적용
            "ess": float(b["essKw"]), "soc": float(b["socAfter"]),
        })
    return rows


report = {}
for name in ("backend", "nobias"):
    rows = build(name)
    a, s, w = summarize(rows, "grid100"), summarize(rows, "solarOnly"), summarize(rows, "wattever")
    discharge = sum(max(0.0, r["ess"]) * 0.25 for r in rows)
    wear = discharge * WEAR_WON
    end_fix = (START_SOC - rows[-1]["soc"]) / 100 * CAPACITY / EFFICIENCY * 123.0
    solar_used = sum(min(r["solar"], r["load"]) * 0.25 for r in rows)
    solar_all = sum(r["solar"] * 0.25 for r in rows)
    w_total = w["total"] + end_fix  # 연말 잔량 보정 포함 요금
    report[name] = {
        "grid100": a, "solarOnly": s, "wattever": w,
        "endSocFix": end_fix, "wear": wear, "watteverTotalFixed": w_total,
        "savingSolar": a["total"] - s["total"],
        "savingEss": s["total"] - w_total - wear,
        "savingAll": a["total"] - w_total - wear,
        "savingAllPercent": (a["total"] - w_total - wear) / a["total"] * 100,
        "savingBaseAll": a["base"] - w["base"],
        "savingEnergyAll": a["energy"] - (w["energy"] + end_fix),
        "solarUsedKwh": solar_used, "solarAllKwh": solar_all,
        "carbonT": solar_used * EMISSION / 1000,
    }

json.dump(report, open(f"{out}/grid100_summary.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)


def man(won):
    return f"{won / 1e4:,.0f}만"


for name, r in report.items():
    print(f"== {name}")
    for key, label in (("grid100", "한전 100%"), ("solarOnly", "태양광만"), ("wattever", "태양광+ESS")):
        x = r[key]
        print(f"  {label:8s} 피크 {x['yearPeak']:,.0f} kW | 넘은 구간 {x['overSlots']}개 ({x['overDays']}일) | "
              f"기본 {man(x['base'])} | 전력량 {man(x['energy'])} | 합계 {man(x['total'])} | 구매 {x['kwh'] / 1e3:,.0f} MWh")
    print(f"  연말 잔량 보정 {man(r['endSocFix'])}, 마모 {man(r['wear'])}")
    print(f"  절감: 태양광 몫 {man(r['savingSolar'])}, ESS 몫 {man(r['savingEss'])}, "
          f"합계 {man(r['savingAll'])} ({r['savingAllPercent']:.1f}%)")
    print(f"  합계 내역: 기본요금 {man(r['savingBaseAll'])}, 전력량요금 {man(r['savingEnergyAll'])}, 마모 -{man(r['wear'])}")
    print(f"  태양광 발전 {r['solarAllKwh'] / 1e3:,.0f} MWh 중 공장 사용 {r['solarUsedKwh'] / 1e3:,.0f} MWh, 탄소 {r['carbonT']:,.0f} t")
