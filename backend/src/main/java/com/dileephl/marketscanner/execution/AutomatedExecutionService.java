package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.AutomatedExecutionProperties;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.live.SignalVerificationService;
import com.dileephl.marketscanner.marketdata.MarketInstrumentEntity;
import com.dileephl.marketscanner.marketdata.MarketInstrumentRepository;
import com.dileephl.marketscanner.service.SignalEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

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
    private final LiveExecutionPreflightService preflight;
    private final LiveOrderRiskService liveRisk;
    private final LiveExecutionProperties liveExecutionProperties;
    private final SignalVerificationService verifications;
    private final MarketInstrumentRepository instruments;

    public AutomatedExecutionService(AutomatedExecutionProperties properties, TradingProfileService profiles,
                                     MarketSessionService sessions, KiteConnectProperties kiteProperties,
                                     KiteAuthService auth, SafetyService safety, ExecutionPreviewService previews,
                                     BrokerOrderExecutionService execution, ExecutionRequestRepository requests,
                                     AutomationSafetyStateRepository safetyStates, AuditTrailService audit,
                                     LiveExecutionPreflightService preflight, LiveOrderRiskService liveRisk,
                                     LiveExecutionProperties liveExecutionProperties,
                                     SignalVerificationService verifications,
                                     MarketInstrumentRepository instruments) {
        properties.validate();
        this.properties = properties; this.profiles = profiles; this.sessions = sessions;
        this.kiteProperties = kiteProperties; this.auth = auth; this.safety = safety;
        this.previews = previews; this.execution = execution; this.requests = requests;
        this.safetyStates = safetyStates; this.audit = audit; this.preflight = preflight; this.liveRisk = liveRisk;
        this.liveExecutionProperties = liveExecutionProperties;
        this.verifications = verifications;
        this.instruments = instruments;
    }

    public AutomatedStatus status() {
        TradingProfile profile = profiles.current();
        return new AutomatedStatus(properties.isEnabled(), liveExecutionEnabled(), profile.mode().name(),
                kiteProperties.isMarketDataEnabled(), kiteProperties.isOrderRoutingEnabled(),
                auth.hasCurrentAccessToken(), safety.state().killSwitchEnabled(), properties.getMaxSignalAgeSeconds(),
                properties.getMaxOrderQuantity(), properties.getOrderType(), currentSafetyState().uncertainBrokerState());
    }

    private boolean liveExecutionEnabled() {
        return liveExecutionProperties.isEnabled();
    }

    public AutomatedStatus recoverAfterReconciliation() {
        var report = execution.reconcileTrackedOrders();
        if (!report.issues().isEmpty()) throw new IllegalStateException(String.join("; ", report.issues()));
        execution.verifyProtection();
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

        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        String signalKey = "AUTO-" + provider + "-" + normalizedSymbol + "-" + candle.timestamp() + "-" + signal.type();
        String analysis = describeSignal(signal);
        boolean brokerAttempted = false;
        safeRecord(provider, instrumentToken, normalizedSymbol, candle, signal,
                "RECEIVED", analysis, null);
        try {
            if (currentSafetyState().uncertainBrokerState()) throw new IllegalStateException("Previous broker result is uncertain; manual reconciliation is required");

            LiveExecutionPreflightService.Preflight preflightResult = preflight.run();
            analysis += "\n\nExecution preflight:\n" + describePreflight(preflightResult);
            if (!preflightResult.ready()) {
                throw new IllegalStateException("Live execution preflight failed: " + preflightResult.checks().stream()
                        .filter(check -> !check.passed()).map(LiveExecutionPreflightService.Check::detail).reduce((a, b) -> a + "; " + b).orElse("unknown"));
            }
            if (!properties.isEnabled()) {
                throw new IllegalStateException("Automated execution is disabled; signal recorded for review only");
            }

            TradingProfile profile = profiles.current();
            OffsetDateTime now = OffsetDateTime.now(INDIA);
            require(profile, sessions, now, kiteProperties, auth, safety, candle, interval);
            OrderSide side = signal.type() == SignalEngine.SignalType.WATCH_LONG ? OrderSide.BUY : OrderSide.SELL;
            BigDecimal entry = signal.referencePrice();
            BigDecimal stop = signal.invalidationReference();
            if (entry == null || stop == null || entry.signum() <= 0 || stop.signum() <= 0) {
                throw new IllegalStateException("Signal has no positive entry and invalidation prices");
            }
            if ((side == OrderSide.BUY && stop.compareTo(entry) >= 0)
                    || (side == OrderSide.SELL && stop.compareTo(entry) <= 0)) {
                throw new IllegalStateException("Signal invalidation must be below entry for BUY and above entry for SELL");
            }
            MarketInstrumentEntity instrument = instruments.findByProviderAndInstrumentToken(provider, instrumentToken)
                    .orElseThrow(() -> new IllegalStateException("Signal instrument is no longer present in the synced NSE instrument list"));
            entry = alignToTick(entry, instrument.getTickSize(), side == OrderSide.BUY ? RoundingMode.DOWN : RoundingMode.UP);
            stop = alignToTick(stop, instrument.getTickSize(), side == OrderSide.BUY ? RoundingMode.DOWN : RoundingMode.UP);
            if ((side == OrderSide.BUY && stop.compareTo(entry) >= 0)
                    || (side == OrderSide.SELL && stop.compareTo(entry) <= 0)) {
                throw new IllegalStateException("Signal entry and invalidation collapse to the same exchange tick");
            }
            OrderPreview preview = previews.previewLive(new ExecutionPreviewService.OrderRequest(normalizedSymbol, side, entry, stop, 1));
            analysis += "\n\nRisk preview: allowed=" + preview.allowed()
                    + ", maxQuantity=" + preview.maxAllowedQuantity()
                    + ", proposedRisk=" + preview.proposedRisk()
                    + ", reasons=" + String.join(" · ", preview.reasons());
            if (!preview.allowed() || preview.maxAllowedQuantity() <= 0) {
                throw new IllegalStateException("Signal failed risk validation: " + String.join("; ", preview.reasons()));
            }
            long quantity = Math.min(preview.maxAllowedQuantity(), properties.getMaxOrderQuantity());
            if (quantity <= 0) throw new IllegalStateException("Calculated automated quantity is zero");

            BrokerOrderRequest request = new BrokerOrderRequest(normalizedSymbol, side, quantity,
                    properties.getOrderType(), entry,
                    null, profile.product(), profile.exchange(), "DAY", stop);
            LiveOrderRiskService.RiskCheck liveCheck = liveRisk.verify(request, now);
            analysis += "\n\nSecond live verification: allowed=" + liveCheck.allowed()
                    + ", proposedRisk=" + liveCheck.proposedRisk()
                    + ", tradesToday=" + liveCheck.tradesToday()
                    + ", occupiedSlots=" + liveCheck.occupiedPositionSlots()
                    + ", reasons=" + String.join(" · ", liveCheck.reasons());
            if (!liveCheck.allowed()) {
                throw new IllegalStateException("Second live verification failed: " + String.join("; ", liveCheck.reasons()));
            }

            safeRecord(provider, instrumentToken, normalizedSymbol, candle, signal,
                    "PASSED", analysis + "\n\nDecision: ready for broker order.", null);
            audit.record("AUTOMATED_SIGNAL_VERIFIED", "AUTOMATION_SIGNAL", signalKey,
                    "symbol=" + normalizedSymbol + ", side=" + side + ", risk=" + liveCheck.proposedRisk()
                            + ", tradesToday=" + liveCheck.tradesToday() + ", occupiedSlots=" + liveCheck.occupiedPositionSlots());
            brokerAttempted = true;
            BrokerOrderResponse response = execution.place(signalKey, request);
            safeRecord(provider, instrumentToken, normalizedSymbol, candle, signal,
                    "ORDER_SUBMITTED", analysis + "\n\nDecision: broker entry submitted.", response.orderId());
            audit.record("AUTOMATED_ORDER_SUBMITTED", "AUTOMATION_SIGNAL", signalKey,
                    "symbol=" + normalizedSymbol + ", signal=" + signal.type() + ", token=" + instrumentToken
                            + ", candle=" + candle.timestamp() + ", quantity=" + quantity);
        } catch (RuntimeException error) {
            String eventType = brokerAttempted ? "AUTOMATED_ORDER_UNCERTAIN" : "AUTOMATED_ORDER_SKIPPED";
            String decision = brokerAttempted ? "ORDER_UNCERTAIN" : "BLOCKED";
            String finalAnalysis = analysis + "\n\nDecision: " + decision + "\nReason: " + safe(error.getMessage());
            safeRecord(provider, instrumentToken, normalizedSymbol, candle, signal,
                    decision, finalAnalysis, null);
            if (brokerAttempted) {
                AutomationSafetyStateEntity state = currentSafetyState();
                state.markUncertain(safe(error.getMessage()));
                safetyStates.save(state);
            }
            audit.record(eventType, "AUTOMATION_SIGNAL", signalKey,
                    "symbol=" + normalizedSymbol + ", signal=" + signal.type() + ", reason=" + safe(error.getMessage()));
        }
    }

    private void safeRecord(String provider, long instrumentToken, String symbol,
                             SignalEngine.Candle candle, SignalEngine.TradeSignal signal,
                             String decision, String analysis, String orderId) {
        try {
            verifications.record(provider, instrumentToken, symbol, candle, signal, decision, analysis, orderId);
        } catch (RuntimeException error) {
            audit.record("SIGNAL_VERIFICATION_RECORD_FAILED", "SIGNAL_VERIFICATION",
                    symbol + "-" + candle.timestamp(), safe(error.getMessage()));
        }
    }

    private String describeSignal(SignalEngine.TradeSignal signal) {
        return "Signal: " + signal.type().name()
                + "\nReference: " + decimal(signal.referencePrice())
                + " · VWAP: " + decimal(signal.vwap())
                + "\nInvalidation: " + decimal(signal.invalidationReference())
                + " · Opening range: " + decimal(signal.openingRangeLow())
                + " – " + decimal(signal.openingRangeHigh())
                + "\nVolume baseline: " + decimal(signal.averageVolume())
                + " · Required threshold: " + decimal(signal.volumeThreshold())
                + "\nRationale: " + String.join(" · ", signal.reasons());
    }

    private String describePreflight(LiveExecutionPreflightService.Preflight result) {
        return result.checks().stream()
                .map(check -> (check.passed() ? "PASS " : "BLOCK ") + check.name() + ": " + check.detail())
                .collect(Collectors.joining("\n"));
    }

    private String decimal(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private void require(TradingProfile profile, MarketSessionService sessions, OffsetDateTime now,
                         KiteConnectProperties kiteProperties, KiteAuthService auth, SafetyService safety,
                         SignalEngine.Candle candle, CandleInterval interval) {
        if (profile.mode() != TradingMode.LIVE) throw new IllegalStateException("Trading profile is not LIVE");
        if (!kiteProperties.isOrderRoutingEnabled()) throw new IllegalStateException("Kite order routing is disabled");
        if (!auth.hasCurrentAccessToken()) throw new IllegalStateException("Kite session is not connected");
        if (safety.state().killSwitchEnabled()) throw new IllegalStateException("Kill switch is enabled");
        if (!sessions.isNewEntryAllowed(now)) throw new IllegalStateException("New entries are closed outside the configured 09:30-14:45 window");
        OffsetDateTime candleEnd = candle.timestamp().plus(interval.candleDuration());
        if (candleEnd.isAfter(now)) throw new IllegalStateException("Candle is not complete");
        if (Duration.between(candleEnd, now).getSeconds() > properties.getMaxSignalAgeSeconds()) {
            throw new IllegalStateException("Signal is stale");
        }
    }

    private String safe(String message) { return message == null || message.isBlank() ? "unknown safety rejection" : message; }

    private BigDecimal alignToTick(BigDecimal value, BigDecimal tickSize, RoundingMode roundingMode) {
        if (tickSize == null || tickSize.signum() <= 0) return value;
        return value.divide(tickSize, 0, roundingMode).multiply(tickSize).stripTrailingZeros();
    }

    private AutomationSafetyStateEntity currentSafetyState() {
        return safetyStates.findById(1).orElseGet(() -> safetyStates.save(AutomationSafetyStateEntity.initial()));
    }

    public record AutomatedStatus(boolean enabled, boolean liveExecutionEnabled, String profileMode,
                                  boolean marketDataEnabled, boolean routingEnabled,
                                  boolean sessionConnected, boolean killSwitchEnabled,
                                  int maxSignalAgeSeconds, long maxOrderQuantity, String orderType,
                                  boolean uncertainBrokerState) { }
}
