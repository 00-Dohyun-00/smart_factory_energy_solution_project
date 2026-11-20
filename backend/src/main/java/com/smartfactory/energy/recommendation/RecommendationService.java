package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.ess.EssCommand;
import com.smartfactory.energy.ess.EssCommandHolder;
import com.smartfactory.energy.ess.EssStatus;
import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventLogRepository;
import com.smartfactory.energy.event.EventType;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import com.smartfactory.energy.setting.SettingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    // 한 번에 조회할 수 있는 최대 기간 (일)
    // 추이 분석 화면에서 동절기(11월 1일 ~ 2월 말, 윤년이면 121일)를 한 번에 보려고 62 → 121로 늘림
    private static final int MAX_DAYS = 121;

    // 로그인이 없어서 화면에서 적용/넘기면 처리자는 항상 "운영자"
    private static final String OPERATOR = "운영자";

    private final RecommendationRepository recommendationRepository;
    private final DemandInterval15mRepository demandInterval15mRepository;
    private final EventLogRepository eventLogRepository;
    private final SettingRepository settingRepository;
    private final EssCommandHolder essCommandHolder;

    // 기간 안의 추천 이력 (최신 순)
    public List<RecommendationResponse> findInPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        // 2026-11-14 ~ 2026-11-15 → 11-14 00:00 이상 ~ 11-16 00:00 미만
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // 15분 구간 시작 시각 → 실제 피크. 기간 끝 직전 추천은 다음 구간을 볼 수 있어서 15분 더 읽음
        Map<LocalDateTime, BigDecimal> resultPeaks = new HashMap<>();
        for (DemandInterval15m d : demandInterval15mRepository.findInPeriod(start, end.plusMinutes(15))) {
            // 15분이 다 찬 구간만 결과로 봄 (진행 중인 구간은 아직 결과 없음)
            if (d.getMinuteCount() != null && d.getMinuteCount() >= 15) {
                resultPeaks.put(d.getIntervalStart(), d.getActualAvgKw());
            }
        }

        // 대기(IDLE) 추천은 이력에서 뺌 (명세 4-3: 너무 많아져서 빼도 됨. 최신 1건 조회에는 나옴)
        return recommendationRepository.findInPeriod(start, end)
                .stream()
                .filter(r -> r.getAction() != RecommendationAction.IDLE)
                .map(r -> RecommendationResponse.from(r, resultPeaks.get(r.getIntervalStart())))
                .toList();
    }

    // 지금 추천 1건 (가장 최근 추천). 추천이 하나도 없으면 빈 값
    public Optional<RecommendationResponse> findLatest() {
        return recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc()
                .map(r -> RecommendationResponse.from(r, resultPeakOf(r.getIntervalStart())));
    }

    // 추천 적용 / 넘기기. status는 화면 코드 APPLIED 또는 IGNORED
    @Transactional
    public RecommendationResponse handle(Long id, String status) {
        // 1. 요청 값 검사
        RecommendationStatus newStatus;
        if ("APPLIED".equals(status)) {
            newStatus = RecommendationStatus.APPLIED;
        } else if ("IGNORED".equals(status)) {
            newStatus = RecommendationStatus.SKIPPED; // 화면의 IGNORED(넘김) = DB의 SKIPPED
        } else {
            throw new IllegalArgumentException("status는 APPLIED 또는 IGNORED여야 합니다.");
        }

        // 2. 없는 추천이면 404, 지금 추천이 아니거나 이미 처리했으면 400
        Recommendation recommendation = recommendationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("추천을 찾을 수 없습니다. id=" + id));
        Long latestId = recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc()
                .map(Recommendation::getRecommendationId)
                .orElse(null);
        // 가장 최근 추천이어도 대상 15분 구간이 이미 끝났으면 지난 추천임 (판단 엔진이 멈춘 경우 등)
        LocalDateTime now = LocalDateTime.now().withNano(0); // 초 단위까지만
        boolean windowEnded = !now.isBefore(recommendation.getIntervalStart().plusMinutes(15));
        if (!recommendation.getRecommendationId().equals(latestId) || windowEnded) {
            throw new IllegalArgumentException("이미 지난 추천입니다. 최신 추천을 확인해 주세요.");
        }
        if (recommendation.getStatus() != RecommendationStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리한 추천입니다.");
        }

        // 3. 처리 결과 저장 + 이벤트 1줄 + (적용이면) ESS에 명령
        recommendation.handle(newStatus, OPERATOR, now);
        if (newStatus == RecommendationStatus.APPLIED) {
            applyToEss(recommendation, now);
        }
        eventLogRepository.save(new EventLog(now, EventType.HANDLE, null, handleMessage(recommendation),
                recommendation.getRecommendationId()));

        return RecommendationResponse.from(recommendation, resultPeakOf(recommendation.getIntervalStart()));
    }

    // 추천의 action(충전/방전)과 power_kw로 ESS에 명령을 내림. 유지 시간은 decision_valid_minutes와
    // 그 추천의 15분 구간이 끝나는 시각 중 더 빨리 오는 쪽까지
    // 판단 엔진(RecommendationEngine)도 자동 모드에서 이 메서드로 적용함
    void applyToEss(Recommendation recommendation, LocalDateTime now) {
        if (recommendation.getAction() == RecommendationAction.IDLE) {
            return;
        }
        LocalDateTime windowEnd = recommendation.getIntervalStart().plusMinutes(15);
        LocalDateTime validUntil = now.plusMinutes(decisionValidMinutes());
        if (validUntil.isAfter(windowEnd)) {
            validUntil = windowEnd;
        }
        EssStatus essStatus = EssStatus.valueOf(recommendation.getAction().name());
        essCommandHolder.set(new EssCommand(essStatus, recommendation.getPowerKw()), validUntil);
    }

    // 추천 적용을 ESS에 유지하는 시간(분). setting에 없으면 15분
    private int decisionValidMinutes() {
        return settingRepository.findById("operation.decision_valid_minutes")
                .map(setting -> (int) Double.parseDouble(setting.getValue()))
                .orElse(15);
    }

    // 이벤트 문장. 예: "방전 500 kW 적용 (운영자)", "피크 방어 추천을 넘김 (운영자)"
    private String handleMessage(Recommendation r) {
        if (r.getStatus() == RecommendationStatus.APPLIED) {
            String actionText = switch (r.getAction()) {
                case CHARGE -> "충전";
                case DISCHARGE -> "방전";
                case IDLE -> "대기";
            };
            return actionText + " " + r.getPowerKw().stripTrailingZeros().toPlainString() + " kW 적용 (" + OPERATOR + ")";
        }
        return RecommendationResponse.from(r, null).ruleLabel() + " 추천을 넘김 (" + OPERATOR + ")";
    }

    // 그 15분 구간의 실제 피크. 15분이 다 차지 않았으면 null
    private BigDecimal resultPeakOf(LocalDateTime intervalStart) {
        return demandInterval15mRepository.findInPeriod(intervalStart, intervalStart.plusMinutes(1)).stream()
                .filter(d -> d.getMinuteCount() != null && d.getMinuteCount() >= 15)
                .map(DemandInterval15m::getActualAvgKw)
                .findFirst()
                .orElse(null);
    }
}
