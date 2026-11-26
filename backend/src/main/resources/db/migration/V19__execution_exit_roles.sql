ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS exit_role VARCHAR(16);

UPDATE execution_request
SET exit_role = CASE WHEN protective THEN 'STOP' ELSE 'ENTRY' END
WHERE exit_role IS NULL;

ALTER TABLE execution_request ALTER COLUMN exit_role SET DEFAULT 'ENTRY';
ALTER TABLE execution_request ALTER COLUMN exit_role SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_execution_request_parent_exit_role
    ON execution_request (parent_order_id, exit_role);
