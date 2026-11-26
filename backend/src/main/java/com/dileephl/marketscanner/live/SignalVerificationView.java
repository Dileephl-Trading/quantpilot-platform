package com.dileephl.marketscanner.live;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SignalVerificationView(
        UUID id,
        String symbol,
        long instrumentToken,
        OffsetDateTime candleStart,
        String signalType,
        String direction,
        BigDecimal referencePrice,
        BigDecimal vwap,
        BigDecimal invalidationReference,
        BigDecimal openingRangeHigh,
        BigDecimal openingRangeLow,
        BigDecimal averageVolume,
        BigDecimal volumeThreshold,
        String decision,
        String analysis,
        String orderId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
