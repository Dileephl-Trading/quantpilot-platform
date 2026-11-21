package com.dileephl.marketscanner.audit;

import java.time.OffsetDateTime;

public record AuditEventView(OffsetDateTime occurredAt, String eventType, String aggregateType,
                            String aggregateId, String details) { }
