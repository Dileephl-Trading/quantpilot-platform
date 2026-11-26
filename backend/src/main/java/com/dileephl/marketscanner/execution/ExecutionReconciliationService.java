package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


/** Polls broker orders because a local-only deployment cannot receive Kite postbacks. */
@Service
public class ExecutionReconciliationService {
    private final TradingProfileService profiles;
    private final KiteConnectProperties properties;
    private final ExecutionRequestRepository requests;
    private final BrokerOrderExecutionService execution;

    public ExecutionReconciliationService(TradingProfileService profiles, KiteConnectProperties properties,
                                          ExecutionRequestRepository requests, BrokerOrderExecutionService execution) {
        this.profiles = profiles;
        this.properties = properties;
        this.requests = requests;
        this.execution = execution;
    }

    @Scheduled(fixedDelayString = "${scanner.execution.status-poll-ms:5000}")
    public void pollOpenOrders() {
        if (profiles.current().mode() != TradingMode.LIVE || !properties.isOrderRoutingEnabled()
                || !properties.hasApiKey() || !properties.hasApiSecret()) return;
        try {
            execution.reconcileTrackedOrders();
        } catch (RuntimeException ignored) {
            // A transient broker/network error must not stop the scheduler. The next poll retries it.
        }
    }
}
