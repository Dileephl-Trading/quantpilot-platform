package com.dileephl.marketscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Explicit, fail-closed controls for signal-to-order automation. */
@ConfigurationProperties(prefix = "scanner.execution")
public class AutomatedExecutionProperties {
    private boolean enabled;
    private long statusPollMs = 5000;
    private int maxSignalAgeSeconds = 60;
    private long maxOrderQuantity = 1;
    private String orderType = "LIMIT";
    private BigDecimal targetRiskReward = new BigDecimal("2.0");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getStatusPollMs() { return statusPollMs; }
    public void setStatusPollMs(long statusPollMs) { this.statusPollMs = statusPollMs; }
    public int getMaxSignalAgeSeconds() { return maxSignalAgeSeconds; }
    public void setMaxSignalAgeSeconds(int value) { maxSignalAgeSeconds = value; }
    public long getMaxOrderQuantity() { return maxOrderQuantity; }
    public void setMaxOrderQuantity(long value) { maxOrderQuantity = value; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String value) { orderType = value == null ? "LIMIT" : value.trim().toUpperCase(); }
    public BigDecimal getTargetRiskReward() { return targetRiskReward; }
    public void setTargetRiskReward(BigDecimal value) {
        targetRiskReward = value == null ? new BigDecimal("2.0") : value;
    }

    public void validate() {
        if (statusPollMs < 1000 || maxSignalAgeSeconds <= 0 || maxOrderQuantity <= 0
                || !java.util.List.of("LIMIT", "MARKET").contains(orderType)
                || targetRiskReward.signum() <= 0 || targetRiskReward.compareTo(new BigDecimal("10")) > 0) {
            throw new IllegalArgumentException("Invalid automated execution configuration");
        }
    }
}
