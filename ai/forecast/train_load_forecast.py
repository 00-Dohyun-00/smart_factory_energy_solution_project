"""
공장 부하 96구간 예측 모델 학습 스크립트

하는 일
  1. 공장 부하 데이터(15분 단위 1년치)를 읽습니다.
  2. 생산계획 더미를 만듭니다. (요일별 표준 생산 일정 → 구간별 계획 부하)
  3. 예측에 쓸 입력값(특징)을 만듭니다.
  4. 모델을 검증합니다. (1~9월로 학습 → 10~12월 예측 오차 확인)
  5. 시뮬레이터가 쓸 1년치 예측값을 만듭니다.
     (각 달은 그 달을 뺀 나머지 달로 학습한 모델로 예측)

실행 (저장소 루트에서)
  pip install -r ai/forecast/requirements.txt
  python ai/forecast/train_load_forecast.py

만드는 파일
  ai/forecast/production_plan.csv        생산계획 더미 (날짜, 시각, 계획 부하)
  frontend/src/api/fakeLoadForecast.json 1년치 96구간 예측값 (가짜 백엔드가 하루 계획에 씀)
  ai/app/data/load_forecast_2018.json    같은 내용 (AI 서버 FastAPI가 GET /load-forecasts 로 돌려줌)
    - loadKwh     : 평균 예측 (요금 계산용)
    - peakLoadKwh : 상위 90% 예측 (방어선 판단용)

데이터: frontend/src/api/fakeEnergyData.json 의 loadKwh
  UCI Machine Learning Repository, Steel Industry Energy Consumption (CC BY 4.0)
  2018-01-01 00:00부터 15분 간격 35,040칸, 값은 15분 전력량(kWh). kW = kWh × 4
"""

import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.inspection import permutation_importance

ROOT = Path(__file__).resolve().parents[2]  # 저장소 루트
ENERGY_DATA = ROOT / "frontend" / "src" / "api" / "fakeEnergyData.json"
PLAN_CSV = Path(__file__).resolve().parent / "production_plan.csv"
FORECAST_JSON = ROOT / "frontend" / "src" / "api" / "fakeLoadForecast.json"
# AI 서버(FastAPI)가 읽는 같은 내용의 파일. Docker 빌드가 ai 폴더만 보므로 따로 한 부 둠
AI_SERVER_JSON = ROOT / "ai" / "app" / "data" / "load_forecast_2018.json"

YEAR = 2018
TRAIN_END_MONTH = 9  # 검증: 1~9월 학습, 10~12월 시험

# 우리 공장 크기로 바꾸는 식 (fakeBackend.js와 같음): 공장 kW = 1,300 + 1.35 × 원본 kW
BASE_LOAD_KW = 1300
LOAD_SCALE = 1.35

# 2018년 공휴일 (대체공휴일, 지방선거일 포함)
# 생산계획은 휴무일을 미리 알고 있으므로, 공휴일은 일요일 일정으로 계획합니다.
HOLIDAYS = {
    "2018-01-01", "2018-02-15", "2018-02-16", "2018-02-17", "2018-03-01",
    "2018-05-05", "2018-05-07", "2018-05-22", "2018-06-06", "2018-06-13",
    "2018-08-15", "2018-09-23", "2018-09-24", "2018-09-25", "2018-09-26",
    "2018-10-03", "2018-10-09", "2018-12-25",
}

# 모델에 넣는 입력값 (특징)
FEATURES = [
    "plan_kw",  # 생산계획의 그 구간 계획 부하
    "slot",  # 구간 번호 0~95 (17:00 → 68)
    "day_of_week",  # 0=월 ~ 6=일
    "is_holiday",  # 공휴일 여부
    "month",  # 계절 차이
    "lag_1d",  # 어제 같은 구간 부하
    "lag_7d",  # 지난주 같은 요일·같은 구간 부하
    "mean_4w",  # 최근 4주 같은 요일·같은 구간 평균
    "yesterday_max",  # 어제 하루 최대 부하
    "yesterday_mean",  # 어제 하루 평균 부하
]


