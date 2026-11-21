package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.execution.SafetyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/safety")
public class SafetyController {
    private final SafetyService safety;
    public SafetyController(SafetyService safety) { this.safety = safety; }

    @GetMapping("/status")
    public ResponseEntity<SafetyService.SafetyState> status() { return ResponseEntity.ok(safety.state()); }

    @PostMapping("/kill-switch/enable")
    public ResponseEntity<SafetyService.SafetyState> enable(@RequestBody(required = false) ReasonRequest request) {
        return ResponseEntity.ok(safety.enable(request == null ? null : request.reason()));
    }

    @PostMapping("/kill-switch/disable")
    public ResponseEntity<SafetyService.SafetyState> disable(@RequestBody(required = false) ReasonRequest request) {
        return ResponseEntity.ok(safety.disable(request == null ? null : request.reason()));
    }

    public record ReasonRequest(String reason) { }
}
