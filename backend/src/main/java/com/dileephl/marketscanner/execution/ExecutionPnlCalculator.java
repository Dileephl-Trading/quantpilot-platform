package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/** Calculates realised gross P&L from broker-confirmed entry and exit fills. */
final class ExecutionPnlCalculator {
    private static final int SCALE = 8;

    private ExecutionPnlCalculator() { }

    static Result calculate(OrderSide entrySide, List<BrokerTrade> entryTrades, List<BrokerTrade> exitTrades) {
        if (entrySide == null || entryTrades == null || exitTrades == null) return null;
        long entryQuantity = entryTrades.stream().filter(t -> t != null && t.quantity() > 0)
                .mapToLong(BrokerTrade::quantity).sum();
        long exitQuantity = exitTrades.stream().filter(t -> t != null && t.quantity() > 0)
                .mapToLong(BrokerTrade::quantity).sum();
        long matched = Math.min(entryQuantity, exitQuantity);
        if (matched <= 0) return null;

        BigDecimal entryNotional = weightedNotional(entryTrades, matched);
        BigDecimal exitNotional = weightedNotional(exitTrades, matched);
        BigDecimal entryAverage = entryNotional.divide(BigDecimal.valueOf(matched), SCALE, RoundingMode.HALF_UP);
        BigDecimal exitAverage = exitNotional.divide(BigDecimal.valueOf(matched), SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = entrySide == OrderSide.BUY
                ? exitAverage.subtract(entryAverage).multiply(BigDecimal.valueOf(matched))
                : entryAverage.subtract(exitAverage).multiply(BigDecimal.valueOf(matched));
        OffsetDateTime completedAt = exitTrades.stream().filter(t -> t != null && t.fillTimestamp() != null)
                .map(BrokerTrade::fillTimestamp).max(OffsetDateTime::compareTo).orElse(null);
        return new Result(matched, entryAverage, exitAverage, pnl.setScale(4, RoundingMode.HALF_UP), completedAt);
    }

    private static BigDecimal weightedNotional(List<BrokerTrade> trades, long requiredQuantity) {
        BigDecimal notional = BigDecimal.ZERO;
        long remaining = requiredQuantity;
        for (BrokerTrade trade : trades) {
            if (trade == null || trade.quantity() <= 0 || trade.averagePrice() == null || remaining <= 0) continue;
            long quantity = Math.min(remaining, trade.quantity());
            notional = notional.add(trade.averagePrice().multiply(BigDecimal.valueOf(quantity)));
            remaining -= quantity;
        }
        return notional;
    }

    record Result(long matchedQuantity, BigDecimal entryAveragePrice, BigDecimal exitAveragePrice,
                  BigDecimal realisedPnl, OffsetDateTime completedAt) { }
}
