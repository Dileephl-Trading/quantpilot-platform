package com.dileephl.marketscanner.integration.zerodha;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles Kite's daily OAuth-style login. The access token is encrypted at rest in PostgreSQL
 * and kept in memory for request use; it is never returned to the frontend.
 */
@Service
public class KiteAuthService {
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    private final KiteConnectProperties properties;
    private final KiteSessionTokenStore sessionTokenStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicReference<String> currentAccessToken;

    public KiteAuthService(KiteConnectProperties properties,
                           KiteSessionTokenStore sessionTokenStore,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.sessionTokenStore = sessionTokenStore;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.currentAccessToken = new AtomicReference<>(sessionTokenStore.loadValid().orElse(null));
    }

    public KiteIntegrationStatus status() {
        String state;
        String nextStep;
        if (!properties.hasApiKey()) {
            state = "NOT_CONFIGURED";
            nextStep = "Create a Kite Connect app, then add only KITE_API_KEY to your local .env file.";
        } else if (!properties.hasApiSecret()) {
            state = "AUTH_CONFIGURATION_INCOMPLETE";
            nextStep = "Add KITE_API_SECRET locally before completing the login-token exchange.";
        } else if (!hasCurrentAccessToken()) {
            state = "READY_FOR_LOGIN";
            nextStep = "Complete the daily Kite login flow to obtain a session access token.";
        } else if (!properties.isMarketDataEnabled()) {
            state = "CONFIGURED_BUT_DISABLED";
            nextStep = "Enable only KITE_MARKET_DATA_ENABLED after validating the session and data connection.";
        } else {
            state = "READY_FOR_MARKET_DATA";
            nextStep = "Market-data endpoints may be used. Broker order routing remains disabled until the separate execution gate is enabled.";
        }
        return new KiteIntegrationStatus("ZERODHA_KITE", state, properties.isMarketDataEnabled(),
                properties.hasApiKey(), properties.hasApiSecret(), hasCurrentAccessToken(),
                properties.getRedirectUrl(), properties.isOrderRoutingEnabled() ? "CONFIGURED_BUT_GATED" : "DISABLED",
                nextStep, sessionTokenStore.expiry().filter(value -> value.isAfter(Instant.now())).map(Instant::toString).orElse(null));
    }

    public String loginUrl() {
        require(properties.hasApiKey(), "KITE_API_KEY is not configured");
        return UriComponentsBuilder.fromUriString(properties.getLoginBaseUrl())
                .path("/connect/login")
                .queryParam("v", "3")
                .queryParam("api_key", properties.getApiKey())
                .build()
                .encode()
                .toUriString();
    }

    /** Kite requires SHA-256(api_key + request_token + api_secret) for session-token exchange. */
    public String sessionChecksum(String requestToken) {
        require(properties.hasApiKey() && properties.hasApiSecret(), "Kite API key and secret are required");
        require(requestToken != null && !requestToken.isBlank(), "request token is required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((properties.getApiKey() + requestToken + properties.getApiSecret())
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder();
            for (byte value : digest) encoded.append(String.format("%02x", value));
            return encoded.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /** Exchanges Kite's single-use request token and persists today's encrypted access token. */
    public void completeLogin(String requestToken) {
        require(properties.hasApiKey() && properties.hasApiSecret(), "Kite API key and secret are required");
        require(requestToken != null && !requestToken.isBlank(), "Kite did not return a request token");

        String body = "api_key=" + formValue(properties.getApiKey())
                + "&request_token=" + formValue(requestToken)
                + "&checksum=" + formValue(sessionChecksum(requestToken));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + "/session/token"))
                .header("X-Kite-Version", "3")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Kite session exchange failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String accessToken = root.path("data").path("access_token").asText();
            if (accessToken.isBlank()) throw new IllegalStateException("Kite session exchange did not return an access token");
            Instant expiresAt = nextKiteExpiry();
            sessionTokenStore.save(accessToken, expiresAt);
            currentAccessToken.set(accessToken);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to complete Kite login", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kite login was interrupted", error);
        }
    }

    public String currentAccessToken() {
        if (!hasCurrentAccessToken()) throw new IllegalStateException("Complete today's Kite login before requesting market data");
        return currentAccessToken.get();
    }

    public void clearCurrentAccessToken() {
        currentAccessToken.set(null);
        sessionTokenStore.clear();
    }

    public boolean hasCurrentAccessToken() {
        String token = currentAccessToken.get();
        return token != null && !token.isBlank();
    }

    private Instant nextKiteExpiry() {
        ZonedDateTime now = ZonedDateTime.now(INDIA_ZONE);
        ZonedDateTime expiry = now.with(LocalTime.of(6, 0));
        if (!now.isBefore(expiry)) expiry = expiry.plusDays(1);
        return expiry.toInstant();
    }

    private String formValue(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
