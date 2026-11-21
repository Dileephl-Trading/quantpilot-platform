package com.dileephl.marketscanner.integration.marketdata;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Map;

public record LiveFeedStatus(String provider, String state, int subscribedInstruments, OffsetDateTime connectedAt,
                             OffsetDateTime lastTickAt, Map<Long, BigDecimal> latestPrices, String lastError) { }
