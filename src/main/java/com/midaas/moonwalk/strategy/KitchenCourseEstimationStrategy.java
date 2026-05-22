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

    public int calculateItemEtaInSeconds(OrderItem targetItem, Long restaurantId) {
        Instant now = Instant.now();

        PriorityQueue<Instant> chefTimelines = new PriorityQueue<>(Comparator.naturalOrder());
        List<KitchenResource> activeResources = resourceRepository.findAllByRestaurantId(restaurantId);

        if (activeResources.isEmpty()) {
            return targetItem.getBasePrepTime();
        }

        for (KitchenResource resource : activeResources) {
            if (resource.isAvailable() || resource.getCurrentOrderItemId() == null) {
                chefTimelines.add(now);
            } else {
                chefTimelines.add(now);
            }
        }

        List<OrderItem> pendingQueue = orderItemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(
                targetItem.getOrderId(),
                OrderItemStatus.QUEUED
        );

        for (OrderItem backloggedItem : pendingQueue) {
            if (backloggedItem.getId().equals(targetItem.getId())) {
                continue;
            }

            Instant earliestFreeTime = chefTimelines.poll();
            if (earliestFreeTime == null) earliestFreeTime = now;

            chefTimelines.add(earliestFreeTime.plusSeconds(backloggedItem.getBasePrepTime()));
        }

        Instant nextAvailableChefTime = chefTimelines.peek();
        if (nextAvailableChefTime == null) nextAvailableChefTime = now;

        Instant finalCompletionTime = nextAvailableChefTime.plusSeconds(targetItem.getBasePrepTime());

        return Math.max((int) Duration.between(now, finalCompletionTime).toSeconds(), targetItem.getBasePrepTime());
    }
}