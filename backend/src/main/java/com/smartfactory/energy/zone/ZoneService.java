package com.smartfactory.energy.zone;

import com.smartfactory.energy.zone.dto.ZoneResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public List<ZoneResponse> findAll() {
        return zoneRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ZoneResponse::from)
                .toList();
    }
}
