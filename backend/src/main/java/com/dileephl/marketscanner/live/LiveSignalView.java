package com.dileephl.marketscanner.live;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LiveSignalView(String symbol, long instrumentToken, OffsetDateTime candleStart, String type,
                             BigDecimal referencePrice, BigDecimal vwap, BigDecimal invalidationReference,
                             BigDecimal openingRangeHigh, BigDecimal openingRangeLow, BigDecimal averageVolume,
                             BigDecimal volumeThreshold, String reasons, OffsetDateTime generatedAt) {
    static LiveSignalView from(LiveSignalEntity signal) {
        return new LiveSignalView(signal.getSymbol(), signal.getInstrumentToken(), signal.getCandleStart(), signal.getSignalType(),
                signal.getReferencePrice(), signal.getVwap(), signal.getInvalidationReference(), signal.getOpeningRangeHigh(),
                signal.getOpeningRangeLow(), signal.getAverageVolume(), signal.getVolumeThreshold(), signal.getReasons(), signal.getGeneratedAt());
    }
}
