package com.dileephl.marketscanner.ingestion;

import com.dileephl.marketscanner.service.SignalEngine.Candle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.time.Duration;

/**
 * Converts ordered trade ticks into completed five-minute candles. A candle is emitted only
 * when the first tick of the next five-minute bucket arrives, so incomplete candles are never
 * passed to the signal engine.
 */
public final class FiveMinuteCandleAggregator {
    private final Duration interval;
    private final Map<String, CandleState> activeCandles = new HashMap<>();
    private final Map<String, OffsetDateTime> lastTickTimestamps = new HashMap<>();
    private final Set<String> seenTicks = new HashSet<>();

    public FiveMinuteCandleAggregator() { this(Duration.ofMinutes(5)); }

    public FiveMinuteCandleAggregator(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative() || interval.toSeconds() % 60 != 0) {
            throw new IllegalArgumentException("Candle interval must be a positive whole number of minutes");
        }
        this.interval = interval;
    }

    public synchronized Optional<Candle> accept(MarketTick tick) {
        validate(tick);
        String fingerprint = tick.symbol() + "|" + tick.timestamp() + "|" + tick.price() + "|" + tick.quantity();
        if (!seenTicks.add(fingerprint)) return Optional.empty();
        OffsetDateTime previousTick = lastTickTimestamps.get(tick.symbol());
        if (previousTick != null && tick.timestamp().isBefore(previousTick)) return Optional.empty();
        lastTickTimestamps.put(tick.symbol(), tick.timestamp());
        OffsetDateTime bucketStart = bucketStart(tick.timestamp());
        CandleState active = activeCandles.get(tick.symbol());

        if (active == null) {
            activeCandles.put(tick.symbol(), CandleState.from(tick, bucketStart));
            return Optional.empty();
        }
        if (bucketStart.isBefore(active.bucketStart())) {
            return Optional.empty();
        }
        if (bucketStart.equals(active.bucketStart())) {
            active.update(tick);
            return Optional.empty();
        }

        Candle completed = active.toCandle();
        activeCandles.put(tick.symbol(), CandleState.from(tick, bucketStart));
        return Optional.of(completed);
    }

    /** Clears only in-memory partial candles when a live subscription is restarted. */
    public synchronized void reset() {
        activeCandles.clear();
        lastTickTimestamps.clear();
        seenTicks.clear();
    }

    private OffsetDateTime bucketStart(OffsetDateTime timestamp) {
        long minutes = interval.toMinutes();
        long absoluteMinute = timestamp.getHour() * 60L + timestamp.getMinute();
        long bucketMinute = (absoluteMinute / minutes) * minutes;
        return timestamp.withHour((int) (bucketMinute / 60)).withMinute((int) (bucketMinute % 60)).withSecond(0).withNano(0);
    }

    private void validate(MarketTick tick) {
        if (tick == null || tick.symbol() == null || tick.symbol().isBlank()
                || tick.timestamp() == null || tick.price() == null || tick.price().signum() <= 0
                || tick.quantity() <= 0) {
            throw new IllegalArgumentException("Tick needs a symbol, timestamp, positive price and positive quantity");
        }
    }

    private static final class CandleState {
        private final OffsetDateTime bucketStart;
        private final BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;

        private CandleState(OffsetDateTime bucketStart, BigDecimal price, long quantity) {
            this.bucketStart = bucketStart;
            this.open = price;
            this.high = price;
            this.low = price;
            this.close = price;
            this.volume = quantity;
        }

        static CandleState from(MarketTick tick, OffsetDateTime bucketStart) {
            return new CandleState(bucketStart, tick.price(), tick.quantity());
        }

        void update(MarketTick tick) {
            high = high.max(tick.price());
            low = low.min(tick.price());
            close = tick.price();
            volume += tick.quantity();
        }

        OffsetDateTime bucketStart() {
            return bucketStart;
        }

        Candle toCandle() {
            return new Candle(bucketStart, open, high, low, close, volume);
        }
    }
}
