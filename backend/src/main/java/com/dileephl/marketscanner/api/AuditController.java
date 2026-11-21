package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.audit.AuditEventView;
import com.dileephl.marketscanner.audit.AuditTrailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditTrailService audit;
    public AuditController(AuditTrailService audit) { this.audit = audit; }
    @GetMapping("/events")
    public List<AuditEventView> events(@RequestParam(defaultValue = "100") int limit) { return audit.recent(limit); }
}
