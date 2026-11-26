package com.dileephl.marketscanner.execution;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_request")
class ExecutionRequestEntity {
    @Id private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String orderId;
    @Column(nullable = false, length = 64) private String symbol;
    @Column(nullable = false, length = 8) private String side;
    @Column(nullable = false) private long quantity;
    @Column(nullable = false) private String status;
    private long filledQuantity;
    @Column(precision = 19, scale = 4) private BigDecimal averagePrice;
    @Column(length = 16) private String orderType;
    @Column(precision = 19, scale = 4) private BigDecimal price;
    @Column(precision = 19, scale = 4) private BigDecimal triggerPrice;
    @Column(precision = 19, scale = 4) private BigDecimal stopPrice;
    @Column(length = 64) private String requestFingerprint;
    @Column(length = 16) private String product;
    @Column(length = 16) private String exchange;
    @Column(length = 16) private String validity;
    @Column(nullable = false) private boolean protective;
    @Column(name = "exit_role", nullable = false, length = 16) private String exitRole;
    @Column(length = 64) private String parentOrderId;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "exit_average_price", precision = 19, scale = 4) private BigDecimal exitAveragePrice;
    @Column(name = "exit_filled_quantity", nullable = false) private long exitFilledQuantity;
    @Column(name = "realised_pnl", precision = 19, scale = 4) private BigDecimal realisedPnl;
    @Column(name = "broker_realised_pnl", precision = 19, scale = 4) private BigDecimal brokerRealisedPnl;
    @Column(name = "pnl_source", length = 32) private String pnlSource;
    @Column(name = "exit_completed_at") private OffsetDateTime exitCompletedAt;
    @Column(name = "pnl_updated_at") private OffsetDateTime pnlUpdatedAt;
    protected ExecutionRequestEntity() { }
    static ExecutionRequestEntity from(String key, BrokerOrderRequest request, BrokerOrderResponse response) {
        ExecutionRequestEntity e = new ExecutionRequestEntity(); e.id = UUID.randomUUID(); e.idempotencyKey = key;
        e.orderId = response.orderId(); e.symbol = response.symbol() == null ? "" : response.symbol();
        e.side = response.side() == null ? "" : response.side().name(); e.quantity = response.quantity();
        e.status = response.status().name(); e.filledQuantity = response.filledQuantity(); e.averagePrice = response.averagePrice();
        e.orderType = request.orderType(); e.price = request.price(); e.triggerPrice = request.triggerPrice(); e.stopPrice = request.stopPrice();
        e.requestFingerprint = BrokerOrderFingerprint.of(request);
        e.product = request.product(); e.exchange = request.exchange(); e.validity = request.validity();
        e.protective = false; e.exitRole = "ENTRY"; e.parentOrderId = null;
        e.message = response.message() == null ? "" : response.message(); e.createdAt = response.observedAt(); e.updatedAt = response.observedAt();
        return e;
    }

    static ExecutionRequestEntity fromProtective(String key, BrokerOrderRequest request,
                                                 BrokerOrderResponse response, String parentOrderId) {
        ExecutionRequestEntity entity = from(key, request, response);
        entity.protective = true;
        entity.exitRole = "STOP";
        entity.parentOrderId = parentOrderId;
        return entity;
    }

    static ExecutionRequestEntity fromTarget(String key, BrokerOrderRequest request,
                                             BrokerOrderResponse response, String parentOrderId) {
        ExecutionRequestEntity entity = from(key, request, response);
        entity.protective = true;
        entity.exitRole = "TARGET";
        entity.parentOrderId = parentOrderId;
        return entity;
    }
    static ExecutionRequestEntity fromClose(String key, BrokerOrderRequest request,
                                            BrokerOrderResponse response, String parentOrderId) {
        var entity = fromProtective(key, request, response, parentOrderId);
        entity.exitRole = "CLOSE";
        return entity;
    }

    void apply(BrokerOrderResponse response) {
        status = response.status().name();
        filledQuantity = response.filledQuantity();
        averagePrice = response.averagePrice();
        if (response.symbol() != null) symbol = response.symbol();
        if (response.side() != null) side = response.side().name();
        if (response.quantity() > 0) quantity = response.quantity();
        message = response.message() == null ? "" : response.message();
        updatedAt = response.observedAt();
    }
    String orderId() { return orderId; }
    String symbol() { return symbol; }
    String side() { return side; }
    String status() { return status; }
    boolean protective() { return protective; }
    String exitRole() { return exitRole == null ? (protective ? "STOP" : "ENTRY") : exitRole; }
    String parentOrderId() { return parentOrderId; }
    long quantity() { return quantity; }
    long filledQuantity() { return filledQuantity; }
    BigDecimal stopPrice() { return stopPrice; }
    BigDecimal price() { return price; }
    String orderType() { return orderType; }
    String product() { return product; }
    String exchange() { return exchange; }
    String validity() { return validity; }
    OffsetDateTime updatedAt() { return updatedAt; }
    OffsetDateTime createdAt() { return createdAt; }
    BigDecimal averagePrice() { return averagePrice; }
    BigDecimal triggerPrice() { return triggerPrice; }
    BigDecimal exitAveragePrice() { return exitAveragePrice; }
    long exitFilledQuantity() { return exitFilledQuantity; }
    BigDecimal realisedPnl() { return realisedPnl; }
    BigDecimal brokerRealisedPnl() { return brokerRealisedPnl; }
    String pnlSource() { return pnlSource; }
    OffsetDateTime exitCompletedAt() { return exitCompletedAt; }
    void updateOutcome(ExecutionPnlCalculator.Result result, BigDecimal brokerPnl, String source, OffsetDateTime observedAt) {
        if (result != null) {
            exitFilledQuantity = result.matchedQuantity();
            exitAveragePrice = result.exitAveragePrice();
            realisedPnl = result.realisedPnl();
            exitCompletedAt = result.completedAt();
        }
        brokerRealisedPnl = brokerPnl;
        pnlSource = source;
        pnlUpdatedAt = observedAt;
        updatedAt = observedAt;
    }
    ExecutionOrderView view() {
        return new ExecutionOrderView(orderId, symbol, side, quantity, filledQuantity, averagePrice,
                status, orderType, price, triggerPrice, stopPrice, protective, exitRole(), parentOrderId,
                product, exchange, validity, message, createdAt, updatedAt,
                exitAveragePrice, exitFilledQuantity, realisedPnl, brokerRealisedPnl, pnlSource, exitCompletedAt);
    }
    ExecutionOrderHistoryView history(ExecutionRequestEntity stop, ExecutionRequestEntity target) {
        return new ExecutionOrderHistoryView(orderId, symbol, side, quantity, filledQuantity, status, orderType,
                price, averagePrice,
                stop == null ? null : stop.orderId(), stop == null ? null : stop.status(),
                stop == null ? 0 : stop.filledQuantity(), stop == null ? null : stop.triggerPrice(),
                stop == null ? null : stop.averagePrice(),
                target == null ? null : target.orderId(), target == null ? null : target.status(),
                target == null ? 0 : target.filledQuantity(), target == null ? null : target.price(),
                target == null ? null : target.averagePrice(), exitFilledQuantity, exitAveragePrice,
                realisedPnl, brokerRealisedPnl, pnlSource, exitCompletedAt, message, createdAt, updatedAt);
    }
    boolean matches(BrokerOrderRequest request) { return requestFingerprint == null || requestFingerprint.equals(BrokerOrderFingerprint.of(request)); }
    BrokerOrderResponse response() { return new BrokerOrderResponse(orderId, symbol.isBlank() ? null : symbol,
            side.isBlank() ? null : OrderSide.valueOf(side), quantity, filledQuantity, averagePrice,
            BrokerOrderStatus.valueOf(status), message, createdAt); }
}
