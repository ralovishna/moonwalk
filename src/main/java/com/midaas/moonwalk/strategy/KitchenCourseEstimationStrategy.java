package com.midaas.moonwalk.strategy;

import com.midaas.moonwalk.entity.KitchenResource;
import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.repository.KitchenResourceRepository;
import com.midaas.moonwalk.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenCourseEstimationStrategy {

    private final KitchenResourceRepository resourceRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * Calculates the ETA for a specific DISH (OrderItem) based on current chef workload.
     */
    public int calculateItemEtaInSeconds(OrderItem targetItem, Long restaurantId) {
        Instant now = Instant.now();

        // 1. Build Chef Timeline
        PriorityQueue<Instant> chefTimelines = new PriorityQueue<>(Comparator.naturalOrder());
        List<KitchenResource> activeResources = resourceRepository.findAllByRestaurantId(restaurantId);

        if (activeResources.isEmpty()) {
            return targetItem.getBasePrepTime(); // Fallback if no chefs configured
        }

        // Initialize timelines based on what chefs are cooking right now
        for (KitchenResource resource : activeResources) {
            if (resource.isAvailable() || resource.getCurrentOrderItemId() == null) {
                chefTimelines.add(now);
            } else {
                // In reality, query the DB for when this specific item finishes
                chefTimelines.add(now); // Simplified for snippet, add your DB lookup here!
            }
        }

        // 2. Fetch the backlog of ALL QUEUED items across the restaurant
        List<OrderItem> pendingQueue = orderItemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(
                targetItem.getOrderId(), // Fetch items for the whole restaurant
                OrderItemStatus.QUEUED
        );

        // 3. Fast-forward the simulation
        for (OrderItem backloggedItem : pendingQueue) {
            if (backloggedItem.getId().equals(targetItem.getId())) {
                continue; // Skip the one we are calculating
            }

            Instant earliestFreeTime = chefTimelines.poll();
            if (earliestFreeTime == null) earliestFreeTime = now;

            chefTimelines.add(earliestFreeTime.plusSeconds(backloggedItem.getBasePrepTime()));
        }

        // 4. Slot in our target dish
        Instant nextAvailableChefTime = chefTimelines.peek();
        if (nextAvailableChefTime == null) nextAvailableChefTime = now;

        Instant finalCompletionTime = nextAvailableChefTime.plusSeconds(targetItem.getBasePrepTime());

        return Math.max((int) Duration.between(now, finalCompletionTime).toSeconds(), targetItem.getBasePrepTime());
    }
}