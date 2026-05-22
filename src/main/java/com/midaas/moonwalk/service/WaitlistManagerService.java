package com.midaas.moonwalk.service;

import com.midaas.moonwalk.service.impl.WaitlistManagerServiceImpl;

import java.util.Optional;

public interface WaitlistManagerService {
    void addCustomer(Long restaurantId, String customerName, int partySize);

    Optional<WaitlistManagerServiceImpl.WaitlistNode> popBestMatchForTable(Long restaurantId, int tableCapacity);
}
