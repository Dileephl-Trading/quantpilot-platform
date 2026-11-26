package com.dileephl.marketscanner.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Moves data outside the seven-day operational window into durable archive tables. */
@Service
public class RetentionService {
    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final ZoneOffset INDIA_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private final JdbcTemplate jdbc;

    public RetentionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    @Transactional
    public void archiveOldRows() {
        OffsetDateTime cutoff = OffsetDateTime.now(INDIA_OFFSET).minusDays(7);
        Timestamp cutoffTimestamp = Timestamp.from(cutoff.toInstant());
        try {
            archiveAuditEvents(cutoffTimestamp);
            archiveSignals(cutoffTimestamp);
            archiveSignalVerifications(cutoffTimestamp);
            // Orders and fills are the durable ownership and idempotency ledger.
            // The history endpoint applies its own seven-day display window.
            archiveCandles(cutoffTimestamp);
        } catch (RuntimeException exception) {
            // A retention failure must not stop market-data ingestion or order safeguards.
            log.warn("Seven-day retention pass could not complete; it will retry on the next schedule", exception);
        }
    }

    private void archiveAuditEvents(Timestamp cutoff) {
        jdbc.update("""
                INSERT INTO audit_event_archive
                    (id, occurred_at, event_type, aggregate_type, aggregate_id, details, archived_at)
                SELECT id, occurred_at, event_type, aggregate_type, aggregate_id, details, CURRENT_TIMESTAMP
                FROM audit_event
                WHERE occurred_at < ?
                ON CONFLICT (id) DO NOTHING
                """, cutoff);
        jdbc.update("DELETE FROM audit_event WHERE occurred_at < ?", cutoff);
    }

    private void archiveSignals(Timestamp cutoff) {
        jdbc.update("""
                INSERT INTO live_signal_archive
                    (id, provider, instrument_token, symbol, candle_start, signal_type, reference_price, vwap,
                     invalidation_reference, reasons, generated_at, opening_range_high, opening_range_low,
                     average_volume, volume_threshold, archived_at)
                SELECT id, provider, instrument_token, symbol, candle_start, signal_type, reference_price, vwap,
                       invalidation_reference, reasons, generated_at, opening_range_high, opening_range_low,
                       average_volume, volume_threshold, CURRENT_TIMESTAMP
                FROM live_signal
                WHERE generated_at < ?
                ON CONFLICT (id) DO NOTHING
                """, cutoff);
        jdbc.update("DELETE FROM live_signal WHERE generated_at < ?", cutoff);
    }

    private void archiveSignalVerifications(Timestamp cutoff) {
        jdbc.update("""
                INSERT INTO signal_verification_archive
                    (id, provider, instrument_token, symbol, candle_start, signal_type, direction,
                     reference_price, vwap, invalidation_reference, opening_range_high, opening_range_low,
                     average_volume, volume_threshold, decision, analysis, order_id, created_at, updated_at, archived_at)
                SELECT id, provider, instrument_token, symbol, candle_start, signal_type, direction,
                       reference_price, vwap, invalidation_reference, opening_range_high, opening_range_low,
                       average_volume, volume_threshold, decision, analysis, order_id, created_at, updated_at, CURRENT_TIMESTAMP
                FROM signal_verification
                WHERE created_at < ?
                ON CONFLICT (id) DO NOTHING
                """, cutoff);
        jdbc.update("DELETE FROM signal_verification WHERE created_at < ?", cutoff);
    }

    private void archiveCandles(Timestamp cutoff) {
        jdbc.update("""
                INSERT INTO live_candle_archive
                    (id, provider, instrument_token, symbol, candle_start, open_price, high_price, low_price,
                     close_price, volume, evaluation_type, reference_price, vwap, invalidation_reference,
                     reasons, generated_at, opening_range_high, opening_range_low, average_volume,
                     volume_threshold, archived_at)
                SELECT id, provider, instrument_token, symbol, candle_start, open_price, high_price, low_price,
                       close_price, volume, evaluation_type, reference_price, vwap, invalidation_reference,
                       reasons, generated_at, opening_range_high, opening_range_low, average_volume,
                       volume_threshold, CURRENT_TIMESTAMP
                FROM live_candle
                WHERE generated_at < ?
                ON CONFLICT (id) DO NOTHING
                """, cutoff);
        jdbc.update("DELETE FROM live_candle WHERE generated_at < ?", cutoff);
    }
}
