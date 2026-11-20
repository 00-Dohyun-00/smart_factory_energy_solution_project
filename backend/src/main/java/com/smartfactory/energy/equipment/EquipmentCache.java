package com.smartfactory.energy.equipment;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// equipment 전체를 서버 시작 때 한 번 읽어 메모리에 캐시합니다.
// 설비 구성은 운영 중에 안 바뀌므로, 그 뒤로는 DB를 다시 읽지 않습니다.
@Component
@RequiredArgsConstructor
public class EquipmentCache {

    private final EquipmentRepository equipmentRepository;

    private volatile List<Equipment> all = List.of();

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        all = equipmentRepository.findAll();
    }

    public List<Equipment> findAll() {
        return all;
    }

    public List<Equipment> findByEquipmentRole(EquipmentRole role) {
        return all.stream().filter(e -> e.getEquipmentRole() == role).toList();
    }
}
