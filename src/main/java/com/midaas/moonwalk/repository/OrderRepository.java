package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.Order;
import com.midaas.moonwalk.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatusAndEstimatedCompletionAtBefore(OrderStatus status, Instant time);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtAsc(Long restaurantId, OrderStatus status);

    long countByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurantId = :restaurantId AND o.status = :status")
    long countByRestaurantIdAndStatusWithLock(@Param("restaurantId") Long restaurantId, @Param("status") OrderStatus status);

    List<Order> findByRestaurantIdAndStatusIn(Long restaurantId, List<OrderStatus> kitchenPreparing);
}