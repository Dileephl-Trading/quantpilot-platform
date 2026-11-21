CREATE TABLE market_watchlist_entry (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_watchlist_provider_token UNIQUE (provider, instrument_token)
);

CREATE TABLE live_signal (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    candle_start TIMESTAMP WITH TIME ZONE NOT NULL,
    signal_type VARCHAR(24) NOT NULL,
    reference_price NUMERIC(19, 4) NOT NULL,
    vwap NUMERIC(19, 4) NOT NULL,
    invalidation_reference NUMERIC(19, 4),
    reasons TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_live_signal_candle UNIQUE (provider, instrument_token, candle_start)
);

CREATE INDEX ix_live_signal_generated_at ON live_signal (generated_at DESC);
