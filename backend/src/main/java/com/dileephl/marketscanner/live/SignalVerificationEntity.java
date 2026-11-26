package com.dileephl.marketscanner.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "signal_verification")
public class SignalVerificationEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "instrument_token", nullable = false)
    private long instrumentToken;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(name = "candle_start", nullable = false)
    private OffsetDateTime candleStart;

    @Column(name = "signal_type", nullable = false, length = 24)
    private String signalType;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(name = "reference_price", precision = 19, scale = 4)
    private BigDecimal referencePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal vwap;

    @Column(name = "invalidation_reference", precision = 19, scale = 4)
    private BigDecimal invalidationReference;

    @Column(name = "opening_range_high", precision = 19, scale = 4)
    private BigDecimal openingRangeHigh;

    @Column(name = "opening_range_low", precision = 19, scale = 4)
    private BigDecimal openingRangeLow;

    @Column(name = "average_volume", precision = 19, scale = 4)
    private BigDecimal averageVolume;

    @Column(name = "volume_threshold", precision = 19, scale = 4)
    private BigDecimal volumeThreshold;

    @Column(nullable = false, length = 24)
    private String decision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String analysis;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SignalVerificationEntity() {
    }

    public SignalVerificationEntity(String provider, long instrumentToken, String symbol,
                                    OffsetDateTime candleStart, String signalType,
                                    BigDecimal referencePrice, BigDecimal vwap,
                                    BigDecimal invalidationReference, BigDecimal openingRangeHigh,
                                    BigDecimal openingRangeLow, BigDecimal averageVolume,
                                    BigDecimal volumeThreshold, String analysis) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.instrumentToken = instrumentToken;
        this.symbol = symbol;
        this.candleStart = candleStart;
        this.signalType = signalType;
        this.direction = "WATCH_LONG".equals(signalType) ? "LONG" : "SHORT";
        this.referencePrice = referencePrice;
        this.vwap = vwap;
        this.invalidationReference = invalidationReference;
        this.openingRangeHigh = openingRangeHigh;
        this.openingRangeLow = openingRangeLow;
        this.averageVolume = averageVolume;
        this.volumeThreshold = volumeThreshold;
        this.decision = "RECEIVED";
        this.analysis = analysis == null ? "" : analysis;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String decision, String analysis, String orderId) {
        this.decision = decision;
        this.analysis = analysis == null ? "" : analysis;
        if (orderId != null && !orderId.isBlank()) this.orderId = orderId;
        this.updatedAt = OffsetDateTime.now();
    }

    public SignalVerificationView view() {
        return new SignalVerificationView(id, symbol, instrumentToken, candleStart, signalType, direction,
                referencePrice, vwap, invalidationReference, openingRangeHigh, openingRangeLow,
                averageVolume, volumeThreshold, decision, analysis, orderId, createdAt, updatedAt);
    }

    public String getProvider() { return provider; }
    public long getInstrumentToken() { return instrumentToken; }
    public OffsetDateTime getCandleStart() { return candleStart; }
    public String getSignalType() { return signalType; }
    public String getDecision() { return decision; }
}
