package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.journal.PaperTradeService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExecutionPreviewService {
    private final TradingProfileService profiles;
    private final PaperTradeService paperTrades;
    public ExecutionPreviewService(TradingProfileService profiles, PaperTradeService paperTrades) {
        this.profiles = profiles;
        this.paperTrades = paperTrades;
    }

    public OrderPreview preview(OrderRequest request) {
        TradingProfile profile = profiles.current();
        return preview(request, profile.mode() == com.dileephl.marketscanner.config.TradingMode.PAPER);
    }

    /**
     * Live orders use the broker-aware risk layer, not the paper journal.  A
     * paper intent left open in the UI must never consume live capacity.
     */
    public OrderPreview previewLive(OrderRequest request) {
        return preview(request, false);
    }

    private OrderPreview preview(OrderRequest request, boolean includePaperJournalGuards) {
        TradingProfile profile = profiles.current();
        List<String> reasons = new ArrayList<>();
        if (request == null || request.symbol() == null || request.symbol().isBlank() || request.side() == null) {
            return new OrderPreview(false, 0, BigDecimal.ZERO,
                    List.of("Symbol and order side are required"), profile.mode().name());
        }
        if (request.entryPrice() == null || request.stopPrice() == null || request.quantity() <= 0
                || request.entryPrice().signum() <= 0 || request.stopPrice().signum() <= 0) {
            reasons.add("Entry price, stop price and quantity must be positive");
            return new OrderPreview(false, 0, BigDecimal.ZERO, reasons, profile.mode().name());
        }
        BigDecimal stopDistance = request.entryPrice().subtract(request.stopPrice()).abs();
        if (stopDistance.signum() == 0) reasons.add("Entry and stop price cannot be equal");
        if (request.side() == OrderSide.BUY && request.stopPrice().compareTo(request.entryPrice()) >= 0) {
            reasons.add("For BUY, stop price must be below entry price");
        }
        if (request.side() == OrderSide.SELL && request.stopPrice().compareTo(request.entryPrice()) <= 0) {
            reasons.add("For SELL, stop price must be above entry price");
        }
        long riskLimitedQuantity = stopDistance.signum() == 0 ? 0 : profile.maxLossPerTrade()
                .divide(stopDistance, 0, RoundingMode.DOWN).longValue();
        long capitalLimitedQuantity = profile.allocatedCapital()
                .divide(request.entryPrice(), 0, RoundingMode.DOWN).longValue();
        long maximumQuantity = Math.min(riskLimitedQuantity, capitalLimitedQuantity);
        BigDecimal proposedRisk = stopDistance.multiply(BigDecimal.valueOf(request.quantity()));
        if (request.quantity() > riskLimitedQuantity) reasons.add("Requested quantity exceeds configured per-trade risk limit");
        if (request.quantity() > capitalLimitedQuantity) reasons.add("Requested quantity exceeds configured capital allocation");
        if (includePaperJournalGuards && stopDistance.signum() > 0 && request.quantity() <= maximumQuantity && reasons.isEmpty()) {
            reasons.addAll(paperTrades.intentBlockReasons(request.symbol(), request.entryPrice(), request.stopPrice(), request.quantity()));
        }
        boolean allowed = reasons.isEmpty();
        if (allowed) reasons.add(profile.mode() == com.dileephl.marketscanner.config.TradingMode.PAPER
                ? "Paper order may be recorded; no broker request will be sent" : "Dry-run order intent approved; no broker request will be sent");
        return new OrderPreview(allowed, maximumQuantity, proposedRisk, List.copyOf(reasons), profile.mode().name());
    }

    public record OrderRequest(String symbol, OrderSide side, BigDecimal entryPrice, BigDecimal stopPrice, long quantity) { }
}
