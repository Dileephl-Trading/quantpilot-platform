package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

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

    public BrokerOrderExecutionService(TradingProfileService profiles, ExecutionPreviewService previews, KiteOrderExecutionProvider provider, SafetyService safety,
                                       ExecutionRequestRepository requests, ExecutionOrderEventRepository events, AuditTrailService audit,
                                       LiveOrderRiskService liveRisk, KiteConnectProperties kiteProperties,
                                       LiveExecutionProperties liveExecutionProperties) {
        this.profiles = profiles; this.previews = previews; this.provider = provider; this.safety = safety; this.requests = requests; this.events = events; this.audit = audit;
        this.liveRisk = liveRisk; this.kiteProperties = kiteProperties; this.liveExecutionProperties = liveExecutionProperties;
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
    synchronized BrokerOrderResponse modifyProtective(String orderId, BrokerOrderRequest request) {
        requireLiveForProtective();
        ExecutionRequestEntity tracked = requireTracked(orderId);
        if (!tracked.protective()) throw new IllegalArgumentException("Only a protective order can be modified by this path");
        validateProtectiveRequest(request);
        return persist(provider.modify(orderId, request), "PROTECTIVE_ORDER_MODIFIED");
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
        for (ExecutionRequestEntity request : requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED"))) {
            responses.add(reconcile(request));
        }
        return java.util.List.copyOf(responses);
    }

    @Transactional(readOnly = true)
    public java.util.List<BrokerOrderResponse> trackedOrders() {
        return requests.findAll().stream().map(ExecutionRequestEntity::response).toList();
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
    BrokerOrderResponse reconcile(ExecutionRequestEntity request) {
        BrokerOrderResponse response = provider.status(request.orderId());
        BrokerOrderResponse persisted = persist(response, "BROKER_ORDER_STATUS_POLLED");
        if (!request.protective()) ensureProtectiveStop(request, response);
        return persisted;
    }

    private void ensureProtectiveStop(ExecutionRequestEntity parent, BrokerOrderResponse response) {
        if (response == null || response.filledQuantity() <= 0
                || (response.status() != BrokerOrderStatus.COMPLETE
                && response.status() != BrokerOrderStatus.PARTIALLY_FILLED
                && response.status() != BrokerOrderStatus.CANCELLED
                && response.status() != BrokerOrderStatus.UNKNOWN)) return;
        if (parent.stopPrice() == null || parent.stopPrice().signum() <= 0) {
            failProtectiveStop(parent.orderId(), "Parent entry has no positive protective stop");
            return;
        }
        ExecutionRequestEntity existing = requests.findByParentOrderIdAndProtectiveTrue(parent.orderId()).orElse(null);
        OrderSide exitSide = "BUY".equalsIgnoreCase(parent.side()) ? OrderSide.SELL : OrderSide.BUY;
        BrokerOrderRequest protective = new BrokerOrderRequest(parent.symbol(), exitSide, response.filledQuantity(),
                "SL-M", null, parent.stopPrice(), parent.product(), parent.exchange(), parent.validity(), parent.stopPrice());
        if (existing == null) {
            try {
                placeProtective("PROTECTIVE-" + parent.orderId(), protective, parent.orderId());
            } catch (RuntimeException error) {
                failProtectiveStop(parent.orderId(), error.getMessage());
            }
            return;
        }
        if (existing.quantity() != response.filledQuantity()
                && List.of("PENDING", "OPEN", "PARTIALLY_FILLED").contains(existing.status())) {
            try {
                modifyProtective(existing.orderId(), protective);
            } catch (RuntimeException error) {
                failProtectiveStop(parent.orderId(), error.getMessage());
            }
        }
        if (List.of("REJECTED", "CANCELLED", "UNKNOWN").contains(existing.status())) {
            failProtectiveStop(parent.orderId(), "Protective order is " + existing.status());
        }
    }

    private void failProtectiveStop(String parentOrderId, String reason) {
        String detail = reason == null || reason.isBlank() ? "unknown protective-stop failure" : reason;
        audit.record("PROTECTIVE_ORDER_FAILED", "BROKER_ORDER", parentOrderId, detail);
        try {
            provider.squareOffPositions();
        } catch (RuntimeException squareOffError) {
            audit.record("PROTECTIVE_FAILSAFE_SQUARE_OFF_FAILED", "TRADING_SAFETY", "GLOBAL", squareOffError.getMessage());
        }
        try {
            safety.enable("Protective stop failed for parent order " + parentOrderId + ": " + detail);
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
