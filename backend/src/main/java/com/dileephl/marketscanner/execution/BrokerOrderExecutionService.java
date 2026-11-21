package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider;
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
    private final LiveExecutionProperties liveExecutionProperties;

    public BrokerOrderExecutionService(TradingProfileService profiles, ExecutionPreviewService previews, KiteOrderExecutionProvider provider, SafetyService safety,
                                       ExecutionRequestRepository requests, ExecutionOrderEventRepository events, AuditTrailService audit,
                                       LiveExecutionProperties liveExecutionProperties) {
        this.profiles = profiles; this.previews = previews; this.provider = provider; this.safety = safety; this.requests = requests; this.events = events; this.audit = audit; this.liveExecutionProperties = liveExecutionProperties;
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
            BrokerOrderResponse response = provider.place(request);
            requests.save(ExecutionRequestEntity.from(key, request, response));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("BROKER_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(), "symbol=" + request.symbol() + ", quantity=" + request.quantity());
            return response;
        });
    }
    @Transactional
    public BrokerOrderResponse status(String orderId) {
        requireLive();
        ExecutionRequestEntity request = requireTracked(orderId);
        return reconcile(request);
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
        if (!liveExecutionProperties.isEnabled()) throw new IllegalStateException("Live execution master gate is disabled");
        if (profiles.current().mode() != TradingMode.LIVE) {
            throw new IllegalStateException("Broker order routing requires explicit LIVE trading mode; current mode is PAPER/DRY_RUN");
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
        OrderPreview preview = previews.preview(new ExecutionPreviewService.OrderRequest(
                request.symbol(), request.side(), request.price(), request.stopPrice(), request.quantity()));
        if (!preview.allowed()) {
            throw new IllegalStateException("Order blocked by risk controls: " + String.join("; ", preview.reasons()));
        }
    }

    private ExecutionRequestEntity requireTracked(String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        return requests.findByOrderId(orderId).orElseThrow(() -> new IllegalArgumentException("Order is not tracked by this application"));
    }

    @Transactional
    BrokerOrderResponse reconcile(ExecutionRequestEntity request) {
        BrokerOrderResponse response = provider.status(request.orderId());
        BrokerOrderResponse persisted = persist(response, "BROKER_ORDER_STATUS_POLLED");
        if (!request.protective()
                && (persisted.status() == BrokerOrderStatus.PARTIALLY_FILLED
                    || persisted.status() == BrokerOrderStatus.COMPLETE)) {
            ensureProtectiveStop(request, persisted);
        }
        return persisted;
    }

    /**
     * Creates one broker-side stop-market order for the filled quantity and increases its
     * quantity when an entry order receives additional fills. A missing/rejected stop is
     * treated as an uncertain broker state by the reconciliation scheduler.
     */
    private void ensureProtectiveStop(ExecutionRequestEntity entry, BrokerOrderResponse entryResponse) {
        long filled = entryResponse.filledQuantity();
        if (filled <= 0 || entry.stopPrice() == null || entry.stopPrice().signum() <= 0) return;

        ExecutionRequestEntity stop = requests.findByParentOrderId(entry.orderId()).stream().findFirst().orElse(null);
        OrderSide stopSide = entryResponse.side() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        BrokerOrderRequest stopRequest = new BrokerOrderRequest(
                entry.symbol(), stopSide, filled, "SL-M", null, entry.stopPrice(),
                entry.product(), entry.exchange(), entry.validity(), entry.stopPrice());

        if (stop == null) {
            BrokerOrderResponse response = provider.place(stopRequest);
            ExecutionRequestEntity tracked = ExecutionRequestEntity.from(
                    entry.orderId() + ":STOP", stopRequest, response, entry.orderId(), true);
            requests.save(tracked);
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("PROTECTIVE_STOP_SUBMITTED", "BROKER_ORDER", response.orderId(),
                    "parentOrderId=" + entry.orderId() + ", quantity=" + filled
                            + ", triggerPrice=" + entry.stopPrice());
            return;
        }

        if (stop.status().equals("OPEN") || stop.status().equals("PENDING")
                || stop.status().equals("PARTIALLY_FILLED")) {
            if (stop.quantity() < filled) {
                BrokerOrderResponse response = provider.modify(stop.orderId(), stopRequest);
                persist(response, "PROTECTIVE_STOP_MODIFIED");
            }
            return;
        }

        if (stop.status().equals("COMPLETE") && entryResponse.filledQuantity() <= stop.quantity()) return;
        throw new IllegalStateException("Protective stop is not active for filled entry " + entry.orderId());
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
