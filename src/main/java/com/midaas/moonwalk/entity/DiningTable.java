package com.midaas.moonwalk.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dining_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "table_number", nullable = false)
    private String tableNumber;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "is_occupied", nullable = false)
    private boolean isOccupied = false;
}