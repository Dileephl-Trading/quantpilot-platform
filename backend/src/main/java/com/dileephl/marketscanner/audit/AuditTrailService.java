package com.dileephl.marketscanner.audit;

import com.dileephl.marketscanner.api.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class AuditTrailService {
    private static final ZoneOffset INDIA_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private static final int MAX_PAGE_SIZE = 50;
    private final AuditEventRepository events;

    public AuditTrailService(AuditEventRepository events) { this.events = events; }

    @Transactional
    public void record(String eventType, String aggregateType, String aggregateId, String details) {
        events.save(AuditEventEntity.of(eventType, aggregateType, aggregateId, details));
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEventView> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return events.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        OffsetDateTime.now(INDIA_OFFSET).minusDays(7), PageRequest.of(0, safeLimit))
                .map(AuditEventEntity::view).getContent();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventView> recentPage(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        return PagedResponse.from(events.findByOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                OffsetDateTime.now(INDIA_OFFSET).minusDays(7), PageRequest.of(safePage, safePageSize)), AuditEventEntity::view);
    }
}
