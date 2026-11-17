package com.smartfactory.energy.equipment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    List<Equipment> findByEquipmentRole(EquipmentRole equipmentRole);
}
