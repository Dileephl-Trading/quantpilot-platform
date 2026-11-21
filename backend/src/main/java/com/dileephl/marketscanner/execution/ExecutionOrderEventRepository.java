package com.dileephl.marketscanner.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

interface ExecutionOrderEventRepository extends JpaRepository<ExecutionOrderEventEntity, UUID> {
    List<ExecutionOrderEventEntity> findByOrderIdOrderByObservedAtDesc(String orderId);
}
