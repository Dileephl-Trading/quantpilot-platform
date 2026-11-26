package com.dileephl.marketscanner.execution;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider;
import org.springframework.stereotype.Service;

@Service
public class BrokerReconciliationService {
    private final BrokerObservations provider;
    private final ExecutionRequestRepository requests;
    private final ExecutionTradeRepository trades;
    private final AuditTrailService audit;
    private final SubmissionJournal journal;
    private final ExecutionOrderEventRepository events;

    public BrokerReconciliationService(BrokerObservations provider, ExecutionRequestRepository requests,
            ExecutionTradeRepository trades, AuditTrailService audit, SubmissionJournal journal,
            ExecutionOrderEventRepository events) {
        this.provider = provider;
        this.requests = requests;
        this.trades = trades;
        this.audit = audit;
        this.journal = journal;
        this.events = events;
    }

    public record Reconciliation(int checked, List<String> issues) {}

    public synchronized Reconciliation reconcileTrackedOrders() {
        var book = provider.orderBook();
        var positions = provider.positions();
        var issues = new ArrayList<String>();
        for (var intent : journal.pending()) {
            var matches = book.stream().filter(row -> intent.tag().equals(row.tag())).toList();
            if (matches.size() != 1) {
                issues.add("Unconfirmed submission " + intent.tag());
                continue;
            }
            var state = matches.get(0).state();
            var order = intent.parent() == null ? ExecutionRequestEntity.from(intent.key(), intent.request(), state)
                    : intent.key().startsWith("CLOSE-") ? ExecutionRequestEntity.fromTarget(intent.key(), intent.request(), state, intent.parent())
                    : ExecutionRequestEntity.fromProtective(intent.key(), intent.request(), state, intent.parent());
            requests.save(order);
        }
        var rows = requests.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(BrokerOrderExecutionService.sessionStart());
        int checked = 0;
        for (var order : rows) {
            var found = book.stream().filter(row -> row.id().equals(order.orderId())).findFirst();
            if (found.isEmpty()) {
                issues.add(order.orderId() + " missing from broker order book");
                continue;
            }
            var state = found.get().state();
            if (!order.response().status().equals(state.status()) || order.filledQuantity() != state.filledQuantity()
                    || !java.util.Objects.equals(order.averagePrice(), state.averagePrice()))
                persist(state, "BROKER_ORDER_STATUS_POLLED");
            checked++;
        }
        for (var order : rows) {
            if (order.protective()) continue;
            try {
                reconcileOutcome(order.orderId(), book);
                var updated = requests.findByOrderId(order.orderId()).orElseThrow();
                if (updated.exitFilledQuantity() < updated.filledQuantity()
                        && positions.stream().noneMatch(p -> BrokerOrderExecutionService.sameInstrument(updated, p.symbol(), p.exchange(), p.product())))
                    issues.add(order.orderId() + ": flat at broker, exit allocation incomplete");
            }
            catch (RuntimeException error) { issues.add(order.orderId() + ": " + error.getMessage()); }
        }
        return new Reconciliation(checked, List.copyOf(issues));
    }

    /**
     * Pulls broker fills and the day position snapshot after an entry/exit
     * changes.  Local fill P&L is always retained; the broker value is shown
     * separately because Kite may aggregate several trades for one symbol.
     */
    private void reconcileOutcome(String parentOrderId, List<KiteOrderExecutionProvider.BookOrder> book) {
        if (parentOrderId == null || parentOrderId.isBlank()) return;
        ExecutionRequestEntity parent = requests.findByOrderId(parentOrderId).orElse(null);
        if (parent == null || parent.protective()) return;
        ExecutionRequestEntity stop = requests.findByParentOrderIdAndExitRole(parentOrderId, "STOP").orElse(null);
        ExecutionRequestEntity target = requests.findByParentOrderIdAndExitRole(parentOrderId, "TARGET").orElse(null);
        if (parent.filledQuantity() == 0) return;

        try {
            upsertTrades(provider.trades(parent.orderId()));
            if (stop != null) upsertTrades(provider.trades(stop.orderId()));
            if (target != null) upsertTrades(provider.trades(target.orderId()));

            List<BrokerTrade> entryTrades = trades.findByOrderIdOrderByFillTimestampAsc(parent.orderId())
                    .stream().map(ExecutionTradeEntity::view).toList();
            List<BrokerTrade> exitTrades = new ArrayList<>();
            if (stop != null) exitTrades.addAll(trades.findByOrderIdOrderByFillTimestampAsc(stop.orderId()).stream().map(ExecutionTradeEntity::view).toList());
            if (target != null) exitTrades.addAll(trades.findByOrderIdOrderByFillTimestampAsc(target.orderId()).stream().map(ExecutionTradeEntity::view).toList());
            if (entryTrades.stream().mapToLong(BrokerTrade::quantity).sum() != parent.filledQuantity()) throw new IllegalStateException("Entry fills unavailable");
            var sameInstrument = book.stream().filter(row -> parent.symbol().equalsIgnoreCase(row.state().symbol())
                    && BrokerOrderExecutionService.sameInstrument(parent, row.state().symbol(), row.exchange(), row.product())).toList();
            var external = sameInstrument.stream().filter(row -> row.state().filledQuantity() > 0
                    && !row.state().side().name().equals(parent.side()) && requests.findByOrderId(row.id()).isEmpty()).toList();
            if (!external.isEmpty()) {
                // Only allocate an external exit when the entire session has one entry and
                // every fill belongs to the same, temporally ordered round trip.
                var entries = sameInstrument.stream().filter(row -> row.state().filledQuantity() > 0
                        && row.state().side().name().equals(parent.side())).toList();
                if (entries.size() != 1 || !entries.get(0).id().equals(parentOrderId))
                    throw new IllegalStateException("External fills need allocation");
                List<BrokerTrade> extra = new ArrayList<>();
                for (var row : external) extra.addAll(provider.trades(row.id()));
                long exits = exitTrades.stream().mapToLong(BrokerTrade::quantity).sum() + extra.stream().mapToLong(BrokerTrade::quantity).sum();
                if (exits > parent.filledQuantity() || extra.stream().anyMatch(t -> !t.fillTimestamp().isAfter(entryTrades.get(entryTrades.size()-1).fillTimestamp())))
                    throw new IllegalStateException("External fills need allocation");
                upsertTrades(extra);
                exitTrades.addAll(extra);
            }
            if (exitTrades.isEmpty()) {
                if ((stop != null && stop.filledQuantity() > 0) || (target != null && target.filledQuantity() > 0))
                    throw new IllegalStateException("Exit fills unavailable");
                return;
            }
            exitTrades.sort(java.util.Comparator.comparing(BrokerTrade::fillTimestamp));

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
                audit.record("BROKER_DAY_PNL_UNAVAILABLE", "BROKER_ORDER", parent.orderId(), brokerError.getMessage());
            }
            OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30));
            parent.updateOutcome(result, brokerPnl, source, observedAt);
            requests.save(parent);
            audit.record("BROKER_ORDER_PNL_RECONCILED", "BROKER_ORDER", parent.orderId(),
                    "exitQuantity=" + result.matchedQuantity() + ", exitAveragePrice=" + result.exitAveragePrice()
                            + ", realisedPnl=" + result.realisedPnl() + ", source=" + source);
        } catch (RuntimeException error) {
            // P&L display is best effort; it must never stop protective-order polling.
            throw error;
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
