package com.dileephl.marketscanner.journal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTradeSummary(
        LocalDate tradeDate,
        long initiatedTrades,
        long openPositions,
        BigDecimal realisedPnl,
        BigDecimal realisedLoss,
        BigDecimal activeRisk,
        BigDecimal remainingDailyRiskCapacity,
        BigDecimal maxDailyLoss,
        boolean dailyLimitReached) {
}
