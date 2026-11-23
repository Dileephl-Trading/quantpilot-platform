package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.config.TradingMode;
import com.dileephl.marketscanner.config.TradingProfileService;
import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * End-of-day safety supervisor. It prevents late entries, cancels pending entries,
 * and repeatedly submits exits for open MIS positions during the configured window.
 */
@Service
public class SessionCloseoutService {
    private final MarketSessionService sessions;
    private final TradingProfileService profiles;
    private final KiteConnectProperties kite;
    private final KiteAuthService auth;
    private final BrokerOrderExecutionService execution;
    private final SafetyService safety;
    private final AuditTrailService audit;
    private LocalDate lastCancelDate;
    private LocalDate lastDeadlineDate;

    public SessionCloseoutService(MarketSessionService sessions, TradingProfileService profiles,
                                  KiteConnectProperties kite, KiteAuthService auth,
                                  BrokerOrderExecutionService execution, SafetyService safety,
                                  AuditTrailService audit) {
        this.sessions = sessions; this.profiles = profiles; this.kite = kite; this.auth = auth;
        this.execution = execution; this.safety = safety; this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${scanner.execution.status-poll-ms:5000}")
    public synchronized void supervise() {
        if (profiles.current().mode() != TradingMode.LIVE || !kite.isOrderRoutingEnabled()
                || !auth.hasCurrentAccessToken()) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalDate date = now.toLocalDate();
        if (sessions.isCancelPendingDue(now) && !date.equals(lastCancelDate)) {
            try {
                execution.cancelTrackedOpenOrders();
                lastCancelDate = date;
                audit.record("SESSION_PENDING_ENTRIES_CANCELLED", "TRADING_SAFETY", "GLOBAL", "cutoff=14:55");
            } catch (RuntimeException error) {
                audit.record("SESSION_PENDING_CANCEL_FAILED", "TRADING_SAFETY", "GLOBAL", error.getMessage());
            }
        }
        if (sessions.isSquareOffDue(now)) {
            try {
                execution.squareOffPositionsForSafety();
            } catch (RuntimeException error) {
                audit.record("SESSION_SQUARE_OFF_RETRY", "TRADING_SAFETY", "GLOBAL", error.getMessage());
            }
        }
        if (sessions.isHardFlatDeadline(now) && !date.equals(lastDeadlineDate)) {
            lastDeadlineDate = date;
            safety.enable("Hard flat deadline reached; live automation remains blocked until positions are reviewed");
            audit.record("SESSION_HARD_FLAT_DEADLINE_REACHED", "TRADING_SAFETY", "GLOBAL", "deadline=15:03");
        }
    }
}
