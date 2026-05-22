package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    Optional<DiningTable> findFirstByRestaurantIdAndCapacityGreaterThanEqualAndIsOccupiedFalseOrderByCapacityAsc(Long restaurantId, Integer partySize);

    @Query("SELECT MAX(t.capacity) FROM DiningTable t WHERE t.restaurantId = :restaurantId")
    Integer findMaxCapacityByRestaurantId(Long restaurantId);
}
