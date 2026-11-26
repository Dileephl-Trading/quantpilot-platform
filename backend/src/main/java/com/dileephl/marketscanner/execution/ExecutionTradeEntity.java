package com.dileephl.marketscanner.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_trade")
class ExecutionTradeEntity {
    @Id private UUID id;
    @Column(name = "trade_id", nullable = false, unique = true, length = 64) private String tradeId;
    @Column(name = "order_id", nullable = false, length = 64) private String orderId;
    @Column(nullable = false, length = 64) private String symbol;
    @Column(nullable = false, length = 8) private String side;
    @Column(nullable = false) private long quantity;
    @Column(name = "average_price", nullable = false, precision = 19, scale = 4) private BigDecimal averagePrice;
    @Column(name = "fill_timestamp", nullable = false) private OffsetDateTime fillTimestamp;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected ExecutionTradeEntity() { }

    static ExecutionTradeEntity from(BrokerTrade trade) {
        ExecutionTradeEntity entity = new ExecutionTradeEntity();
        entity.id = UUID.randomUUID();
        entity.tradeId = trade.tradeId();
        entity.orderId = trade.orderId();
        entity.symbol = trade.symbol() == null ? "" : trade.symbol();
        entity.side = trade.side() == null ? "" : trade.side().name();
        entity.quantity = trade.quantity();
        entity.averagePrice = trade.averagePrice();
        entity.fillTimestamp = trade.fillTimestamp();
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    void apply(BrokerTrade trade) {
        orderId = trade.orderId();
        symbol = trade.symbol() == null ? symbol : trade.symbol();
        side = trade.side() == null ? side : trade.side().name();
        quantity = trade.quantity();
        averagePrice = trade.averagePrice();
        fillTimestamp = trade.fillTimestamp();
    }

    String tradeId() { return tradeId; }
    String orderId() { return orderId; }
    String symbol() { return symbol; }
    OrderSide side() { return side == null || side.isBlank() ? null : OrderSide.valueOf(side); }
    long quantity() { return quantity; }
    BigDecimal averagePrice() { return averagePrice; }
    OffsetDateTime fillTimestamp() { return fillTimestamp; }

    BrokerTrade view() {
        return new BrokerTrade(tradeId, orderId, symbol, side(), quantity, averagePrice, fillTimestamp);
    }
}
