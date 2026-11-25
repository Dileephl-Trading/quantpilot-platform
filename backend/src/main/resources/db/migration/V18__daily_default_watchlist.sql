ALTER TABLE market_watchlist_entry
    ADD COLUMN auto_managed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_watchlist_provider_enabled
    ON market_watchlist_entry (provider, enabled);

CREATE INDEX ix_watchlist_provider_auto_managed
    ON market_watchlist_entry (provider, auto_managed);
