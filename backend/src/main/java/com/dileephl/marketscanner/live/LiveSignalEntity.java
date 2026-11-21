package com.dileephl.marketscanner.live;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_signal")
public class LiveSignalEntity {
    @Id private UUID id;
    private String provider;
    private long instrumentToken;
    private String symbol;
    private OffsetDateTime candleStart;
    private String signalType;
    private BigDecimal referencePrice;
    private BigDecimal vwap;
    private BigDecimal openingRangeHigh;
    private BigDecimal openingRangeLow;
    private BigDecimal averageVolume;
    private BigDecimal volumeThreshold;
    private BigDecimal invalidationReference;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reasons;
    private OffsetDateTime generatedAt;
    protected LiveSignalEntity() { }
    public LiveSignalEntity(String provider, long instrumentToken, String symbol, OffsetDateTime candleStart,
                            String signalType, BigDecimal referencePrice, BigDecimal vwap,
                            BigDecimal invalidationReference, BigDecimal openingRangeHigh, BigDecimal openingRangeLow,
                            BigDecimal averageVolume, BigDecimal volumeThreshold, String reasons) {
        this.id = UUID.randomUUID(); this.provider = provider; this.instrumentToken = instrumentToken; this.symbol = symbol;
        this.candleStart = candleStart; this.signalType = signalType; this.referencePrice = referencePrice;
        this.vwap = vwap; this.invalidationReference = invalidationReference; this.reasons = reasons;
        this.openingRangeHigh = openingRangeHigh; this.openingRangeLow = openingRangeLow;
        this.averageVolume = averageVolume; this.volumeThreshold = volumeThreshold;
        this.generatedAt = OffsetDateTime.now();
    }
    public String getProvider() { return provider; } public long getInstrumentToken() { return instrumentToken; }
    public String getSymbol() { return symbol; } public OffsetDateTime getCandleStart() { return candleStart; }
    public String getSignalType() { return signalType; } public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getVwap() { return vwap; } public BigDecimal getInvalidationReference() { return invalidationReference; }
    public BigDecimal getOpeningRangeHigh() { return openingRangeHigh; }
    public BigDecimal getOpeningRangeLow() { return openingRangeLow; }
    public BigDecimal getAverageVolume() { return averageVolume; }
    public BigDecimal getVolumeThreshold() { return volumeThreshold; }
    public String getReasons() { return reasons; } public OffsetDateTime getGeneratedAt() { return generatedAt; }
}
