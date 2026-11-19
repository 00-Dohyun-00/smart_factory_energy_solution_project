from fastapi import FastAPI
from pydantic import BaseModel

# WATTever AI 서버 (15분 피크 예측 / ESS 추천).
# 지금은 기본 뼈대만 있고, 실제 예측 모델은 아직 없습니다. Spring이 호출할 수 있게
# 요청/응답 모양만 미리 잡아두고, 모델이 생기면 predict() 안쪽 로직만 바꾸면 됩니다.
app = FastAPI(title="WATTever AI")


class PredictRequest(BaseModel):
    site_power_kw: float  # 지금 공장 전체 소비 kW
    target_peak_kw: float  # 목표 피크 kW (setting에서 가져온 값)


class PredictResponse(BaseModel):
    predicted_avg_kw: float  # 15분 평균 예상 kW
    action: str  # CHARGE / DISCHARGE / IDLE
    reason: str


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    # 아직 실제 모델이 없어서 규칙 기반 자리만 채워둔 상태입니다 (항상 대기 반환).
    return PredictResponse(
        predicted_avg_kw=request.site_power_kw,
        action="IDLE",
        reason="모델 미구현 - 기본값 반환",
    )