def load_data():
    """15분 부하를 표로 읽음. 한 줄 = 어느 날의 어느 구간"""
    energy = json.loads(ENERGY_DATA.read_text(encoding="utf-8"))
    kwh = np.array(energy["loadKwh"], dtype=float)
    times = pd.date_range(f"{YEAR}-01-01", periods=len(kwh), freq="15min")
    df = pd.DataFrame({"time": times, "load_kw": kwh * 4})
    df["date"] = df["time"].dt.date.astype(str)
    df["slot"] = df["time"].dt.hour * 4 + df["time"].dt.minute // 15
    df["day_of_week"] = df["time"].dt.dayofweek
    df["month"] = df["time"].dt.month
    df["is_holiday"] = df["date"].isin(HOLIDAYS).astype(int)
    return df


def make_production_plan(df):
    """
    생산계획 더미: 학습 기간(1~9월) 데이터로 요일별 표준 일정을 만듭니다.
    요일·구간마다 부하의 중앙값을 계획 부하로 씁니다. (평소 일정이 이렇다는 뜻)
    공휴일은 휴무로 보고 일요일 일정을 씁니다.
    실제 생산계획이 생기면 이 함수 대신 그 CSV를 읽으면 됩니다.
    """
    train = df[(df["month"] <= TRAIN_END_MONTH) & (df["is_holiday"] == 0)]
    template = train.groupby(["day_of_week", "slot"])["load_kw"].median()

    plan_day = np.where(df["is_holiday"] == 1, 6, df["day_of_week"])  # 공휴일 → 일요일 일정
    keys = list(zip(plan_day, df["slot"]))
    df["plan_kw"] = template.loc[keys].to_numpy()

    plan = df[["date", "time", "plan_kw"]].copy()
    plan["time"] = plan["time"].dt.strftime("%H:%M")
    plan["day_type"] = np.where(df["is_holiday"] == 1, "휴무(공휴일)", "정상")
    plan["plan_kw"] = plan["plan_kw"].round(1)
    plan.to_csv(PLAN_CSV, index=False, encoding="utf-8-sig")
    return df


def past(values, steps):
    """steps칸 전의 값. 1년 데이터를 이어 붙인 것처럼 봄 (1월 1일의 '어제'는 12월 31일).
    시뮬레이터도 이 1년 데이터를 해마다 반복해서 쓰므로 같은 방식으로 맞춤"""
    return pd.Series(np.roll(values.to_numpy(), steps), index=values.index)


def make_features(df):
    """0시에 이미 알 수 있는 값(어제 이전 기록)으로 특징을 만듭니다."""
    # 휴일이었던 날의 기록은 평소 패턴이 아니라서 과거 기록으로 쓰지 않음 (빈 값, 모델이 스스로 처리함)
    usual = df["load_kw"].where(df["is_holiday"] == 0)
    df["lag_1d"] = past(usual, 96)
    df["lag_7d"] = past(usual, 96 * 7)
    df["mean_4w"] = pd.concat([past(usual, 96 * 7 * k) for k in range(1, 5)], axis=1).mean(axis=1)

    daily = usual.groupby(df["date"]).agg(["max", "mean"])
    yesterday = pd.DataFrame(np.roll(daily.to_numpy(), 1, axis=0), index=daily.index, columns=daily.columns)
    df["yesterday_max"] = df["date"].map(yesterday["max"])
    df["yesterday_mean"] = df["date"].map(yesterday["mean"])
    return df


def new_model(kind="mean"):
    """
    kind="mean": 평균적인 부하를 예측 (요금 계산용)
    kind="peak": 10번 중 9번은 이보다 낮을 값(상위 90%)을 예측 (방어선 판단용)
      평균 예측은 하루 피크를 낮게 보는 경향이 있어서(높은 값이 평균에 묻힘), 피크 판단에는 보수적인 값을 씁니다.
    """
    options = {"loss": "quantile", "quantile": 0.9} if kind == "peak" else {}
    return HistGradientBoostingRegressor(
        max_iter=400,
        learning_rate=0.05,
        max_leaf_nodes=31,
        min_samples_leaf=40,
        random_state=0,
        **options,
    )


def daily_peak_error(df, predicted):
    """하루 최대 피크 오차: (예측 하루 최대) − (실제 하루 최대)"""
    table = pd.DataFrame({"date": df["date"], "actual": df["load_kw"], "predicted": predicted})
    daily = table.groupby("date").max()
    return daily["predicted"] - daily["actual"]


