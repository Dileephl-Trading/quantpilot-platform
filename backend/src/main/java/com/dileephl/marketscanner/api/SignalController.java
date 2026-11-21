package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.service.SignalEngine;
import com.dileephl.marketscanner.service.SignalEngine.Candle;
import com.dileephl.marketscanner.service.SignalEngine.TradeSignal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/signals")
public class SignalController {
    private final SignalEngine signalEngine;

    public SignalController(SignalEngine signalEngine) { this.signalEngine = signalEngine; }

    @PostMapping("/evaluate")
    public ResponseEntity<TradeSignal> evaluate(@Valid @RequestBody SignalEvaluationRequest request) {
        return ResponseEntity.ok(signalEngine.evaluate(request.symbol(), request.candles()));
    }

    public record SignalEvaluationRequest(
            @NotBlank String symbol,
            @NotEmpty List<Candle> candles) { }
}
