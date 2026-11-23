package com.dileephl.marketscanner.journal;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A deterministic view of the lifecycle recorded for one journal trade.
 * It is derived from persisted trade state and never changes the trade.
 */
public record TradeReplayEvent(
        String event,
        OffsetDateTime occurredAt,
        TradeStatus status,
        BigDecimal price,
        Long quantity,
        BigDecimal grossPnl,
        BigDecimal charges,
        BigDecimal netPnl,
        String details) {
}
