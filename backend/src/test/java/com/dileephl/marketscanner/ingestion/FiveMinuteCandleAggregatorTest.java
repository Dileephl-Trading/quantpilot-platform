package com.dileephl.marketscanner.ingestion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiveMinuteCandleAggregatorTest {
    @Test
    void emitsOnlyACompletedFiveMinuteCandle() {
        FiveMinuteCandleAggregator aggregator = new FiveMinuteCandleAggregator();
        OffsetDateTime start = OffsetDateTime.parse("2026-11-18T09:15:10+05:30");

        assertTrue(aggregator.accept(tick(start, "100", 10)).isEmpty());
        assertTrue(aggregator.accept(tick(start.plusMinutes(2), "103", 20)).isEmpty());
        var completed = aggregator.accept(tick(start.plusMinutes(5), "102", 5)).orElseThrow();

        assertEquals(new BigDecimal("100"), completed.open());
        assertEquals(new BigDecimal("103"), completed.high());
        assertEquals(new BigDecimal("100"), completed.low());
        assertEquals(new BigDecimal("103"), completed.close());
        assertEquals(30, completed.volume());
    }

    @Test
    void resetDiscardsThePartialCandleFromAPreviousSubscription() {
        FiveMinuteCandleAggregator aggregator = new FiveMinuteCandleAggregator();
        OffsetDateTime start = OffsetDateTime.parse("2026-11-18T09:15:10+05:30");

        aggregator.accept(tick(start, "100", 10));
        aggregator.reset();

        assertTrue(aggregator.accept(tick(start.plusMinutes(5), "105", 10)).isEmpty());
    }

    @Test
    void ignoresDuplicateTicksAndLateTicksWithoutCreatingAFalseCandle() {
        FiveMinuteCandleAggregator aggregator = new FiveMinuteCandleAggregator();
        OffsetDateTime start = OffsetDateTime.parse("2026-11-18T09:15:10+05:30");

        aggregator.accept(tick(start, "100", 10));
        aggregator.accept(tick(start, "100", 10));
        aggregator.accept(tick(start.minusMinutes(1), "999", 100));
        aggregator.accept(tick(start.plusMinutes(2), "103", 20));

        var completed = aggregator.accept(tick(start.plusMinutes(5), "102", 5)).orElseThrow();
        assertEquals(30, completed.volume());
        assertEquals(new BigDecimal("103"), completed.close());
    }

    private MarketTick tick(OffsetDateTime timestamp, String price, long quantity) {
        return new MarketTick("AXISBANK", timestamp, new BigDecimal(price), quantity);
    }
}
