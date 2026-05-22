package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.OrderExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderExecutionLogRepository extends JpaRepository<OrderExecutionLog, Long> {
    // Standard CRUD is enough here. Analytics services can use this later.
}