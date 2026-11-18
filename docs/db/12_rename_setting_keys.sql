-- 12. setting 키 이름을 API 명세 v0.2 형식으로 변경 (TARGET_PEAK → peak.target_kw 등)
-- 백엔드·프론트 코드는 이미 명세 키를 쓰고 있어서, DB 키만 바꾸면 설정값을 못 찾던 문제가 해결됨
-- setting_history.setting_key 가 setting.setting_key 를 외래키로 참조하므로
-- "새 키 행 추가 → 이력 키 옮기기 → 옛 키 행 삭제" 순서로 처리함
-- 한 번만 실행. 중간에 오류가 나면 전체가 취소됨 (BEGIN ~ COMMIT)

BEGIN;

-- 옛 키 → 새 키 대응표 (이 트랜잭션 안에서만 쓰는 임시 테이블)
CREATE TEMP TABLE setting_key_map (old_key varchar(40) PRIMARY KEY, new_key varchar(40) NOT NULL UNIQUE) ON COMMIT DROP;

INSERT INTO setting_key_map (old_key, new_key) VALUES
    ('TARGET_PEAK',        'peak.target_kw'),
    ('WARN_RATIO',         'peak.warn_ratio'),
    ('BASE_RATE',          'tariff.base_charge'),
    ('SOC_MIN',            'ess.soc_low'),
    ('SOC_MAX',            'ess.soc_high'),
    ('BATTERY_TEMP_LIMIT', 'ess.room_temp_limit'),
    ('ESS_CAPACITY',       'ess.capacity_kwh'),
    ('ESS_MAX_POWER',      'ess.max_power_kw'),
    ('ESS_EFFICIENCY',     'ess.efficiency_percent'),
    ('SOH_PERCENT',        'ess.soh_percent'),
    ('CARBON_FACTOR',      'carbon.emission_factor'),
    ('SOLAR_CAPACITY',     'solar.capacity_kw'),
    ('SOLAR_PR',           'solar.performance_ratio'),
    ('OP_MODE',            'operation.auto_mode'),
    ('RECOMMEND_TTL_MIN',  'operation.decision_valid_minutes'),
    ('REFRESH_SEC',        'operation.poll_seconds');

-- 1. 새 키로 행 복사 (값·라벨·단위·수정 시각은 그대로)
--    운영 모드는 값 형식도 바꿈: 'AUTO' → 'true'
INSERT INTO public.setting (setting_key, group_name, label, value, unit, updated_at, updated_by)
SELECT m.new_key,
       s.group_name,
       s.label,
       CASE WHEN s.setting_key = 'OP_MODE' AND s.value = 'AUTO' THEN 'true' ELSE s.value END,
       s.unit,
       s.updated_at,
       s.updated_by
FROM public.setting s
JOIN setting_key_map m ON m.old_key = s.setting_key;

-- 2. 변경 이력의 키를 새 키로 옮김 (이력의 옛 값·새 값은 기록이므로 그대로 둠)
UPDATE public.setting_history h
SET setting_key = m.new_key
FROM setting_key_map m
WHERE h.setting_key = m.old_key;

-- 3. 옛 키 행 삭제
DELETE FROM public.setting s
USING setting_key_map m
WHERE s.setting_key = m.old_key;

-- 확인: 대문자 키가 남아 있지 않아야 하고, 16개가 모두 새 키로 있어야 함
SELECT setting_key, group_name, label, value, unit
FROM public.setting
ORDER BY setting_key;

COMMIT;
