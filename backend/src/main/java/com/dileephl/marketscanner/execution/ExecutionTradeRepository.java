package com.dileephl.marketscanner.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExecutionTradeRepository extends JpaRepository<ExecutionTradeEntity, UUID> {
    Optional<ExecutionTradeEntity> findByTradeId(String tradeId);
    List<ExecutionTradeEntity> findByOrderIdOrderByFillTimestampAsc(String orderId);
}
