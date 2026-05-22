package com.midaas.moonwalk.mapper;

import com.midaas.moonwalk.dto.OrderResponse;
import com.midaas.moonwalk.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Duration;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(
            target = "nextCourseCountdownSeconds",
            expression = "java(calculateCountdownTimer(order.getEstimatedCompletionAt()))"
    )
    OrderResponse toResponse(Order order);

    default Integer calculateCountdownTimer(Instant estimatedCompletionAt) {
        if (estimatedCompletionAt == null) {
            return 0;
        }

        long seconds = Duration
                .between(Instant.now(), estimatedCompletionAt)
                .toSeconds();

        return (int) Math.max(0, seconds);
    }
}