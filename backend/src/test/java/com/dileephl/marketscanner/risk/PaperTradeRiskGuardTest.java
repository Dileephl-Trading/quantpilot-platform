package com.dileephl.marketscanner.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PaperTradeRiskGuardTest {
    @Test
    void blocksTradeWhenRiskToStopIsTooLarge() {
        var request = new PaperTradeRiskGuard.RiskRequest(
                new BigDecimal("100000"), new BigDecimal("500"), new BigDecimal("490"), 100,
                BigDecimal.ZERO, 0, 3, new BigDecimal("0.5"), new BigDecimal("1"));

        assertFalse(new PaperTradeRiskGuard().evaluate(request).allowed());
    }

    @Test
    void blocksTradeWhenQuantityExceedsCapitalAllocation() {
        var request = new PaperTradeRiskGuard.RiskRequest(
                new BigDecimal("50000"), new BigDecimal("1000"), new BigDecimal("999"), 51,
                BigDecimal.ZERO, 0, 3, new BigDecimal("5"), new BigDecimal("10"));

        assertFalse(new PaperTradeRiskGuard().evaluate(request).allowed());
    }
}
