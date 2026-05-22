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

    private final OrderItemRepository orderItemRepository;
    private final KitchenDispatcherService kitchenDispatcher;

    @Scheduled(fixedDelay = 30000)
    @Override
    public void recoverStuckOrders() {

        // =================================================================
        // PHASE 1: HEAL "PREPARING" DISHES (Chef died/crashed mid-cooking)
        // =================================================================
        Instant threshold = Instant.now().minusSeconds(600); // 10 minutes fallback

        List<OrderItem> stuckDishes = orderItemRepository.findByStatusAndUpdatedAtBefore(
                OrderItemStatus.PREPARING,
                threshold
        );

        if (!stuckDishes.isEmpty()) {
            log.warn("CRON RECOVERY: Found {} stuck PREPARING dishes. Auto-healing now...", stuckDishes.size());

            for (OrderItem dish : stuckDishes) {
                try {
                    log.info("Healing Dish (Item ID: {})", dish.getId());
                    // We now use the actual restaurantId from the database!
                    kitchenDispatcher.markDishReady(dish.getRestaurantId(), dish.getId());
                } catch (Exception e) {
                    log.error("Failed to heal Dish (Item ID: {})", dish.getId(), e);
                }
            }
        }

        // =================================================================
        // PHASE 2: WAKE UP "QUEUED" DISHES (Kafka message dropped/Server restarted)
        // =================================================================
        List<Long> stalledRestaurants = orderItemRepository.findDistinctRestaurantIdsByStatus(OrderItemStatus.QUEUED);

        for (Long restaurantId : stalledRestaurants) {
            try {
                // This tells the dispatcher: "Hey, check if you have free chefs and queued food!"
                kitchenDispatcher.kickstartDispatcher(restaurantId);
            } catch (Exception e) {
                log.error("Failed to kickstart dispatcher for Restaurant {}", restaurantId, e);
            }
        }
    }
}