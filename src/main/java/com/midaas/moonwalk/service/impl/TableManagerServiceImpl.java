package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.dto.WalkInResponse;
import com.midaas.moonwalk.entity.DiningTable;
import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.entity.WaitlistEntry;
import com.midaas.moonwalk.enums.OrderStatus;
import com.midaas.moonwalk.repository.DiningTableRepository;
import com.midaas.moonwalk.repository.OrderRepository;
import com.midaas.moonwalk.repository.WaitlistRepository;
import com.midaas.moonwalk.service.TableManagerService;
import com.midaas.moonwalk.strategy.TableTurnoverEstimationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableManagerServiceImpl implements TableManagerService {

    private final DiningTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final WaitlistRepository waitlistRepository;
    private final TableTurnoverEstimationStrategy turnoverStrategy;

    @Override
    @Transactional
    public WalkInResponse processWalkIn(Long restaurantId, String customerName, Integer partySize) {

        // 1. Look for an empty table big enough for the party
        var availableTable = tableRepository.findFirstByRestaurantIdAndCapacityGreaterThanEqualAndIsOccupiedFalseOrderByCapacityAsc(restaurantId, partySize);

        if (availableTable.isPresent()) {
            DiningTable table = availableTable.get();
            table.setOccupied(true);
            tableRepository.save(table);

            log.info("Customer {} seated immediately at Table {}", customerName, table.getTableNumber());
            return new WalkInResponse(true, table.getId(), "Table available! Please follow the host to table " + table.getTableNumber(), 0);
        }

        // 2. NO TABLES FREE: Add to Database Waitlist!
        log.info("Restaurant full. Adding Customer {} to Waitlist...", customerName);
        WaitlistEntry entry = WaitlistEntry.builder()
                .restaurantId(restaurantId)
                .customerName(customerName)
                .partySize(partySize)
                .status("WAITING")
                .build();
        waitlistRepository.save(entry);

        // 3. Calculate Waitlist ETA using Strategy
        // Fetch all active orders (people currently eating or waiting for food)
        List<Order> activeOrders = orderRepository.findByRestaurantIdAndStatusIn(
                restaurantId,
                // Using TABLE_ASSIGNED in case they are seated but haven't sent food to kitchen yet
                List.of(OrderStatus.TABLE_ASSIGNED, OrderStatus.KITCHEN_PREPARING, OrderStatus.SERVED)
        );

        int waitlistEtaSeconds = 900; // Default fallback: 15 mins

        if (!activeOrders.isEmpty()) {
            // Find the table that will finish the SOONEST
            Optional<Instant> soonestFreedTimeOpt = activeOrders.stream()
                    .map(turnoverStrategy::calculateTableFreedTime)
                    .min(Comparator.naturalOrder());

            Instant soonestFreedTime = soonestFreedTimeOpt.orElse(Instant.now().plusSeconds(900));

            // Calculate seconds from now until that table is free
            waitlistEtaSeconds = (int) Duration.between(Instant.now(), soonestFreedTime).toSeconds();
            waitlistEtaSeconds = Math.max(0, waitlistEtaSeconds); // Ensure no negative time
        }

        log.info("Calculated Waitlist ETA for Customer {}: {} seconds.", customerName, waitlistEtaSeconds);

        return new WalkInResponse(
                false,
                null,
                "Added to Waitlist. Your estimated wait time is " + (waitlistEtaSeconds / 60) + " minutes.",
                waitlistEtaSeconds
        );
    }
}