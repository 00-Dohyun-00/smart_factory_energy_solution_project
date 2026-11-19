package com.smartfactory.energy.setting;

import static com.smartfactory.energy.TestData.setting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.setting.dto.SettingUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 환경 설정 저장: 없는 키, 숫자 모양, 범위, 보기만 하는 키, 잔량 하한·상한, 하나라도 틀리면 아무것도 저장 안 함
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingServiceTest {

    @Mock SettingRepository settingRepository;
    @Mock SettingHistoryRepository settingHistoryRepository;
    @InjectMocks SettingService settingService;

    Setting target;
    Setting socLow;
    Setting socHigh;

    @BeforeEach
    void setUp() {
        target = setting("peak.target_kw", "목표 피크", "1800");
        socLow = setting("ess.soc_low", "잔량 하한", "20");
        socHigh = setting("ess.soc_high", "잔량 상한", "90");
        when(settingRepository.findAll()).thenReturn(List.of(
                target, socLow, socHigh,
                setting("ess.capacity_kwh", "배터리 용량", "1000"),
                setting("operation.auto_mode", "자동 모드", "false")
        ));
    }

    @Test
    void 값이_바뀐_키만_저장하고_변경_기록을_남김() {
        settingService.update(List.of(
                new SettingUpdateRequest("peak.target_kw", "1750"),
                new SettingUpdateRequest("ess.soc_low", "20") // 그대로인 값은 기록하지 않음
        ));

        assertThat(target.getValue()).isEqualTo("1750");
        ArgumentCaptor<SettingHistory> history = ArgumentCaptor.forClass(SettingHistory.class);
        verify(settingHistoryRepository).save(history.capture()); // 딱 1번
        assertThat(history.getValue().getOldValue()).isEqualTo("1800");
        assertThat(history.getValue().getNewValue()).isEqualTo("1750");
    }

    @Test
    void 숫자_모양이_아니면_400() {
        for (String value : List.of("NaN", "Infinity", "5f", "1e3", "abc", "1,800")) {
            assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("peak.target_kw", value))))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 범위_밖이면_400() {
        assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("ess.soc_low", "101"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 ~ 100");
    }

    @Test
    void 없는_키와_보기만_하는_키는_400() {
        assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("no.such_key", "1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("ess.capacity_kwh", "2000"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 자동_모드는_true_false만() {
        assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("operation.auto_mode", "yes"))))
                .isInstanceOf(IllegalArgumentException.class);
        settingService.update(List.of(new SettingUpdateRequest("operation.auto_mode", "true")));
    }

    @Test
    void 잔량_하한이_상한_이상이면_400이고_아무것도_저장하지_않음() {
        assertThatThrownBy(() -> settingService.update(List.of(
                new SettingUpdateRequest("peak.target_kw", "1700"), // 이 값은 맞지만
                new SettingUpdateRequest("ess.soc_low", "90")       // 하한 90 ≥ 상한 90
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThat(target.getValue()).isEqualTo("1800"); // 맞는 값도 저장되지 않음
        verify(settingHistoryRepository, never()).save(any());
    }

    @Test
    void 빈_목록이나_빈_값은_400() {
        assertThatThrownBy(() -> settingService.update(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settingService.update(List.of(new SettingUpdateRequest("peak.target_kw", " "))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 변경_기록_조회는_시작이_끝보다_늦으면_400() {
        LocalDate day = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> settingService.findHistories(day, day.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
