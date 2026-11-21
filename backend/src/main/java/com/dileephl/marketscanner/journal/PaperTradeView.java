package com.dileephl.marketscanner.journal;

import com.dileephl.marketscanner.execution.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaperTradeView(
        UUID id, String symbol, OrderSide side, TradeStatus status, LocalDate tradeDate,
        OffsetDateTime createdAt, OffsetDateTime filledAt, OffsetDateTime closedAt,
        BigDecimal intendedEntryPrice, BigDecimal stopPrice, long intendedQuantity,
        BigDecimal actualEntryPrice, BigDecimal slippagePerUnit, BigDecimal slippageAmount,
        Long filledQuantity, BigDecimal actualExitPrice,
        BigDecimal grossPnl, BigDecimal brokerage, BigDecimal netPnl, ExitReason exitReason,
        String strategy, String notes) {
}
