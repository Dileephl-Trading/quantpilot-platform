package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.AutomatedExecutionProperties;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.api.PagedResponse;
import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import com.dileephl.marketscanner.marketdata.MarketInstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;

/** Safety boundary: no broker call is possible unless LIVE mode and the explicit flag are enabled. */
@Service
public class BrokerOrderExecutionService {
    private final TradingProfileService profiles;
    private final ExecutionPreviewService previews;
    private final KiteOrderExecutionProvider provider;
    private final SafetyService safety;
    private final ExecutionRequestRepository requests;
    private final ExecutionOrderEventRepository events;
    private final AuditTrailService audit;
    private final LiveOrderRiskService liveRisk;
    private final KiteConnectProperties kiteProperties;
    private final LiveExecutionProperties liveExecutionProperties;
    private final AutomatedExecutionProperties automationProperties;
    private final MarketInstrumentRepository instruments;
    private final ExecutionTradeRepository trades;

    public BrokerOrderExecutionService(TradingProfileService profiles, ExecutionPreviewService previews, KiteOrderExecutionProvider provider, SafetyService safety,
                                       ExecutionRequestRepository requests, ExecutionOrderEventRepository events, AuditTrailService audit,
                                       LiveOrderRiskService liveRisk, KiteConnectProperties kiteProperties,
                                       LiveExecutionProperties liveExecutionProperties,
                                       AutomatedExecutionProperties automationProperties,
                                       MarketInstrumentRepository instruments,
                                       ExecutionTradeRepository trades) {
        this.profiles = profiles; this.previews = previews; this.provider = provider; this.safety = safety; this.requests = requests; this.events = events; this.audit = audit;
        this.liveRisk = liveRisk; this.kiteProperties = kiteProperties;
        this.liveExecutionProperties = liveExecutionProperties;
        this.automationProperties = automationProperties;
        this.instruments = instruments;
        this.trades = trades;
    }

    @Transactional
    public synchronized BrokerOrderResponse place(String idempotencyKey, BrokerOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required");
        requireLive();
        validateRequest(request);
        validateRisk(request);
        String symbol = request.symbol().trim().toUpperCase();
        String key = idempotencyKey.trim();
        return requests.findByIdempotencyKey(key).map(existing -> {
            if (!existing.matches(request)) {
                throw new IllegalArgumentException("Idempotency-Key was already used for a different order payload");
            }
            audit.record("BROKER_ORDER_IDEMPOTENT_REPLAY", "EXECUTION_REQUEST", existing.response().orderId(), "key=" + key);
            return existing.response();
        }).orElseGet(() -> {
            if (requests.existsBySymbolAndSideAndStatusIn(symbol, request.side().name(), List.of("PENDING", "OPEN"))) {
                throw new IllegalStateException("An active broker order already exists for " + symbol + " " + request.side());
            }
            LiveOrderRiskService.RiskCheck risk = liveRisk.verify(request, java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            if (!risk.allowed()) throw new IllegalStateException("Live order blocked by safety checks: " + String.join("; ", risk.reasons()));
            BrokerOrderResponse response = provider.place(request);
            requests.save(ExecutionRequestEntity.from(key, request, response));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("BROKER_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(), "symbol=" + request.symbol() + ", quantity=" + request.quantity());
            return response;
        });
    }

    /**
     * Protective exits are submitted only by the reconciliation path after a
     * parent entry has a broker-confirmed fill.  They intentionally bypass the
     * new-entry kill switch and daily-entry limits: an open position must remain
     * protected even after automation is stopped.
     */
    @Transactional
    synchronized BrokerOrderResponse placeProtective(String idempotencyKey, BrokerOrderRequest request,
                                                      String parentOrderId) {
        requireLiveForProtective();
        if (parentOrderId == null || parentOrderId.isBlank()) throw new IllegalArgumentException("Parent order id is required");
        validateProtectiveRequest(request);
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank()) throw new IllegalArgumentException("Protective idempotency key is required");
        return requests.findByIdempotencyKey(key).map(existing -> {
            if (!existing.matches(request)) {
                throw new IllegalArgumentException("Protective idempotency key was already used for a different order payload");
            }
            return existing.response();
        }).orElseGet(() -> {
            BrokerOrderResponse response = provider.place(request);
            requests.save(ExecutionRequestEntity.fromProtective(key, request, response, parentOrderId));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("PROTECTIVE_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(),
                    "parentOrderId=" + parentOrderId + ", symbol=" + request.symbol() + ", quantity=" + request.quantity());
            return response;
        });
    }


    @Transactional
    synchronized BrokerOrderResponse placeTarget(String idempotencyKey, BrokerOrderRequest request,
                                                  String parentOrderId) {
        requireLiveForProtective();
        if (parentOrderId == null || parentOrderId.isBlank()) throw new IllegalArgumentException("Parent order id is required");
        validateTargetRequest(request);
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank()) throw new IllegalArgumentException("Target idempotency key is required");
        return requests.findByIdempotencyKey(key).map(existing -> {
            if (!existing.matches(request)) {
                throw new IllegalArgumentException("Target idempotency key was already used for a different order payload");
            }
            return existing.response();
        }).orElseGet(() -> {
            BrokerOrderResponse response = provider.place(request);
            requests.save(ExecutionRequestEntity.fromTarget(key, request, response, parentOrderId));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("TARGET_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(),
                    "parentOrderId=" + parentOrderId + ", symbol=" + request.symbol()
                            + ", quantity=" + request.quantity() + ", price=" + request.price());
            return response;
        });
    }

