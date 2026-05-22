package com.midaas.moonwalk.controller;

import com.midaas.moonwalk.service.KitchenDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    // Note: Use the Interface here, not the Impl class!
    private final KitchenDispatcherService kitchenDispatcher;

    // Notice the URL changed to "items/{orderItemId}" instead of "orders/{orderId}"
    @PostMapping("/restaurants/{restaurantId}/items/{orderItemId}/ready")
    public ResponseEntity<Void> markDishAsReady(
            @PathVariable Long restaurantId,
            @PathVariable Long orderItemId) {

        log.info("HTTP POST /api/v1/kitchen/restaurants/{}/items/{}/ready - Chef marked dish as done", restaurantId, orderItemId);

        // Triggers the chain reaction to free the chef and start the customer's eating timer
        kitchenDispatcher.markDishReady(restaurantId, orderItemId);

        return ResponseEntity.ok().build();
    }
}