package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.journal.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paper-trades")
public class PaperTradeController {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private final PaperTradeService trades;

    public PaperTradeController(PaperTradeService trades) { this.trades = trades; }

    @GetMapping
    public List<PaperTradeView> list(@RequestParam(required = false) LocalDate tradeDate) {
        return trades.list(tradeDate == null ? LocalDate.now(MARKET_ZONE) : tradeDate);
    }

    @GetMapping("/summary")
    public DailyTradeSummary summary(@RequestParam(required = false) LocalDate tradeDate) {
        return trades.summary(tradeDate == null ? LocalDate.now(MARKET_ZONE) : tradeDate);
    }

    @GetMapping("/report")
    public DailyTradeReport report(@RequestParam(required = false) LocalDate tradeDate) {
        return trades.report(tradeDate == null ? LocalDate.now(MARKET_ZONE) : tradeDate);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now(MARKET_ZONE) : tradeDate;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=paper-trades-" + date + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(trades.csv(date).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaperTradeView create(@Valid @RequestBody CreatePaperTradeRequest request) { return trades.create(request); }

    @PostMapping("/{id}/fill")
    public PaperTradeView fill(@PathVariable UUID id, @Valid @RequestBody FillPaperTradeRequest request) {
        return trades.fill(id, request);
    }

    @PostMapping("/{id}/close")
    public PaperTradeView close(@PathVariable UUID id, @Valid @RequestBody ClosePaperTradeRequest request) {
        return trades.close(id, request);
    }

    @PostMapping("/{id}/cancel")
    public PaperTradeView cancel(@PathVariable UUID id) { return trades.cancel(id); }
}