    @Transactional
    synchronized BrokerOrderResponse modifyProtective(String orderId, BrokerOrderRequest request) {
        requireLiveForProtective();
        ExecutionRequestEntity tracked = requireTracked(orderId);
        if (!tracked.protective()) throw new IllegalArgumentException("Only a protective order can be modified by this path");
        validateProtectiveRequest(request);
        return persist(provider.modify(orderId, request), "PROTECTIVE_ORDER_MODIFIED");
    }


    @Transactional
    synchronized BrokerOrderResponse modifyTarget(String orderId, BrokerOrderRequest request) {
        requireLiveForProtective();
        ExecutionRequestEntity tracked = requireTracked(orderId);
        if (!"TARGET".equals(tracked.exitRole())) throw new IllegalArgumentException("Only a target order can be modified by this path");
        validateTargetRequest(request);
        return persist(provider.modify(orderId, request), "TARGET_ORDER_MODIFIED");
    }

    public java.util.List<BrokerPosition> currentPositions() {
        requireLiveForProtective();
        return provider.positions();
    }
    @Transactional
    public BrokerOrderResponse status(String orderId) {
        requireLive();
        requireTracked(orderId);
        return persist(provider.status(orderId), "BROKER_ORDER_STATUS_RECONCILED");
    }
    @Transactional
    public BrokerOrderResponse cancel(String orderId) {
        requireLive();
        requireTracked(orderId);
        return persist(provider.cancel(orderId), "BROKER_ORDER_CANCEL_REQUESTED");
    }
    @Transactional
    public BrokerOrderResponse modify(String orderId, BrokerOrderRequest request) {
        requireLive();
        requireTracked(orderId);
        validateRequest(request);
        validateRisk(request);
        return persist(provider.modify(orderId, request), "BROKER_ORDER_MODIFY_REQUESTED");
    }
    @Transactional
    public java.util.List<BrokerOrderResponse> cancelOpenOrders() {
        if (profiles.current().mode() != TradingMode.LIVE) {
            throw new IllegalStateException("Open-order cancellation requires explicit LIVE trading mode");
        }
        requireLive();
        return provider.cancelOpenOrders().stream().map(response -> persist(response, "BROKER_ORDER_CANCEL_REQUESTED")).toList();
    }

    @Transactional
    public java.util.List<BrokerOrderResponse> cancelTrackedOpenOrders() {
        requireLive();
        java.util.List<BrokerOrderResponse> responses = new java.util.ArrayList<>();
        for (ExecutionRequestEntity request : requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED"))) {
            responses.add(cancel(request.orderId()));
        }
        return java.util.List.copyOf(responses);
    }

