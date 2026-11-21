package com.dileephl.marketscanner.journal;

import com.dileephl.marketscanner.execution.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperTradeEntityTest {
    @Test
    void calculatesNetPnlUsingActualBuyFillAndExit() {
        PaperTradeEntity trade = PaperTradeEntity.intended(UUID.randomUUID(), "SBIN", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("98"), 10, LocalDate.of(2026, 11, 18),
                OffsetDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), null, null);

        trade.recordFill(new BigDecimal("101"), 10, OffsetDateTime.now(ZoneOffset.UTC));
        trade.recordExit(new BigDecimal("105"), new BigDecimal("5"), ExitReason.TARGET, OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(TradeStatus.CLOSED, trade.status());
        assertEquals(0, new BigDecimal("40").compareTo(trade.grossPnl()));
        assertEquals(0, new BigDecimal("35").compareTo(trade.netPnl()));
        assertEquals(0, new BigDecimal("1").compareTo(trade.slippagePerUnit()));
        assertEquals(0, new BigDecimal("10").compareTo(trade.slippageAmount()));
    }

    @Test
    void calculatesNetPnlUsingActualShortFillAndExit() {
        PaperTradeEntity trade = PaperTradeEntity.intended(UUID.randomUUID(), "SBIN", OrderSide.SELL,
                new BigDecimal("100"), new BigDecimal("102"), 10, LocalDate.of(2026, 11, 18),
                OffsetDateTime.now(ZoneOffset.UTC), null, null);

        trade.recordFill(new BigDecimal("100"), 10, OffsetDateTime.now(ZoneOffset.UTC));
        trade.recordExit(new BigDecimal("98"), BigDecimal.ZERO, ExitReason.MANUAL, OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(0, new BigDecimal("20").compareTo(trade.netPnl()));
    }
}
