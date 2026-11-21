package com.dileephl.marketscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Separate production arming switch for broker order routing.
 * It is intentionally false by default and must be enabled only after a reviewed restart.
 */
@ConfigurationProperties(prefix = "scanner.live-execution")
public class LiveExecutionProperties {
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
