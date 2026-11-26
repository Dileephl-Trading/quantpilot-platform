package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.integration.marketdata.LiveFeedStatus;
import com.dileephl.marketscanner.live.LiveMarketService;
import com.dileephl.marketscanner.live.LiveSignalView;
import com.dileephl.marketscanner.live.LiveCandleView;
import com.dileephl.marketscanner.live.LiveWatchlistView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only live market-data orchestration. No broker order endpoint exists here. */
@RestController
@RequestMapping("/api/v1/live-market")
public class LiveMarketController {
    private final LiveMarketService liveMarketService;
    public LiveMarketController(LiveMarketService liveMarketService) { this.liveMarketService = liveMarketService; }

    @PostMapping("/watchlist")
    public ResponseEntity<LiveWatchlistView> addToWatchlist(@Valid @RequestBody AddWatchlistRequest request) {
        return ResponseEntity.ok(liveMarketService.addToWatchlist(request.instrumentToken()));
    }
    @GetMapping("/watchlist") public ResponseEntity<List<LiveWatchlistView>> watchlist() { return ResponseEntity.ok(liveMarketService.watchlist()); }
    @DeleteMapping("/watchlist/{instrumentToken}")
    public ResponseEntity<Void> removeFromWatchlist(@PathVariable long instrumentToken) {
        liveMarketService.removeFromWatchlist(instrumentToken);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/watchlist/groups/{group}")
    public ResponseEntity<List<LiveWatchlistView>> addGroup(@PathVariable String group) {
        return ResponseEntity.ok(liveMarketService.addGroup(group));
    }
    @PostMapping("/start") public ResponseEntity<LiveFeedStatus> start() { return ResponseEntity.ok(liveMarketService.start()); }
    @PostMapping("/stop") public ResponseEntity<LiveFeedStatus> stop() { return ResponseEntity.ok(liveMarketService.stop()); }
    @GetMapping("/status") public ResponseEntity<LiveFeedStatus> status() { return ResponseEntity.ok(liveMarketService.status()); }
    @GetMapping("/signals") public ResponseEntity<List<LiveSignalView>> signals(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(liveMarketService.recentSignals(symbol, type, limit));
    }
    @GetMapping("/signals/page") public ResponseEntity<PagedResponse<LiveSignalView>> signalPage(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(liveMarketService.currentSignalsPage(symbol, type, page, pageSize));
    }
    @GetMapping("/candles") public ResponseEntity<List<LiveCandleView>> candles() { return ResponseEntity.ok(liveMarketService.recentCandles()); }
    @GetMapping("/candles/page") public ResponseEntity<PagedResponse<LiveCandleView>> candlePage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(liveMarketService.currentCandlesPage(page, pageSize));
    }
    @GetMapping("/verifications/page")
    public ResponseEntity<PagedResponse<com.dileephl.marketscanner.live.SignalVerificationView>> verificationPage(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(liveMarketService.verificationPage(days, page, pageSize));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(Map.of(
                "days", Math.max(1, Math.min(days, 7)),
                "watchSetups", liveMarketService.historyCandles(days)));
    }

    @GetMapping("/history/page")
    public ResponseEntity<LiveMarketService.MonitorHistoryPage> historyPage(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(liveMarketService.historyPage(days, page, pageSize));
    }

    public record AddWatchlistRequest(@Positive long instrumentToken) { }
}
