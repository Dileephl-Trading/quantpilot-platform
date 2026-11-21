package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.AutomatedExecutionProperties;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.service.SignalEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Converts a completed scanner alert into a risk-checked broker request only when every
 * independent automation gate is open. The default is fail-closed: automation is disabled.
 */
@Service
public class AutomatedExecutionService {
    private static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);
    private static final List<String> OPEN_STATUSES = List.of("PENDING", "OPEN", "PARTIALLY_FILLED");

    private final AutomatedExecutionProperties properties;
    private final TradingProfileService profiles;
    private final MarketSessionService sessions;
    private final KiteConnectProperties kiteProperties;
    private final KiteAuthService auth;
    private final SafetyService safety;
    private final ExecutionPreviewService previews;
    private final BrokerOrderExecutionService execution;
    private final ExecutionRequestRepository requests;
    private final AutomationSafetyStateRepository safetyStates;
    private final AuditTrailService audit;
    private final LiveExecutionProperties liveExecutionProperties;

    public AutomatedExecutionService(AutomatedExecutionProperties properties, TradingProfileService profiles,
                                     MarketSessionService sessions, KiteConnectProperties kiteProperties,
                                     KiteAuthService auth, SafetyService safety, ExecutionPreviewService previews,
                                     BrokerOrderExecutionService execution, ExecutionRequestRepository requests,
                                     AutomationSafetyStateRepository safetyStates, AuditTrailService audit,
                                     LiveExecutionProperties liveExecutionProperties) {
        properties.validate();
        this.properties = properties; this.profiles = profiles; this.sessions = sessions;
        this.kiteProperties = kiteProperties; this.auth = auth; this.safety = safety;
        this.previews = previews; this.execution = execution; this.requests = requests;
        this.safetyStates = safetyStates; this.audit = audit;
    }

    public AutomatedStatus status() {
        TradingProfile profile = profiles.current();
        return new AutomatedStatus(properties.isEnabled(), liveExecutionProperties.isEnabled(), profile.mode().name(), kiteProperties.isOrderRoutingEnabled(),
                auth.hasCurrentAccessToken(), safety.state().killSwitchEnabled(), properties.getMaxSignalAgeSeconds(),
                properties.getMaxOrderQuantity(), properties.getOrderType(), currentSafetyState().uncertainBrokerState());
    }

    public AutomatedStatus recoverAfterReconciliation() {
        if (safety.state().killSwitchEnabled()) throw new IllegalStateException("Disable the kill switch only after broker status has been reviewed");
        execution.reconcileTrackedOrders();
        if (requests.countByStatusIn(OPEN_STATUSES) > 0) {
            throw new IllegalStateException("Open or partially filled orders remain; automation stays blocked");
        }
        AutomationSafetyStateEntity state = currentSafetyState();
        state.clear();
        safetyStates.save(state);
        audit.record("AUTOMATION_RECOVERED", "AUTOMATION_SAFETY", "GLOBAL", "Broker state reconciled and automation unblocked");
        return status();
    }

    public void onCompletedSignal(String provider, long instrumentToken, String symbol,
                                  SignalEngine.Candle candle, SignalEngine.TradeSignal signal,
                                  CandleInterval interval) {
        if (signal == null || signal.type() == SignalEngine.SignalType.NO_TRADE) return;
        if (!properties.isEnabled()) return;

        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        String signalKey = "AUTO-" + provider + "-" + normalizedSymbol + "-" + candle.timestamp() + "-" + signal.type();
        boolean brokerAttempted = false;
        try {
            if (currentSafetyState().uncertainBrokerState()) throw new IllegalStateException("Previous broker result is uncertain; manual reconciliation is required");
            TradingProfile profile = profiles.current();
            OffsetDateTime now = OffsetDateTime.now(INDIA);
            require(profile, sessions, now, kiteProperties, auth, safety, candle, interval);
            OrderSide side = signal.type() == SignalEngine.SignalType.WATCH_LONG ? OrderSide.BUY : OrderSide.SELL;
            BigDecimal entry = signal.referencePrice();
            BigDecimal stop = signal.invalidationReference();
            if (entry == null || stop == null || entry.signum() <= 0 || stop.signum() <= 0) {
                throw new IllegalStateException("Signal has no positive entry and invalidation prices");
            }
            if (requests.existsBySymbolAndStatusIn(normalizedSymbol, OPEN_STATUSES)) {
                throw new IllegalStateException("An active order already exists for " + normalizedSymbol);
            }
            if (requests.countByStatusIn(OPEN_STATUSES) >= profile.maxOpenPositions()) {
                throw new IllegalStateException("Maximum open positions has been reached");
            }
            requests.findTopBySymbolOrderByCreatedAtDesc(normalizedSymbol).ifPresent(previous -> {
                long elapsed = Math.max(0, Duration.between(previous.createdAt(), now).getSeconds());
                if (elapsed < profile.cooldownSeconds()) {
                    throw new IllegalStateException("Cooldown is active for " + normalizedSymbol);
                }
            });

            OrderPreview preview = previews.preview(new ExecutionPreviewService.OrderRequest(normalizedSymbol, side, entry, stop, 1));
            if (!preview.allowed() || preview.maxAllowedQuantity() <= 0) {
                throw new IllegalStateException("Signal failed risk validation: " + String.join("; ", preview.reasons()));
            }
            long quantity = Math.min(preview.maxAllowedQuantity(), properties.getMaxOrderQuantity());
            if (quantity <= 0) throw new IllegalStateException("Calculated automated quantity is zero");

            BrokerOrderRequest request = new BrokerOrderRequest(normalizedSymbol, side, quantity,
                    properties.getOrderType(), "MARKET".equals(properties.getOrderType()) ? null : entry,
                    null, profile.product(), profile.exchange(), "DAY", stop);
            brokerAttempted = true;
            execution.place(signalKey, request);
            audit.record("AUTOMATED_ORDER_SUBMITTED", "AUTOMATION_SIGNAL", signalKey,
                    "symbol=" + normalizedSymbol + ", signal=" + signal.type() + ", token=" + instrumentToken
                            + ", candle=" + candle.timestamp() + ", quantity=" + quantity);
        } catch (RuntimeException error) {
            String eventType = brokerAttempted ? "AUTOMATED_ORDER_UNCERTAIN" : "AUTOMATED_ORDER_SKIPPED";
            if (brokerAttempted) {
                AutomationSafetyStateEntity state = currentSafetyState();
                state.markUncertain(safe(error.getMessage()));
                safetyStates.save(state);
            }
            audit.record(eventType, "AUTOMATION_SIGNAL", signalKey,
                    "symbol=" + normalizedSymbol + ", signal=" + signal.type() + ", reason=" + safe(error.getMessage()));
        }
    }

    private void require(TradingProfile profile, MarketSessionService sessions, OffsetDateTime now,
                         KiteConnectProperties kiteProperties, KiteAuthService auth, SafetyService safety,
                         SignalEngine.Candle candle, CandleInterval interval) {
        if (profile.mode() != TradingMode.LIVE) throw new IllegalStateException("Trading profile is not LIVE");
        if (!liveExecutionProperties.isEnabled()) throw new IllegalStateException("Live execution master gate is disabled");
        if (!kiteProperties.isOrderRoutingEnabled()) throw new IllegalStateException("Kite order routing is disabled");
        if (!auth.hasCurrentAccessToken()) throw new IllegalStateException("Kite session is not connected");
        if (safety.state().killSwitchEnabled()) throw new IllegalStateException("Kill switch is enabled");
        if (!sessions.isOpen(now)) throw new IllegalStateException("NSE cash session is closed");
        OffsetDateTime candleEnd = candle.timestamp().plus(interval.candleDuration());
        if (candleEnd.isAfter(now)) throw new IllegalStateException("Candle is not complete");
        if (Duration.between(candleEnd, now).getSeconds() > properties.getMaxSignalAgeSeconds()) {
            throw new IllegalStateException("Signal is stale");
        }
    }

    private String safe(String message) { return message == null || message.isBlank() ? "unknown safety rejection" : message; }

    private AutomationSafetyStateEntity currentSafetyState() {
        return safetyStates.findById(1).orElseGet(() -> safetyStates.save(AutomationSafetyStateEntity.initial()));
    }

    public record AutomatedStatus(boolean enabled, boolean liveExecutionArmed, String profileMode, boolean routingEnabled,
                                  boolean sessionConnected, boolean killSwitchEnabled,
                                  int maxSignalAgeSeconds, long maxOrderQuantity, String orderType,
                                  boolean uncertainBrokerState) { }
}
