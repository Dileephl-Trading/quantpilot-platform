package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Second, broker-aware risk layer for live entries.  Paper-trade rows are not
 * used here: a live order must be checked against today's live submissions and
 * the broker's current net positions.
 */
@Service
public class LiveOrderRiskService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final List<String> COUNTED_STATUSES = List.of("PENDING", "OPEN", "PARTIALLY_FILLED", "COMPLETE", "UNKNOWN");
    private final TradingProfileService profiles;
    private final ExecutionRequestRepository requests;
    private final OrderExecutionProvider provider;

    public LiveOrderRiskService(TradingProfileService profiles, ExecutionRequestRepository requests,
                                OrderExecutionProvider provider) {
        this.profiles = profiles;
        this.requests = requests;
        this.provider = provider;
    }

    /**
     * This method is synchronized so two signal callbacks in the same JVM do
     * not both pass the local reservation checks before either reaches Kite.
     * The broker position check remains authoritative for restarts/multiple
     * workers; a database unique idempotency key protects the final submit.
     */
    public synchronized RiskCheck verify(BrokerOrderRequest request, OffsetDateTime now) {
        TradingProfile profile = profiles.currentForRiskDecision();
        List<String> reasons = new ArrayList<>();
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.side() == null || request.quantity() <= 0) {
            return new RiskCheck(false, List.of("Symbol, side and positive quantity are required"),
                    BigDecimal.ZERO, 0, 0);
        }

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        OffsetDateTime start = startOfDay(now == null ? OffsetDateTime.now(INDIA) : now);
        List<ExecutionRequestEntity> today = requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(start);
        List<ExecutionRequestEntity> entryRequests = today.stream()
                .filter(item -> !item.protective())
                .filter(item -> COUNTED_STATUSES.contains(item.status()))
                .toList();

        if (entryRequests.size() >= profile.maxTradesPerDay()) {
            reasons.add("Maximum live trades per day has already been reached");
        }

        List<BrokerPosition> positions = provider.positions().stream()
                .filter(BrokerPosition::open)
                // Net positions for other products (for example CNC holdings)
                // must not consume this intraday MIS profile's position slots.
                .filter(position -> profile.product() == null
                        || profile.product().isBlank()
                        || profile.product().equalsIgnoreCase(position.product()))
                .filter(position -> profile.exchange() == null
                        || profile.exchange().isBlank()
                        || profile.exchange().equalsIgnoreCase(position.exchange()))
                .toList();
        long activeRequestCount = requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED")).stream()
                .filter(item -> !item.protective())
                .map(ExecutionRequestEntity::symbol)
                .distinct()
                .count();
        long openPositionCount = positions.stream().map(BrokerPosition::symbol).distinct().count();
        long occupiedSlots = Math.max(activeRequestCount, openPositionCount);
        if (occupiedSlots >= profile.maxOpenPositions()) {
            reasons.add("Maximum live open positions has been reached");
        }
        if (positions.stream().anyMatch(position -> symbol.equalsIgnoreCase(position.symbol()))) {
            reasons.add("A live position already exists for " + symbol);
        }
        if (requests.existsBySymbolAndStatusIn(symbol, List.of("PENDING", "OPEN", "PARTIALLY_FILLED"))) {
            reasons.add("An active entry order already exists for " + symbol);
        }

        requests.findTopBySymbolAndProtectiveFalseOrderByCreatedAtDesc(symbol).ifPresent(previous -> {
            long elapsed = Math.max(0, Duration.between(previous.createdAt(), now == null ? OffsetDateTime.now(INDIA) : now).getSeconds());
            if (elapsed < profile.cooldownSeconds()) {
                reasons.add("Cooldown is active for " + symbol);
            }
        });
        requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(start).stream()
                .filter(item -> !item.protective())
                .findFirst()
                .ifPresent(previous -> {
                    long elapsed = Math.max(0, Duration.between(previous.createdAt(), now == null ? OffsetDateTime.now(INDIA) : now).getSeconds());
                    if (elapsed < profile.cooldownSeconds()) reasons.add("Global cooldown is active");
                });

        BigDecimal proposedRisk = risk(request.price(), request.stopPrice(), request.quantity());
        if (proposedRisk.signum() <= 0) {
            reasons.add("A positive expected entry and protective stop are required");
        } else {
            BigDecimal reserved = entryRequests.stream().map(this::reservedRisk)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (reserved.add(proposedRisk).compareTo(profile.maxDailyLoss()) > 0) {
                reasons.add("This entry would exceed the remaining live daily risk reserve");
            }
            if (proposedRisk.compareTo(profile.maxLossPerTrade()) > 0) {
                reasons.add("Proposed risk exceeds the configured maximum loss per trade");
            }
        }
        return new RiskCheck(reasons.isEmpty(), List.copyOf(reasons), proposedRisk,
                entryRequests.size(), occupiedSlots);
    }

    private BigDecimal reservedRisk(ExecutionRequestEntity request) {
        return risk(request.price(), request.stopPrice(), request.quantity());
    }

    private BigDecimal risk(BigDecimal entry, BigDecimal stop, long quantity) {
        if (entry == null || stop == null || entry.signum() <= 0 || stop.signum() <= 0 || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        return entry.subtract(stop).abs().multiply(BigDecimal.valueOf(quantity));
    }

    private OffsetDateTime startOfDay(OffsetDateTime value) {
        ZonedDateTime local = value.toInstant().atZone(INDIA).toLocalDate().atStartOfDay(INDIA);
        return local.toOffsetDateTime();
    }

    public record RiskCheck(boolean allowed, List<String> reasons, BigDecimal proposedRisk,
                            int tradesToday, long occupiedPositionSlots) { }
}
