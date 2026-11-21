package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/** Polls broker orders because a local-only deployment cannot receive Kite postbacks. */
@Service
public class ExecutionReconciliationService {
    private final TradingProfileService profiles;
    private final KiteConnectProperties properties;
    private final ExecutionRequestRepository requests;
    private final AutomationSafetyStateRepository safetyStates;
    private final AuditTrailService audit;
    private final BrokerOrderExecutionService execution;

    public ExecutionReconciliationService(TradingProfileService profiles, KiteConnectProperties properties,
                                          ExecutionRequestRepository requests, BrokerOrderExecutionService execution,
                                          AutomationSafetyStateRepository safetyStates, AuditTrailService audit) {
        this.profiles = profiles;
        this.properties = properties;
        this.requests = requests;
        this.execution = execution;
        this.safetyStates = safetyStates;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${scanner.execution.status-poll-ms:5000}")
    public void pollOpenOrders() {
        if (profiles.current().mode() != TradingMode.LIVE || !properties.isOrderRoutingEnabled()
                || !properties.hasApiKey() || !properties.hasApiSecret()) return;
        List<ExecutionRequestEntity> open = requests.findByStatusIn(List.of("PENDING", "OPEN", "PARTIALLY_FILLED"));
        for (ExecutionRequestEntity request : open) {
            try {
                execution.reconcile(request);
            } catch (RuntimeException error) {
                AutomationSafetyStateEntity state = safetyStates.findById(1)
                        .orElseGet(() -> AutomationSafetyStateEntity.initial());
                state.markUncertain(error.getMessage() == null ? "Broker reconciliation failed" : error.getMessage());
                safetyStates.save(state);
                audit.record("BROKER_RECONCILIATION_UNCERTAIN", "AUTOMATION_SAFETY", "GLOBAL",
                        "orderId=" + request.orderId() + ", reason=" + error.getMessage());
                // Stop further automated submissions until the broker state is explicitly reconciled.
            }
        }
    }
}
