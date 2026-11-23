# 시뮬레이션 결과로 두 경우의 1년 전기요금을 계산하고 그래프를 그린다.
#   ① ESS 없음 (태양광만): 한전 구매 = max(0, 공장 − 태양광)
#   ② ESS + WATTever 추천: EssYearSim 결과의 한전 구매
# 기본요금: docs/기본요금_정책.md (한전 약관 제68조)
#   요금적용전력 = 당월 포함 직전 12개월 중 7·8·9·12·1·2월과 당월의 최대수요전력 중 가장 큰 값
#   그 전해 기록이 없으므로 "해마다 같은 모양이 반복된다"고 보고 그해 값으로 대신함
# 사용법: python -I analyze.py <폴더> <결과 이름 (result_{이름}.csv)> [연도, 기본 2026 → inputs_{연도}.csv]
import csv
import json
import sys
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

folder, name = sys.argv[1], sys.argv[2]
year = sys.argv[3] if len(sys.argv) > 3 else "2026"
BASE_PRICE = 8320  # 기본요금 단가 원/kW (산업용(을) 고압A 선택II)
WEAR_WON = 20  # 배터리 마모 비용 원/kWh (EssPlanner.WEAR_COST_WON_PER_KWH)
TARGET = 1800
CAPACITY, EFFICIENCY, START_SOC = 1000, 0.895, 50
RATCHET_MONTHS = {7, 8, 9, 12, 1, 2}

inputs = list(csv.DictReader(open(f"{folder}/inputs_{year}.csv", encoding="utf-8")))
result = list(csv.DictReader(open(f"{folder}/result_{name}.csv", encoding="utf-8")))
assert len(inputs) == len(result) == 35040

rows = []
for a, b in zip(inputs, result):
    assert a["ts"] == b["ts"]
    load, solar, price = float(a["loadKw"]), float(a["solarKw"]), float(a["price"])
    rows.append({
        "ts": a["ts"], "month": int(a["ts"][5:7]), "date": a["ts"][:10],
        "load": load, "solar": solar, "price": price, "period": a["period"],
        "gridNo": max(0.0, load - solar), "gridEss": float(b["gridKw"]),
        "ess": float(b["essKw"]), "soc": float(b["socAfter"]), "reason": b["reason"],
    })


def summarize(key):
    by_month = defaultdict(lambda: {"kwh": 0.0, "energy": 0.0, "peak": 0.0, "peakAt": ""})
    for r in rows:
        m = by_month[r["month"]]
        m["kwh"] += r[key] * 0.25
        m["energy"] += r[key] * 0.25 * r["price"]
        if r[key] > m["peak"]:
            m["peak"], m["peakAt"] = r[key], r["ts"]
    ratchet = max(by_month[m]["peak"] for m in RATCHET_MONTHS)
    months = []
    for month in range(1, 13):
        m = by_month[month]
        applied = max(m["peak"], ratchet)
        months.append({"month": month, "kwh": m["kwh"], "energy": m["energy"], "peak": m["peak"],
                       "peakAt": m["peakAt"], "appliedKw": applied, "base": applied * BASE_PRICE})
    over = [r for r in rows if r[key] > TARGET]
    return {
        "months": months,
        "energy": sum(m["energy"] for m in months),
        "base": sum(m["base"] for m in months),
        "kwh": sum(m["kwh"] for m in months),
        "yearPeak": max(m["peak"] for m in months),
        "yearPeakAt": max(months, key=lambda m: m["peak"])["peakAt"],
        "appliedKw": ratchet,
        "overSlots": len(over),
        "overDays": len({r["date"] for r in over}),
    }


no_ess = summarize("gridNo")
with_ess = summarize("gridEss")

discharge_kwh = sum(max(0.0, r["ess"]) * 0.25 for r in rows)
charge_kwh = sum(max(0.0, -r["ess"]) * 0.25 for r in rows)
end_soc = rows[-1]["soc"]
# 연말 잔량이 시작(50%)보다 적으면 그만큼 경부하(겨울 123원)로 다시 채우는 값을 더함 (많으면 뺌)
end_soc_fix = (START_SOC - end_soc) / 100 * CAPACITY / EFFICIENCY * 123.0
wear = discharge_kwh * WEAR_WON
reasons = defaultdict(int)
for r in rows:
    reasons[r["reason"]] += 1

saving_base = no_ess["base"] - with_ess["base"]
saving_energy = no_ess["energy"] - (with_ess["energy"] + end_soc_fix)
saving_net = saving_base + saving_energy - wear

# 피크 위험일: ESS가 없을 때 15분 피크가 가장 높은 날
by_date = defaultdict(list)
for r in rows:
    by_date[r["date"]].append(r)
