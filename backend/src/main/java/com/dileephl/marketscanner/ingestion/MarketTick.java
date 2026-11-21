package com.dileephl.marketscanner.ingestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A normalised trade tick. Quantity is the quantity for this update, not cumulative day volume. */
public record MarketTick(
        String symbol,
        OffsetDateTime timestamp,
        BigDecimal price,
        long quantity) {
}
