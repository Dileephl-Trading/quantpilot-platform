ALTER TABLE paper_trade ADD COLUMN IF NOT EXISTS slippage_per_unit NUMERIC(19, 4);
ALTER TABLE paper_trade ADD COLUMN IF NOT EXISTS slippage_amount NUMERIC(19, 4);
