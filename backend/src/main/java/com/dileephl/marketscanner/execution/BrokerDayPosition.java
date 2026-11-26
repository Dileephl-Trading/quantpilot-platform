package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;

/** Provider-neutral day position snapshot used to display broker P&L. */
public record BrokerDayPosition(
        String symbol,
        String exchange,
        String product,
        long quantity,
        long buyQuantity,
        BigDecimal buyPrice,
        long sellQuantity,
        BigDecimal sellPrice,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal pnl) {
}
