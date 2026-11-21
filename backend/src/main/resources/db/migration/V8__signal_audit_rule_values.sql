ALTER TABLE live_candle ADD COLUMN IF NOT EXISTS opening_range_high NUMERIC(19, 4);
ALTER TABLE live_candle ADD COLUMN IF NOT EXISTS opening_range_low NUMERIC(19, 4);
ALTER TABLE live_candle ADD COLUMN IF NOT EXISTS average_volume NUMERIC(19, 4);
ALTER TABLE live_candle ADD COLUMN IF NOT EXISTS volume_threshold NUMERIC(19, 4);

ALTER TABLE live_signal ADD COLUMN IF NOT EXISTS opening_range_high NUMERIC(19, 4);
ALTER TABLE live_signal ADD COLUMN IF NOT EXISTS opening_range_low NUMERIC(19, 4);
ALTER TABLE live_signal ADD COLUMN IF NOT EXISTS average_volume NUMERIC(19, 4);
ALTER TABLE live_signal ADD COLUMN IF NOT EXISTS volume_threshold NUMERIC(19, 4);
