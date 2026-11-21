package com.dileephl.marketscanner.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditTrailService {
    private final AuditEventRepository events;

    public AuditTrailService(AuditEventRepository events) { this.events = events; }

    @Transactional
    public void record(String eventType, String aggregateType, String aggregateId, String details) {
        events.save(AuditEventEntity.of(eventType, aggregateType, aggregateId, details));
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEventView> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return events.findTop200ByOrderByOccurredAtDesc().stream().limit(safeLimit).map(AuditEventEntity::view).toList();
    }
}
