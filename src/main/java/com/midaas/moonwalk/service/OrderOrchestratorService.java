package com.midaas.moonwalk.service;

import com.midaas.moonwalk.dto.OrderRequest;
import com.midaas.moonwalk.entity.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

public interface OrderOrchestratorService {
    @Transactional(readOnly = true)
    Order getOrder(Long orderId);

    @Transactional
    Order placeDineInOrder(Long restaurantId, Long tableId, OrderRequest request);

    @Async
    @Transactional
    void simulateEatingAndQueueNextCourse(Long orderId, Integer currentCourse, int longestPrepTime);
}
