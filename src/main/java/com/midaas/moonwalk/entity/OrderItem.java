package com.midaas.moonwalk.entity;

import com.midaas.moonwalk.enums.OrderItemStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "order_items", indexes = {
        // Updated index to include restaurant_id for blazing fast queue queries!
        @Index(name = "idx_item_tenant_status", columnList = "restaurant_id, status"),
        @Index(name = "idx_item_order_id", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "dish_name", nullable = false)
    private String dishName;

    @Column(name = "course_sequence", nullable = false)
    private Integer courseSequence;

    @Column(name = "base_prep_time", nullable = false)
    private Integer basePrepTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderItemStatus status;
}