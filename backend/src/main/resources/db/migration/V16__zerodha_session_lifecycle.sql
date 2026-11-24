ALTER TABLE zerodha_session_token
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    ADD COLUMN IF NOT EXISTS connected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_validated_at TIMESTAMPTZ;

ALTER TABLE zerodha_session_token
    DROP CONSTRAINT IF EXISTS ck_zerodha_session_status;

ALTER TABLE zerodha_session_token
    ADD CONSTRAINT ck_zerodha_session_status
    CHECK (status IN ('CONNECTED', 'EXPIRED', 'INVALID', 'REVOKED'));

UPDATE zerodha_session_token
SET status = CASE WHEN expires_at <= CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE 'CONNECTED' END
WHERE id = 1;