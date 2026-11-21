package com.dileephl.marketscanner.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "trading_profile")
@Table(name = "trading_profile")
class TradingProfileEntity {
    @Id
    private Long id;
    private String mode;
    private String broker;
    private String exchange;
    private String segment;
    private String product;
    private BigDecimal allocatedCapital;
    private BigDecimal maxLossPerTrade;
    private BigDecimal maxDailyLoss;
    private int maxTradesPerDay;
    private int maxOpenPositions;
    private int cooldownSeconds;
    private boolean killSwitchEnabled;

    protected TradingProfileEntity() { }

    static TradingProfileEntity from(TradingProfile profile) {
        TradingProfileEntity entity = new TradingProfileEntity();
        entity.id = 1L;
        entity.update(profile);
        return entity;
    }

    void update(TradingProfile profile) {
        mode = profile.mode().name(); broker = profile.broker(); exchange = profile.exchange();
        segment = profile.segment(); product = profile.product(); allocatedCapital = profile.allocatedCapital();
        maxLossPerTrade = profile.maxLossPerTrade(); maxDailyLoss = profile.maxDailyLoss();
        maxTradesPerDay = profile.maxTradesPerDay(); maxOpenPositions = profile.maxOpenPositions();
        cooldownSeconds = profile.cooldownSeconds();
        killSwitchEnabled = profile.killSwitchEnabled();
    }

    TradingProfile toProfile() {
        return new TradingProfile(TradingMode.valueOf(mode), broker, exchange, segment, product,
                allocatedCapital, maxLossPerTrade, maxDailyLoss, maxTradesPerDay, maxOpenPositions, cooldownSeconds, killSwitchEnabled);
    }
}
