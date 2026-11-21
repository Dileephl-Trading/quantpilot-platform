CREATE TABLE live_candle (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    candle_start TIMESTAMP WITH TIME ZONE NOT NULL,
    open_price NUMERIC(19, 4) NOT NULL,
    high_price NUMERIC(19, 4) NOT NULL,
    low_price NUMERIC(19, 4) NOT NULL,
    close_price NUMERIC(19, 4) NOT NULL,
    volume BIGINT NOT NULL,
    evaluation_type VARCHAR(24) NOT NULL,
    reference_price NUMERIC(19, 4),
    vwap NUMERIC(19, 4),
    invalidation_reference NUMERIC(19, 4),
    reasons TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_live_candle UNIQUE (provider, instrument_token, candle_start)
);

CREATE INDEX ix_live_candle_start ON live_candle (candle_start DESC);
