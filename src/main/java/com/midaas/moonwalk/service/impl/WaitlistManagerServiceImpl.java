package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.service.WaitlistManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WaitlistManagerServiceImpl implements WaitlistManagerService {

    public record WaitlistNode(String customerName, int partySize, Instant joinedAt) {}

    private final Map<Long, List<WaitlistNode>> restaurantQueues = new ConcurrentHashMap<>();

    @Override
    public void addCustomer(Long restaurantId, String customerName, int partySize) {
        restaurantQueues.computeIfAbsent(restaurantId, k -> Collections.synchronizedList(new LinkedList<>()))
                .add(new WaitlistNode(customerName, partySize, Instant.now()));
        log.info("Added {} (Party of {}) to in-memory waitlist for Restaurant {}", customerName, partySize, restaurantId);
    }

    @Override
    public Optional<WaitlistNode> popBestMatchForTable(Long restaurantId, int tableCapacity) {
        List<WaitlistNode> queue = restaurantQueues.get(restaurantId);
        
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }

        synchronized (queue) {
            WaitlistNode bestMatch = null;

            for (WaitlistNode node : queue) {
                if (node.partySize() <= tableCapacity && node.partySize() >= tableCapacity - 1) {
                    bestMatch = node;
                    break;
                }
            }

            if (bestMatch == null) {
                for (WaitlistNode node : queue) {
                    if (node.partySize() <= tableCapacity) {
                        bestMatch = node;
                        break;
                    }
                }
            }

            if (bestMatch != null) {
                queue.remove(bestMatch);
                log.info("Popped {} (Party of {}) from waitlist for Table Capacity {}", 
                        bestMatch.customerName(), bestMatch.partySize(), tableCapacity);
            }

            return Optional.ofNullable(bestMatch);
        }
    }
}