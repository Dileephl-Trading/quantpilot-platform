package com.dileephl.marketscanner.integration.marketdata;

import java.time.Duration;

/** Intervals supported by a market-data provider. */
public enum CandleInterval {
    MINUTE("minute", Duration.ofMinutes(1)),
    THREE_MINUTE("3minute", Duration.ofMinutes(3)),
    FIVE_MINUTE("5minute", Duration.ofMinutes(5)),
    TEN_MINUTE("10minute", Duration.ofMinutes(10)),
    FIFTEEN_MINUTE("15minute", Duration.ofMinutes(15)),
    THIRTY_MINUTE("30minute", Duration.ofMinutes(30)),
    SIXTY_MINUTE("60minute", Duration.ofHours(1)),
    DAY("day", Duration.ofDays(1));

    private final String providerValue;
    private final Duration candleDuration;

    CandleInterval(String providerValue, Duration candleDuration) {
        this.providerValue = providerValue;
        this.candleDuration = candleDuration;
    }

    public String providerValue() {
        return providerValue;
    }

    public Duration candleDuration() { return candleDuration; }
}
