package com.midaas.moonwalk.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.mapper.OrderExecutionLogMapper;
import com.midaas.moonwalk.repository.KitchenResourceRepository;
import com.midaas.moonwalk.repository.OrderExecutionLogRepository;
import com.midaas.moonwalk.repository.OrderItemRepository;
import com.midaas.moonwalk.repository.OrderRepository;
import com.midaas.moonwalk.service.OrderOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final OrderExecutionLogRepository logRepository;
    private final KitchenResourceRepository resourceRepository;
    private final OrderOrchestratorService orchestratorService;
    private final OrderExecutionLogMapper logMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "moonwalk.kitchen.events", groupId = "order-service-group")
    @Transactional
    public void handleKitchenEvents(String messagePayload) {
        try {
            JsonNode payload = objectMapper.readTree(messagePayload);

            // We only care about when a chef picks up a dish, or finishes a dish
            if (!payload.has("assigned") && !payload.has("isReady")) {
                return;
            }

            var orderItemId = payload.get("orderItemId").asLong();
            var orderItem = orderItemRepository.findById(orderItemId)
                    .orElseThrow(() -> new RuntimeException("Dish not found: " + orderItemId));

            var newStatus = orderItem.getStatus();

            if (payload.has("assigned") && payload.get("assigned").asBoolean()) {
                newStatus = OrderItemStatus.PREPARING;
            } else if (payload.has("isReady") && payload.get("isReady").asBoolean()) {
                newStatus = OrderItemStatus.READY_FOR_SERVE;
            }

            // If the status changed, update the DB and log it
            if (orderItem.getStatus() != newStatus) {
                orderItem.setStatus(newStatus);
                orderItemRepository.save(orderItem);

                // --- 1. WRITE THE EXECUTION LOG FOR THIS DISH ---
                writeExecutionLog(orderItem, newStatus);

                // --- 2. THE COURSE SEQUENCING MAGIC ---
                if (newStatus == OrderItemStatus.READY_FOR_SERVE) {
                    checkAndTriggerNextCourse(orderItem);
                }
            }

        } catch (Exception exception) {
            log.error("Failed to process Kitchen event in Order service", exception);
        }
    }

    private void writeExecutionLog(OrderItem item, OrderItemStatus newStatus) {
        Order parentOrder = orderRepository.findById(item.getOrderId()).orElseThrow();
        Long restaurantId = parentOrder.getRestaurantId();

        var now = Instant.now();
        int timeElapsed = (int) Duration.between(item.getCreatedAt(), now).toSeconds();

        var totalChefs = resourceRepository.findAllByRestaurantId(restaurantId).size();
        var availableChefs = resourceRepository.countByRestaurantIdAndIsAvailableTrue(restaurantId);
        int activeWorkers = Math.toIntExact(totalChefs - availableChefs);

        int backlogCount = orderItemRepository.countByRestaurantIdAndStatus(restaurantId, OrderItemStatus.QUEUED);

        // We use the parent Order's status just to satisfy the old log structure,
        // but we record the ITEM's prep time and elapsed time!
        var executionLog = logMapper.toExecutionLog(
                parentOrder, restaurantId, parentOrder.getStatus(),
                item.getBasePrepTime(), timeElapsed, activeWorkers, backlogCount,
                "DISH_STATUS_CHANGE"
        );
        logRepository.save(executionLog);
    }

    private void checkAndTriggerNextCourse(OrderItem finishedItem) {
        Long orderId = finishedItem.getOrderId();
        Integer currentCourse = finishedItem.getCourseSequence();

        // Find all items for this specific course
        List<OrderItem> courseItems = orderItemRepository.findByOrderIdAndCourseSequence(orderId, currentCourse);

        // Check if ANY item in this course is still cooking
        boolean allReady = courseItems.stream()
                .allMatch(item -> item.getStatus() == OrderItemStatus.READY_FOR_SERVE);

        if (allReady) {
            log.info("All dishes for Course {} are ready! Triggering eating phase...", currentCourse);

            int longestPrepTime = courseItems.stream().mapToInt(OrderItem::getBasePrepTime).max().orElse(0);

            // Tell the Orchestrator to start the eating timer, and then queue up Course N+1
            orchestratorService.simulateEatingAndQueueNextCourse(orderId, currentCourse, longestPrepTime);
        }
    }
}