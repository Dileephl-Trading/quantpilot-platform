package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerReconciliationServiceTest {
    @Test void unrelatedPositionAndHistoricalOrdersDoNotTriggerBrokerWrites() {
        var broker = mock(KiteOrderExecutionProvider.class);
        var requests = mock(ExecutionRequestRepository.class);
        var journal = mock(SubmissionJournal.class);
        when(broker.positions()).thenReturn(List.of(new BrokerPosition("ICICIBANK", "NSE", "MIS", 10, null)));
        var service = new BrokerReconciliationService(broker, requests, mock(ExecutionTradeRepository.class),
                mock(AuditTrailService.class), journal, mock(ExecutionOrderEventRepository.class));
        assertEquals(0, service.reconcileTrackedOrders().checked());
        verify(broker).orderBook();
        verify(broker).positions();
        verifyNoMoreInteractions(broker);
        verify(requests).findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(BrokerOrderExecutionService.sessionStart());
        verifyNoMoreInteractions(requests);
    }

    @Test void absentBrokerAcknowledgementRemainsUnresolved() {
        var broker = mock(KiteOrderExecutionProvider.class);
        var journal = mock(SubmissionJournal.class);
        when(journal.pending()).thenReturn(List.of(new SubmissionJournal.Pending("entry", "tag1", null, null, java.time.OffsetDateTime.now())));
        var service = new BrokerReconciliationService(broker, mock(ExecutionRequestRepository.class),
                mock(ExecutionTradeRepository.class), mock(AuditTrailService.class), journal,
                mock(ExecutionOrderEventRepository.class));
        assertEquals(List.of("Unconfirmed submission tag1"), service.reconcileTrackedOrders().issues());
        verify(broker).orderBook();
        verify(broker).positions();
        verifyNoMoreInteractions(broker);
    }
    @Test void expiredDayIntentClearsOnlyItsExposurePause() {
        var broker = mock(KiteOrderExecutionProvider.class);
        var journal = mock(SubmissionJournal.class);
        var request = new BrokerOrderRequest("SBIN", OrderSide.BUY, 100, "MARKET", null, null,
                "MIS", "NSE", "DAY", java.math.BigDecimal.ONE);
        when(journal.pending()).thenReturn(List.of(new SubmissionJournal.Pending("old", "tag", null,
                request, BrokerOrderExecutionService.sessionStart().minusDays(1))));
        when(broker.positions()).thenReturn(List.of(new BrokerPosition("ICICIBANK", "NSE", "MIS", 10, null)));
        var service = new BrokerReconciliationService(broker, mock(ExecutionRequestRepository.class),
                mock(ExecutionTradeRepository.class), mock(AuditTrailService.class), journal,
                mock(ExecutionOrderEventRepository.class));
        var report = service.reconcileTrackedOrders();
        assertTrue(report.riskIssues().isEmpty());
        assertFalse(report.issues().isEmpty());
        verify(journal).recordExpiredFlat("old");
        verify(broker).orderBook();
        verify(broker).positions();
        verifyNoMoreInteractions(broker);
    }
}
