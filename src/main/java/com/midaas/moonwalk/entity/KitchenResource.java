package com.midaas.moonwalk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "kitchen_resources", indexes = {
        @Index(name = "idx_available_resources", columnList = "restaurant_id, is_available")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KitchenResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable = true;

    @Column(name = "current_order_item_id")
    @Nullable
    private Long currentOrderItemId;

    @Version
    private Integer version;
}