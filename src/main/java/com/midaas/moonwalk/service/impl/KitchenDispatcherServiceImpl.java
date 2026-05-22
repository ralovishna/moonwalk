package com.midaas.moonwalk.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midaas.moonwalk.entity.KitchenResource;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.repository.KitchenResourceRepository;
import com.midaas.moonwalk.repository.OrderItemRepository;
import com.midaas.moonwalk.service.CookingSimulatorService;
import com.midaas.moonwalk.service.KitchenDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenDispatcherServiceImpl implements KitchenDispatcherService {

    private final KitchenResourceRepository resourceRepository;
    private final OrderItemRepository orderItemRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CookingSimulatorService cookingSimulator;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private KitchenDispatcherService self;

    @Override
    @Transactional
    public boolean tryAssignDishToChef(Long restaurantId, Long orderItemId) {
        try {
            var item = orderItemRepository.findById(orderItemId).orElseThrow();

            if (item.getStatus() != OrderItemStatus.QUEUED) {
                return false;
            }

            var availableChefOpt = resourceRepository.findFirstByRestaurantIdAndIsAvailableTrue(restaurantId);
            if (availableChefOpt.isEmpty()) return false;

            item.setStatus(OrderItemStatus.PREPARING);
            orderItemRepository.save(item);

            KitchenResource chef = availableChefOpt.get();
            chef.setAvailable(false);
            chef.setCurrentOrderItemId(orderItemId);
            resourceRepository.save(chef);

            log.info("Assigned Dish {} to Chef {}", orderItemId, chef.getResourceName());
            return true;

        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            log.debug("Concurrency collision for Dish {}: Another chef grabbed it first. Safely ignoring.", orderItemId);
            return false;
        }
    }

    @Override
    @Transactional
    public void processDishQueuedEvent(Long restaurantId, Long orderItemId, int prepTime) {
        boolean assigned = self.tryAssignDishToChef(restaurantId, orderItemId);

        if (assigned) {
            try {
                Map<String, Object> payloadMap = Map.of(
                        "orderItemId", orderItemId,
                        "restaurantId", restaurantId,
                        "assigned", true
                );
                kafkaTemplate.send("moonwalk.kitchen.events", String.valueOf(restaurantId), objectMapper.writeValueAsString(payloadMap));

                cookingSimulator.simulateCooking(restaurantId, orderItemId, prepTime);
            } catch (Exception e) {
                log.error("Failed to send ChefAssignedEvent for dish", e);
            }
        } else {
            log.info("All chefs busy. Dish (Item ID: {}) will remain QUEUED until a chef is free.", orderItemId);
        }
    }

    @Override
    @Transactional
    public void markDishReady(Long restaurantId, Long orderItemId) {
        log.info("Dish (Item ID: {}) is ready! Freeing chef and pulling next item...", orderItemId);

        freeAssignedChef(orderItemId);
        emitDishReadyEvent(restaurantId, orderItemId);

        dispatchAsMuchAsPossible(restaurantId);
    }

    @Override
    @Transactional
    public void kickstartDispatcher(Long restaurantId) {
        log.info("CRON RECOVERY: Waking up dispatcher for Restaurant {} to process stranded QUEUED dishes...", restaurantId);
        dispatchAsMuchAsPossible(restaurantId);
    }

    private void freeAssignedChef(Long orderItemId) {
        resourceRepository.findFirstByCurrentOrderItemId(orderItemId)
                .ifPresent(chef -> {
                    chef.setAvailable(true);
                    chef.setCurrentOrderItemId(null);
                    resourceRepository.save(chef);
                    log.info("Chef {} is now free!", chef.getResourceName());
                });
    }

    private void emitDishReadyEvent(Long restaurantId, Long orderItemId) {
        try {
            Map<String, Object> payloadMap = Map.of(
                    "orderItemId", orderItemId,
                    "restaurantId", restaurantId,
                    "isReady", true
            );
            kafkaTemplate.send("moonwalk.kitchen.events", String.valueOf(restaurantId), objectMapper.writeValueAsString(payloadMap));
        } catch (Exception e) {
            log.error("Failed to send DishReadyEvent for Item {}", orderItemId, e);
        }
    }

    private void dispatchAsMuchAsPossible(Long restaurantId) {

        Integer activeCourse = orderItemRepository.findMinCourseSequenceForStatus(restaurantId, OrderItemStatus.QUEUED);
        if (activeCourse == null) return;

        while (true) {
            var chefOpt = resourceRepository.findFirstByRestaurantIdAndIsAvailableTrue(restaurantId);
            if (chefOpt.isEmpty()) return;

            var nextDishOpt = orderItemRepository
                    .findFirstByRestaurantIdAndStatusAndCourseSequenceOrderByCreatedAtAsc(
                            restaurantId, OrderItemStatus.QUEUED, activeCourse);

            if (nextDishOpt.isEmpty()) return;

            var nextDish = nextDishOpt.get();

            boolean assigned = self.tryAssignDishToChef(restaurantId, nextDish.getId());
            if (!assigned) {
                continue;
            }

            try {
                Map<String, Object> payloadMap = Map.of(
                        "orderItemId", nextDish.getId(),
                        "restaurantId", restaurantId,
                        "assigned", true
                );
                kafkaTemplate.send("moonwalk.kitchen.events", String.valueOf(restaurantId),
                        objectMapper.writeValueAsString(payloadMap));
            } catch (Exception e) {
                log.error("Failed to send ChefAssignedEvent", e);
            }

            cookingSimulator.simulateCooking(restaurantId, nextDish.getId(), nextDish.getBasePrepTime());
        }
    }
}