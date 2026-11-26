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
    private final BrokerReconciliationService reconciliation;
    private final SubmissionJournal journal;

    public BrokerOrderExecutionService(TradingProfileService profiles, ExecutionPreviewService previews, KiteOrderExecutionProvider provider, SafetyService safety,
                                       ExecutionRequestRepository requests, ExecutionOrderEventRepository events, AuditTrailService audit,
                                       LiveOrderRiskService liveRisk, KiteConnectProperties kiteProperties,
                                       LiveExecutionProperties liveExecutionProperties,
                                       AutomatedExecutionProperties automationProperties,
                                       MarketInstrumentRepository instruments,
                                       BrokerReconciliationService reconciliation, SubmissionJournal journal) {
        this.profiles = profiles; this.previews = previews; this.provider = provider; this.safety = safety; this.requests = requests; this.events = events; this.audit = audit;
        this.liveRisk = liveRisk; this.kiteProperties = kiteProperties;
        this.liveExecutionProperties = liveExecutionProperties;
        this.automationProperties = automationProperties;
        this.instruments = instruments;
        this.reconciliation = reconciliation;
        this.journal = journal;
    }

    @Transactional
    public synchronized BrokerOrderResponse place(String idempotencyKey, BrokerOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required");
        validateRequest(request);
        String symbol = request.symbol().trim().toUpperCase();
        String key = idempotencyKey.trim();
        return requests.findByIdempotencyKey(key).map(existing -> {
            if (!existing.matches(request)) {
                throw new IllegalArgumentException("Idempotency-Key was already used for a different order payload");
            }
            audit.record("BROKER_ORDER_IDEMPOTENT_REPLAY", "EXECUTION_REQUEST", existing.response().orderId(), "key=" + key);
            return existing.response();
        }).orElseGet(() -> {
            requireLive();
            validateRisk(request);
            if (journal.hasUnresolvedOtherThan(key)) throw new IllegalStateException("Unconfirmed broker submission requires reconciliation");
            verifyProtection();
            LiveOrderRiskService.RiskCheck risk = liveRisk.verify(request, java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            if (!risk.allowed()) throw new IllegalStateException("Live order blocked by safety checks: " + String.join("; ", risk.reasons()));
            BrokerOrderResponse response = submit(key, request, null);
            requests.save(ExecutionRequestEntity.from(key, request, response));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("BROKER_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(), "symbol=" + request.symbol() + ", quantity=" + request.quantity());
            return response;
        });
    }

    private BrokerOrderResponse submit(String key, BrokerOrderRequest request, String parent) {
        SubmissionJournal.Reservation intent = journal.reserve(key, request, parent);
        if (intent.fresh()) return provider.place(request, intent.tag());
        var matches = provider.orderBook().stream().filter(row -> intent.tag().equals(row.tag())).toList();
        if (matches.size() == 1) return matches.get(0).state();
        throw new IllegalStateException("Previous submission requires broker confirmation: " + intent.tag());
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
            BrokerOrderResponse response = submit(key, request, parentOrderId);
            requests.save(ExecutionRequestEntity.fromProtective(key, request, response, parentOrderId));
            events.save(ExecutionOrderEventEntity.from(response));
            audit.record("PROTECTIVE_ORDER_SUBMITTED", "BROKER_ORDER", response.orderId(),
                    "parentOrderId=" + parentOrderId + ", symbol=" + request.symbol() + ", quantity=" + request.quantity());
            return response;
        });
    }


    public java.util.List<BrokerPosition> currentPositions() {
        requireLiveForProtective();
        return provider.positions();
    }
    @Transactional
    public BrokerOrderResponse status(String orderId) {
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
    public java.util.List<BrokerOrderResponse> cancelTrackedOpenOrders() {
        requireLive();
        java.util.List<BrokerOrderResponse> responses = new java.util.ArrayList<>();
        for (ExecutionRequestEntity request : today()) {
            if (!working(request.response()) || request.protective()) continue;
            responses.add(cancel(request.orderId()));
        }
        return java.util.List.copyOf(responses);
    }

    /** Emergency path: cancellation remains allowed after the kill switch is enabled. */
    @Transactional
    public java.util.List<BrokerOrderResponse> cancelTrackedOpenOrdersForSafety() {
        if (profiles.current().mode() != TradingMode.LIVE) return java.util.List.of();
        java.util.List<BrokerOrderResponse> responses = new java.util.ArrayList<>();
        for (ExecutionRequestEntity request : today()) {
            if (request.protective() || !working(request.response())) continue;
            try {
                responses.add(persist(provider.cancel(request.orderId()), "BROKER_ORDER_EMERGENCY_CANCEL_REQUESTED"));
            } catch (RuntimeException error) {
                audit.record("BROKER_ORDER_EMERGENCY_CANCEL_FAILED", "BROKER_ORDER", request.orderId(), error.getMessage());
            }
        }
        return java.util.List.copyOf(responses);
    }

    public BrokerReconciliationService.Reconciliation reconcileTrackedOrders() {
        return reconciliation.reconcileTrackedOrders();
    }

    static OffsetDateTime sessionStart() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
                .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
    }

    private List<ExecutionRequestEntity> today() {
        return requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(sessionStart());
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

    private static boolean working(BrokerOrderResponse order) {
        return switch (order.status()) {
            case COMPLETE, CANCELLED, REJECTED -> false;
            default -> true;
        };
    }

    static boolean sameInstrument(ExecutionRequestEntity entry, String symbol, String exchange, String product) {
        return entry.symbol().equalsIgnoreCase(symbol)
                && java.util.Objects.toString(entry.exchange(), "NSE").equalsIgnoreCase(exchange)
                && java.util.Objects.toString(entry.product(), "MIS").equalsIgnoreCase(product);
    }

    void verifyProtection() {
        var positions = provider.positions();
        var book = provider.orderBook();
        for (var row : today()) {
            if (row.protective()) continue;
            var outstanding = book.stream().filter(o -> requests.findByOrderId(o.id())
                    .map(child -> row.orderId().equals(child.parentOrderId())).orElse(false) && working(o.state())).toList();
            long net = positions.stream().filter(p -> sameInstrument(row, p.symbol(), p.exchange(), p.product()))
                    .mapToLong(BrokerPosition::quantity).sum();
            if (net == 0 && outstanding.isEmpty()) continue;
            if (row.filledQuantity() <= row.exitFilledQuantity() && outstanding.isEmpty()) continue;
            if (outstanding.size() != 1 || !outstanding.get(0).orderType().startsWith("SL")
                    || outstanding.get(0).state().status() == BrokerOrderStatus.UNKNOWN
                    || outstanding.get(0).state().side() != (net > 0 ? OrderSide.SELL : OrderSide.BUY)
                    || outstanding.get(0).state().quantity() - outstanding.get(0).state().filledQuantity() != Math.abs(net))
                throw new IllegalStateException("Review outstanding protection for " + row.symbol());
        }
    }

    // Only the supervisor may manage protection. Keep one executable exit at the
    // exchange. A target or session close converts that same stop to a market exit.
    public synchronized void manageProtection(boolean sessionClose) {
        requireLiveForProtective();
        for (var entry : today()) {
            if (entry.protective() || entry.filledQuantity() == 0) continue;
            try { manageEntry(entry, sessionClose); }
            catch (RuntimeException error) {
                audit.record("PROTECTION_REVIEW_REQUIRED", "BROKER_ORDER", entry.orderId(), error.getMessage());
            }
        }
    }

    private void manageEntry(ExecutionRequestEntity entry, boolean sessionClose) {
        var book = provider.orderBook().stream()
                .filter(o -> sameInstrument(entry, o.state().symbol(), o.exchange(), o.product())).toList();
        var parent = book.stream().filter(o -> o.id().equals(entry.orderId())).findFirst().orElse(null);
        if (parent == null) return;
        var stop = requests.findByParentOrderIdAndExitRole(entry.orderId(), "STOP").orElse(null);
        var target = requests.findByParentOrderIdAndExitRole(entry.orderId(), "TARGET").orElse(null);
        var stopRow = book.stream().filter(o -> stop != null && stop.orderId().equals(o.id())).findFirst().orElse(null);
        var targetRow = book.stream().filter(o -> target != null && target.orderId().equals(o.id())).findFirst().orElse(null);
        if (stop != null && stopRow == null || target != null && targetRow == null) return;
        if (targetRow != null && working(targetRow.state())) {
            if ("LIMIT".equals(targetRow.orderType()) && stopRow != null && working(stopRow.state()))
                provider.cancel(targetRow.id()); // migrate legacy dual exits; await broker confirmation
            return;
        }
        var pos = provider.positions().stream().filter(p -> sameInstrument(entry, p.symbol(), p.exchange(), p.product())).findFirst();
        long net = pos.map(BrokerPosition::quantity).orElse(0L);
        if (net == 0) {
            if (stopRow != null && working(stopRow.state())) provider.cancel(stopRow.id());
            return;
        }
        long ownedRemaining = parent.state().filledQuantity()
                - (stopRow == null ? 0 : stopRow.state().filledQuantity())
                - (targetRow == null ? 0 : targetRow.state().filledQuantity());
        long remaining = Math.abs(net);
        // Shared or externally edited positions need explicit attribution.
        if (ownedRemaining != remaining || (net > 0) != "BUY".equals(entry.side())
                || book.stream().anyMatch(o -> !o.id().equals(entry.orderId())
                    && (stop == null || !o.id().equals(stop.orderId()))
                    && (target == null || !o.id().equals(target.orderId()))
                    && (working(o.state()) || o.state().filledQuantity() > 0)
                    && requests.findByOrderId(o.id()).map(r -> !completedLifecycle(r)).orElse(true))) return;
        OrderSide side = net > 0 ? OrderSide.SELL : OrderSide.BUY;
        if (stopRow != null && working(stopRow.state()) && ("MARKET".equals(stopRow.orderType())
                || stopRow.state().status() == BrokerOrderStatus.UNKNOWN)) return;
        boolean close = sessionClose && "MIS".equalsIgnoreCase(entry.product());
        if (!close && stopRow != null && working(stopRow.state())) {
            BigDecimal limit = targetRequest(entry, parent.state(), side).price();
            int comparison = provider.lastPrice(entry.exchange(), entry.symbol()).compareTo(limit);
            close = net > 0 ? comparison >= 0 : comparison <= 0;
        }
        if (stopRow != null && working(stopRow.state())) {
            long total = remaining + stopRow.state().filledQuantity();
            if (!close && total == stopRow.state().quantity()) return;
            var change = new BrokerOrderRequest(entry.symbol(), side, total, close ? "MARKET" : "SL-M",
                    null, close ? null : entry.stopPrice(), entry.product(), entry.exchange(), "DAY", entry.stopPrice());
            provider.modify(stopRow.id(), change);
        } else if (close && target == null) {
            var exit = new BrokerOrderRequest(entry.symbol(), side, remaining, "MARKET", null, null,
                    entry.product(), entry.exchange(), "DAY", null);
            String key = "CLOSE-" + entry.orderId();
            var result = submit(key, exit, entry.orderId());
            requests.save(ExecutionRequestEntity.fromTarget(key, exit, result, entry.orderId()));
        } else if (stop == null) {
            placeProtective("PROTECTIVE-" + entry.orderId(), new BrokerOrderRequest(entry.symbol(), side, remaining,
                    "SL-M", null, entry.stopPrice(), entry.product(), entry.exchange(), "DAY", entry.stopPrice()), entry.orderId());
        }
    }

    private boolean completedLifecycle(ExecutionRequestEntity order) {
        var parent = order.protective() ? requests.findByOrderId(order.parentOrderId()).orElse(null) : order;
        return parent != null && parent.filledQuantity() > 0 && parent.filledQuantity() == parent.exitFilledQuantity()
                && !working(order.response());
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
