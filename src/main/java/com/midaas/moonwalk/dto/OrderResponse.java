package com.midaas.moonwalk.dto;

import com.midaas.moonwalk.enums.OrderStatus;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        Long restaurantId,
        Long tableId,
        String customerName,

        OrderStatus status,

        Instant estimatedCompletionAt,

        Integer nextCourseCountdownSeconds
) {
}