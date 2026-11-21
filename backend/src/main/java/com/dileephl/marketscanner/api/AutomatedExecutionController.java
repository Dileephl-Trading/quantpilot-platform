package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.execution.AutomatedExecutionService;
import com.dileephl.marketscanner.execution.LiveExecutionPreflightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation")
public class AutomatedExecutionController {
    private final AutomatedExecutionService automation;
    private final LiveExecutionPreflightService preflight;

    public AutomatedExecutionController(AutomatedExecutionService automation, LiveExecutionPreflightService preflight) {
        this.automation = automation;
        this.preflight = preflight;
    }

    @GetMapping("/status")
    public AutomatedExecutionService.AutomatedStatus status() { return automation.status(); }

    @GetMapping("/preflight")
    public LiveExecutionPreflightService.Preflight preflight() { return preflight.run(); }

    @PostMapping("/recover")
    public AutomatedExecutionService.AutomatedStatus recover() { return automation.recoverAfterReconciliation(); }
}
