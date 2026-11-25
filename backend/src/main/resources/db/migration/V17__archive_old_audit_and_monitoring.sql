CREATE TABLE IF NOT EXISTS audit_event_archive (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_audit_event_archive_occurred_at
    ON audit_event_archive (occurred_at DESC);

CREATE TABLE IF NOT EXISTS live_signal_archive (
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
    opening_range_high NUMERIC(19, 4),
    opening_range_low NUMERIC(19, 4),
    average_volume NUMERIC(19, 4),
    volume_threshold NUMERIC(19, 4),
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_live_signal_archive_generated_at
    ON live_signal_archive (generated_at DESC);

CREATE TABLE IF NOT EXISTS live_candle_archive (
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
    opening_range_high NUMERIC(19, 4),
    opening_range_low NUMERIC(19, 4),
    average_volume NUMERIC(19, 4),
    volume_threshold NUMERIC(19, 4),
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_live_candle_archive_generated_at
    ON live_candle_archive (generated_at DESC);
