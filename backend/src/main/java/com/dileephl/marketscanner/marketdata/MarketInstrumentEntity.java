package com.dileephl.marketscanner.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "market_instrument")
public class MarketInstrumentEntity {
    @Id
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private String instrumentId;
    @Column(nullable = false) private String provider;
    @Column(name = "instrument_token", nullable = false) private long instrumentToken;
    @Column(nullable = false) private String exchange;
    @Column(nullable = false) private String segment;
    @Column(nullable = false) private String tradingsymbol;
    @Column(name = "instrument_name") private String instrumentName;
    @Column(name = "instrument_type", nullable = false) private String instrumentType;
    @Column(name = "tick_size", nullable = false) private BigDecimal tickSize;
    @Column(name = "lot_size", nullable = false) private int lotSize;
    @Column(name = "last_synced_at", nullable = false) private OffsetDateTime lastSyncedAt;

    protected MarketInstrumentEntity() { }

    public MarketInstrumentEntity(String provider, long instrumentToken, String exchange, String segment,
                                  String tradingsymbol, String instrumentName, String instrumentType,
                                  BigDecimal tickSize, int lotSize, OffsetDateTime lastSyncedAt) {
        this.instrumentId = provider + ":" + exchange + ":" + tradingsymbol;
        this.provider = provider;
        this.instrumentToken = instrumentToken;
        this.exchange = exchange;
        this.segment = segment;
        this.tradingsymbol = tradingsymbol;
        this.instrumentName = instrumentName;
        this.instrumentType = instrumentType;
        this.tickSize = tickSize;
        this.lotSize = lotSize;
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getProvider() { return provider; }
    public long getInstrumentToken() { return instrumentToken; }
    public String getExchange() { return exchange; }
    public String getSegment() { return segment; }
    public String getTradingsymbol() { return tradingsymbol; }
    public String getInstrumentName() { return instrumentName; }
    public String getInstrumentType() { return instrumentType; }
    public BigDecimal getTickSize() { return tickSize; }
    public int getLotSize() { return lotSize; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
}
