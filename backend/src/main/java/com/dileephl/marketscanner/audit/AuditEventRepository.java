package com.dileephl.marketscanner.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findTop200ByOrderByOccurredAtDesc();
    Page<AuditEventEntity> findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(OffsetDateTime cutoff, Pageable pageable);
}
