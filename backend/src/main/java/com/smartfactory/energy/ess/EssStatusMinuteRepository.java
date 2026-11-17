package com.smartfactory.energy.ess;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EssStatusMinuteRepository extends JpaRepository<EssStatusMinute, LocalDateTime> {
}
