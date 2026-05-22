package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.repository.OrderExecutionLogRepository;
import com.midaas.moonwalk.mapper.OrderExecutionLogMapper;
import com.midaas.moonwalk.repository.KitchenResourceRepository;

// (Keep your other imports...)
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midaas.moonwalk.dto.OrderRequest;
import com.midaas.moonwalk.entity.DiningTable;
import com.midaas.moonwalk.entity.MenuItem;
import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.enums.OrderStatus;
import com.midaas.moonwalk.repository.DiningTableRepository;
import com.midaas.moonwalk.repository.MenuItemRepository;
import com.midaas.moonwalk.repository.OrderItemRepository;
import com.midaas.moonwalk.repository.OrderRepository;
import com.midaas.moonwalk.service.OrderOrchestratorService;
import com.midaas.moonwalk.service.WaitlistManagerService;
import com.midaas.moonwalk.strategy.KitchenCourseEstimationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOrchestratorServiceImpl implements OrderOrchestratorService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenCourseEstimationStrategy courseEstimationStrategy;
    private final DiningTableRepository tableRepository;
    private final WaitlistManagerService waitlistManager;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OrderExecutionLogRepository logRepository;
    private final OrderExecutionLogMapper logMapper;
    private final KitchenResourceRepository resourceRepository;

    private void logOrderStateChange(Order order, String eventType) {
        var totalChefs = resourceRepository.findAllByRestaurantId(order.getRestaurantId()).size();
        var availableChefs = resourceRepository.countByRestaurantIdAndIsAvailableTrue(order.getRestaurantId());
        int activeWorkers = Math.toIntExact(totalChefs - availableChefs);
        int backlogCount = orderItemRepository.countByRestaurantIdAndStatus(order.getRestaurantId(), OrderItemStatus.QUEUED);

        int timeElapsed = (int) Duration.between(order.getCreatedAt(), Instant.now()).toSeconds();

        var executionLog = logMapper.toExecutionLog(
                order, order.getRestaurantId(), order.getStatus(),
                0, timeElapsed, activeWorkers, backlogCount,
                eventType
        );
        logRepository.save(executionLog);
    }

    @Override
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @Override
    @Transactional
    public Order placeDineInOrder(Long restaurantId, Long tableId, OrderRequest request) {

        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table ID " + tableId + " does not exist."));

        if (!table.isOccupied()) {
            table.setOccupied(true);
            tableRepository.save(table);
        }

        log.info("Creating new Dine-in Order for Table {}", tableId);

        Order order = Order.builder()
                .restaurantId(restaurantId)
                .tableId(tableId)
                .customerName(request.customerName())
                .partySize(request.partySize())
                .status(OrderStatus.QUEUED)
                .build();

        order = orderRepository.save(order);

        logOrderStateChange(order, "ORDER_QUEUED");

        List<OrderItem> savedItems = new ArrayList<>();

        for (var itemReq : request.items()) {
            MenuItem menuItem = menuItemRepository.findById(Math.toIntExact(itemReq.menuItemId()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Menu Item ID: " + itemReq.menuItemId()));

            OrderItem item = OrderItem.builder()
                    .orderId(order.getId())
                    .restaurantId(restaurantId)
                    .dishName(menuItem.getName())
                    .basePrepTime(menuItem.getBasePrepTime())
                    .courseSequence(menuItem.getCourseSequence())
                    .status(OrderItemStatus.QUEUED)
                    .build();

            savedItems.add(orderItemRepository.save(item));
        }

        order.setItems(savedItems);

        OrderItem firstCourseItem = savedItems.stream()
                .filter(i -> i.getCourseSequence() == 1)
                .findFirst()
                .orElse(savedItems.get(0));

        int firstCourseEta = courseEstimationStrategy.calculateItemEtaInSeconds(firstCourseItem, restaurantId);
        order.setEstimatedCompletionAt(Instant.now().plusSeconds(firstCourseEta));
        order.setStatus(OrderStatus.KITCHEN_PREPARING);
        orderRepository.save(order);

        logOrderStateChange(order, "ORDER_PREPARING");

        savedItems.stream()
                .filter(i -> i.getCourseSequence() == 1)
                .forEach(item -> publishDishToKitchen(restaurantId, item));

        return order;
    }

    @Async
    @Override
    @Transactional
    public void simulateEatingAndQueueNextCourse(Long orderId, Integer currentCourse, int longestPrepTime) {
        try {
            int eatingTimeSeconds = longestPrepTime * 2;
            log.info("AUTO-EATING: Customers for Order {} started eating Course {}. Waiting {} seconds...",
                    orderId, currentCourse, eatingTimeSeconds);

            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setStatus(OrderStatus.SERVED);
            orderRepository.save(order);

            logOrderStateChange(order, "ORDER_SERVED");

            Thread.sleep(eatingTimeSeconds * 1000L);
            log.info("AUTO-EATING: Customers for Order {} finished eating Course {}.", orderId, currentCourse);

            Integer nextCourse = currentCourse + 1;
            List<OrderItem> nextCourseItems = orderItemRepository.findByOrderIdAndCourseSequence(orderId, nextCourse);

            if (!nextCourseItems.isEmpty()) {
                log.info("AUTOMATION: Sending Course {} to the Kitchen for Order {}...", nextCourse, orderId);

                order.setStatus(OrderStatus.KITCHEN_PREPARING);

                OrderItem firstNextCourseItem = nextCourseItems.get(0);
                int nextCourseEta = courseEstimationStrategy.calculateItemEtaInSeconds(firstNextCourseItem, order.getRestaurantId());
                order.setEstimatedCompletionAt(Instant.now().plusSeconds(nextCourseEta));
                orderRepository.save(order);

                logOrderStateChange(order, "ORDER_PREPARING");

                nextCourseItems.forEach(item -> publishDishToKitchen(order.getRestaurantId(), item));

            } else {
                log.info("AUTOMATION: Meal complete for Order {}. Freeing the table.", orderId);
                completeOrderAndFreeTable(orderId);
            }

        } catch (InterruptedException e) {
            log.error("Eating simulation interrupted for Order {}", orderId);
            Thread.currentThread().interrupt();
        }
    }

    private void completeOrderAndFreeTable(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        logOrderStateChange(order, "ORDER_COMPLETED");

        if (order.getTableId() != null) {
            DiningTable table = tableRepository.findById(order.getTableId()).orElseThrow();

            var bestMatchOpt = waitlistManager.popBestMatchForTable(order.getRestaurantId(), table.getCapacity());

            if (bestMatchOpt.isPresent()) {
                var queuedCustomer = bestMatchOpt.get();
                log.info("Table {} (Capacity {}) freed, automatically reserved for Waitlist Customer: {} (Party of {})",
                        table.getTableNumber(), table.getCapacity(), queuedCustomer.customerName(), queuedCustomer.partySize());
            } else {
                table.setOccupied(false);
                tableRepository.save(table);
                log.info("Table {} is now clean and empty.", table.getTableNumber());
            }
        }
    }

    private void publishDishToKitchen(Long restaurantId, OrderItem item) {
        try {
            Map<String, Object> payload = Map.of(
                    "orderItemId", item.getId(),
                    "restaurantId", restaurantId,
                    "prepTime", item.getBasePrepTime()
            );
            kafkaTemplate.send("moonwalk.kitchen.events", String.valueOf(restaurantId), objectMapper.writeValueAsString(payload));
            log.info("Sent Course {} item '{}' to kitchen queue.", item.getCourseSequence(), item.getDishName());
        } catch (Exception e) {
            log.error("Failed to send item {} to Kafka", item.getId(), e);
        }
    }
}