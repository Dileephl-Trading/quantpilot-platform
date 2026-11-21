CREATE TABLE execution_request (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    average_price NUMERIC(19, 4),
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
