package com.midaas.moonwalk.controller;

import com.midaas.moonwalk.dto.WalkInResponse;
import com.midaas.moonwalk.service.TableManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final TableManagerService tableManagerService;

    @PostMapping("/restaurants/{restaurantId}/walk-in")
    public ResponseEntity<WalkInResponse> handleWalkIn(
            @PathVariable Long restaurantId, 
            @RequestParam String customerName, 
            @RequestParam Integer partySize) {
        
        log.info("Customer {} (Party of {}) walked into Restaurant {}", customerName, partySize, restaurantId);

        WalkInResponse response = tableManagerService.processWalkIn(restaurantId, customerName, partySize);
        return ResponseEntity.ok(response);
    }
}