package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A single broker-confirmed fill, retained for auditable realised P&L. */
public record BrokerTrade(
        String tradeId,
        String orderId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal averagePrice,
        OffsetDateTime fillTimestamp) {
}
