package com.dileephl.marketscanner.service;

import com.dileephl.marketscanner.config.ScannerProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SignalEngine {
    private final ScannerProperties properties;

    public SignalEngine(ScannerProperties properties) {
        properties.validate();
        this.properties = properties;
    }

    public SignalEngine() { this.properties = new ScannerProperties(); }

    public TradeSignal evaluate(String symbol, List<Candle> input) {
        if (input == null || input.size() < properties.getMinimumCandles()) {
            throw new IllegalArgumentException("At least " + properties.getMinimumCandles() + " five-minute candles are required");
        }

        List<Candle> candles = input.stream()
                .sorted(Comparator.comparing(Candle::timestamp))
                .toList();
        Candle current = candles.getLast();
        BigDecimal openingHigh = candles.subList(0, properties.getOpeningRangeCandles()).stream()
                .map(Candle::high).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal openingLow = candles.subList(0, properties.getOpeningRangeCandles()).stream()
                .map(Candle::low).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal vwap = vwap(candles);
        BigDecimal averageVolume = candles.subList(candles.size() - 1 - properties.getVolumeLookback(), candles.size() - 1).stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(properties.getVolumeLookback()), 2, RoundingMode.HALF_UP);
        boolean volumeConfirmed = BigDecimal.valueOf(current.volume()).compareTo(averageVolume.multiply(properties.getVolumeMultiplier())) >= 0;

        List<String> reasons = new ArrayList<>();
        reasons.add("VWAP=" + vwap);
        reasons.add("Opening range=" + openingLow + "–" + openingHigh);
        reasons.add("Volume confirmation=" + volumeConfirmed);

        if (!properties.isEnabled()) {
            reasons.add("Strategy is disabled by configuration");
            return new TradeSignal(symbol, SignalType.NO_TRADE, current.close(), vwap, openingHigh, openingLow,
                    averageVolume, averageVolume.multiply(properties.getVolumeMultiplier()), null, List.copyOf(reasons));
        }

        if (current.close().compareTo(openingHigh) > 0 && current.close().compareTo(vwap) > 0 && volumeConfirmed) {
            reasons.add("Breakout is above opening range and VWAP");
            return new TradeSignal(symbol, SignalType.WATCH_LONG, current.close(), vwap, openingHigh, openingLow,
                    averageVolume, averageVolume.multiply(properties.getVolumeMultiplier()), openingHigh, List.copyOf(reasons));
        }
        if (current.close().compareTo(openingLow) < 0 && current.close().compareTo(vwap) < 0 && volumeConfirmed) {
            reasons.add("Breakdown is below opening range and VWAP");
            return new TradeSignal(symbol, SignalType.WATCH_SHORT, current.close(), vwap, openingHigh, openingLow,
                    averageVolume, averageVolume.multiply(properties.getVolumeMultiplier()), openingLow, List.copyOf(reasons));
        }

        reasons.add("No aligned, volume-confirmed opening-range setup");
        return new TradeSignal(symbol, SignalType.NO_TRADE, current.close(), vwap, openingHigh, openingLow,
                averageVolume, averageVolume.multiply(properties.getVolumeMultiplier()), null, List.copyOf(reasons));
    }

    private BigDecimal vwap(List<Candle> candles) {
        BigDecimal numerator = BigDecimal.ZERO;
        long totalVolume = 0;
        for (Candle candle : candles) {
            numerator = numerator.add(candle.typicalPrice().multiply(BigDecimal.valueOf(candle.volume())));
            totalVolume += candle.volume();
        }
        if (totalVolume <= 0) {
            throw new IllegalArgumentException("Total volume must be positive");
        }
        return numerator.divide(BigDecimal.valueOf(totalVolume), 2, RoundingMode.HALF_UP);
    }

    public record Candle(
            java.time.OffsetDateTime timestamp,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume) {
        public BigDecimal typicalPrice() {
            return high.add(low).add(close).divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
        }
    }

    public enum SignalType { WATCH_LONG, WATCH_SHORT, NO_TRADE }

    public record TradeSignal(
            String symbol,
            SignalType type,
            BigDecimal referencePrice,
            BigDecimal vwap,
            BigDecimal openingRangeHigh,
            BigDecimal openingRangeLow,
            BigDecimal averageVolume,
            BigDecimal volumeThreshold,
            BigDecimal invalidationReference,
            List<String> reasons) { }
}
