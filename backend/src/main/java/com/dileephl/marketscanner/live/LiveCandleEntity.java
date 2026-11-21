package com.dileephl.marketscanner.live;

import com.dileephl.marketscanner.service.SignalEngine.Candle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_candle")
public class LiveCandleEntity {
    @Id private UUID id;
    private String provider;
    private long instrumentToken;
    private String symbol;
    private OffsetDateTime candleStart;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private long volume;
    private String evaluationType;
    private BigDecimal referencePrice;
    private BigDecimal vwap;
    private BigDecimal openingRangeHigh;
    private BigDecimal openingRangeLow;
    private BigDecimal averageVolume;
    private BigDecimal volumeThreshold;
    private BigDecimal invalidationReference;
    @Column(columnDefinition = "TEXT", nullable = false) private String reasons;
    private OffsetDateTime generatedAt;

    protected LiveCandleEntity() { }

    public LiveCandleEntity(String provider, long instrumentToken, String symbol, Candle candle,
                            String evaluationType, BigDecimal referencePrice, BigDecimal vwap,
                            BigDecimal invalidationReference, BigDecimal openingRangeHigh, BigDecimal openingRangeLow,
                            BigDecimal averageVolume, BigDecimal volumeThreshold, String reasons) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.instrumentToken = instrumentToken;
        this.symbol = symbol;
        this.candleStart = candle.timestamp();
        this.openPrice = candle.open();
        this.highPrice = candle.high();
        this.lowPrice = candle.low();
        this.closePrice = candle.close();
        this.volume = candle.volume();
        this.evaluationType = evaluationType;
        this.referencePrice = referencePrice;
        this.vwap = vwap;
        this.openingRangeHigh = openingRangeHigh;
        this.openingRangeLow = openingRangeLow;
        this.averageVolume = averageVolume;
        this.volumeThreshold = volumeThreshold;
        this.invalidationReference = invalidationReference;
        this.reasons = reasons;
        this.generatedAt = OffsetDateTime.now();
    }

    public String getSymbol() { return symbol; }
    public long getInstrumentToken() { return instrumentToken; }
    public OffsetDateTime getCandleStart() { return candleStart; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public long getVolume() { return volume; }
    public String getEvaluationType() { return evaluationType; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getVwap() { return vwap; }
    public BigDecimal getOpeningRangeHigh() { return openingRangeHigh; }
    public BigDecimal getOpeningRangeLow() { return openingRangeLow; }
    public BigDecimal getAverageVolume() { return averageVolume; }
    public BigDecimal getVolumeThreshold() { return volumeThreshold; }
    public BigDecimal getInvalidationReference() { return invalidationReference; }
    public String getReasons() { return reasons; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
}
