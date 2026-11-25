package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.config.AutomatedExecutionProperties;
import com.dileephl.marketscanner.config.LiveExecutionProperties;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class LiveExecutionPreflightService {
    private static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);

    private final TradingProfileService profiles;
    private final LiveExecutionProperties liveProperties;
    private final AutomatedExecutionProperties automationProperties;
    private final KiteConnectProperties kiteProperties;
    private final KiteAuthService auth;
    private final MarketSessionService sessions;
    private final SafetyService safety;
    private final AutomationSafetyStateRepository safetyStates;

    public LiveExecutionPreflightService(TradingProfileService profiles,
                                         LiveExecutionProperties liveProperties,
                                         AutomatedExecutionProperties automationProperties,
                                         KiteConnectProperties kiteProperties,
                                         KiteAuthService auth,
                                         MarketSessionService sessions,
                                         SafetyService safety,
                                         AutomationSafetyStateRepository safetyStates) {
        this.profiles = profiles;
        this.liveProperties = liveProperties;
        this.automationProperties = automationProperties;
        this.kiteProperties = kiteProperties;
        this.auth = auth;
        this.sessions = sessions;
        this.safety = safety;
        this.safetyStates = safetyStates;
    }

    public Preflight run() {
        TradingProfile profile = profiles.current();
        OffsetDateTime now = OffsetDateTime.now(INDIA);
        boolean marketOpen = sessions.isOpen(now);
        boolean newEntryWindow = sessions.isNewEntryAllowed(now);
        boolean uncertain = safetyStates.findById(1).map(AutomationSafetyStateEntity::uncertainBrokerState).orElse(false);

        List<Check> checks = List.of(
                check("liveExecutionEnabled", liveProperties.isEnabled(), "LIVE_EXECUTION_ENABLED must be true"),
                check("automatedExecutionEnabled", automationProperties.isEnabled(), "AUTOMATED_EXECUTION_ENABLED must be true"),
                check("profileLive", profile.mode() == TradingMode.LIVE, "Trading profile mode must be LIVE"),
                check("kiteMarketDataEnabled", kiteProperties.isMarketDataEnabled(), "KITE_MARKET_DATA_ENABLED must be true"),
                check("kiteRoutingEnabled", kiteProperties.isOrderRoutingEnabled(), "KITE_ORDER_ROUTING_ENABLED must be true"),
                check("kiteCredentialsConfigured", kiteProperties.hasApiKey() && kiteProperties.hasApiSecret(), "Kite API key and secret must be configured"),
                check("kiteSessionConnected", auth.hasCurrentAccessToken(), "Complete today's Kite login"),
                check("marketSessionOpen", marketOpen, "NSE cash session is closed"),
                check("newEntryWindow", newEntryWindow, "New entries are allowed only from 09:30 until 14:45"),
                check("killSwitchDisabled", !safety.state().killSwitchEnabled(), "Kill switch is enabled"),
                check("brokerStateCertain", !uncertain, "Broker state is uncertain; reconcile before resuming")
        );
        return new Preflight(checks.stream().allMatch(Check::passed), checks);
    }

    private Check check(String name, boolean passed, String failureReason) {
        return new Check(name, passed, passed ? "ok" : failureReason);
    }

    public record Preflight(boolean ready, List<Check> checks) { }
    public record Check(String name, boolean passed, String detail) { }
}
