package com.dileephl.marketscanner.integration.zerodha;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "zerodha_session_token")
public class KiteSessionTokenEntity {
    @Id
    private Short id;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    protected KiteSessionTokenEntity() { }

    public KiteSessionTokenEntity(Short id, String encryptedAccessToken, Instant expiresAt, Instant now) {
        this.id = id;
        this.encryptedAccessToken = encryptedAccessToken;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
        this.connectedAt = now;
        this.status = "CONNECTED";
    }

    public Short getId() { return id; }
    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getStatus() { return status; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }

    public void setEncryptedAccessToken(String value) { encryptedAccessToken = value; }
    public void setExpiresAt(Instant value) { expiresAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
    public void setStatus(String value) { status = value; }
    public void setConnectedAt(Instant value) { connectedAt = value; }
    public void setLastValidatedAt(Instant value) { lastValidatedAt = value; }
}