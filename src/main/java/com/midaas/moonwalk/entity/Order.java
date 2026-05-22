package com.midaas.moonwalk.entity;

import com.midaas.moonwalk.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_tenant_status", columnList = "restaurant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    @Nullable
    private String customerPhone;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status;

    // This is null if they are waiting in the lobby
    @Column(name = "table_id")
    @Nullable
    private Long tableId;

    // The max ETA of all their dishes
    @Column(name = "estimated_completion_at")
    @Nullable
    private Instant estimatedCompletionAt;

    @OneToMany(mappedBy = "orderId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}