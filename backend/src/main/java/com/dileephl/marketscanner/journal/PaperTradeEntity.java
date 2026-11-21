package com.dileephl.marketscanner.journal;

import com.dileephl.marketscanner.execution.OrderSide;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "paper_trade")
@Table(name = "paper_trade")
class PaperTradeEntity {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TradeStatus status;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime filledAt;
    private OffsetDateTime closedAt;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal intendedEntryPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal stopPrice;

    @Column(nullable = false)
    private long intendedQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal actualEntryPrice;

    /** Positive values indicate adverse entry slippage; negative values indicate price improvement. */
    @Column(precision = 19, scale = 4)
    private BigDecimal slippageAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal slippagePerUnit;

    private Long filledQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal actualExitPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal grossPnl;

    @Column(precision = 19, scale = 4)
    private BigDecimal brokerage;

    @Column(precision = 19, scale = 4)
    private BigDecimal netPnl;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    @Column(length = 80)
    private String strategy;

    @Column(length = 500)
    private String notes;

    protected PaperTradeEntity() { }

    static PaperTradeEntity intended(UUID id, String symbol, OrderSide side, BigDecimal intendedEntryPrice,
                                     BigDecimal stopPrice, long intendedQuantity, LocalDate tradeDate,
                                     OffsetDateTime createdAt, String strategy, String notes) {
        PaperTradeEntity trade = new PaperTradeEntity();
        trade.id = id;
        trade.symbol = symbol;
        trade.side = side;
        trade.status = TradeStatus.INTENDED;
        trade.tradeDate = tradeDate;
        trade.createdAt = createdAt;
        trade.intendedEntryPrice = intendedEntryPrice;
        trade.stopPrice = stopPrice;
        trade.intendedQuantity = intendedQuantity;
        trade.strategy = strategy;
        trade.notes = notes;
        return trade;
    }

    void recordFill(BigDecimal entryPrice, long quantity, OffsetDateTime at) {
        if (status != TradeStatus.INTENDED) throw new IllegalStateException("Only an intended trade can be marked filled");
        actualEntryPrice = entryPrice;
        slippagePerUnit = side == OrderSide.BUY
                ? entryPrice.subtract(intendedEntryPrice)
                : intendedEntryPrice.subtract(entryPrice);
        slippageAmount = slippagePerUnit.multiply(BigDecimal.valueOf(quantity));
        filledQuantity = quantity;
        filledAt = at;
        status = TradeStatus.OPEN;
    }

    void recordExit(BigDecimal exitPrice, BigDecimal charges, ExitReason reason, OffsetDateTime at) {
        if (status != TradeStatus.OPEN) throw new IllegalStateException("Only an open trade can be closed");
        actualExitPrice = exitPrice;
        brokerage = charges;
        exitReason = reason;
        closedAt = at;
        BigDecimal priceMove = side == OrderSide.BUY ? exitPrice.subtract(actualEntryPrice)
                : actualEntryPrice.subtract(exitPrice);
        grossPnl = priceMove.multiply(BigDecimal.valueOf(filledQuantity));
        netPnl = grossPnl.subtract(charges);
        status = TradeStatus.CLOSED;
    }

    void cancel() {
        if (status != TradeStatus.INTENDED) throw new IllegalStateException("Only an intended trade can be cancelled");
        status = TradeStatus.CANCELLED;
    }

    UUID id() { return id; }
    String symbol() { return symbol; }
    OrderSide side() { return side; }
    TradeStatus status() { return status; }
    LocalDate tradeDate() { return tradeDate; }
    OffsetDateTime createdAt() { return createdAt; }
    OffsetDateTime filledAt() { return filledAt; }
    OffsetDateTime closedAt() { return closedAt; }
    BigDecimal intendedEntryPrice() { return intendedEntryPrice; }
    BigDecimal stopPrice() { return stopPrice; }
    long intendedQuantity() { return intendedQuantity; }
    BigDecimal actualEntryPrice() { return actualEntryPrice; }
    BigDecimal slippageAmount() { return slippageAmount; }
    BigDecimal slippagePerUnit() { return slippagePerUnit; }
    Long filledQuantity() { return filledQuantity; }
    BigDecimal actualExitPrice() { return actualExitPrice; }
    BigDecimal grossPnl() { return grossPnl; }
    BigDecimal brokerage() { return brokerage; }
    BigDecimal netPnl() { return netPnl; }
    ExitReason exitReason() { return exitReason; }
    String strategy() { return strategy; }
    String notes() { return notes; }
}
