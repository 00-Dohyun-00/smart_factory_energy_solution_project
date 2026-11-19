from datetime import datetime

from fastapi import FastAPI, HTTPException, Query

from app import load_forecast

# WATTever AI 서버: 공장 부하 96구간 예측
# 충전·방전 판단은 Spring 판단 엔진(최적화)이 하고, AI는 판단에 필요한 공장 부하만 예측합니다. (11/18 결정)
# Spring(RecommendationEngine)이 1분마다 GET /load-forecasts 로 앞으로 24시간 부하 예측을 받아 계획에 씁니다.
app = FastAPI(title="WATTever AI")

# 서버를 켤 때 예측값 파일을 한 번만 읽어 둠
DATA = load_forecast.load_data()

MAX_SLOTS = 192  # 한 번에 줄 수 있는 최대 칸 수 (48시간)


@app.get("/health")
def health():
    return {"status": "UP"}


@app.get("/load-forecasts")
def get_load_forecasts(
    start: datetime = Query(description="시작 시각 (한국 시각, 예: 2026-11-19T16:15:00). 이 시각이 들어 있는 15분 칸부터"),
    slots: int = Query(96, description="칸 수 (15분 단위, 기본 96칸 = 24시간)"),
):
    """
    공장 부하 예측 (15분 칸마다 kW)
    - load_kw: 평균 예측 (요금 계산용)
    - peak_load_kw: 상위 90% 예측 (방어선 판단용, 피크를 낮게 보지 않게)
    """
    if slots < 1 or slots > MAX_SLOTS:
        raise HTTPException(status_code=400, detail=f"slots는 1~{MAX_SLOTS} 사이여야 합니다.")
    return {
        "model": load_forecast.MODEL_NAME,
        "slots": load_forecast.forecast(DATA, start, slots),
    }
