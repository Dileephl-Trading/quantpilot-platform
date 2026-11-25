package com.dileephl.marketscanner.live;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "market_watchlist_entry")
public class WatchlistEntryEntity {
    @Id private UUID id;
    private String provider;
    private long instrumentToken;
    private String symbol;
    private boolean enabled;
    @Column(name = "auto_managed", nullable = false)
    private boolean autoManaged;
    private OffsetDateTime addedAt;

    protected WatchlistEntryEntity() { }
    public WatchlistEntryEntity(String provider, long instrumentToken, String symbol) {
        this.id = UUID.randomUUID(); this.provider = provider; this.instrumentToken = instrumentToken;
        this.symbol = symbol; this.enabled = true; this.addedAt = OffsetDateTime.now();
    }
    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public long getInstrumentToken() { return instrumentToken; }
    public String getSymbol() { return symbol; }
    public boolean isEnabled() { return enabled; }
    public boolean isAutoManaged() { return autoManaged; }
    public OffsetDateTime getAddedAt() { return addedAt; }
    public void disable() { enabled = false; }
    public void enable() { enabled = true; addedAt = OffsetDateTime.now(); }
    public void markAutoManaged() { autoManaged = true; }
    public void markUserManaged() { autoManaged = false; }
}
