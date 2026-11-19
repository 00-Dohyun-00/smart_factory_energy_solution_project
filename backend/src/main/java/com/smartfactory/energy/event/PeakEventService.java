package com.smartfactory.energy.event;

import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.sitepower.SitePowerService;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피크 이벤트 기록. 1분마다 불려서 아래 두 가지를 event_log에 남김.
 * 1. 이번 15분 구간의 피크 단계(정상·주의·경고)가 직전 기록과 달라지면 1줄
 * 2. 15분 구간이 끝나면 그 구간의 실제 피크 결과 1줄
 * 예상 피크와 단계는 지금 상태 API(SitePowerService)와 같은 계산을 그대로 씀
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PeakEventService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    // 마지막 1분 기록이 이보다 오래됐으면 시뮬레이터가 멈춘 것으로 보고 아무것도 남기지 않음
    private static final int STALE_MINUTES = 2;

    private final SitePowerService sitePowerService;
    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final DemandInterval15mRepository demandInterval15mRepository;
    private final EventLogRepository eventLogRepository;

    public void record() {
        // 기록이 하나도 없으면 할 일이 없음
        if (sitePowerMinuteRepository.findFirstByOrderByTsDesc().isEmpty()) {
            return;
        }
        SitePowerLatestResponse latest = sitePowerService.getLatest();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (latest.recordedAt().isBefore(now.minusMinutes(STALE_MINUTES))) {
            return;
        }

        recordStageChange(latest, now);
        recordIntervalEnd(latest);
    }

    // 1. 피크 단계가 바뀌었으면 1줄
    private void recordStageChange(SitePowerLatestResponse latest, LocalDateTime now) {
        EventStage stage = toStage(latest.peakStatus());
        Optional<EventLog> last = eventLogRepository.findFirstByEventTypeOrderByOccurredAtDescEventIdDesc(EventType.STAGE_CHANGE);

        // 처음 시작할 때 정상이면 굳이 남기지 않음 (주의·경고부터 남김)
        EventStage lastStage = last.map(EventLog::getStage).orElse(EventStage.NORMAL);
        if (stage == lastStage) {
            return;
        }
        eventLogRepository.save(new EventLog(now, EventType.STAGE_CHANGE, stage, stageMessage(stage, latest), null));
    }

    // 2. 바로 전 15분 구간이 다 찼으면 결과 1줄 (구간 끝 시각으로 남겨서 두 번 남기지 않음)
    private void recordIntervalEnd(SitePowerLatestResponse latest) {
        LocalDateTime end = latest.windowStart();
        LocalDateTime start = end.minusMinutes(15);
        if (eventLogRepository.existsByEventTypeAndOccurredAt(EventType.INTERVAL_END, end)) {
            return;
        }
        Optional<DemandInterval15m> interval = demandInterval15mRepository.findInPeriod(start, start.plusMinutes(1)).stream()
                .filter(d -> d.getMinuteCount() != null && d.getMinuteCount() >= 15 && d.getActualAvgKw() != null)
                .findFirst();
        if (interval.isEmpty()) {
            return; // 시뮬레이터를 구간 중간에 켜서 15분이 다 차지 않은 구간은 남기지 않음
        }

        double peakKw = interval.get().getActualAvgKw().doubleValue();
        Double targetKw = latest.targetPeakKw();
        boolean over = targetKw != null && peakKw > targetKw;
        String message = start.format(HH_MM) + "–" + end.format(HH_MM) + " 구간 " + kw(peakKw) + "로 종료";
        if (targetKw != null) {
            message += over ? " (목표 " + kw(targetKw) + " 초과)" : " (목표 이내)";
        }
        eventLogRepository.save(new EventLog(end, EventType.INTERVAL_END,
                over ? EventStage.WARNING : EventStage.NORMAL, message, null));
    }

    // 지금 상태 API의 단계 코드를 DB 단계로 바꿈 (CRITICAL 경고 → WARNING, WARNING 주의 → CAUTION)
    private EventStage toStage(String peakStatus) {
        return switch (peakStatus) {
            case "CRITICAL" -> EventStage.WARNING;
            case "WARNING" -> EventStage.CAUTION;
            default -> EventStage.NORMAL;
        };
    }

    // 예: "경고 단계: 이번 구간 예상 1,830 kW (목표 1,800 kW 초과)"
    private String stageMessage(EventStage stage, SitePowerLatestResponse latest) {
        String predicted = "이번 구간 예상 " + kw(latest.predictedKw());
        return switch (stage) {
            case WARNING -> "경고 단계: " + predicted
                    + (latest.targetPeakKw() != null ? " (목표 " + kw(latest.targetPeakKw()) + " 초과)" : "");
            case CAUTION -> "주의 단계: " + predicted
                    + (latest.warnKw() != null ? " (주의선 " + kw(latest.warnKw()) + " 이상)" : "");
            case NORMAL -> "정상으로 돌아옴: " + predicted;
        };
    }

    // 1830.4 → "1,830 kW"
    private String kw(double value) {
        return String.format("%,.0f kW", value);
    }
}
