package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveAdmissionTest {
    private final TradingProfileService profiles = mock(TradingProfileService.class);
    private final ExecutionRequestRepository requests = mock(ExecutionRequestRepository.class);
    private final OrderExecutionProvider broker = mock(OrderExecutionProvider.class);
    private final MarketSessionService sessions = mock(MarketSessionService.class);
    private final LiveOrderRiskService risk = new LiveOrderRiskService(profiles, requests, broker, sessions);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-26T10:00:00+05:30");
    private final BrokerOrderRequest proposed = order("HDFCBANK");
    private BrokerOrderRequest order(String symbol) {
        return new BrokerOrderRequest(symbol, OrderSide.BUY, 10, "LIMIT", BigDecimal.TEN, null, "MIS", "NSE", "DAY", BigDecimal.ONE);
    }
    LiveAdmissionTest() {
        when(profiles.currentForRiskDecision()).thenReturn(new TradingProfile(TradingMode.LIVE, "KITE", "NSE", "EQ", "MIS",
                new BigDecimal("50000"), new BigDecimal("1000"), new BigDecimal("2000"), 10, 2, 0, false));
        when(sessions.isOpen(now)).thenReturn(true);
        when(sessions.isNewEntryAllowed(now)).thenReturn(true);
    }
    @Test void pendingEntryAndDifferentOpenPositionConsumeBothSlots() {
        var pending = ExecutionRequestEntity.from("pending", order("AXISBANK"), new BrokerOrderResponse("id","AXISBANK",
                OrderSide.BUY,10,0,null,BrokerOrderStatus.OPEN,"",now));
        when(requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any())).thenReturn(List.of(pending));
        when(broker.positions()).thenReturn(List.of(new BrokerPosition("SBIN","NSE","MIS",10,BigDecimal.TEN)));
        var decision = risk.verify(proposed,now);
        assertEquals(2, decision.occupiedPositionSlots());
        assertFalse(decision.allowed());
    }
    @Test void flatBrokerLossConsumesDailyLimit() {
        when(broker.dayPositions()).thenReturn(List.of(new BrokerDayPosition("SBIN","NSE","MIS",0,100,BigDecimal.TEN,
                100,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("-2100"))));
        assertFalse(risk.verify(proposed,now).allowed());
    }
    @Test void manualAndAutomatedAdmissionUseTheSameWindow() {
        when(sessions.isNewEntryAllowed(now)).thenReturn(false);
        assertFalse(risk.verify(proposed,now).allowed());
    }
}
