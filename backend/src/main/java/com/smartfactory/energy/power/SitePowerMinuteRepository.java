package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SitePowerMinuteRepository extends JpaRepository<SitePowerMinute, LocalDateTime> {
}
