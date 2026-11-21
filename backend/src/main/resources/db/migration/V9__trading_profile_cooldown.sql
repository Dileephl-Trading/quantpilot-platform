ALTER TABLE trading_profile ADD COLUMN IF NOT EXISTS cooldown_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE trading_profile ADD CONSTRAINT ck_trading_profile_cooldown_seconds CHECK (cooldown_seconds >= 0);
