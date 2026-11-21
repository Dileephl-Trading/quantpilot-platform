ALTER TABLE execution_request ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);

CREATE TABLE IF NOT EXISTS automation_safety_state (
    id INTEGER PRIMARY KEY,
    uncertain_broker_state BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO automation_safety_state (id, uncertain_broker_state, reason, updated_at)
VALUES (1, FALSE, '', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
