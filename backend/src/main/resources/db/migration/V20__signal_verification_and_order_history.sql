CREATE TABLE IF NOT EXISTS execution_request_archive (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    filled_quantity BIGINT NOT NULL,
    average_price NUMERIC(19, 4),
    order_type VARCHAR(16),
    price NUMERIC(19, 4),
    trigger_price NUMERIC(19, 4),
    stop_price NUMERIC(19, 4),
    request_fingerprint VARCHAR(64),
    product VARCHAR(16),
    exchange VARCHAR(16),
    validity VARCHAR(16),
    protective BOOLEAN NOT NULL,
    exit_role VARCHAR(16) NOT NULL,
    parent_order_id VARCHAR(64),
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_execution_request_archive_created_at
    ON execution_request_archive (created_at DESC);

CREATE TABLE IF NOT EXISTS signal_verification (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    candle_start TIMESTAMP WITH TIME ZONE NOT NULL,
    signal_type VARCHAR(24) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    reference_price NUMERIC(19, 4),
    vwap NUMERIC(19, 4),
    invalidation_reference NUMERIC(19, 4),
    opening_range_high NUMERIC(19, 4),
    opening_range_low NUMERIC(19, 4),
    average_volume NUMERIC(19, 4),
    volume_threshold NUMERIC(19, 4),
    decision VARCHAR(24) NOT NULL,
    analysis TEXT NOT NULL,
    order_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_signal_verification_signal UNIQUE (provider, instrument_token, candle_start)
);

CREATE INDEX IF NOT EXISTS ix_signal_verification_created_at
    ON signal_verification (created_at DESC);

CREATE TABLE IF NOT EXISTS signal_verification_archive (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    candle_start TIMESTAMP WITH TIME ZONE NOT NULL,
    signal_type VARCHAR(24) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    reference_price NUMERIC(19, 4),
    vwap NUMERIC(19, 4),
    invalidation_reference NUMERIC(19, 4),
    opening_range_high NUMERIC(19, 4),
    opening_range_low NUMERIC(19, 4),
    average_volume NUMERIC(19, 4),
    volume_threshold NUMERIC(19, 4),
    decision VARCHAR(24) NOT NULL,
    analysis TEXT NOT NULL,
    order_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_signal_verification_archive_created_at
    ON signal_verification_archive (created_at DESC);
