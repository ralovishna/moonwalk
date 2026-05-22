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

    private final KitchenDispatcherService kitchenDispatcher;

    @PostMapping("/restaurants/{restaurantId}/items/{orderItemId}/ready")
    public ResponseEntity<Void> markDishAsReady(
            @PathVariable Long restaurantId,
            @PathVariable Long orderItemId) {

        log.info("HTTP POST /api/v1/kitchen/restaurants/{}/items/{}/ready - Chef marked dish as done", restaurantId, orderItemId);

        kitchenDispatcher.markDishReady(restaurantId, orderItemId);

        return ResponseEntity.ok().build();
    }
}