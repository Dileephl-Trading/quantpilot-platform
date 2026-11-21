CREATE TABLE market_instrument (
    instrument_id VARCHAR(96) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    instrument_token BIGINT NOT NULL,
    exchange VARCHAR(16) NOT NULL,
    segment VARCHAR(32) NOT NULL,
    tradingsymbol VARCHAR(64) NOT NULL,
    instrument_name VARCHAR(256),
    instrument_type VARCHAR(32) NOT NULL,
    tick_size NUMERIC(19, 6) NOT NULL,
    lot_size INTEGER NOT NULL,
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_market_instrument_provider_symbol UNIQUE (provider, exchange, tradingsymbol)
);

CREATE INDEX ix_market_instrument_symbol ON market_instrument (exchange, tradingsymbol);
CREATE INDEX ix_market_instrument_name ON market_instrument (exchange, instrument_name);
