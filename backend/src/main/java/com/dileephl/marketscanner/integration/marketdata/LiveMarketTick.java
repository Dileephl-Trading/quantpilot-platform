package com.dileephl.marketscanner.integration.marketdata;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A normalised, non-cumulative trade tick from a provider live feed. */
public record LiveMarketTick(long instrumentToken, OffsetDateTime timestamp, BigDecimal price, long quantity) { }
