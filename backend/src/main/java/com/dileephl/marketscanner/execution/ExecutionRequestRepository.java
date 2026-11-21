package com.dileephl.marketscanner.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExecutionRequestRepository extends JpaRepository<ExecutionRequestEntity, UUID> {
    Optional<ExecutionRequestEntity> findByIdempotencyKey(String idempotencyKey);
    boolean existsBySymbolAndSideAndStatusIn(String symbol, String side, Collection<String> statuses);
    boolean existsBySymbolAndStatusIn(String symbol, Collection<String> statuses);
    List<ExecutionRequestEntity> findByStatusIn(Collection<String> statuses);
    long countByStatusIn(Collection<String> statuses);
    Optional<ExecutionRequestEntity> findTopBySymbolOrderByCreatedAtDesc(String symbol);
    java.util.Optional<ExecutionRequestEntity> findByOrderId(String orderId);
    List<ExecutionRequestEntity> findByParentOrderId(String parentOrderId);
}
