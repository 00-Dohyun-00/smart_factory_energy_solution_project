package com.smartfactory.energy.solar;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolarPowerRepository extends JpaRepository<SolarPower, SolarPowerId> {

    Optional<SolarPower> findFirstByPowerTypeOrderByTsDesc(SolarPowerType powerType);
}
