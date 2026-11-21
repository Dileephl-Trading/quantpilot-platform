package com.dileephl.marketscanner.journal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyTradeReport(
        LocalDate tradeDate,
        long initiatedTrades,
        long closedTrades,
        long winningTrades,
        long losingTrades,
        BigDecimal grossPnl,
        BigDecimal brokerage,
        BigDecimal netPnl,
        BigDecimal totalSlippage,
        BigDecimal winRate,
        List<PaperTradeView> trades) {
}
