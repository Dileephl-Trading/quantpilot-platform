ALTER TABLE execution_request
    ADD COLUMN IF NOT EXISTS exit_average_price NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS exit_filled_quantity BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS realised_pnl NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS broker_realised_pnl NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS pnl_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS exit_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS pnl_updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE execution_request_archive
    ADD COLUMN IF NOT EXISTS exit_average_price NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS exit_filled_quantity BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS realised_pnl NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS broker_realised_pnl NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS pnl_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS exit_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS pnl_updated_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS execution_trade (
    id UUID PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    average_price NUMERIC(19, 4) NOT NULL,
    fill_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_execution_trade_order_id
    ON execution_trade (order_id, fill_timestamp);

CREATE TABLE IF NOT EXISTS execution_trade_archive (
    id UUID PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    average_price NUMERIC(19, 4) NOT NULL,
    fill_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_execution_trade_archive_order_id
    ON execution_trade_archive (order_id, fill_timestamp);
