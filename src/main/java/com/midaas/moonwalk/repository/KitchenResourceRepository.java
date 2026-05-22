package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.KitchenResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenResourceRepository extends JpaRepository<KitchenResource, Long> {

    Optional<KitchenResource> findFirstByRestaurantIdAndIsAvailableTrue(Long restaurantId);

    List<KitchenResource> findAllByRestaurantId(Long restaurantId);

    long countByRestaurantIdAndIsAvailableTrue(Long restaurantId);

    Optional<KitchenResource> findFirstByCurrentOrderItemId(Long orderItemId);
}