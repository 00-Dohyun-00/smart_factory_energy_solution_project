package com.smartfactory.energy.recommendation;

import static com.smartfactory.energy.TestData.interval;
import static com.smartfactory.energy.TestData.recommendation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.ess.EssCommandHolder;
import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventLogRepository;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import com.smartfactory.energy.setting.SettingRepository;
import java.time.LocalDate;
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

// 추천: 이력의 결과 피크·상태 코드, 적용/넘기기 검사(값·지난 추천·이미 처리·없는 id)와 이벤트 기록
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

    @Mock RecommendationRepository recommendationRepository;
    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @Mock EventLogRepository eventLogRepository;
    @Mock SettingRepository settingRepository;
    @Mock EssCommandHolder essCommandHolder;
    @InjectMocks RecommendationService recommendationService;

    // 처리는 "지금 구간"의 추천만 되므로 지금 시각이 들어 있는 15분 구간으로 시험함
    LocalDateTime window;

    Recommendation latest;
    Recommendation old;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        window = now.withMinute(now.getMinute() / 15 * 15);
        old = discharge(11, window.minusMinutes(15), RecommendationStatus.PENDING);
        latest = discharge(12, window, RecommendationStatus.PENDING);
        when(recommendationRepository.findById(11L)).thenReturn(Optional.of(old));
        when(recommendationRepository.findById(12L)).thenReturn(Optional.of(latest));
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc())
                .thenReturn(Optional.of(latest));
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());
    }

    @Test
    void 이력은_다_찬_구간만_결과를_붙이고_상태를_화면_코드로_바꿈() {
        LocalDateTime w2 = window.plusMinutes(15);
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of(
                discharge(1, window, RecommendationStatus.EXPIRED),
                discharge(2, w2, RecommendationStatus.PENDING)
        ));
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(window, 1800.01, 1900, 15),  // 목표 1800 초과 → EXCEEDED
                interval(w2, 1500, 1500, 9)      // 진행 중 → 결과 없음
        ));

        List<RecommendationResponse> list = recommendationService.findInPeriod(window.toLocalDate(), window.toLocalDate());

        assertThat(list.get(0).status()).isEqualTo("IGNORED");
        assertThat(list.get(0).resultStatus()).isEqualTo("EXCEEDED");
        assertThat(list.get(1).status()).isEqualTo("RECOMMENDED");
        assertThat(list.get(1).resultPeakKw()).isNull();
        assertThat(list.get(1).resultStatus()).isNull();
    }

    @Test
    void 이력_기간은_62일까지() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        assertThatThrownBy(() -> recommendationService.findInPeriod(day, day.plusDays(62)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recommendationService.findInPeriod(day, day.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 넘기기는_DB에_SKIPPED로_저장하고_화면에는_IGNORED로_돌려주며_이벤트를_남김() {
        RecommendationResponse response = recommendationService.handle(12L, "IGNORED");

        assertThat(latest.getStatus()).isEqualTo(RecommendationStatus.SKIPPED);
        assertThat(latest.getHandledBy()).isEqualTo("운영자");
        assertThat(response.status()).isEqualTo("IGNORED");
        assertThat(response.appliedAt()).isNotNull();
        ArgumentCaptor<EventLog> event = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(event.capture());
        assertThat(event.getValue().getMessage()).isEqualTo("피크 방어 추천을 넘김 (운영자)");
        assertThat(event.getValue().getRecommendationId()).isEqualTo(12L);
    }

    @Test
    void 적용하면_방전_kW가_이벤트_문장에_들어감() {
        recommendationService.handle(12L, "APPLIED");

        ArgumentCaptor<EventLog> event = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(event.capture());
        assertThat(event.getValue().getMessage()).isEqualTo("방전 500 kW 적용 (운영자)");
    }

    @Test
    void status_값이_틀리면_400() {
        assertThatThrownBy(() -> recommendationService.handle(12L, "AUTO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recommendationService.handle(12L, "applied"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지난_추천이면_400이고_저장하지_않음() {
        assertThatThrownBy(() -> recommendationService.handle(11L, "APPLIED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 지난 추천입니다. 최신 추천을 확인해 주세요.");
        assertThat(old.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void 가장_최근_추천이어도_구간이_끝났으면_400() {
        Recommendation ended = discharge(13, window.minusMinutes(15), RecommendationStatus.PENDING);
        when(recommendationRepository.findById(13L)).thenReturn(Optional.of(ended));
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc())
                .thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> recommendationService.handle(13L, "APPLIED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 지난 추천입니다. 최신 추천을 확인해 주세요.");
        assertThat(ended.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void 이미_처리한_추천이면_400() {
        recommendationService.handle(12L, "APPLIED");
        assertThatThrownBy(() -> recommendationService.handle(12L, "IGNORED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_id면_404() {
        when(recommendationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> recommendationService.handle(99L, "APPLIED"))
                .isInstanceOf(NotFoundException.class);
    }

    private Recommendation discharge(long id, LocalDateTime window, RecommendationStatus status) {
        return recommendation(id, window.plusMinutes(1), window, RecommendationAction.DISCHARGE,
                RecommendationReasonType.PEAK_DEFENSE, status, 1800);
    }
}
