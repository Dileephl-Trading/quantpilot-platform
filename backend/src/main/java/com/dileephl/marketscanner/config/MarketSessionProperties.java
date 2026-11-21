package com.dileephl.marketscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Configurable NSE cash-session calendar. Holidays are supplied as ISO dates. */
@ConfigurationProperties(prefix = "scanner.market-session")
public class MarketSessionProperties {
    private String zoneId = "Asia/Kolkata";
    private LocalTime open = LocalTime.of(9, 15);
    private LocalTime close = LocalTime.of(15, 30);
    private Set<LocalDate> holidays = new LinkedHashSet<>();

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public LocalTime getOpen() { return open; }
    public void setOpen(LocalTime open) { this.open = open; }
    public LocalTime getClose() { return close; }
    public void setClose(LocalTime close) { this.close = close; }
    public Set<LocalDate> getHolidays() { return holidays; }
    public void setHolidays(Set<LocalDate> holidays) { this.holidays = holidays == null ? new LinkedHashSet<>() : holidays; }
}
