package com.dileephl.marketscanner.integration.zerodha;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;

@Service
public class KiteAuthService {
    private static final short SESSION_ID = 1;
    private static final String CONNECTED = "CONNECTED";
    private static final String EXPIRED = "EXPIRED";
    private static final String INVALID = "INVALID";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final KiteConnectProperties properties;
    private final KiteSessionTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecureRandom secureRandom = new SecureRandom();

    public KiteAuthService(KiteConnectProperties properties, KiteSessionTokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KiteIntegrationStatus status() {
        KiteSessionTokenEntity token = tokenRepository.findById(SESSION_ID).orElse(null);
        if (token != null && CONNECTED.equals(token.getStatus()) && isExpired(token)) {
            markExpired(token);
        }

        boolean active = token != null && CONNECTED.equals(token.getStatus()) && !isExpired(token);
        String state;
        String nextStep;
        if (!properties.hasApiKey()) {
            state = "NOT_CONFIGURED";
            nextStep = "Create a Kite Connect app, then add only KITE_API_KEY to your local .env file.";
        } else if (!properties.hasApiSecret()) {
            state = "AUTH_CONFIGURATION_INCOMPLETE";
            nextStep = "Add KITE_API_SECRET locally before completing the login-token exchange.";
        } else if (token == null) {
            state = "READY_FOR_LOGIN";
            nextStep = "Complete the daily Kite login flow to obtain a session access token.";
        } else if (EXPIRED.equals(token.getStatus())) {
            state = "SESSION_EXPIRED";
            nextStep = "Reconnect Kite to obtain a fresh daily session token.";
        } else if (INVALID.equals(token.getStatus())) {
            state = "SESSION_INVALID";
            nextStep = "Reconnect Kite to replace the rejected session token.";
        } else if (!properties.isMarketDataEnabled()) {
            state = "CONFIGURED_BUT_DISABLED";
            nextStep = "Enable only KITE_MARKET_DATA_ENABLED after validating the session and data connection.";
        } else {
            state = "READY_FOR_MARKET_DATA";
            nextStep = "Market-data endpoints may be used. Broker order routing remains disabled until the separate execution gate is enabled.";
        }

        return new KiteIntegrationStatus("ZERODHA_KITE", state, properties.isMarketDataEnabled(),
                properties.hasApiKey(), properties.hasApiSecret(), active, properties.getRedirectUrl(),
                properties.isOrderRoutingEnabled() ? "CONFIGURED_BUT_GATED" : "DISABLED", nextStep,
                token == null ? null : token.getExpiresAt().toString(),
                token == null ? null : token.getStatus());
    }

    public String loginUrl() {
        require(properties.hasApiKey(), "KITE_API_KEY is not configured");
        return UriComponentsBuilder.fromUriString(properties.getLoginBaseUrl())
                .path("/connect/login").queryParam("v", "3").queryParam("api_key", properties.getApiKey())
                .build().encode().toUriString();
    }

    public String sessionChecksum(String requestToken) {
        require(properties.hasApiKey() && properties.hasApiSecret(), "Kite API key and secret are required");
        require(requestToken != null && !requestToken.isBlank(), "request token is required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((properties.getApiKey() + requestToken + properties.getApiSecret()).getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder();
            for (byte value : digest) encoded.append(String.format("%02x", value));
            return encoded.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    @Transactional
    public void completeLogin(String requestToken) {
        require(properties.hasApiKey() && properties.hasApiSecret(), "Kite API key and secret are required");
        require(requestToken != null && !requestToken.isBlank(), "Kite did not return a request token");

        String body = "api_key=" + formValue(properties.getApiKey()) + "&request_token=" + formValue(requestToken)
                + "&checksum=" + formValue(sessionChecksum(requestToken));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + "/session/token"))
                .header("X-Kite-Version", "3").header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Kite session exchange failed with HTTP " + response.statusCode());
            }
            String accessToken = objectMapper.readTree(response.body()).path("data").path("access_token").asText();
            require(!accessToken.isBlank(), "Kite session exchange did not return an access token");

            Instant now = Instant.now();
            KiteSessionTokenEntity token = tokenRepository.findById(SESSION_ID)
                    .orElseGet(() -> new KiteSessionTokenEntity(SESSION_ID, "", nextKiteExpiry(now), now));
            token.setEncryptedAccessToken(encrypt(accessToken));
            token.setConnectedAt(now);
            token.setExpiresAt(nextKiteExpiry(now));
            token.setStatus(CONNECTED);
            token.setLastValidatedAt(now);
            token.setUpdatedAt(now);
            tokenRepository.save(token);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to complete Kite login", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kite login was interrupted", error);
        }
    }

    public String currentAccessToken() {
        KiteSessionTokenEntity token = tokenRepository.findById(SESSION_ID).orElse(null);
        if (token == null || !CONNECTED.equals(token.getStatus()) || isExpired(token)) {
            if (token != null && isExpired(token)) markExpired(token);
            throw new IllegalStateException("Complete today's Kite login before requesting market data");
        }
        return decrypt(token.getEncryptedAccessToken());
    }

    public boolean hasCurrentAccessToken() {
        KiteSessionTokenEntity token = tokenRepository.findById(SESSION_ID).orElse(null);
        return token != null && CONNECTED.equals(token.getStatus()) && !isExpired(token);
    }

    @Transactional
    public void markInvalid() {
        tokenRepository.findById(SESSION_ID).ifPresent(token -> {
            token.setStatus(INVALID);
            token.setUpdatedAt(Instant.now());
            tokenRepository.save(token);
        });
    }

    @Scheduled(fixedDelayString = "${scanner.zerodha.session-expiry-check-ms:60000}")
    @Transactional
    public void expireStaleSession() {
        tokenRepository.findById(SESSION_ID).ifPresent(token -> {
            if (CONNECTED.equals(token.getStatus()) && isExpired(token)) markExpired(token);
        });
    }

    private Instant nextKiteExpiry(Instant now) {
        ZonedDateTime current = now.atZone(INDIA);
        LocalDate date = current.toLocalTime().isBefore(LocalTime.of(6, 0))
                ? current.toLocalDate() : current.toLocalDate().plusDays(1);
        return date.atTime(6, 0).atZone(INDIA).toInstant();
    }

    private boolean isExpired(KiteSessionTokenEntity token) {
        return !Instant.now().isBefore(token.getExpiresAt());
    }

    private void markExpired(KiteSessionTokenEntity token) {
        token.setStatus(EXPIRED);
        token.setUpdatedAt(Instant.now());
        tokenRepository.save(token);
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return Base64.getEncoder().encodeToString(iv) + ":" +
                    Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt Kite session token", error);
        }
    }

    private String decrypt(String value) {
        try {
            String[] parts = value.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to decrypt Kite session token", error);
        }
    }

    private SecretKeySpec key() {
        require(properties.getSessionEncryptionKey() != null && !properties.getSessionEncryptionKey().isBlank(),
                "KITE_SESSION_ENCRYPTION_KEY is not configured");
        byte[] decoded = Base64.getDecoder().decode(properties.getSessionEncryptionKey());
        require(decoded.length == 32, "KITE_SESSION_ENCRYPTION_KEY must decode to 32 bytes");
        return new SecretKeySpec(decoded, "AES");
    }

    private String formValue(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}