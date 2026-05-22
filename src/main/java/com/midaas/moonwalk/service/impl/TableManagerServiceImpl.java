package com.midaas.moonwalk.service.impl;

import com.midaas.moonwalk.dto.WalkInResponse;
import com.midaas.moonwalk.entity.DiningTable;
import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.enums.OrderStatus;
import com.midaas.moonwalk.repository.DiningTableRepository;
import com.midaas.moonwalk.repository.OrderRepository;
import com.midaas.moonwalk.service.TableManagerService;
import com.midaas.moonwalk.service.WaitlistManagerService;
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
    private final WaitlistManagerService waitlistManager;
    private final TableTurnoverEstimationStrategy turnoverStrategy;

    @Override
    @Transactional
    public WalkInResponse processWalkIn(Long restaurantId, String customerName, Integer partySize) {

        Integer maxTableCapacity = tableRepository.findMaxCapacityByRestaurantId(restaurantId);
        if (maxTableCapacity == null || partySize > maxTableCapacity) {
            log.warn("Rejected Walk-in: Party of {} exceeds max table capacity of {}", partySize, maxTableCapacity);
            return new WalkInResponse(
                    false,
                    null,
                    "Sorry, our largest table only seats " + maxTableCapacity + " people. We cannot accommodate your party.",
                    0
            );
        }

        // 1. Look for an empty table big enough for the party
        var availableTable = tableRepository.findFirstByRestaurantIdAndCapacityGreaterThanEqualAndIsOccupiedFalseOrderByCapacityAsc(restaurantId, partySize);

        if (availableTable.isPresent()) {
            DiningTable table = availableTable.get();
            table.setOccupied(true);
            tableRepository.save(table);

            log.info("Customer {} seated immediately at Table {}", customerName, table.getTableNumber());
            return new WalkInResponse(true, table.getId(), "Table available! Please follow the host to table " + table.getTableNumber(), 0);
        }

        // 2. NO TABLES FREE: Add to IN-MEMORY Waitlist!
        log.info("Restaurant full. Adding Customer {} to Waitlist...", customerName);
        waitlistManager.addCustomer(restaurantId, customerName, partySize);

        // 3. Calculate ETA
        List<Order> activeOrders = orderRepository.findByRestaurantIdAndStatusIn(
                restaurantId,
                List.of(OrderStatus.TABLE_ASSIGNED, OrderStatus.KITCHEN_PREPARING, OrderStatus.SERVED)
        );

        int waitlistEtaSeconds = 900; // Default 15 minutes

        if (!activeOrders.isEmpty()) {
            Optional<Instant> soonestFreedTimeOpt = activeOrders.stream()
                    .map(turnoverStrategy::calculateTableFreedTime)
                    // Only consider times in the future to avoid 0 ETA
                    .filter(time -> time.isAfter(Instant.now()))
                    .min(Comparator.naturalOrder());

            if (soonestFreedTimeOpt.isPresent()) {
                waitlistEtaSeconds = (int) Duration.between(Instant.now(), soonestFreedTimeOpt.get()).toSeconds();
            }
        }

        return new WalkInResponse(
                false,
                null,
                "Added to Waitlist. Your estimated wait time is " + (waitlistEtaSeconds / 60) + " minutes.",
                waitlistEtaSeconds
        );
    }
}