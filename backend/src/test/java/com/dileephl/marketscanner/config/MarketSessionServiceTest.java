package com.dileephl.marketscanner.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarketSessionServiceTest {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Test
    void acceptsWeekdayDuringCashSession() {
        MarketSessionService service = serviceAt("2026-11-19T10:00:00+05:30", Set.of());
        assertTrue(service.isOpenNow());
        assertTrue(service.status().tradingDate());
    }

    @Test
    void rejectsBeforeOpenAndAtClose() {
        assertFalse(serviceAt("2026-11-19T09:14:59+05:30", Set.of()).isOpenNow());
        assertFalse(serviceAt("2026-11-19T15:30:00+05:30", Set.of()).isOpenNow());
    }

    @Test
    void rejectsWeekendAndConfiguredHoliday() {
        assertFalse(serviceAt("2026-11-21T10:00:00+05:30", Set.of()).isOpenNow());
        assertFalse(serviceAt("2026-11-19T10:00:00+05:30", Set.of(LocalDate.of(2026, 11, 19))).isOpenNow());
    }

    @Test
    void allowsEntriesOnlyInsideConfiguredWindow() {
        assertFalse(serviceAt("2026-11-19T09:29:59+05:30", Set.of()).isNewEntryAllowed(OffsetDateTime.parse("2026-11-19T09:29:59+05:30")));
        assertTrue(serviceAt("2026-11-19T09:30:00+05:30", Set.of()).isNewEntryAllowed(OffsetDateTime.parse("2026-11-19T09:30:00+05:30")));
        assertFalse(serviceAt("2026-11-19T14:45:00+05:30", Set.of()).isNewEntryAllowed(OffsetDateTime.parse("2026-11-19T14:45:00+05:30")));
    }

    @Test
    void exposesCloseoutWindows() {
        MarketSessionService service = serviceAt("2026-11-19T15:00:00+05:30", Set.of());
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T15:00:00+05:30");
        assertTrue(service.isCancelPendingDue(now));
        assertTrue(service.isSquareOffDue(now));
        assertFalse(service.isHardFlatDeadline(now));
        assertTrue(service.isHardFlatDeadline(OffsetDateTime.parse("2026-11-19T15:03:00+05:30")));
    }

    private MarketSessionService serviceAt(String timestamp, Set<LocalDate> holidays) {
        MarketSessionProperties properties = new MarketSessionProperties();
        properties.setHolidays(holidays);
        return new MarketSessionService(properties,
                Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), INDIA));
    }
}
