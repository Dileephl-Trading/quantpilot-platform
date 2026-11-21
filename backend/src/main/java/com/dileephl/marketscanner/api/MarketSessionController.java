package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.config.MarketSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-session")
public class MarketSessionController {
    private final MarketSessionService service;

    public MarketSessionController(MarketSessionService service) { this.service = service; }

    @GetMapping("/status")
    public ResponseEntity<MarketSessionService.MarketSessionStatus> status() {
        return ResponseEntity.ok(service.status());
    }
}
