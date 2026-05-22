package com.midaas.moonwalk.service;

import com.midaas.moonwalk.dto.WalkInResponse;
import org.springframework.transaction.annotation.Transactional;

public interface TableManagerService {
    @Transactional
    WalkInResponse processWalkIn(Long restaurantId, String customerName, Integer partySize);
}
