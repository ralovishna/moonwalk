package com.midaas.moonwalk.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.midaas.moonwalk.dto.OrderRequest;
import com.midaas.moonwalk.dto.OrderResponse;
import com.midaas.moonwalk.mapper.OrderMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.midaas.moonwalk.service.OrderOrchestratorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderOrchestratorService orchestratorService;
    private final OrderMapper orderMapper;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        log.info("HTTP POST /api/v1/orders - Received new Dine-In order for Table ID: {}", request.tableId());

        // Calls our new DDD method that handles course splitting
        var savedOrder = orchestratorService.placeDineInOrder(request.restaurantId(), request.tableId(), request);

        log.debug("HTTP POST /api/v1/orders - Successfully created Order ID: {}", savedOrder.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderMapper.toResponse(savedOrder));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderStatus(@PathVariable Long orderId) {
        log.debug("HTTP GET /api/v1/orders/{} - Fetching live status and current course ETA", orderId);

        var order = orchestratorService.getOrder(orderId);

        return ResponseEntity.ok(orderMapper.toResponse(order));
    }
}