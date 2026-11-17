package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawFactoryLoadRepository extends JpaRepository<RawFactoryLoad, LocalDateTime> {

    List<RawFactoryLoad> findAllByOrderByRecordedAtAsc();
}
