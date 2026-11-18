package com.smartfactory.energy.setting;

import com.smartfactory.energy.setting.dto.SettingHistoryResponse;
import com.smartfactory.energy.setting.dto.SettingResponse;
import com.smartfactory.energy.setting.dto.SettingUpdateRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

    // 로그인이 없어서 화면에서 바꾸면 처리자는 항상 "운영자"
    private static final String OPERATOR = "운영자";

    // 화면에서 바꿀 수 있는 숫자 키와 허용 범위 (최소, 최대). 여기 없는 키는 보기만 가능
    private static final Map<String, double[]> NUMBER_RANGES = Map.of(
            "peak.target_kw", new double[]{1, 100000},
            "peak.warn_ratio", new double[]{50, 100},
            "ess.soc_low", new double[]{0, 100},
            "ess.soc_high", new double[]{0, 100},
            "ess.room_temp_limit", new double[]{0, 80}
    );

    // 화면에서 바꿀 수 있는 true/false 키
    private static final String AUTO_MODE_KEY = "operation.auto_mode";

    private final SettingRepository settingRepository;
    private final SettingHistoryRepository settingHistoryRepository;

    // 기준값 전체 조회 (키 이름 순)
    public List<SettingResponse> findAll() {
        return settingRepository.findAll().stream()
                .sorted(Comparator.comparing(Setting::getSettingKey))
                .map(SettingResponse::from)
                .toList();
    }

    // 기준값 여러 개 저장. 값이 바뀐 키만 저장하고 변경 기록을 1줄씩 남김
    @Transactional
    public List<SettingResponse> update(List<SettingUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("저장할 값이 없습니다.");
        }

        // 1. 먼저 전부 검사함 (하나라도 틀리면 아무것도 저장하지 않음)
        Map<String, Setting> settings = new HashMap<>();
        for (Setting setting : settingRepository.findAll()) {
            settings.put(setting.getSettingKey(), setting);
        }
        Map<String, String> newValues = new HashMap<>();
        for (SettingUpdateRequest request : requests) {
            if (request.key() == null || request.key().isBlank() || request.value() == null || request.value().isBlank()) {
                throw new IllegalArgumentException("key와 value는 비워 둘 수 없습니다.");
            }
            Setting setting = settings.get(request.key());
            if (setting == null) {
                throw new IllegalArgumentException("없는 설정입니다: " + request.key());
            }
            String value = request.value().trim();
            // 값이 그대로면 검사하지 않고 넘어감 (보기만 하는 값이 같이 와도 괜찮음)
            if (value.equals(setting.getValue())) {
                continue;
            }
            checkValue(setting, value);
            newValues.put(request.key(), value);
        }
        checkSocRange(settings, newValues);

        // 2. 바뀐 값만 저장 + 변경 기록
        LocalDateTime now = LocalDateTime.now().withNano(0); // 초 단위까지만
        for (String key : newValues.keySet()) {
            Setting setting = settings.get(key);
            String oldValue = setting.getValue();
            setting.changeValue(newValues.get(key), now, OPERATOR);
            settingHistoryRepository.save(new SettingHistory(now, key, oldValue, newValues.get(key), OPERATOR));
        }

        return findAll();
    }

    // 기간 안의 변경 기록 (최신 순)
    public List<SettingHistoryResponse> findHistories(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }

        // 키 → 화면 이름 (기록에는 키만 있어서 setting에서 찾아 붙임)
        Map<String, String> labels = new HashMap<>();
        for (Setting setting : settingRepository.findAll()) {
            labels.put(setting.getSettingKey(), setting.getLabel());
        }

        return settingHistoryRepository.findInPeriod(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(history -> SettingHistoryResponse.from(history, labels.get(history.getSettingKey())))
                .toList();
    }

    // 바꿀 수 있는 키인지, 값이 올바른지 검사
    private void checkValue(Setting setting, String value) {
        String key = setting.getSettingKey();

        if (key.equals(AUTO_MODE_KEY)) {
            if (!value.equals("true") && !value.equals("false")) {
                throw new IllegalArgumentException(setting.getLabel() + " 값은 true 또는 false여야 합니다.");
            }
            return;
        }

        double[] range = NUMBER_RANGES.get(key);
        if (range == null) {
            throw new IllegalArgumentException(setting.getLabel() + "은(는) 화면에서 바꿀 수 없는 값입니다.");
        }
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(setting.getLabel() + " 값은 숫자여야 합니다.");
        }
        if (number < range[0] || number > range[1]) {
            throw new IllegalArgumentException(
                    setting.getLabel() + " 값은 " + format(range[0]) + " ~ " + format(range[1]) + " 사이여야 합니다.");
        }
    }

    // 잔량 하한은 상한보다 작아야 함 (바꾼 값과 지금 값을 섞어서 확인)
    private void checkSocRange(Map<String, Setting> settings, Map<String, String> newValues) {
        if (!newValues.containsKey("ess.soc_low") && !newValues.containsKey("ess.soc_high")) {
            return;
        }
        String low = newValues.getOrDefault("ess.soc_low", valueOf(settings.get("ess.soc_low")));
        String high = newValues.getOrDefault("ess.soc_high", valueOf(settings.get("ess.soc_high")));
        if (low != null && high != null && Double.parseDouble(low) >= Double.parseDouble(high)) {
            throw new IllegalArgumentException("잔량 하한은 잔량 상한보다 작아야 합니다.");
        }
    }

    private String valueOf(Setting setting) {
        return setting == null ? null : setting.getValue();
    }

    // 100000.0 → "100000" 처럼 소수점 없이 보여줌
    private String format(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
