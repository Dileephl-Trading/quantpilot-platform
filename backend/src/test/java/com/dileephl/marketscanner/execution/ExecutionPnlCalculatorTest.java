package com.dileephl.marketscanner.execution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPnlCalculatorTest {
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-11-26T10:00:00+05:30");

    @Test
    void calculatesLongProfitFromMatchedFills() {
        var result = ExecutionPnlCalculator.calculate(OrderSide.BUY,
                List.of(fill("e1", "BUY", 2, "100.00")),
                List.of(fill("x1", "SELL", 2, "103.50")));

        assertThat(result.matchedQuantity()).isEqualTo(2);
        assertThat(result.entryAveragePrice()).isEqualByComparingTo("100");
        assertThat(result.exitAveragePrice()).isEqualByComparingTo("103.5");
        assertThat(result.realisedPnl()).isEqualByComparingTo("7.0000");
    }

    @Test
    void calculatesShortLossAndUsesOnlyMatchedQuantity() {
        var result = ExecutionPnlCalculator.calculate(OrderSide.SELL,
                List.of(fill("e1", "SELL", 3, "200.00")),
                List.of(fill("x1", "BUY", 2, "205.00")));

        assertThat(result.matchedQuantity()).isEqualTo(2);
        assertThat(result.realisedPnl()).isEqualByComparingTo("-10.0000");
    }

    private BrokerTrade fill(String id, String side, long quantity, String price) {
        return new BrokerTrade(id, "order-1", "SBIN", OrderSide.valueOf(side), quantity,
                new BigDecimal(price), TIME);
    }
}
