package com.midaas.moonwalk.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WaitlistEntry extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    // WAITING, SEATED, or ABANDONED
    @Column(name = "status", nullable = false)
    private String status; 
}