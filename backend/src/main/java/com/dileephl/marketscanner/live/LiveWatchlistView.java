package com.dileephl.marketscanner.live;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LiveWatchlistView(UUID id, long instrumentToken, String symbol, boolean enabled, OffsetDateTime addedAt) {
    static LiveWatchlistView from(WatchlistEntryEntity entry) {
        return new LiveWatchlistView(entry.getId(), entry.getInstrumentToken(), entry.getSymbol(), entry.isEnabled(), entry.getAddedAt());
    }
}
