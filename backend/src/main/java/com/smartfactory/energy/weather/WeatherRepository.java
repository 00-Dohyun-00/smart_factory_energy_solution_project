package com.smartfactory.energy.weather;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository extends JpaRepository<Weather, LocalDateTime> {
}
