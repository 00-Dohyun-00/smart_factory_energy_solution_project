package com.smartfactory.energy.equipment;

import com.smartfactory.energy.equipment.dto.EquipmentResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentCache equipmentCache;

    public List<EquipmentResponse> findAll() {
        return equipmentCache.findAll().stream()
                .map(EquipmentResponse::from)
                .toList();
    }
}
