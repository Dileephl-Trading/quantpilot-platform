package com.dileephl.marketscanner.config;

import com.dileephl.marketscanner.audit.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import java.math.BigDecimal;

@Service
public class TradingProfileService {
    private static final long PROFILE_ID = 1L;
    private final TradingProfileRepository repository;
    private final AuditTrailService auditTrail;
    private final KiteConnectProperties kiteProperties;
    private final LiveExecutionProperties liveExecutionProperties;

    public TradingProfileService(TradingProfileRepository repository, AuditTrailService auditTrail,
                                 KiteConnectProperties kiteProperties,
                                 LiveExecutionProperties liveExecutionProperties) {
        this.repository = repository;
        this.auditTrail = auditTrail;
        this.kiteProperties = kiteProperties;
        this.liveExecutionProperties = liveExecutionProperties;
    }

    @Transactional
    public TradingProfile current() {
        return repository.findById(PROFILE_ID).orElseGet(() -> repository.save(TradingProfileEntity.from(defaultProfile()))).toProfile();
    }

    /** Serializes decisions that consume the shared daily risk and position limits. */
    @Transactional
    public TradingProfile currentForRiskDecision() {
        return repository.findByIdForUpdate(PROFILE_ID)
                .orElseGet(() -> repository.save(TradingProfileEntity.from(defaultProfile())))
                .toProfile();
    }

    @Transactional
    public TradingProfile update(TradingProfile profile) {
        validate(profile);
        TradingProfileEntity entity = repository.findByIdForUpdate(PROFILE_ID).orElseGet(() -> TradingProfileEntity.from(profile));
        entity.update(profile);
        TradingProfile updated = repository.save(entity).toProfile();
        auditTrail.record("TRADING_PROFILE_UPDATED", "TRADING_PROFILE", String.valueOf(PROFILE_ID),
                "mode=" + updated.mode() + ", capital=" + updated.allocatedCapital()
                        + ", maxLossPerTrade=" + updated.maxLossPerTrade()
                        + ", maxDailyLoss=" + updated.maxDailyLoss()
                        + ", killSwitch=" + updated.killSwitchEnabled());
        return updated;
    }

    @Transactional
    public TradingProfile setKillSwitch(boolean enabled) {
        TradingProfile current = currentForRiskDecision();
        TradingProfile updated = new TradingProfile(current.mode(), current.broker(), current.exchange(), current.segment(),
                current.product(), current.allocatedCapital(), current.maxLossPerTrade(), current.maxDailyLoss(),
                current.maxTradesPerDay(), current.maxOpenPositions(), current.cooldownSeconds(), enabled);
        return update(updated);
    }

    private TradingProfile defaultProfile() {
        return new TradingProfile(TradingMode.PAPER, "GENERIC", "NSE", "CASH", "MIS",
                new BigDecimal("50000"), new BigDecimal("1000"), new BigDecimal("2000"), 2, 1, 60, false);
    }

    private void validate(TradingProfile profile) {
        if (profile == null || profile.allocatedCapital() == null || profile.maxLossPerTrade() == null
                || profile.maxDailyLoss() == null || profile.allocatedCapital().signum() <= 0
                || profile.maxLossPerTrade().signum() <= 0 || profile.maxDailyLoss().signum() <= 0
                || profile.maxTradesPerDay() <= 0 || profile.maxOpenPositions() <= 0 || profile.cooldownSeconds() < 0) {
            throw new IllegalArgumentException("Capital, loss limits and position limits must be positive");
        }
        if (profile.mode() == TradingMode.LIVE && !liveExecutionProperties.isEnabled()) {
            throw new IllegalArgumentException("LIVE mode requires LIVE_EXECUTION_ENABLED=true and an explicit application restart");
        }
        if (profile.mode() == TradingMode.LIVE && !kiteProperties.isOrderRoutingEnabled()) {
            throw new IllegalArgumentException("LIVE mode requires KITE_ORDER_ROUTING_ENABLED=true and an explicit application restart");
        }
    }
}
