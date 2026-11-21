package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import com.dileephl.marketscanner.config.TradingMode;

/** Central emergency-stop boundary shared by previews, paper intents and broker routing. */
@Service
public class SafetyService {
    private final TradingProfileService profiles;
    private final AuditTrailService audit;
    private final ObjectProvider<BrokerOrderExecutionService> execution;

    public SafetyService(TradingProfileService profiles, AuditTrailService audit, ObjectProvider<BrokerOrderExecutionService> execution) {
        this.profiles = profiles; this.audit = audit; this.execution = execution;
    }

    public SafetyState state() {
        TradingProfile profile = profiles.current();
        return new SafetyState(profile.killSwitchEnabled(),
                profile.killSwitchEnabled() ? "All new trade intents and broker submissions are blocked" : "Trading actions are not blocked");
    }

    public SafetyState enable(String reason) {
        TradingProfile updated = profiles.setKillSwitch(true);
        audit.record("KILL_SWITCH_ENABLED", "TRADING_SAFETY", "GLOBAL", reason == null || reason.isBlank() ? "manual emergency stop" : reason.trim());
        if (updated.mode() == TradingMode.LIVE) {
            try {
                BrokerOrderExecutionService orderExecution = execution.getIfAvailable();
                if (orderExecution != null) orderExecution.cancelTrackedOpenOrdersForSafety();
            }
            catch (RuntimeException error) { audit.record("KILL_SWITCH_CANCEL_FAILED", "TRADING_SAFETY", "GLOBAL", error.getMessage()); }
        }
        return new SafetyState(updated.killSwitchEnabled(), "All new trade intents and broker submissions are blocked");
    }

    public SafetyState disable(String reason) {
        TradingProfile updated = profiles.setKillSwitch(false);
        audit.record("KILL_SWITCH_DISABLED", "TRADING_SAFETY", "GLOBAL", reason == null || reason.isBlank() ? "manual resume" : reason.trim());
        return new SafetyState(updated.killSwitchEnabled(), "Trading actions are not blocked");
    }

    public record SafetyState(boolean killSwitchEnabled, String message) { }
}
