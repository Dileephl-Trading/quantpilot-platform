package com.dileephl.marketscanner.integration.zerodha;

import java.time.Instant;

/** Safe-to-display integration state. Credential values are never present here. */
public record KiteIntegrationStatus(
        String provider,
        String state,
        boolean marketDataEnabled,
        boolean apiKeyConfigured,
        boolean apiSecretConfigured,
        boolean accessTokenConfigured,
        String redirectUrl,
        String executionState,
        String nextStep,
        String sessionExpiresAt,
        String sessionStatus) { }