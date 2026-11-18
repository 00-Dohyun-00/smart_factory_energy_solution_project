package com.smartfactory.energy.setting;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettingHistoryRepository extends JpaRepository<SettingHistory, Long> {

    // start 이상 ~ end 미만 기간의 변경 기록을 최신 순으로 조회
    @Query("select h from SettingHistory h where h.changedAt >= :start and h.changedAt < :end order by h.changedAt desc")
    List<SettingHistory> findInPeriod(LocalDateTime start, LocalDateTime end);
}
