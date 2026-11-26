package com.dileephl.marketscanner.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

interface ExecutionRequestRepository extends JpaRepository<ExecutionRequestEntity, UUID> {
    Optional<ExecutionRequestEntity> findByIdempotencyKey(String idempotencyKey);
    boolean existsBySymbolAndSideAndStatusIn(String symbol, String side, Collection<String> statuses);
    boolean existsBySymbolAndStatusIn(String symbol, Collection<String> statuses);
    List<ExecutionRequestEntity> findByStatusIn(Collection<String> statuses);
    List<ExecutionRequestEntity> findByProtectiveFalseAndStatusIn(Collection<String> statuses);
    long countByStatusIn(Collection<String> statuses);
    Optional<ExecutionRequestEntity> findTopBySymbolOrderByCreatedAtDesc(String symbol);
    Optional<ExecutionRequestEntity> findTopBySymbolAndProtectiveFalseOrderByCreatedAtDesc(String symbol);
    java.util.Optional<ExecutionRequestEntity> findByOrderId(String orderId);
    Optional<ExecutionRequestEntity> findByParentOrderIdAndProtectiveTrue(String parentOrderId);
    Optional<ExecutionRequestEntity> findByParentOrderIdAndExitRole(String parentOrderId, String exitRole);
    List<ExecutionRequestEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(OffsetDateTime createdAt);
    Page<ExecutionRequestEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(OffsetDateTime createdAt, Pageable pageable);
    Page<ExecutionRequestEntity> findByProtectiveFalseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(OffsetDateTime createdAt, Pageable pageable);
}
