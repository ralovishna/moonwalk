package com.midaas.moonwalk.mapper;

import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.OrderExecutionLog;
import com.midaas.moonwalk.enums.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderExecutionLogMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "restaurantId", source = "restaurantId")
    @Mapping(target = "orderStatus", source = "status")
    @Mapping(target = "timeEstimated", source = "timeEstimated")
    @Mapping(target = "timeElapsed", source = "timeElapsed")
    @Mapping(target = "activeWorkersCount", source = "activeWorkersCount")
    @Mapping(target = "queueBacklogCount", source = "queueBacklogCount")
    @Mapping(target = "algorithmChosen", source = "algorithmChosen")
    OrderExecutionLog toExecutionLog(
            Order order,
            Long restaurantId,
            OrderStatus status,
            Integer timeEstimated,
            Integer timeElapsed,
            Integer activeWorkersCount,
            Integer queueBacklogCount,
            String algorithmChosen
    );
}