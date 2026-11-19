package com.smartfactory.energy.event;

import static com.smartfactory.energy.TestData.interval;
import static com.smartfactory.energy.TestData.power;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.sitepower.SitePowerService;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 피크 이벤트: 단계가 바뀔 때만 1줄, 구간이 끝나면 결과 1줄(한 번만), 시뮬레이터가 멈췄으면 아무것도 안 남김
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeakEventServiceTest {

    @Mock SitePowerService sitePowerService;
    @Mock SitePowerMinuteRepository sitePowerMinuteRepository;
    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @Mock EventLogRepository eventLogRepository;
    @InjectMocks PeakEventService peakEventService;

    LocalDateTime now;
    LocalDateTime windowStart;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        windowStart = now.withMinute(now.getMinute() / 15 * 15);
        when(sitePowerMinuteRepository.findFirstByOrderByTsDesc()).thenReturn(Optional.of(power(now, 1000, 0, 0, 1000)));
        when(eventLogRepository.findFirstByEventTypeOrderByOccurredAtDescEventIdDesc(EventType.STAGE_CHANGE))
                .thenReturn(Optional.empty());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());
    }

    @Test
    void 정상에서_경고로_바뀌면_경고_이벤트_1줄() {
        when(sitePowerService.getLatest()).thenReturn(latest(now, 1830.4, "CRITICAL"));

        peakEventService.record();

        EventLog saved = savedOne();
        assertThat(saved.getEventType()).isEqualTo(EventType.STAGE_CHANGE);
        assertThat(saved.getStage()).isEqualTo(EventStage.WARNING);
        assertThat(saved.getMessage()).isEqualTo("경고 단계: 이번 구간 예상 1,830 kW (목표 1,800 kW 초과)");
    }

    @Test
    void 단계가_그대로면_남기지_않음() {
        when(sitePowerService.getLatest()).thenReturn(latest(now, 1760, "WARNING"));
        when(eventLogRepository.findFirstByEventTypeOrderByOccurredAtDescEventIdDesc(EventType.STAGE_CHANGE))
                .thenReturn(Optional.of(new EventLog(now.minusMinutes(3), EventType.STAGE_CHANGE, EventStage.CAUTION, "주의", null)));

        peakEventService.record();

        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void 처음_시작할_때_정상이면_남기지_않음() {
        when(sitePowerService.getLatest()).thenReturn(latest(now, 1500, "NORMAL"));

        peakEventService.record();

        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void 마지막_기록이_오래됐으면_시뮬레이터가_멈춘_것으로_보고_남기지_않음() {
        when(sitePowerService.getLatest()).thenReturn(latest(now.minusMinutes(10), 1900, "CRITICAL"));

        peakEventService.record();

        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void 바로_전_구간이_다_찼으면_결과를_구간_끝_시각으로_1줄() {
        when(sitePowerService.getLatest()).thenReturn(latest(now, 1500, "NORMAL"));
        LocalDateTime prev = windowStart.minusMinutes(15);
        when(demandInterval15mRepository.findInPeriod(prev, prev.plusMinutes(1)))
                .thenReturn(List.of(interval(prev, 1830.4, 1900, 15)));

        peakEventService.record();

        EventLog saved = savedOne();
        assertThat(saved.getEventType()).isEqualTo(EventType.INTERVAL_END);
        assertThat(saved.getOccurredAt()).isEqualTo(windowStart);
        assertThat(saved.getStage()).isEqualTo(EventStage.WARNING);
        assertThat(saved.getMessage()).endsWith("구간 1,830 kW로 종료 (목표 1,800 kW 초과)");
    }

    @Test
    void 이미_남긴_구간이나_덜_찬_구간은_다시_남기지_않음() {
        when(sitePowerService.getLatest()).thenReturn(latest(now, 1500, "NORMAL"));
        LocalDateTime prev = windowStart.minusMinutes(15);
        when(demandInterval15mRepository.findInPeriod(prev, prev.plusMinutes(1)))
                .thenReturn(List.of(interval(prev, 1700, 1700, 9))); // 9분만 있음

        peakEventService.record();
        verify(eventLogRepository, never()).save(any());

        when(demandInterval15mRepository.findInPeriod(prev, prev.plusMinutes(1)))
                .thenReturn(List.of(interval(prev, 1700, 1700, 15)));
        when(eventLogRepository.existsByEventTypeAndOccurredAt(EventType.INTERVAL_END, windowStart)).thenReturn(true);

        peakEventService.record();
        verify(eventLogRepository, never()).save(any());
    }

    private EventLog savedOne() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // 테스트에 필요한 칸만 채운 지금 상태 응답 (목표 1800, 주의선 1746)
    private SitePowerLatestResponse latest(LocalDateTime recordedAt, double predictedKw, String peakStatus) {
        return new SitePowerLatestResponse(recordedAt, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, "STANDBY", null, null, 1,
                windowStart, windowStart.plusMinutes(15), 0, predictedKw, 1800.0, 1746.0, peakStatus, 0.0,
                List.of(), null, null, null, null, null, List.of());
    }
}
