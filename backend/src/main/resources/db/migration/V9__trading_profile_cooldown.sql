ALTER TABLE trading_profile
    ADD COLUMN IF NOT EXISTS cooldown_seconds INTEGER NOT NULL DEFAULT 60;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_trading_profile_cooldown_seconds'
          AND conrelid = 'trading_profile'::regclass
    ) THEN
        ALTER TABLE trading_profile
            ADD CONSTRAINT ck_trading_profile_cooldown_seconds
            CHECK (cooldown_seconds >= 0);
    END IF;
END
$$;
