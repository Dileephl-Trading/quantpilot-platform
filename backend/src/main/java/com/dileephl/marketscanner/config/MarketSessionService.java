package com.dileephl.marketscanner.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Central session guard. All live ingestion must use this service so that weekends,
 * configured exchange holidays, pre-open and post-close ticks cannot create signals.
 */
@Service
public class MarketSessionService {
    private final MarketSessionProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public MarketSessionService(MarketSessionProperties properties) {
        this(properties, Clock.system(ZoneId.of(properties.getZoneId())));
    }

    MarketSessionService(MarketSessionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.getZoneId());
    }

    public boolean isOpenNow() { return isOpen(OffsetDateTime.now(clock)); }

    public boolean isOpen(OffsetDateTime timestamp) {
        ZonedDateTime local = timestamp.toInstant().atZone(zone);
        return isTradingDate(local.toLocalDate())
                && !local.toLocalTime().isBefore(properties.getOpen())
                && local.toLocalTime().isBefore(properties.getClose());
    }

    public boolean isTradingDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                && !properties.getHolidays().contains(date);
    }

    public MarketSessionStatus status() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        boolean tradingDate = isTradingDate(now.toLocalDate());
        boolean open = isOpen(now.toOffsetDateTime());
        String reason = open ? "NSE cash session is open"
                : !tradingDate ? "Weekend or configured NSE holiday"
                : now.toLocalTime().isBefore(properties.getOpen()) ? "Before NSE cash-session open"
                : "After NSE cash-session close";
        return new MarketSessionStatus(now.toOffsetDateTime(), tradingDate, open,
                properties.getOpen(), properties.getClose(), reason);
    }

    public record MarketSessionStatus(OffsetDateTime now, boolean tradingDate, boolean open,
                                      java.time.LocalTime sessionOpen, java.time.LocalTime sessionClose,
                                      String reason) { }
}
