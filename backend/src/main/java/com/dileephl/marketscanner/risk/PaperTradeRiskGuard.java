package com.dileephl.marketscanner.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A pre-trade safety gate for paper trading. It never sends an order and treats a rejected trade
 * as a successful risk-control outcome.
 */
public final class PaperTradeRiskGuard {
    public RiskDecision evaluate(RiskRequest request) {
        validate(request);
        BigDecimal proposedRisk = request.entryPrice().subtract(request.stopPrice()).abs()
                .multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal maxRiskPerTrade = request.accountCapital()
                .multiply(request.maxRiskPerTradePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal maxDailyLoss = request.accountCapital()
                .multiply(request.maxDailyLossPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        long capitalLimitedQuantity = request.accountCapital()
                .divide(request.entryPrice(), 0, RoundingMode.DOWN).longValue();

        if (request.tradesTakenToday() >= request.maxTradesPerDay()) {
            return RiskDecision.rejected(proposedRisk, "Daily trade limit reached");
        }
        if (request.realisedLossToday().compareTo(maxDailyLoss) >= 0) {
            return RiskDecision.rejected(proposedRisk, "Daily loss limit already reached");
        }
        if (proposedRisk.compareTo(maxRiskPerTrade) > 0) {
            return RiskDecision.rejected(proposedRisk, "Risk to stop exceeds the per-trade limit");
        }
        if (request.quantity() > capitalLimitedQuantity) {
            return RiskDecision.rejected(proposedRisk, "Quantity exceeds the configured capital allocation");
        }
        if (request.realisedLossToday().add(proposedRisk).compareTo(maxDailyLoss) > 0) {
            return RiskDecision.rejected(proposedRisk, "This loss would breach the daily loss limit");
        }
        return new RiskDecision(true, proposedRisk, List.of("Paper-trade risk checks passed"));
    }

    private void validate(RiskRequest request) {
        if (request == null || request.accountCapital().signum() <= 0 || request.entryPrice().signum() <= 0
                || request.stopPrice().signum() <= 0 || request.quantity() <= 0 || request.maxTradesPerDay() <= 0
                || request.maxRiskPerTradePercent().signum() <= 0 || request.maxDailyLossPercent().signum() <= 0) {
            throw new IllegalArgumentException("All risk inputs must be positive");
        }
    }

    public record RiskRequest(
            BigDecimal accountCapital,
            BigDecimal entryPrice,
            BigDecimal stopPrice,
            long quantity,
            BigDecimal realisedLossToday,
            int tradesTakenToday,
            int maxTradesPerDay,
            BigDecimal maxRiskPerTradePercent,
            BigDecimal maxDailyLossPercent) { }

    public record RiskDecision(boolean allowed, BigDecimal proposedRisk, List<String> reasons) {
        static RiskDecision rejected(BigDecimal proposedRisk, String reason) {
            return new RiskDecision(false, proposedRisk, List.of(reason));
        }
    }
}
