package com.midaas.moonwalk.strategy;

import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TableTurnoverEstimationStrategy {

    private static final double EATING_TIME_MULTIPLIER = 2.0; // Eating takes 200% of prep time

    /**
     * Calculates the exact instant a table will become completely free.
     */
    public Instant calculateTableFreedTime(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return Instant.now();
        }

        // Group items by their course sequence (1=Appetizers, 2=Mains, 3=Desserts)
        Map<Integer, List<OrderItem>> itemsByCourse = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getCourseSequence));

        int totalTableDurationSeconds = 0;

        // Calculate time for each course sequentially
        for (Integer course : itemsByCourse.keySet().stream().sorted().toList()) {
            List<OrderItem> courseItems = itemsByCourse.get(course);
            
            // The course takes as long as the SLOWEST dish to prepare
            int maxPrepTime = courseItems.stream()
                    .mapToInt(OrderItem::getBasePrepTime)
                    .max()
                    .orElse(0);

            // Total Course Time = Prep Time + Eating Time
            int eatingTime = (int) (maxPrepTime * EATING_TIME_MULTIPLIER);
            totalTableDurationSeconds += (maxPrepTime + eatingTime);
        }

        log.debug("Table {} will be occupied for a total of {} seconds for Order {}", 
                order.getTableId(), totalTableDurationSeconds, order.getId());

        // Return the final Instant the table will be free
        return order.getCreatedAt().plusSeconds(totalTableDurationSeconds);
    }
}