def validate(df):
    """1~9월로 학습하고 10~12월(학습에 안 쓴 기간)의 오차를 확인합니다."""
    # 1월 첫 4주는 과거 기록 특징이 12월(시험 기간) 값에서 오므로 학습에서 뺌
    train = df[(df["month"] <= TRAIN_END_MONTH) & (df["time"] >= f"{YEAR}-01-29")]
    test = df[df["month"] > TRAIN_END_MONTH]

    model = new_model("mean")
    model.fit(train[FEATURES], train["load_kw"])
    predicted = model.predict(test[FEATURES]).clip(min=0)
    peak_model = new_model("peak")
    peak_model.fit(train[FEATURES], train["load_kw"])
    peak_predicted = peak_model.predict(test[FEATURES]).clip(min=0)

    error = predicted - test["load_kw"]
    scaled = LOAD_SCALE  # 우리 공장 크기(환산 kW)로 바꾸면 오차도 1.35배
    actual_scaled = BASE_LOAD_KW + scaled * test["load_kw"]

    print("== 검증: 1~9월 학습 → 10~12월 예측 (kW는 우리 공장 크기로 환산한 값) ==")
    print("[평균 예측 - 요금 계산용]")
    print(f"  96구간 평균 오차    : {error.abs().mean() * scaled:.1f} kW "
          f"({(error.abs() * scaled / actual_scaled).mean() * 100:.1f}%)")
    for name, values in [("평균 예측", predicted), ("상위 90% 예측", peak_predicted)]:
        peak_error = daily_peak_error(test, values) * scaled
        print(f"[{name}] 하루 최대 피크 오차 (예측 − 실제)")
        print(f"  평균 {peak_error.mean():.0f} kW, 절댓값 평균 {peak_error.abs().mean():.0f} kW, "
              f"낮게 본 날 {(peak_error < 0).mean() * 100:.0f}%, "
              f"가장 낮게 본 날 {peak_error.idxmin()} ({peak_error.min():.0f} kW)")

    importance = permutation_importance(
        model, test[FEATURES], test["load_kw"], n_repeats=5, random_state=0, scoring="neg_mean_absolute_error"
    )
    order = np.argsort(-importance.importances_mean)
    print("특징 중요도 (값을 섞었을 때 오차가 늘어난 양, 원본 kW)")
    for i in order:
        print(f"  {FEATURES[i]:<15} {importance.importances_mean[i]:6.1f}")


def forecast_year(df):
    """
    시뮬레이터용 1년치 예측. 각 달은 그 달을 뺀 나머지 11개월로 학습한 모델로 예측합니다.
    (예측하는 날의 실제 값은 학습에 들어가지 않음. 시연용이며, 성능 수치는 validate()의 결과를 씁니다)
    """
    predicted = {"mean": np.zeros(len(df)), "peak": np.zeros(len(df))}
    for month in range(1, 13):
        in_month = (df["month"] == month).to_numpy()
        for kind in predicted:
            model = new_model(kind)
            model.fit(df.loc[~in_month, FEATURES], df.loc[~in_month, "load_kw"])
            predicted[kind][in_month] = model.predict(df.loc[in_month, FEATURES]).clip(min=0)

    output = {
        "source": "ai/forecast/train_load_forecast.py 로 만든 공장 부하 예측 (scikit-learn HistGradientBoostingRegressor)",
        "note": "2018-01-01 00:00부터 15분 간격. 값은 15분 전력량(kWh)으로 fakeEnergyData.json의 loadKwh와 같은 단위. "
        "각 달은 그 달을 뺀 나머지 11개월로 학습한 모델로 예측. "
        "loadKwh = 평균 예측(요금 계산용), peakLoadKwh = 상위 90% 예측(방어선 판단용)",
        "loadKwh": [round(v / 4, 2) for v in predicted["mean"]],
        "peakLoadKwh": [round(v / 4, 2) for v in predicted["peak"]],
    }
    for path in (FORECAST_JSON, AI_SERVER_JSON):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(output, ensure_ascii=False), encoding="utf-8")
        print(f"\n1년치 예측 저장: {path.relative_to(ROOT)} ({len(df)}칸)")


def main():
    df = load_data()
    df = make_production_plan(df)
    print(f"생산계획 더미 저장: {PLAN_CSV.relative_to(ROOT)}\n")
    df = make_features(df)
    validate(df)
    forecast_year(df)


if __name__ == "__main__":
    main()
