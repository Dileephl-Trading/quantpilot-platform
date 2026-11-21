ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS order_type VARCHAR(16);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS price NUMERIC(19, 4);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS trigger_price NUMERIC(19, 4);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS stop_price NUMERIC(19, 4);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS product VARCHAR(16);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS exchange VARCHAR(16);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS validity VARCHAR(16);
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE execution_request SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE execution_request ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_execution_request_status ON execution_request (status);
CREATE INDEX IF NOT EXISTS ix_execution_request_order_id ON execution_request (order_id);

CREATE TABLE IF NOT EXISTS execution_order_event (
    id UUID PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    filled_quantity BIGINT NOT NULL,
    average_price NUMERIC(19, 4),
    message TEXT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_execution_order_event_order_time
    ON execution_order_event (order_id, observed_at DESC);
