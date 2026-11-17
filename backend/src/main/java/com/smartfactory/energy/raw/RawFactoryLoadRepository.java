package com.smartfactory.energy.raw;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawFactoryLoadRepository extends JpaRepository<RawFactoryLoad, LocalDateTime> {
}
