package com.dileephl.marketscanner.marketdata;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketInstrumentView(
        long instrumentToken,
        String exchange,
        String segment,
        String tradingsymbol,
        String name,
        String sector,
        String instrumentType,
        BigDecimal tickSize,
        int lotSize,
        OffsetDateTime lastSyncedAt) {
    static MarketInstrumentView from(MarketInstrumentEntity entity) {
        return new MarketInstrumentView(entity.getInstrumentToken(), entity.getExchange(), entity.getSegment(),
                entity.getTradingsymbol(), entity.getInstrumentName(), SectorClassifier.classify(entity.getTradingsymbol()), entity.getInstrumentType(),
                entity.getTickSize(), entity.getLotSize(), entity.getLastSyncedAt());
    }
}
