package com.smartfactory.energy.tariff;

import static com.smartfactory.energy.TestData.dayTariffs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.tariff.dto.TariffPeriodResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 요금 시간대표: season 검사, 화면 코드, 하루 끝 24:00
@ExtendWith(MockitoExtension.class)
class TariffPeriodServiceTest {

    @Mock TariffPeriodRepository tariffPeriodRepository;
    @InjectMocks TariffPeriodService tariffPeriodService;

    @Test
    void season이_틀리면_400() {
        assertThatThrownBy(() -> tariffPeriodService.findAll("SPRING")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소문자도_받고_화면_코드와_24시로_돌려줌() {
        when(tariffPeriodRepository.findBySeason(TariffSeason.WINTER)).thenReturn(dayTariffs(TariffSeason.WINTER));

        List<TariffPeriodResponse> list = tariffPeriodService.findAll("winter");

        assertThat(list).hasSize(7);
        assertThat(list.get(0).period()).isEqualTo("LIGHT");
        assertThat(list.get(0).from()).isEqualTo("00:00");
        assertThat(list.get(6).to()).isEqualTo("24:00");
    }
}
