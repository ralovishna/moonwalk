package com.midaas.moonwalk.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.midaas.moonwalk.dto.OrderRequest;
import com.midaas.moonwalk.entity.DiningTable;
import com.midaas.moonwalk.entity.MenuItem;
import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.entity.WaitlistEntry;
import com.midaas.moonwalk.enums.OrderItemStatus;
import com.midaas.moonwalk.enums.OrderStatus;
import com.midaas.moonwalk.repository.DiningTableRepository;
import com.midaas.moonwalk.repository.MenuItemRepository;
import com.midaas.moonwalk.repository.OrderItemRepository;
import com.midaas.moonwalk.repository.OrderRepository;
import com.midaas.moonwalk.repository.WaitlistRepository;
import com.midaas.moonwalk.service.OrderOrchestratorService;
import com.midaas.moonwalk.strategy.KitchenCourseEstimationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MenuItemRepository menuItemRepository; // Added this!
    private final KitchenCourseEstimationStrategy courseEstimationStrategy;
    private final DiningTableRepository tableRepository;
    private final WaitlistRepository waitlistRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @Override
    @Transactional
    public Order placeDineInOrder(Long restaurantId, Long tableId, OrderRequest request) {
        log.info("Creating new Dine-in Order for Table {}", tableId);

        // 1. Create the parent Order
        Order order = Order.builder()
                .restaurantId(restaurantId)
                .tableId(tableId)
                .customerName(request.customerName())
                .partySize(request.partySize())
                .status(OrderStatus.KITCHEN_PREPARING)
                .build();

        order = orderRepository.save(order);

        List<OrderItem> savedItems = new ArrayList<>();

        for (var itemReq : request.items()) {
            // Securely fetch the official menu item details from the DB
            MenuItem menuItem = menuItemRepository.findById(Math.toIntExact(itemReq.menuItemId()))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Menu Item ID: " + itemReq.menuItemId()));

            OrderItem item = OrderItem.builder()
                    .orderId(order.getId())
                    .restaurantId(restaurantId)
                    .dishName(menuItem.getName())            // From DB!
                    .basePrepTime(menuItem.getBasePrepTime())// From DB!
                    .courseSequence(menuItem.getCourseSequence()) // From DB!
                    .status(OrderItemStatus.QUEUED)
                    .build();

            savedItems.add(orderItemRepository.save(item));
        }

        order.setItems(savedItems);

        // 3. Calculate ETA for the FIRST course to show on the Customer's screen
        OrderItem firstCourseItem = savedItems.stream()
                .filter(i -> i.getCourseSequence() == 1)
                .findFirst()
                .orElse(savedItems.get(0));

        int firstCourseEta = courseEstimationStrategy.calculateItemEtaInSeconds(firstCourseItem, restaurantId);
        order.setEstimatedCompletionAt(Instant.now().plusSeconds(firstCourseEta));
        orderRepository.save(order);

        // 4. Send ONLY Course 1 items to the Kitchen via Kafka!
        // The Kitchen doesn't need to know about Course 2 yet.
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
            // 1. Calculate Eating Time (200% of the longest dish in the course)
            int eatingTimeSeconds = longestPrepTime * 2;
            log.info("AUTO-EATING: Customers for Order {} started eating Course {}. Waiting {} seconds...",
                    orderId, currentCourse, eatingTimeSeconds);

            // 2. The Automation: Thread sleeps while they eat!
            Thread.sleep(eatingTimeSeconds * 1000L);
            log.info("AUTO-EATING: Customers for Order {} finished eating Course {}.", orderId, currentCourse);

            // 3. Find dishes for the NEXT course
            Integer nextCourse = currentCourse + 1;
            List<OrderItem> nextCourseItems = orderItemRepository.findByOrderIdAndCourseSequence(orderId, nextCourse);

            if (!nextCourseItems.isEmpty()) {
                log.info("AUTOMATION: Sending Course {} to the Kitchen for Order {}...", nextCourse, orderId);
                Order order = orderRepository.findById(orderId).orElseThrow();

                // Recalculate the ETA for the Customer's screen for Course 2
                OrderItem firstNextCourseItem = nextCourseItems.get(0);
                int nextCourseEta = courseEstimationStrategy.calculateItemEtaInSeconds(firstNextCourseItem, order.getRestaurantId());
                order.setEstimatedCompletionAt(Instant.now().plusSeconds(nextCourseEta));
                orderRepository.save(order);

                // Publish Course 2 to Kafka so Chefs can start cooking it!
                nextCourseItems.forEach(item -> publishDishToKitchen(order.getRestaurantId(), item));

            } else {
                // No more courses exist! The meal is completely over.
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

        if (order.getTableId() != null) {
            DiningTable table = tableRepository.findById(order.getTableId()).orElseThrow();

            // MAGIC: Check the waitlist for the oldest waiting customer that fits at this table!
            var nextInLine = waitlistRepository.findFirstByRestaurantIdAndStatusAndPartySizeLessThanEqualOrderByCreatedAtAsc(
                    order.getRestaurantId(), "WAITING", table.getCapacity());

            if (nextInLine.isPresent()) {
                // Auto-assign the table to the person who waited the longest!
                WaitlistEntry queuedCustomer = nextInLine.get();
                queuedCustomer.setStatus("SEATED");
                waitlistRepository.save(queuedCustomer);

                log.info("Table {} freed, automatically assigned to Waitlist Customer: {}", table.getTableNumber(), queuedCustomer.getCustomerName());
                // (In a real app, this is where you'd trigger a Twilio SMS saying "Your table is ready!")
            } else {
                // No one is waiting, leave the table empty
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
            // Notice we are sending the orderITEMId now, not the OrderId!
            kafkaTemplate.send("moonwalk.kitchen.events", String.valueOf(restaurantId), objectMapper.writeValueAsString(payload));
            log.info("Sent Course {} item '{}' to kitchen queue.", item.getCourseSequence(), item.getDishName());
        } catch (Exception e) {
            log.error("Failed to send item {} to Kafka", item.getId(), e);
        }
    }
}
