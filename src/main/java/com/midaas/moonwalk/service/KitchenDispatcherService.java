package com.midaas.moonwalk.service;

import org.springframework.transaction.annotation.Transactional;

public interface KitchenDispatcherService {
    @Transactional
    boolean tryAssignDishToChef(Long restaurantId, Long orderItemId);

    @Transactional
    void processDishQueuedEvent(Long restaurantId, Long orderItemId, int prepTime);

    @Transactional
    void markDishReady(Long restaurantId, Long orderItemId);

    void kickstartDispatcher(Long restaurantId);
}
