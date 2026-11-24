package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
import com.dileephl.marketscanner.marketdata.InstrumentSyncResult;
import com.dileephl.marketscanner.marketdata.MarketInstrumentService;
import com.dileephl.marketscanner.marketdata.MarketInstrumentView;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.service.SignalEngine.Candle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-only routes. No endpoint in this controller can create, amend or cancel a broker order. */
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {
    private final MarketInstrumentService marketInstrumentService;
    private final MarketDataProvider marketDataProvider;
    private final MarketSessionService marketSessionService;

    public MarketDataController(MarketInstrumentService marketInstrumentService, MarketDataProvider marketDataProvider,
                                MarketSessionService marketSessionService) {
        this.marketInstrumentService = marketInstrumentService;
        this.marketDataProvider = marketDataProvider;
        this.marketSessionService = marketSessionService;
    }

    @PostMapping("/instruments/sync")
    public ResponseEntity<InstrumentSyncResult> syncInstruments() {
        return ResponseEntity.ok(marketInstrumentService.syncNseCashEquities());
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<MarketInstrumentView>> searchInstruments(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "") String sector) {
        return ResponseEntity.ok(marketInstrumentService.filterNseCashEquities(query, sector));
    }

    @GetMapping("/groups")
    public ResponseEntity<Map<String, java.util.Set<String>>> groups() {
        return ResponseEntity.ok(marketInstrumentService.predefinedGroups());
    }

    @GetMapping("/historical-candles")
    public ResponseEntity<List<Candle>> historicalCandles(
            @RequestParam long instrumentToken,
            @RequestParam(defaultValue = "FIVE_MINUTE") CandleInterval interval,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        if (!to.isAfter(from)) throw new IllegalArgumentException("The end time must be after the start time");
        LocalDate tradingDate = from.toInstant().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate();
        if (!marketSessionService.isTradingDate(tradingDate)) {
            throw new IllegalArgumentException("Selected date is not an NSE trading day (weekend or configured holiday)");
        }
        if (tradingDate.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Historical evaluation cannot use a future date");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Historical verification is limited to seven days per request");
        }
        return ResponseEntity.ok(marketDataProvider.historicalCandles(instrumentToken, interval, from, to));
    }
}