    /** Emergency path: cancellation remains allowed after the kill switch is enabled. */
    @Transactional
    public java.util.List<BrokerOrderResponse> cancelTrackedOpenOrdersForSafety() {
        if (profiles.current().mode() != TradingMode.LIVE) return java.util.List.of();
        java.util.List<BrokerOrderResponse> responses = new java.util.ArrayList<>();
        for (ExecutionRequestEntity request : requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED"))) {
            try {
                responses.add(persist(provider.cancel(request.orderId()), "BROKER_ORDER_EMERGENCY_CANCEL_REQUESTED"));
            } catch (RuntimeException error) {
                audit.record("BROKER_ORDER_EMERGENCY_CANCEL_FAILED", "BROKER_ORDER", request.orderId(), error.getMessage());
            }
        }
        return java.util.List.copyOf(responses);
    }

    /** Closes current MIS net positions during the scheduled end-of-day safety window. */
    @Transactional
    public java.util.List<BrokerOrderResponse> squareOffPositionsForSafety() {
        if (profiles.current().mode() != TradingMode.LIVE) return java.util.List.of();
        try {
            java.util.List<BrokerOrderResponse> responses = provider.squareOffPositions();
            audit.record("BROKER_POSITION_SQUARE_OFF_REQUESTED", "TRADING_SAFETY", "GLOBAL",
                    "count=" + responses.size());
            return responses;
        } catch (RuntimeException error) {
            audit.record("BROKER_POSITION_SQUARE_OFF_FAILED", "TRADING_SAFETY", "GLOBAL", error.getMessage());
            throw error;
        }
    }

    @Transactional
    public java.util.List<BrokerOrderResponse> reconcileTrackedOrders() {
        requireLive();
        java.util.List<BrokerOrderResponse> responses = new java.util.ArrayList<>();
        for (ExecutionRequestEntity request : reconciliationCandidates()) {
            responses.add(reconcile(request));
        }
        return java.util.List.copyOf(responses);
    }

