package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findFirstByRestaurantIdAndStatusAndPartySizeLessThanEqualOrderByCreatedAtAsc(Long restaurantId, String waiting, Integer capacity);
}
