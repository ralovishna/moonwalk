package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.service.CookingSimulatorService;
import com.midaas.moonwalk.service.KitchenDispatcherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CookingSimulatorServiceImpl implements CookingSimulatorService {

    @Lazy
    @Autowired
    private KitchenDispatcherService kitchenDispatcher;

    @Async
    @Override
    public void simulateCooking(Long restaurantId, Long orderItemId, Integer prepTimeSeconds) {
        try {
            log.info("Chef started cooking Dish (Item ID: {}). It will take {} seconds.", orderItemId, prepTimeSeconds);

            Thread.sleep(prepTimeSeconds * 1000L);

            log.info("Cooking time is over for Dish (Item ID: {}). Auto-completing...", orderItemId);

            kitchenDispatcher.markDishReady(restaurantId, orderItemId);

        } catch (InterruptedException exception) {
            log.error("Cooking simulation interrupted for Dish (Item ID: {})", orderItemId);
            Thread.currentThread().interrupt();
        }
    }
}