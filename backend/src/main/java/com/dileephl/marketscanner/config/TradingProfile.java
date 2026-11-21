package com.dileephl.marketscanner.config;

import java.math.BigDecimal;

public record TradingProfile(
        TradingMode mode,
        String broker,
        String exchange,
        String segment,
        String product,
        BigDecimal allocatedCapital,
        BigDecimal maxLossPerTrade,
        BigDecimal maxDailyLoss,
        int maxTradesPerDay,
        int maxOpenPositions,
        int cooldownSeconds,
        boolean killSwitchEnabled) {
}
