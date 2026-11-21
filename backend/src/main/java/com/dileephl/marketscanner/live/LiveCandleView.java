package com.dileephl.marketscanner.live;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LiveCandleView(String symbol, long instrumentToken, OffsetDateTime candleStart,
                             BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                             long volume, String evaluationType, BigDecimal referencePrice,
                             BigDecimal vwap, BigDecimal openingRangeHigh, BigDecimal openingRangeLow,
                             BigDecimal averageVolume, BigDecimal volumeThreshold,
                             BigDecimal invalidationReference, String reasons) {
    static LiveCandleView from(LiveCandleEntity candle) {
        return new LiveCandleView(candle.getSymbol(), candle.getInstrumentToken(), candle.getCandleStart(),
                candle.getOpenPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getClosePrice(),
                candle.getVolume(), candle.getEvaluationType(), candle.getReferencePrice(), candle.getVwap(),
                candle.getOpeningRangeHigh(), candle.getOpeningRangeLow(), candle.getAverageVolume(), candle.getVolumeThreshold(),
                candle.getInvalidationReference(), candle.getReasons());
    }
}
