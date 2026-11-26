package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.*;
import com.dileephl.marketscanner.integration.zerodha.*;
import com.dileephl.marketscanner.marketdata.MarketInstrumentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExitSupervisionTest {
    final KiteOrderExecutionProvider broker = mock(KiteOrderExecutionProvider.class);
    final ExecutionRequestRepository rows = mock(ExecutionRequestRepository.class);
    final TradingProfileService profiles = mock(TradingProfileService.class, RETURNS_DEEP_STUBS);
    final KiteConnectProperties kite = mock(KiteConnectProperties.class);
    final SubmissionJournal journal = mock(SubmissionJournal.class);
    final BrokerOrderExecutionService execution = new BrokerOrderExecutionService(profiles, mock(ExecutionPreviewService.class),
            broker, mock(SafetyService.class), rows, mock(ExecutionOrderEventRepository.class), mock(AuditTrailService.class),
            mock(LiveOrderRiskService.class), kite, mock(LiveExecutionProperties.class), mock(AutomatedExecutionProperties.class),
            mock(MarketInstrumentRepository.class), mock(BrokerReconciliationService.class), journal);

    private KiteOrderExecutionProvider.BookOrder row(String id, OrderSide side, long qty, long filled,
                                                       BrokerOrderStatus state, String type) {
        return new KiteOrderExecutionProvider.BookOrder(id, "", "NSE", "MIS", type,
                new BrokerOrderResponse(id, "SBIN", side, qty, filled, BigDecimal.TEN, state, "", OffsetDateTime.now()));
    }

    @Test void legacyPartialTargetResizesTheStopAfterTargetCancellationIsConfirmed() {
        when(profiles.current().mode()).thenReturn(TradingMode.LIVE);
        when(kite.isOrderRoutingEnabled()).thenReturn(true);
        var buy = row("entry", OrderSide.BUY, 100, 100, BrokerOrderStatus.COMPLETE, "LIMIT");
        var sl = row("stop", OrderSide.SELL, 100, 0, BrokerOrderStatus.OPEN, "SL-M");
        var limit = row("target", OrderSide.SELL, 100, 40, BrokerOrderStatus.CANCELLED, "LIMIT");
        var request = new BrokerOrderRequest("SBIN", OrderSide.BUY, 100, "LIMIT", BigDecimal.TEN, null,
                "MIS", "NSE", "DAY", BigDecimal.ONE);
        var entry = ExecutionRequestEntity.from("e", request, buy.state());
        var stop = ExecutionRequestEntity.fromProtective("s", request, sl.state(), "entry");
        var target = ExecutionRequestEntity.fromTarget("t", request, limit.state(), "entry");
        when(rows.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any())).thenReturn(List.of(entry));
        when(rows.findByParentOrderIdAndExitRole("entry", "STOP")).thenReturn(Optional.of(stop));
        when(rows.findByParentOrderIdAndExitRole("entry", "TARGET")).thenReturn(Optional.of(target));
        when(broker.orderBook()).thenReturn(List.of(buy, sl, limit));
        when(broker.positions()).thenReturn(List.of(new BrokerPosition("SBIN", "NSE", "MIS", 60, BigDecimal.TEN)));
        execution.manageProtection(false);
        var change = ArgumentCaptor.forClass(BrokerOrderRequest.class);
        verify(broker).modify(eq("stop"), change.capture());
        assertEquals(60L, change.getValue().quantity());
        assertEquals("SL-M", change.getValue().orderType());
        verify(broker, never()).squareOffPositions();
        verify(broker, never()).place(any(), anyString());
    }

    @Test void retryWithDurableReservationNeverResubmitsAnUnconfirmedStop() {
        when(profiles.current().mode()).thenReturn(TradingMode.LIVE);
        when(kite.isOrderRoutingEnabled()).thenReturn(true);
        var request = new BrokerOrderRequest("SBIN", OrderSide.SELL, 10, "SL-M", null,
                BigDecimal.ONE, "MIS", "NSE", "DAY", BigDecimal.ONE);
        when(journal.reserve("stop-key", request, "entry"))
                .thenReturn(new SubmissionJournal.Reservation("knownTag", false));
        when(broker.orderBook()).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> execution.placeProtective("stop-key", request, "entry"));
        verify(broker).orderBook();
        verifyNoMoreInteractions(broker);
    }
}