    @Transactional(readOnly = true)
    public java.util.List<BrokerOrderResponse> trackedOrders() {
        return requests.findAll().stream().map(ExecutionRequestEntity::response).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExecutionOrderView> trackedOrdersPage(int days, int page, int pageSize) {
        int safeDays = Math.max(1, Math.min(days, 7));
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).minusDays(safeDays);
        return PagedResponse.from(requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                cutoff, PageRequest.of(safePage, safePageSize)), ExecutionRequestEntity::view);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExecutionOrderHistoryView> trackedOrderHistoryPage(int days, int page, int pageSize) {
        int safeDays = Math.max(1, Math.min(days, 7));
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).minusDays(safeDays);
        var source = requests.findByProtectiveFalseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                cutoff, PageRequest.of(safePage, safePageSize));
        return PagedResponse.from(source, entry -> entry.history(
                requests.findByParentOrderIdAndExitRole(entry.orderId(), "STOP").orElse(null),
                requests.findByParentOrderIdAndExitRole(entry.orderId(), "TARGET").orElse(null)));
    }

    @Transactional(readOnly = true)
    public java.util.List<ExecutionOrderEventView> events(String orderId) {
        requireTracked(orderId);
        return events.findByOrderIdOrderByObservedAtDesc(orderId).stream().map(ExecutionOrderEventEntity::view).toList();
    }

    private void requireLive() {
        if (safety.state().killSwitchEnabled()) throw new IllegalStateException("Kill switch is enabled; broker order routing is blocked");
        if (profiles.current().mode() != TradingMode.LIVE) {
            throw new IllegalStateException("Broker order routing requires explicit LIVE trading mode; current mode is PAPER/DRY_RUN");
        }
        if (!liveExecutionProperties.isEnabled()) {
            throw new IllegalStateException("LIVE_EXECUTION_ENABLED must be true for broker order routing");
        }
    }

    private void validateRequest(BrokerOrderRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.side() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("Symbol, side and positive quantity are required");
        }
        String orderType = request.orderType() == null ? "" : request.orderType().trim().toUpperCase();
        if (!List.of("MARKET", "LIMIT", "SL", "SL-M").contains(orderType)) {
            throw new IllegalArgumentException("Unsupported order type");
        }
        if (("LIMIT".equals(orderType) || "SL".equals(orderType))
                && (request.price() == null || request.price().signum() <= 0)) {
            throw new IllegalArgumentException("A positive price is required for this order type");
        }
        if (("SL".equals(orderType) || "SL-M".equals(orderType))
                && (request.triggerPrice() == null || request.triggerPrice().signum() <= 0)) {
            throw new IllegalArgumentException("A positive trigger price is required for this order type");
        }
    }

    private void validateRisk(BrokerOrderRequest request) {
        if (request.price() == null || request.price().signum() <= 0
                || request.stopPrice() == null || request.stopPrice().signum() <= 0) {
            throw new IllegalArgumentException("A positive expected entry price and stop price are required for risk validation");
        }
        OrderPreview preview = previews.previewLive(new ExecutionPreviewService.OrderRequest(
                request.symbol(), request.side(), request.price(), request.stopPrice(), request.quantity()));
        if (!preview.allowed()) {
            throw new IllegalStateException("Order blocked by risk controls: " + String.join("; ", preview.reasons()));
        }
    }

    private void validateProtectiveRequest(BrokerOrderRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.side() == null || request.quantity() <= 0
                || request.triggerPrice() == null || request.triggerPrice().signum() <= 0
                || request.stopPrice() == null || request.stopPrice().signum() <= 0
                || !"SL-M".equalsIgnoreCase(request.orderType())) {
            throw new IllegalArgumentException("A protective SL-M order requires symbol, side, quantity and positive trigger price");
        }
    }

    private void validateTargetRequest(BrokerOrderRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.side() == null || request.quantity() <= 0
                || request.price() == null || request.price().signum() <= 0
                || !"LIMIT".equalsIgnoreCase(request.orderType())) {
            throw new IllegalArgumentException("A target LIMIT order requires symbol, side, quantity and positive price");
        }
    }

    private void requireLiveForProtective() {
        if (profiles.current().mode() != TradingMode.LIVE) {
            throw new IllegalStateException("Protective broker orders require explicit LIVE trading mode");
        }
        if (!providerRoutingEnabled()) throw new IllegalStateException("Kite order routing is disabled");
    }

    private boolean providerRoutingEnabled() {
        return kiteProperties.isOrderRoutingEnabled();
    }

    private ExecutionRequestEntity requireTracked(String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        return requests.findByOrderId(orderId).orElseThrow(() -> new IllegalArgumentException("Order is not tracked by this application"));
    }

    @Transactional
    synchronized BrokerOrderResponse reconcile(ExecutionRequestEntity request) {
        BrokerOrderResponse response = provider.status(request.orderId());
        BrokerOrderResponse persisted = persist(response, "BROKER_ORDER_STATUS_POLLED");
        if (request.protective()) {
            reconcileExitPair(request.parentOrderId());
            reconcileOutcome(request.parentOrderId());
        } else {
            ensureExitOrders(request, persisted);
            reconcileOutcome(request.orderId());
        }
        return persisted;
    }

    private List<ExecutionRequestEntity> reconciliationCandidates() {
        Map<String, ExecutionRequestEntity> candidates = new LinkedHashMap<>();
        requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED"))
                .forEach(item -> candidates.put(item.orderId(), item));
        // Completed entries must remain eligible: a restart can happen after
        // the entry filled but before the stop/target or fill reconciliation ran.
        for (ExecutionRequestEntity entry : requests.findByProtectiveFalseAndStatusIn(
                List.of("COMPLETE", "PARTIALLY_FILLED"))) {
            boolean missingProtection = requests.findByParentOrderIdAndExitRole(entry.orderId(), "STOP").isEmpty()
                    || requests.findByParentOrderIdAndExitRole(entry.orderId(), "TARGET").isEmpty();
            boolean outcomeIncomplete = entry.realisedPnl() == null
                    || (entry.filledQuantity() > 0 && entry.exitFilledQuantity() < entry.filledQuantity());
            if (missingProtection || outcomeIncomplete) candidates.put(entry.orderId(), entry);
        }
        return List.copyOf(candidates.values());
    }

    /**
     * Pulls broker fills and the day position snapshot after an entry/exit
     * changes.  Local fill P&L is always retained; the broker value is shown
     * separately because Kite may aggregate several trades for one symbol.
     */
    private void reconcileOutcome(String parentOrderId) {
        if (parentOrderId == null || parentOrderId.isBlank()) return;
        ExecutionRequestEntity parent = requests.findByOrderId(parentOrderId).orElse(null);
        if (parent == null || parent.protective()) return;
        ExecutionRequestEntity stop = requests.findByParentOrderIdAndExitRole(parentOrderId, "STOP").orElse(null);
        ExecutionRequestEntity target = requests.findByParentOrderIdAndExitRole(parentOrderId, "TARGET").orElse(null);
        if (stop == null && target == null) return;

        try {
            upsertTrades(provider.trades(parent.orderId()));
            if (stop != null) upsertTrades(provider.trades(stop.orderId()));
            if (target != null) upsertTrades(provider.trades(target.orderId()));

            List<BrokerTrade> entryTrades = trades.findByOrderIdOrderByFillTimestampAsc(parent.orderId())
                    .stream().map(ExecutionTradeEntity::view).toList();
            List<BrokerTrade> exitTrades = new ArrayList<>();
            if (stop != null) exitTrades.addAll(trades.findByOrderIdOrderByFillTimestampAsc(stop.orderId()).stream().map(ExecutionTradeEntity::view).toList());
            if (target != null) exitTrades.addAll(trades.findByOrderIdOrderByFillTimestampAsc(target.orderId()).stream().map(ExecutionTradeEntity::view).toList());
            if (entryTrades.isEmpty() || exitTrades.isEmpty()) return;

            ExecutionPnlCalculator.Result result = ExecutionPnlCalculator.calculate(
                    OrderSide.valueOf(parent.side()), entryTrades, exitTrades);
            if (result == null) return;

            BigDecimal brokerPnl = null;
            String source = "TRADE_FILLS";
            try {
                brokerPnl = provider.dayPositions().stream()
                        .filter(position -> parent.symbol().equalsIgnoreCase(position.symbol()))
                        .filter(position -> parent.exchange() == null || parent.exchange().isBlank()
                                || parent.exchange().equalsIgnoreCase(position.exchange()))
                        .filter(position -> parent.product() == null || parent.product().isBlank()
                                || parent.product().equalsIgnoreCase(position.product()))
                        .map(BrokerDayPosition::realisedPnl)
                        .filter(value -> value != null)
                        .findFirst().orElse(null);
                if (brokerPnl != null) source = "BROKER_POSITION";
            } catch (RuntimeException brokerError) {
                audit.record("BROKER_PNL_RECONCILIATION_FAILED", "BROKER_ORDER", parent.orderId(), brokerError.getMessage());
            }
            OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30));
            parent.updateOutcome(result, brokerPnl, source, observedAt);
            requests.save(parent);
            audit.record("BROKER_ORDER_PNL_RECONCILED", "BROKER_ORDER", parent.orderId(),
                    "exitQuantity=" + result.matchedQuantity() + ", exitAveragePrice=" + result.exitAveragePrice()
                            + ", realisedPnl=" + result.realisedPnl() + ", source=" + source);
        } catch (RuntimeException error) {
            // P&L display is best effort; it must never stop protective-order polling.
            audit.record("BROKER_PNL_RECONCILIATION_FAILED", "BROKER_ORDER", parent.orderId(), error.getMessage());
        }
    }

    private void upsertTrades(List<BrokerTrade> brokerTrades) {
        if (brokerTrades == null) return;
        for (BrokerTrade trade : brokerTrades) {
            if (trade == null || trade.tradeId() == null || trade.tradeId().isBlank()
                    || trade.quantity() <= 0 || trade.averagePrice() == null) continue;
            ExecutionTradeEntity entity = trades.findByTradeId(trade.tradeId()).orElseGet(() -> ExecutionTradeEntity.from(trade));
            if (entity.tradeId().equals(trade.tradeId())) entity.apply(trade);
            trades.save(entity);
        }
    }

    private void ensureExitOrders(ExecutionRequestEntity parent, BrokerOrderResponse response) {
        if (response == null || response.filledQuantity() <= 0
                || (response.status() != BrokerOrderStatus.COMPLETE
                && response.status() != BrokerOrderStatus.PARTIALLY_FILLED
                && response.status() != BrokerOrderStatus.CANCELLED
                && response.status() != BrokerOrderStatus.UNKNOWN)) return;
        if (parent.stopPrice() == null || parent.stopPrice().signum() <= 0) {
            failExitProtection(parent.orderId(), "STOP", "Parent entry has no positive protective stop");
            return;
        }

        ExecutionRequestEntity stop = requests.findByParentOrderIdAndExitRole(parent.orderId(), "STOP").orElse(null);
        ExecutionRequestEntity target = requests.findByParentOrderIdAndExitRole(parent.orderId(), "TARGET").orElse(null);
        if (stop != null && "COMPLETE".equals(stop.status())) {
            reconcileExitPair(parent.orderId());
            return;
        }
        if (target != null && "COMPLETE".equals(target.status())) {
            reconcileExitPair(parent.orderId());
            return;
        }
        if (stop != null && failedExit(stop)) {
            failExitProtection(parent.orderId(), "STOP", "Protective stop is " + stop.status());
            return;
        }
        if (target != null && failedExit(target)) {
            failExitProtection(parent.orderId(), "TARGET", "Target order is " + target.status());
            return;
        }

        OrderSide exitSide = "BUY".equalsIgnoreCase(parent.side()) ? OrderSide.SELL : OrderSide.BUY;
        long filledQuantity = response.filledQuantity();
        if (stop == null) {
            BrokerOrderRequest protective = new BrokerOrderRequest(parent.symbol(), exitSide, filledQuantity,
                    "SL-M", null, parent.stopPrice(), parent.product(), parent.exchange(), "DAY", parent.stopPrice());
            try {
                placeProtective("PROTECTIVE-" + parent.orderId(), protective, parent.orderId());
            } catch (RuntimeException error) {
                failExitProtection(parent.orderId(), "STOP", error.getMessage());
                return;
            }
        } else if (openExit(stop) && stop.quantity() != filledQuantity) {
            BrokerOrderRequest protective = new BrokerOrderRequest(parent.symbol(), exitSide, filledQuantity,
                    "SL-M", null, parent.stopPrice(), parent.product(), parent.exchange(), "DAY", parent.stopPrice());
            try {
                modifyProtective(stop.orderId(), protective);
            } catch (RuntimeException error) {
                failExitProtection(parent.orderId(), "STOP", error.getMessage());
                return;
            }
        }

        target = requests.findByParentOrderIdAndExitRole(parent.orderId(), "TARGET").orElse(null);
        if (target == null) {
            try {
                placeTarget("TARGET-" + parent.orderId(), targetRequest(parent, response, exitSide), parent.orderId());
            } catch (RuntimeException error) {
                failExitProtection(parent.orderId(), "TARGET", error.getMessage());
                return;
            }
        } else if (openExit(target) && target.quantity() != filledQuantity) {
            try {
                modifyTarget(target.orderId(), targetRequest(parent, response, exitSide));
            } catch (RuntimeException error) {
                failExitProtection(parent.orderId(), "TARGET", error.getMessage());
                return;
            }
        }
        reconcileExitPair(parent.orderId());
    }

    private BrokerOrderRequest targetRequest(ExecutionRequestEntity parent, BrokerOrderResponse response,
                                             OrderSide exitSide) {
        BigDecimal entry = response.averagePrice() != null && response.averagePrice().signum() > 0
                ? response.averagePrice() : parent.price();
        BigDecimal stop = parent.stopPrice();
        if (entry == null || entry.signum() <= 0 || stop == null || stop.signum() <= 0) {
            throw new IllegalStateException("A filled entry and protective stop are required before placing target");
        }
        BigDecimal risk = entry.subtract(stop).abs();
        BigDecimal rawTarget = "BUY".equalsIgnoreCase(parent.side())
                ? entry.add(risk.multiply(automationProperties.getTargetRiskReward()))
                : entry.subtract(risk.multiply(automationProperties.getTargetRiskReward()));
        BigDecimal target = alignTarget(parent, rawTarget);
        if (target.signum() <= 0
                || ("BUY".equalsIgnoreCase(parent.side()) && target.compareTo(entry) <= 0)
                || ("SELL".equalsIgnoreCase(parent.side()) && target.compareTo(entry) >= 0)) {
            throw new IllegalStateException("Calculated target is not on the profitable side of the filled entry");
        }
        return new BrokerOrderRequest(parent.symbol(), exitSide, response.filledQuantity(),
                "LIMIT", target, null, parent.product(), parent.exchange(), "DAY", null);
    }

    private BigDecimal alignTarget(ExecutionRequestEntity parent, BigDecimal rawTarget) {
        BigDecimal tick = instruments.findById("ZERODHA_KITE:" + parent.exchange() + ":" + parent.symbol())
                .map(item -> item.getTickSize())
                .orElse(new BigDecimal("0.01"));
        if (tick == null || tick.signum() <= 0) return rawTarget.setScale(2, RoundingMode.HALF_UP);
        RoundingMode rounding = "BUY".equalsIgnoreCase(parent.side()) ? RoundingMode.DOWN : RoundingMode.UP;
        return rawTarget.divide(tick, 0, rounding).multiply(tick).stripTrailingZeros();
    }

    private void reconcileExitPair(String parentOrderId) {
        if (parentOrderId == null || parentOrderId.isBlank()) return;
        ExecutionRequestEntity stop = requests.findByParentOrderIdAndExitRole(parentOrderId, "STOP").orElse(null);
        ExecutionRequestEntity target = requests.findByParentOrderIdAndExitRole(parentOrderId, "TARGET").orElse(null);
        if (stop == null || target == null) return;

        if ("COMPLETE".equals(stop.status())) {
            cancelExitIfOpen(target);
            return;
        }
        if ("COMPLETE".equals(target.status())) {
            cancelExitIfOpen(stop);
            return;
        }
        if (failedExit(stop)) {
            failExitProtection(parentOrderId, "STOP", "Protective stop is " + stop.status());
            return;
        }
        if (failedExit(target)) {
            failExitProtection(parentOrderId, "TARGET", "Target order is " + target.status());
        }
    }

    private void cancelExitIfOpen(ExecutionRequestEntity exit) {
        if (!openExit(exit)) return;
        try {
            persist(provider.cancel(exit.orderId()), "EXIT_OCO_CANCEL_REQUESTED");
        } catch (RuntimeException error) {
            audit.record("EXIT_OCO_CANCEL_FAILED", "BROKER_ORDER", exit.orderId(), error.getMessage());
        }
    }

    private boolean openExit(ExecutionRequestEntity request) {
        return request != null && List.of("PENDING", "OPEN", "PARTIALLY_FILLED").contains(request.status());
    }

    private boolean failedExit(ExecutionRequestEntity request) {
        return request != null && List.of("REJECTED", "CANCELLED", "UNKNOWN").contains(request.status());
    }

    private void failExitProtection(String parentOrderId, String exitRole, String reason) {
        String detail = reason == null || reason.isBlank() ? "unknown exit-protection failure" : reason;
        audit.record("PROTECTIVE_ORDER_FAILED", "BROKER_ORDER", parentOrderId,
                "exitRole=" + exitRole + ", " + detail);
        try {
            provider.squareOffPositions();
        } catch (RuntimeException squareOffError) {
            audit.record("PROTECTIVE_FAILSAFE_SQUARE_OFF_FAILED", "TRADING_SAFETY", "GLOBAL", squareOffError.getMessage());
        }
        try {
            safety.enable("Exit protection failed for parent order " + parentOrderId
                    + " (" + exitRole + "): " + detail);
        } catch (RuntimeException safetyError) {
            audit.record("PROTECTIVE_FAILSAFE_KILL_SWITCH_FAILED", "TRADING_SAFETY", "GLOBAL", safetyError.getMessage());
        }
    }

    private BrokerOrderResponse persist(BrokerOrderResponse response, String eventType) {
        ExecutionRequestEntity request = requests.findByOrderId(response.orderId()).orElse(null);
        if (request != null) {
            request.apply(response);
            requests.save(request);
        }
        events.save(ExecutionOrderEventEntity.from(response));
        audit.record(eventType, "BROKER_ORDER", response.orderId(),
                "status=" + response.status() + ", filledQuantity=" + response.filledQuantity()
                        + ", averagePrice=" + response.averagePrice());
        return response;
    }
}
