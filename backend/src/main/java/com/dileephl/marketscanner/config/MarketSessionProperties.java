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
    private LocalTime newEntryStart = LocalTime.of(9, 30);
    private LocalTime newEntryCutoff = LocalTime.of(14, 45);
    private LocalTime cancelPendingAt = LocalTime.of(14, 55);
    private LocalTime squareOffAt = LocalTime.of(15, 0);
    private LocalTime hardFlatDeadline = LocalTime.of(15, 3);
    private Set<LocalDate> holidays = new LinkedHashSet<>();

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public LocalTime getOpen() { return open; }
    public void setOpen(LocalTime open) { this.open = open; }
    public LocalTime getClose() { return close; }
    public void setClose(LocalTime close) { this.close = close; }
    public LocalTime getNewEntryStart() { return newEntryStart; }
    public void setNewEntryStart(LocalTime value) { this.newEntryStart = value; }
    public LocalTime getNewEntryCutoff() { return newEntryCutoff; }
    public void setNewEntryCutoff(LocalTime value) { this.newEntryCutoff = value; }
    public LocalTime getCancelPendingAt() { return cancelPendingAt; }
    public void setCancelPendingAt(LocalTime value) { this.cancelPendingAt = value; }
    public LocalTime getSquareOffAt() { return squareOffAt; }
    public void setSquareOffAt(LocalTime value) { this.squareOffAt = value; }
    public LocalTime getHardFlatDeadline() { return hardFlatDeadline; }
    public void setHardFlatDeadline(LocalTime value) { this.hardFlatDeadline = value; }
    public Set<LocalDate> getHolidays() { return holidays; }
    public void setHolidays(Set<LocalDate> holidays) { this.holidays = holidays == null ? new LinkedHashSet<>() : holidays; }

    public void validate() {
        if (open == null || close == null || newEntryStart == null || newEntryCutoff == null
                || cancelPendingAt == null || squareOffAt == null || hardFlatDeadline == null
                || !open.isBefore(close) || newEntryStart.isBefore(open)
                || !newEntryStart.isBefore(newEntryCutoff) || !newEntryCutoff.isBefore(cancelPendingAt)
                || !cancelPendingAt.isBefore(squareOffAt) || !squareOffAt.isBefore(hardFlatDeadline)
                || !hardFlatDeadline.isBefore(close)) {
            throw new IllegalArgumentException("Invalid market-session execution windows");
        }
    }
}
