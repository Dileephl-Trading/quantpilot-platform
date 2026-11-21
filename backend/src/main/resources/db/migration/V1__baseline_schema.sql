CREATE TABLE trading_profile (
    id BIGINT PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,
    broker VARCHAR(64) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    segment VARCHAR(32) NOT NULL,
    product VARCHAR(32) NOT NULL,
    allocated_capital NUMERIC(19, 4) NOT NULL CHECK (allocated_capital > 0),
    max_loss_per_trade NUMERIC(19, 4) NOT NULL CHECK (max_loss_per_trade > 0),
    max_daily_loss NUMERIC(19, 4) NOT NULL CHECK (max_daily_loss > 0),
    max_trades_per_day INTEGER NOT NULL CHECK (max_trades_per_day > 0),
    max_open_positions INTEGER NOT NULL CHECK (max_open_positions > 0),
    kill_switch_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO trading_profile (
    id, mode, broker, exchange, segment, product, allocated_capital,
    max_loss_per_trade, max_daily_loss, max_trades_per_day, max_open_positions, kill_switch_enabled
) VALUES (
    1, 'PAPER', 'GENERIC', 'NSE', 'CASH', 'MIS', 50000.0000,
    1000.0000, 2000.0000, 2, 1, FALSE
);

CREATE TABLE paper_trade (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('INTENDED', 'OPEN', 'CLOSED', 'CANCELLED')),
    trade_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    filled_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    intended_entry_price NUMERIC(19, 4) NOT NULL CHECK (intended_entry_price > 0),
    stop_price NUMERIC(19, 4) NOT NULL CHECK (stop_price > 0),
    intended_quantity BIGINT NOT NULL CHECK (intended_quantity > 0),
    actual_entry_price NUMERIC(19, 4),
    filled_quantity BIGINT,
    actual_exit_price NUMERIC(19, 4),
    gross_pnl NUMERIC(19, 4),
    brokerage NUMERIC(19, 4),
    net_pnl NUMERIC(19, 4),
    exit_reason VARCHAR(16),
    strategy VARCHAR(80),
    notes VARCHAR(500),
    CONSTRAINT paper_trade_state_consistency CHECK (
        (status = 'INTENDED' AND actual_entry_price IS NULL AND filled_quantity IS NULL
            AND actual_exit_price IS NULL AND gross_pnl IS NULL AND brokerage IS NULL AND net_pnl IS NULL AND exit_reason IS NULL)
        OR (status = 'OPEN' AND actual_entry_price IS NOT NULL AND filled_quantity IS NOT NULL
            AND actual_exit_price IS NULL AND gross_pnl IS NULL AND brokerage IS NULL AND net_pnl IS NULL AND exit_reason IS NULL)
        OR (status = 'CLOSED' AND actual_entry_price IS NOT NULL AND filled_quantity IS NOT NULL
            AND actual_exit_price IS NOT NULL AND gross_pnl IS NOT NULL AND brokerage IS NOT NULL AND net_pnl IS NOT NULL AND exit_reason IS NOT NULL)
        OR (status = 'CANCELLED' AND actual_entry_price IS NULL AND filled_quantity IS NULL
            AND actual_exit_price IS NULL AND gross_pnl IS NULL AND brokerage IS NULL AND net_pnl IS NULL AND exit_reason IS NULL)
    )
);

CREATE INDEX ix_paper_trade_date_created_at ON paper_trade (trade_date, created_at DESC);
CREATE INDEX ix_paper_trade_status ON paper_trade (status);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    details TEXT NOT NULL
);

CREATE INDEX ix_audit_event_aggregate ON audit_event (aggregate_type, aggregate_id, occurred_at DESC);
