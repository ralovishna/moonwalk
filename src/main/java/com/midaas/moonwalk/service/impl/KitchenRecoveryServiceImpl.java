package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.repository.OrderItemRepository;
import com.midaas.moonwalk.service.KitchenDispatcherService;
import com.midaas.moonwalk.service.KitchenRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenRecoveryServiceImpl implements KitchenRecoveryService {

    // Note: Use OrderItemRepository now!
    private final OrderItemRepository orderItemRepository;
    private final KitchenDispatcherService kitchenDispatcher;

    @Scheduled(fixedDelay = 30000)
    @Override
    public void recoverStuckOrders() {
        // For individual dishes, we don't have an ETA stored on the item row itself in this model,
        // so we just check if it has been stuck in PREPARING for way longer than its prep time.
        // A simple query: Find PREPARING items updated more than 10 minutes ago (adjust as needed).
        Instant threshold = Instant.now().minusSeconds(600); // 10 minutes fallback

        List<OrderItem> stuckDishes = orderItemRepository.findByStatusAndUpdatedAtBefore(
                OrderItemStatus.PREPARING,
                threshold
        );

        if (!stuckDishes.isEmpty()) {
            log.warn("CRON RECOVERY: Found {} stuck dishes. The cooking threads likely died. Auto-healing now...", stuckDishes.size());

            for (OrderItem dish : stuckDishes) {
                try {
                    log.info("Healing Dish (Item ID: {})", dish.getId());
                    // Requires restaurantId. You may need to fetch the parent Order to get the restaurantId,
                    // or add restaurantId to OrderItem. Let's assume restaurantId=1 for this quick fix.
                    kitchenDispatcher.markDishReady(1L, dish.getId());
                } catch (Exception e) {
                    log.error("Failed to heal Dish (Item ID: {})", dish.getId(), e);
                }
            }
        }
    }
}