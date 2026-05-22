package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    Optional<DiningTable> findFirstByRestaurantIdAndCapacityGreaterThanEqualAndIsOccupiedFalseOrderByCapacityAsc(Long restaurantId, Integer partySize);
}
