package com.dileephl.marketscanner.integration.zerodha;

/** Safe-to-display integration state. No credential values are present here. */
public record KiteIntegrationStatus(
        String provider,
        String state,
        boolean marketDataEnabled,
        boolean apiKeyConfigured,
        boolean apiSecretConfigured,
        boolean accessTokenConfigured,
        String redirectUrl,
        String executionState,
        String nextStep) { }
