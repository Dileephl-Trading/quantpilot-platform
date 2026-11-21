package com.dileephl.marketscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quantpilot.security")
public record QuantPilotSecurityProperties(
        boolean enabled,
        String allowedOrigin,
        int requestsPerMinute
) {
    public QuantPilotSecurityProperties {
        allowedOrigin = allowedOrigin == null ? "http://127.0.0.1:8080" : allowedOrigin.trim();
        requestsPerMinute = requestsPerMinute > 0 ? requestsPerMinute : 120;
    }
}
