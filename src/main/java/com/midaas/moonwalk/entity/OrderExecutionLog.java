package com.midaas.moonwalk.entity;

import com.midaas.moonwalk.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "order_execution_logs", indexes = {
        @Index(name = "idx_log_order_id", columnList = "order_id"),
        @Index(name = "idx_log_tenant", columnList = "restaurant_id") // Great for Part 2 analytics!
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 50)
    private OrderStatus orderStatus;

    @Column(name = "time_estimated", nullable = false)
    private Integer timeEstimated; // in seconds

    @Column(name = "time_elapsed", nullable = false)
    private Integer timeElapsed; // in seconds

    @Column(name = "active_workers_count", nullable = false)
    private Integer activeWorkersCount;

    @Column(name = "queue_backlog_count", nullable = false)
    private Integer queueBacklogCount;

    @Column(name = "algorithm_chosen", nullable = false, length = 50)
    private String algorithmChosen;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @PrePersist
    protected void onLogCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }
}