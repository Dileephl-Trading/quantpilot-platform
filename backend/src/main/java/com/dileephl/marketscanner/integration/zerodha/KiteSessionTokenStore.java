package com.dileephl.marketscanner.integration.zerodha;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Stores the daily Kite access token encrypted at rest. The encryption key is supplied only
 * through the local environment and is never persisted in PostgreSQL or returned by an API.
 */
@Component
public class KiteSessionTokenStore {
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final JdbcTemplate jdbcTemplate;
    private final KiteConnectProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public KiteSessionTokenStore(JdbcTemplate jdbcTemplate, KiteConnectProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Optional<String> loadValid() {
        var rows = jdbcTemplate.query(
                "SELECT encrypted_access_token, expires_at FROM zerodha_session_token WHERE id = 1",
                (resultSet, rowNum) -> new StoredToken(resultSet.getString("encrypted_access_token"),
                        resultSet.getTimestamp("expires_at").toInstant()));
        if (rows.isEmpty()) return Optional.empty();
        StoredToken stored = rows.getFirst();
        if (!stored.expiresAt().isAfter(Instant.now())) {
            clear();
            return Optional.empty();
        }
        try {
            return Optional.of(decrypt(stored.encryptedValue()));
        } catch (RuntimeException error) {
            clear();
            throw new IllegalStateException("Stored Kite session token could not be decrypted; connect Kite again", error);
        }
    }

    public void save(String accessToken, Instant expiresAt) {
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("Kite access token is required");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) throw new IllegalArgumentException("Kite access token expiry must be in the future");
        String encrypted = encrypt(accessToken);
        jdbcTemplate.update("""
                INSERT INTO zerodha_session_token (id, encrypted_access_token, expires_at)
                VALUES (1, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    encrypted_access_token = EXCLUDED.encrypted_access_token,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = CURRENT_TIMESTAMP
                """, encrypted, java.sql.Timestamp.from(expiresAt));
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM zerodha_session_token WHERE id = 1");
    }

    public Optional<Instant> expiry() {
        var rows = jdbcTemplate.query("SELECT expires_at FROM zerodha_session_token WHERE id = 1",
                (resultSet, rowNum) -> resultSet.getTimestamp("expires_at").toInstant());
        return rows.stream().findFirst();
    }

    private String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt Kite session token", error);
        }
    }

    private String decrypt(String encoded) {
        try {
            if (encoded == null || !encoded.startsWith("v1:")) throw new IllegalArgumentException("Unsupported encrypted token format");
            byte[] payload = Base64.getDecoder().decode(encoded.substring(3));
            if (payload.length <= IV_BYTES) throw new IllegalArgumentException("Encrypted token is incomplete");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to decrypt Kite session token", error);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.getSessionEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("KITE_SESSION_ENCRYPTION_KEY is not configured");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("KITE_SESSION_ENCRYPTION_KEY must be base64 encoded", error);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("KITE_SESSION_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private record StoredToken(String encryptedValue, Instant expiresAt) {}
}
