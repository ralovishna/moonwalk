package com.midaas.moonwalk.service;

import org.springframework.scheduling.annotation.Scheduled;

public interface KitchenRecoveryService {
    @Scheduled(fixedDelay = 30000)
    void recoverStuckOrders();
}
