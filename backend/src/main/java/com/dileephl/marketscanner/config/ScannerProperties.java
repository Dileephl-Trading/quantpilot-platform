package com.dileephl.marketscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.dileephl.marketscanner.integration.marketdata.CandleInterval;

/** Configurable strategy and symbol-group settings. */
@ConfigurationProperties(prefix = "scanner.strategy")
public class ScannerProperties {
    private boolean enabled = true;
    private CandleInterval candleInterval = CandleInterval.FIVE_MINUTE;
    private int openingRangeCandles = 6;
    private int volumeLookback = 5;
    private BigDecimal volumeMultiplier = new BigDecimal("1.50");
    private int minimumCandles = 12;
    private Map<String, Set<String>> groups = new LinkedHashMap<>();

    public int getOpeningRangeCandles() { return openingRangeCandles; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public CandleInterval getCandleInterval() { return candleInterval; }
    public void setCandleInterval(CandleInterval value) { candleInterval = value == null ? CandleInterval.FIVE_MINUTE : value; }
    public void setOpeningRangeCandles(int value) { openingRangeCandles = value; }
    public int getVolumeLookback() { return volumeLookback; }
    public void setVolumeLookback(int value) { volumeLookback = value; }
    public BigDecimal getVolumeMultiplier() { return volumeMultiplier; }
    public void setVolumeMultiplier(BigDecimal value) { volumeMultiplier = value; }
    public int getMinimumCandles() { return minimumCandles; }
    public void setMinimumCandles(int value) { minimumCandles = value; }
    public Map<String, Set<String>> getGroups() { return groups; }
    public void setGroups(Map<String, Set<String>> value) { groups = value == null ? new LinkedHashMap<>() : value; }

    public void validate() {
        if (openingRangeCandles < 1 || volumeLookback < 1 || minimumCandles < openingRangeCandles + volumeLookback + 1
                || volumeMultiplier == null || volumeMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("Invalid scanner strategy configuration");
        }
        groups.replaceAll((name, symbols) -> symbols == null ? new LinkedHashSet<>() : symbols);
    }
}
