package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.execution.AutomatedExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation")
public class AutomatedExecutionController {
    private final AutomatedExecutionService automation;

    public AutomatedExecutionController(AutomatedExecutionService automation) { this.automation = automation; }

    @GetMapping("/status")
    public AutomatedExecutionService.AutomatedStatus status() { return automation.status(); }

    @PostMapping("/recover")
    public AutomatedExecutionService.AutomatedStatus recover() { return automation.recoverAfterReconciliation(); }
}
