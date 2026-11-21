CREATE TABLE IF NOT EXISTS zerodha_session_token (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    encrypted_access_token TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_zerodha_session_token_expires_at
    ON zerodha_session_token (expires_at);
