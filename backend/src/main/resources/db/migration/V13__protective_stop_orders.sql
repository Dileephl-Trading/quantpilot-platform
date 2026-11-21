ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS protective BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS parent_order_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_execution_request_parent_order_id
    ON execution_request (parent_order_id);
