package com.midaas.moonwalk.service;

import org.springframework.scheduling.annotation.Async;

public interface CookingSimulatorService {
    @Async
    void simulateCooking(Long restaurantId, Long orderId, Integer prepTimeSeconds);
}