risk_date = max(by_date, key=lambda d: max(x["gridNo"] for x in by_date[d]))
day = by_date[risk_date]
day_summary = {
    "date": risk_date,
    "peakNo": max(x["gridNo"] for x in day),
    "peakEss": max(x["gridEss"] for x in day),
    "overSlotsNo": sum(1 for x in day if x["gridNo"] > TARGET),
    "overSlotsEss": sum(1 for x in day if x["gridEss"] > TARGET),
    "energyNo": sum(x["gridNo"] * 0.25 * x["price"] for x in day),
    "energyEss": sum(x["gridEss"] * 0.25 * x["price"] for x in day),
    "dischargeKwh": sum(max(0, x["ess"]) * 0.25 for x in day),
    "chargeKwh": sum(max(0, -x["ess"]) * 0.25 for x in day),
    "socMin": min(x["soc"] for x in day),
    "reasons": {k: sum(1 for x in day if x["reason"] == k) for k in {x["reason"] for x in day}},
}

summary = {
    "noEss": no_ess, "withEss": with_ess,
    "dischargeKwh": discharge_kwh, "chargeKwh": charge_kwh, "cycles": discharge_kwh / CAPACITY,
    "endSoc": end_soc, "endSocFix": end_soc_fix, "wear": wear, "reasonSlots": dict(reasons),
    "savingBase": saving_base, "savingEnergy": saving_energy, "savingNet": saving_net,
    "riskDay": day_summary,
}
json.dump(summary, open(f"{folder}/summary_{name}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# ---------------- 그래프 ----------------
font_manager.fontManager.addfont("C:/Windows/Fonts/malgun.ttf")
font_manager.fontManager.addfont("C:/Windows/Fonts/malgunbd.ttf")
plt.rcParams.update({
    "font.family": "Malgun Gothic", "axes.unicode_minus": False, "font.size": 11,
    "axes.edgecolor": "#d4d4cf", "axes.labelcolor": "#444444", "xtick.color": "#6e6e6a",
    "ytick.color": "#6e6e6a", "axes.spines.top": False, "axes.spines.right": False,
    "axes.grid": True, "grid.color": "#ececE8", "grid.linewidth": 0.8, "axes.axisbelow": True,
})
INK, MUTE = "#1c1c1c", "#6e6e6a"
NO_COLOR, ESS_COLOR, BATT_COLOR = "#8a8f98", "#2b59c3", "#0f8f8a"


VARIANT = {"backend": "A. 지금 백엔드 그대로", "nobias": "B. 오차 보정을 뺀 경우",
           "perfect": "C. 부하를 미리 정확히 안다고 친 경우"}[name]


def man(won):
    return f"{won / 10000:,.0f}만 원"


# 1) 피크 위험일 하루
hours = [i / 4 for i in range(96)]
fig, axes = plt.subplots(3, 1, figsize=(13, 9), sharex=True, gridspec_kw={"height_ratios": [3, 1.4, 1.2]})
ax = axes[0]
for i, x in enumerate(day):  # 최대부하 시간대 옅은 띠
    if x["period"] == "PEAK":
        for a in axes:
            a.axvspan(hours[i], hours[i] + 0.25, color="#f3f1ea", lw=0, zorder=0)
ax.step(hours, [x["gridNo"] for x in day], where="post", color=NO_COLOR, lw=2, ls=(0, (4, 2)),
        label="ESS 미사용")
ax.step(hours, [x["gridEss"] for x in day], where="post", color=ESS_COLOR, lw=2, label="ESS 사용 (WATTever 추천)")
ax.axhline(TARGET, color=INK, lw=1, ls=(0, (2, 2)))
ax.text(0.1, TARGET + 12, f"목표 피크 {TARGET:,} kW", color=INK, fontsize=10, va="bottom")
for key, color, dy in (("gridNo", NO_COLOR, 25), ("gridEss", ESS_COLOR, -60)):
    i = max(range(96), key=lambda k: day[k][key])
    ax.plot(hours[i] + 0.125, day[i][key], "o", ms=8, color=color, mec="white", mew=2, zorder=5)
    label = "ESS 미사용 최고" if key == "gridNo" else "ESS 사용 최고"
    right_side = hours[i] > 18  # 오른쪽 끝 가까이면 라벨을 점 왼쪽에 둠 (그래프 밖으로 안 나가게)
    ax.annotate(f"{label} {day[i][key]:,.0f} kW", (hours[i] + 0.125, day[i][key]),
                xytext=(-10 if right_side else 10, dy / 3), textcoords="offset points",
                ha="right" if right_side else "left", color=INK, fontsize=10.5, fontweight="bold",
                bbox={"boxstyle": "round,pad=0.15", "fc": "white", "ec": "none", "alpha": 0.85})
ax.set_ylabel("한전 구매 (kW, 15분 평균)")
ax.legend(loc="lower left", frameon=False, ncol=2)
ax.set_title(f"피크 위험일 {risk_date} — ESS 사용 vs 미사용, 15분 한전 구매 (회색 띠: 최대부하 시간대)", loc="left",
             color=INK, fontsize=13, fontweight="bold")
ax = axes[1]
ax.bar([h + 0.125 for h in hours], [x["ess"] for x in day], width=0.22, color=BATT_COLOR)
ax.axhline(0, color=MUTE, lw=0.8)
ax.set_ylabel("ESS (kW)\n+방전 / -충전")
ax = axes[2]
ax.plot([h + 0.25 for h in hours], [x["soc"] for x in day], color=BATT_COLOR, lw=2)
for v in (20, 90):
    ax.axhline(v, color=MUTE, lw=0.8, ls=(0, (2, 2)))
ax.set_ylim(10, 100)
ax.set_ylabel("배터리 잔량 (%)")
ax.set_xlim(0, 24)
ax.set_xticks(range(0, 25, 2))
ax.set_xticklabels([f"{h}시" for h in range(0, 25, 2)])
fig.tight_layout()
fig.savefig(f"{folder}/simple_{name}_risk_day.png", dpi=180, facecolor="white")
plt.close(fig)

# 2) 월별 최대 피크
fig, ax = plt.subplots(figsize=(13, 5))
months = list(range(1, 13))
ax.plot(months, [m["peak"] for m in no_ess["months"]], color=NO_COLOR, lw=2, ls=(0, (4, 2)), marker="o", ms=8,
        mec="white", mew=2, label="ESS 미사용")
ax.plot(months, [m["peak"] for m in with_ess["months"]], color=ESS_COLOR, lw=2, marker="o", ms=8, mec="white",
        mew=2, label="ESS 사용 (WATTever 추천)")
ax.axhline(TARGET, color=INK, lw=1, ls=(0, (2, 2)))
ax.text(0.7, TARGET - 6, f"목표 피크 {TARGET:,} kW", color=INK, fontsize=10, va="top")
ax.set_xticks(months)
ax.set_xticklabels([f"{m}월" for m in months])
ax.set_xlim(0.6, 12.4)
ax.set_ylabel("그 달 최대 15분 피크 (kW)")
ax.legend(loc="upper center", frameon=False, ncol=2)
ax.set_title(f"{year}년 월별 최대 15분 피크 — ESS 사용 vs 미사용 (기본요금을 정하는 값)", loc="left", color=INK, fontsize=13, fontweight="bold")
fig.tight_layout()
fig.savefig(f"{folder}/simple_{name}_monthly_peak.png", dpi=180, facecolor="white")
plt.close(fig)

# 3) 1년 절감액 구성
fig, ax = plt.subplots(figsize=(13, 4.6))
labels = ["기본요금 절감", "전력량요금 절감", "배터리 마모 비용", "순 절감"]
values = [saving_base, saving_energy, -wear, saving_net]
colors = [ESS_COLOR, ESS_COLOR, NO_COLOR, "#1f3f8f"]
y = list(range(len(labels)))[::-1]
ax.barh(y, values, color=colors, height=0.56)
ax.axvline(0, color=MUTE, lw=0.8)
span = max(abs(v) for v in values)
for yy, v in zip(y, values):
    ax.text(v + (span * 0.01 if v >= 0 else -span * 0.01), yy, ("-" if v < 0 else "") + man(abs(v)),
            va="center", ha="left" if v >= 0 else "right", color=INK, fontsize=11.5, fontweight="bold")
ax.set_yticks(y)
ax.set_yticklabels(labels, fontsize=12)
ax.set_xlim(min(0, min(values)) - span * 0.3, span * 1.25)
ax.xaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: f"{v / 1e4:,.0f}만"))
ax.grid(axis="y", visible=False)
ax.set_title(f"{year}년 1년 전기요금 절감 추정 — ESS를 쓰면 (ESS 미사용 대비)", loc="left", color=INK, fontsize=13, fontweight="bold")
fig.tight_layout()
fig.savefig(f"{folder}/simple_{name}_annual_saving.png", dpi=180, facecolor="white")
plt.close(fig)

print(json.dumps({k: summary[k] for k in ("savingBase", "savingEnergy", "wear", "savingNet", "endSoc")},
                 ensure_ascii=False))
print("ESS 없음", {k: no_ess[k] for k in ("yearPeak", "yearPeakAt", "appliedKw", "overSlots", "overDays",
                                          "base", "energy")})
print("WATTever", {k: with_ess[k] for k in ("yearPeak", "yearPeakAt", "appliedKw", "overSlots", "overDays",
                                            "base", "energy")})
print("위험일", day_summary)
print("추천 종류(칸 수)", dict(reasons))
