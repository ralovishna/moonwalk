package com.midaas.moonwalk.strategy;

import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TableTurnoverEstimationStrategy {

    private static final double EATING_TIME_MULTIPLIER = 2.0;

    public Instant calculateTableFreedTime(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return Instant.now();
        }

        Map<Integer, List<OrderItem>> pendingItemsByCourse = order.getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.READY_FOR_SERVE)
                .collect(Collectors.groupingBy(OrderItem::getCourseSequence));

        if (pendingItemsByCourse.isEmpty()) {
            return Instant.now();
        }

        int remainingSeconds = 0;

        for (Integer course : pendingItemsByCourse.keySet().stream().sorted().toList()) {
            List<OrderItem> courseItems = pendingItemsByCourse.get(course);

            int maxPrepTime = courseItems.stream()
                    .mapToInt(OrderItem::getBasePrepTime)
                    .max()
                    .orElse(0);

            int eatingTime = (int) (maxPrepTime * EATING_TIME_MULTIPLIER);
            remainingSeconds += (maxPrepTime + eatingTime);
        }

        return Instant.now().plusSeconds(remainingSeconds);
    }
}