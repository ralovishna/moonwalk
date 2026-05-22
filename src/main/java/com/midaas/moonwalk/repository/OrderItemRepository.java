package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.OrderItem;
import com.midaas.moonwalk.enums.OrderItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdAndStatusOrderByCreatedAtAsc(Long orderId, OrderItemStatus orderItemStatus);

    List<OrderItem> findByOrderIdAndCourseSequence(Long orderId, Integer nextCourse);

    List<OrderItem> findByRestaurantIdAndStatusOrderByCreatedAtAsc(Long restaurantId, OrderItemStatus orderItemStatus);

    List<OrderItem> findByStatusAndUpdatedAtBefore(OrderItemStatus orderItemStatus, Instant threshold);

    int countByRestaurantIdAndStatus(Long restaurantId, OrderItemStatus orderItemStatus);

    Optional<OrderItem> findFirstByRestaurantIdAndStatusAndCourseSequenceOrderByCreatedAtAsc(
            Long restaurantId, OrderItemStatus status, Integer courseSequence
    );

    @Query("""
select min(oi.courseSequence)
from OrderItem oi
where oi.restaurantId = :restaurantId and oi.status = :status
""")
    Integer findMinCourseSequenceForStatus(Long restaurantId, OrderItemStatus status);
}
