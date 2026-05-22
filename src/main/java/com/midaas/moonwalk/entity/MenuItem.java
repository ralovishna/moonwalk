package com.midaas.moonwalk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MenuItem extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "course_sequence", nullable = false)
    private Integer courseSequence;

    @Column(name = "base_prep_time", nullable = false)
    private Integer basePrepTime;
}