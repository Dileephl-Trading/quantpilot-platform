package com.dileephl.marketscanner.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final QuantPilotSecurityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ApiRateLimitFilter(QuantPilotSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ApiRateLimitFilter(QuantPilotSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled() || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        long now = clock.instant().getEpochSecond();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= 60) {
                return new Window(now, new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });

        if (window.count().get() > properties.requestsPerMinute()) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many API requests\"}");
            return;
        }

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 120);
        }
        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(long startedAt, AtomicInteger count) {}
}
