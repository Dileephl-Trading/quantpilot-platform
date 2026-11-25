package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;

/**
 * The broker's current net position for one instrument.  This is deliberately
 * provider-neutral so the live risk gate can check positions before submitting
 * a new entry instead of relying only on locally tracked orders.
 */
public record BrokerPosition(String symbol, String exchange, String product,
                             long quantity, BigDecimal averagePrice) {
    public boolean open() {
        return quantity != 0 && symbol != null && !symbol.isBlank();
    }
}
