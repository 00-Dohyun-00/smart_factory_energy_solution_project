package com.smartfactory.energy.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.TestData;
import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.zone.dto.ZoneDetailResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

// 구역 상세: 없는 구역 404, 대소문자, 구역 코드가 빈 줄, 1분 합계와 오늘 요약
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZoneDetailServiceTest {

    @Mock ZoneRepository zoneRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock PowerMinuteRepository powerMinuteRepository;
    @InjectMocks ZoneDetailService zoneDetailService;

    List<PowerMinute> powerTable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Zone noCode = new Zone("이름만", 0, null); // 구역 코드가 빈 줄이 있어도 오류가 나면 안 됨
        Zone z2 = new Zone("브레이징", 2, "Z2");
        ReflectionTestUtils.setField(noCode, "zoneId", 1L);
        ReflectionTestUtils.setField(z2, "zoneId", 2L);
        when(zoneRepository.findAll()).thenReturn(List.of(noCode, z2));
        when(equipmentRepository.findAll()).thenReturn(List.of(
                equipment("Z2-FURN", 2L, EquipmentRole.LOAD, true),
                equipment("Z2-PUMP", 2L, EquipmentRole.LOAD, true),
                equipment("Z2-OLD", 2L, EquipmentRole.LOAD, false),   // 사용 안 함
                equipment("Z1-PRESS", 1L, EquipmentRole.LOAD, true)  // 다른 구역
        ));
        when(powerMinuteRepository.findInPeriod(anyList(), any(), any())).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            LocalDateTime start = call.getArgument(1);
            LocalDateTime end = call.getArgument(2);
            return powerTable.stream()
                    .filter(p -> ids.contains(p.getEquipmentId()))
                    .filter(p -> !p.getTs().isBefore(start) && p.getTs().isBefore(end))
                    .toList();
        });
    }

    @Test
    void 없는_구역이면_404() {
        assertThatThrownBy(() -> zoneDetailService.getDetail("Z9")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 구역_코드는_대소문자를_가리지_않고_사용_중인_그_구역_설비만_더함() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime t = now.minusMinutes(1).isBefore(now.toLocalDate().atStartOfDay()) ? now : now.minusMinutes(1);
        powerTable.add(power("Z2-FURN", t, 600, 10));
        powerTable.add(power("Z2-PUMP", t, 40, 0.7));
        powerTable.add(power("Z2-OLD", t, 999, 99));
        powerTable.add(power("Z1-PRESS", t, 999, 99));

        ZoneDetailResponse detail = zoneDetailService.getDetail("z2");

        assertThat(detail.zoneId()).isEqualTo("Z2");
        assertThat(detail.points()).hasSize(1);
        assertThat(detail.points().get(0).loadKw()).isEqualTo(640.0);
        assertThat(detail.todayMaxKw()).isEqualTo(640.0);
        assertThat(detail.todayKwh()).isEqualTo(10.7);
    }

    @Test
    void 기록이_없으면_빈_목록과_null_요약() {
        ZoneDetailResponse detail = zoneDetailService.getDetail("Z2");

        assertThat(detail.points()).isEmpty();
        assertThat(detail.todayMaxKw()).isNull();
        assertThat(detail.todayAvgKw()).isNull();
        assertThat(detail.todayKwh()).isEqualTo(0.0);
    }

    private static Equipment equipment(String id, Long zoneId, EquipmentRole role, boolean active) {
        return new Equipment(id, zoneId, id, role, TestData.bd(100), true, null, null, active, null);
    }

    private static PowerMinute power(String id, LocalDateTime ts, double kw, double kwh) {
        return new PowerMinute(id, ts, TestData.bd(kw), null, null, TestData.bd(kwh), null, null, null, (short) 1);
    }
}
