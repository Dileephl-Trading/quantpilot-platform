package com.dileephl.marketscanner.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findTop200ByOrderByOccurredAtDesc();
}
