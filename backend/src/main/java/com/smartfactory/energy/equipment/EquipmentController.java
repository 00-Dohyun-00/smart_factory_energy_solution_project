package com.smartfactory.energy.equipment;

import com.smartfactory.energy.equipment.dto.EquipmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "설비", description = "설비(Equipment) 조회 API")
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @Operation(summary = "설비 목록 조회")
    @GetMapping
    public List<EquipmentResponse> findAll() {
        return equipmentService.findAll();
    }
}
