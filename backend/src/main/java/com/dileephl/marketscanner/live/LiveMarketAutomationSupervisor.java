package com.dileephl.marketscanner.live;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.integration.marketdata.LiveFeedStatus;
import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Server-owned monitor lifecycle.  The browser is only an observer: expiring
 * its JSESSIONID must not stop this supervisor or the order safeguards.
 */
@Service
public class LiveMarketAutomationSupervisor {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private final LiveMarketService liveMarket;
    private final KiteConnectProperties kite;
    private final KiteAuthService auth;
    private final MarketSessionService sessions;
    private final AuditTrailService audit;
    private String lastFailure;
    private LocalDate lastStartedDate;
    private LocalDate defaultsPreparedDate;
    private LocalDate defaultsClearedDate;

    public LiveMarketAutomationSupervisor(LiveMarketService liveMarket, KiteConnectProperties kite,
                                          KiteAuthService auth, MarketSessionService sessions,
                                          AuditTrailService audit) {
        this.liveMarket = liveMarket;
        this.kite = kite;
        this.auth = auth;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${scanner.zerodha.automation-supervisor-ms:15000}",
            initialDelayString = "${scanner.zerodha.automation-supervisor-initial-delay-ms:5000}")
    public synchronized void supervise() {
        if (!kite.isMarketDataEnabled()) return;

        LiveFeedStatus status = liveMarket.status();
        if (!sessions.isOpenNow()) {
            int disabled = liveMarket.endSession();
            if (!today().equals(defaultsClearedDate)) {
                defaultsClearedDate = today();
                defaultsPreparedDate = null;
            }
            if (!"STOPPED".equalsIgnoreCase(status.state())) {
                audit.record("LIVE_MONITOR_STOPPED_SESSION_BOUNDARY", "LIVE_MONITOR", "GLOBAL",
                        sessions.status().reason());
            }
            if (disabled > 0) {
                audit.record("LIVE_DEFAULT_WATCHLIST_CLEARED", "LIVE_MONITOR", "GLOBAL",
                        "Disabled " + disabled + " automatically managed banking symbols for the next trading session");
            }
            return;
        }
        if (!auth.hasCurrentAccessToken()) {
            if (!"STOPPED".equalsIgnoreCase(status.state())) {
                liveMarket.stopForSessionBoundary();
                audit.record("LIVE_MONITOR_STOPPED_KITE_SESSION", "LIVE_MONITOR", "GLOBAL",
                        "Daily Kite session is missing, expired, or invalid");
            }
            return;
        }
        if (!today().equals(defaultsPreparedDate)) {
            try {
                List<LiveWatchlistView> prepared = liveMarket.ensureDefaultBankNifty();
                defaultsPreparedDate = today();
                defaultsClearedDate = null;
                audit.record("LIVE_DEFAULT_WATCHLIST_PREPARED", "LIVE_MONITOR", "GLOBAL",
                        "Prepared the default banking watchlist with " + prepared.size() + " enabled symbols");
            } catch (RuntimeException error) {
                String reason = error.getMessage() == null ? "unknown default watchlist failure" : error.getMessage();
                if (!reason.equals(lastFailure)) {
                    lastFailure = reason;
                    audit.record("LIVE_DEFAULT_WATCHLIST_BLOCKED", "LIVE_MONITOR", "GLOBAL", reason);
                }
                return;
            }
        }
        if (!liveMarket.watchlist().isEmpty()
                && "STOPPED".equalsIgnoreCase(status.state())) {
            try {
                LiveFeedStatus started = liveMarket.startAutomatically();
                if (!"STOPPED".equalsIgnoreCase(started.state()) && !today().equals(lastStartedDate)) {
                    lastStartedDate = today();
                    lastFailure = null;
                    audit.record("LIVE_MONITOR_AUTO_STARTED", "LIVE_MONITOR", "GLOBAL",
                            "Started from the server scheduler after a valid Kite session was available");
                }
            } catch (RuntimeException error) {
                String reason = error.getMessage() == null ? "unknown monitor start failure" : error.getMessage();
                if (!reason.equals(lastFailure)) {
                    lastFailure = reason;
                    audit.record("LIVE_MONITOR_AUTO_START_BLOCKED", "LIVE_MONITOR", "GLOBAL", reason);
                }
            }
        }
    }

    private LocalDate today() {
        return java.time.ZonedDateTime.now(INDIA).toLocalDate();
    }
}
