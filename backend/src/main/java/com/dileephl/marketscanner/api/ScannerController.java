package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.config.ScannerProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/scanner")
public class ScannerController {
    private final ScannerProperties properties;
    public ScannerController(ScannerProperties properties) { this.properties = properties; }

    @GetMapping("/config")
    public ResponseEntity<ScannerConfigView> config() {
        return ResponseEntity.ok(new ScannerConfigView(properties.isEnabled(), properties.getCandleInterval(), properties.getOpeningRangeCandles(), properties.getVolumeLookback(),
                properties.getVolumeMultiplier(), properties.getMinimumCandles(), properties.getGroups()));
    }

    @PutMapping("/config")
    public ResponseEntity<ScannerConfigView> update(@RequestBody UpdateScannerConfig request) {
        if (request == null) throw new IllegalArgumentException("Scanner configuration is required");
        properties.setEnabled(request.enabled());
        properties.setCandleInterval(request.candleInterval());
        properties.setOpeningRangeCandles(request.openingRangeCandles());
        properties.setVolumeLookback(request.volumeLookback());
        properties.setVolumeMultiplier(request.volumeMultiplier());
        properties.setMinimumCandles(request.minimumCandles());
        properties.validate();
        return config();
    }

    public record ScannerConfigView(boolean enabled, com.dileephl.marketscanner.integration.marketdata.CandleInterval candleInterval,
                                    int openingRangeCandles, int volumeLookback, BigDecimal volumeMultiplier,
                                    int minimumCandles, Map<String, Set<String>> groups) { }

    public record UpdateScannerConfig(boolean enabled,
                                      com.dileephl.marketscanner.integration.marketdata.CandleInterval candleInterval,
                                      int openingRangeCandles, int volumeLookback, BigDecimal volumeMultiplier,
                                      int minimumCandles) { }
}
