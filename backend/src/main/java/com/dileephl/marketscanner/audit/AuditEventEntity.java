package com.dileephl.marketscanner.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
class AuditEventEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    protected AuditEventEntity() { }

    static AuditEventEntity of(String eventType, String aggregateType, String aggregateId, String details) {
        AuditEventEntity event = new AuditEventEntity();
        event.id = UUID.randomUUID();
        event.occurredAt = OffsetDateTime.now();
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.details = details;
        return event;
    }

    AuditEventView view() { return new AuditEventView(occurredAt, eventType, aggregateType, aggregateId, details); }
}
