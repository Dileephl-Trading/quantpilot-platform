package com.dileephl.marketscanner.journal;

import com.dileephl.marketscanner.audit.AuditTrailService;
import com.dileephl.marketscanner.config.TradingProfile;
import com.dileephl.marketscanner.config.TradingProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PaperTradeService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private final PaperTradeRepository trades;
    private final TradingProfileService profiles;
    private final AuditTrailService auditTrail;

    public PaperTradeService(PaperTradeRepository trades, TradingProfileService profiles, AuditTrailService auditTrail) {
        this.trades = trades;
        this.profiles = profiles;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public List<PaperTradeView> list(LocalDate date) {
        return trades.findByTradeDateOrderByCreatedAtDesc(date).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public DailyTradeSummary summary(LocalDate date) {
        return calculateSummary(date, profiles.current());
    }

    @Transactional(readOnly = true)
    public DailyTradeReport report(LocalDate date) {
        List<PaperTradeEntity> dailyTrades = trades.findByTradeDateOrderByCreatedAtDesc(date);
        BigDecimal gross = dailyTrades.stream().map(PaperTradeEntity::grossPnl).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal brokerage = dailyTrades.stream().map(PaperTradeEntity::brokerage).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = dailyTrades.stream().map(PaperTradeEntity::netPnl).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal slippage = dailyTrades.stream().map(PaperTradeEntity::slippageAmount).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long closed = dailyTrades.stream().filter(t -> t.status() == TradeStatus.CLOSED).count();
        long winning = dailyTrades.stream().filter(t -> t.netPnl() != null && t.netPnl().signum() > 0).count();
        long losing = dailyTrades.stream().filter(t -> t.netPnl() != null && t.netPnl().signum() < 0).count();
        BigDecimal winRate = closed == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(winning * 100.0 / closed).setScale(2, RoundingMode.HALF_UP);
        return new DailyTradeReport(date, dailyTrades.size(), closed, winning, losing, scale(gross), scale(brokerage),
                scale(net), scale(slippage), winRate, dailyTrades.stream().map(this::toView).toList());
    }

    @Transactional(readOnly = true)
    public String csv(LocalDate date) {
        DailyTradeReport report = report(date);
        StringBuilder csv = new StringBuilder();
        csv.append("trade_date,symbol,side,status,created_at,filled_at,closed_at,intended_entry,actual_entry,"
                + "slippage_per_unit,slippage_amount,stop,quantity,filled_quantity,actual_exit,gross_pnl,brokerage,net_pnl,exit_reason,strategy,notes\n");
        for (PaperTradeView t : report.trades()) {
            csv.append(row(date, t.symbol(), t.side(), t.status(), t.createdAt(), t.filledAt(), t.closedAt(),
                    t.intendedEntryPrice(), t.actualEntryPrice(), t.slippagePerUnit(), t.slippageAmount(), t.stopPrice(),
                    t.intendedQuantity(), t.filledQuantity(), t.actualExitPrice(), t.grossPnl(), t.brokerage(), t.netPnl(),
                    t.exitReason(), t.strategy(), t.notes()));
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<String> intentBlockReasons(BigDecimal entryPrice, BigDecimal stopPrice, long quantity) {
        TradingProfile profile = profiles.current();
        return newIntentBlockReasons(profile, LocalDate.now(MARKET_ZONE), null, risk(entryPrice, stopPrice, quantity));
    }

    @Transactional(readOnly = true)
    public List<String> intentBlockReasons(String symbol, BigDecimal entryPrice, BigDecimal stopPrice, long quantity) {
        TradingProfile profile = profiles.current();
        return newIntentBlockReasons(profile, LocalDate.now(MARKET_ZONE), normalizeSymbol(symbol), risk(entryPrice, stopPrice, quantity));
    }

    @Transactional
    public PaperTradeView create(CreatePaperTradeRequest request) {
        TradingProfile profile = profiles.currentForRiskDecision();
        LocalDate today = LocalDate.now(MARKET_ZONE);
        BigDecimal proposedRisk = risk(request.intendedEntryPrice(), request.stopPrice(), request.intendedQuantity());
        String symbol = normalizeSymbol(request.symbol());
        requireNewIntentAllowed(profile, today, symbol, proposedRisk);
        PaperTradeEntity trade = PaperTradeEntity.intended(UUID.randomUUID(), symbol,
                request.side(), request.intendedEntryPrice(), request.stopPrice(), request.intendedQuantity(), today,
                OffsetDateTime.now(MARKET_ZONE), blankToNull(request.strategy()), blankToNull(request.notes()));
        PaperTradeEntity saved = trades.save(trade);
        auditTrail.record("PAPER_TRADE_INTENDED", "PAPER_TRADE", saved.id().toString(),
                "symbol=" + saved.symbol() + ", side=" + saved.side() + ", intendedEntry="
                        + saved.intendedEntryPrice() + ", stop=" + saved.stopPrice() + ", quantity=" + saved.intendedQuantity());
        return toView(saved);
    }

    @Transactional
    public PaperTradeView fill(UUID id, FillPaperTradeRequest request) {
        PaperTradeEntity trade = get(id);
        TradingProfile profile = profiles.currentForRiskDecision();
        if (request.filledQuantity() > trade.intendedQuantity()) {
            throw new IllegalArgumentException("Filled quantity cannot exceed the intended quantity in Phase 1");
        }
        BigDecimal actualRisk = risk(request.actualEntryPrice(), trade.stopPrice(), request.filledQuantity());
        if (actualRisk.compareTo(profile.maxLossPerTrade()) > 0) {
            throw new IllegalArgumentException("Actual fill exceeds the configured maximum loss per trade");
        }
        DailyTradeSummary summary = calculateSummary(trade.tradeDate(), profile);
        BigDecimal intendedRisk = risk(trade.intendedEntryPrice(), trade.stopPrice(), trade.intendedQuantity());
        BigDecimal riskAfterFill = summary.realisedLoss().add(summary.activeRisk()).subtract(intendedRisk).add(actualRisk);
        if (riskAfterFill.compareTo(profile.maxDailyLoss()) > 0) {
            throw new IllegalStateException("Actual fill would exceed the remaining daily risk capacity");
        }
        if (summary.openPositions() >= profile.maxOpenPositions()) {
            throw new IllegalStateException("Maximum open positions has already been reached");
        }
        trade.recordFill(request.actualEntryPrice(), request.filledQuantity(),
                request.filledAt() == null ? OffsetDateTime.now(MARKET_ZONE) : request.filledAt());
        PaperTradeEntity saved = trades.save(trade);
        auditTrail.record("PAPER_TRADE_FILLED", "PAPER_TRADE", saved.id().toString(),
                "actualEntry=" + saved.actualEntryPrice() + ", filledQuantity=" + saved.filledQuantity());
        return toView(saved);
    }

    @Transactional
    public PaperTradeView close(UUID id, ClosePaperTradeRequest request) {
        PaperTradeEntity trade = get(id);
        profiles.currentForRiskDecision();
        trade.recordExit(request.actualExitPrice(), request.brokerage() == null ? BigDecimal.ZERO : request.brokerage(),
                request.exitReason(), request.closedAt() == null ? OffsetDateTime.now(MARKET_ZONE) : request.closedAt());
        PaperTradeEntity saved = trades.save(trade);
        auditTrail.record("PAPER_TRADE_CLOSED", "PAPER_TRADE", saved.id().toString(),
                "actualExit=" + saved.actualExitPrice() + ", grossPnl=" + saved.grossPnl()
                        + ", brokerage=" + saved.brokerage() + ", netPnl=" + saved.netPnl());
        return toView(saved);
    }

    @Transactional
    public PaperTradeView cancel(UUID id) {
        PaperTradeEntity trade = get(id);
        profiles.currentForRiskDecision();
        trade.cancel();
        PaperTradeEntity saved = trades.save(trade);
        auditTrail.record("PAPER_TRADE_CANCELLED", "PAPER_TRADE", saved.id().toString(), "No fill recorded");
        return toView(saved);
    }

    private void requireNewIntentAllowed(TradingProfile profile, LocalDate today, String symbol, BigDecimal proposedRisk) {
        List<String> reasons = newIntentBlockReasons(profile, today, symbol, proposedRisk);
        if (!reasons.isEmpty()) throw new IllegalStateException(String.join(" ", reasons));
    }

    private List<String> newIntentBlockReasons(TradingProfile profile, LocalDate today, String symbol, BigDecimal proposedRisk) {
        List<String> reasons = new ArrayList<>();
        if (profile.killSwitchEnabled()) reasons.add("Kill switch is enabled; new trade intents are blocked.");
        if (proposedRisk.signum() == 0) reasons.add("Entry price and stop price cannot be equal.");
        if (proposedRisk.compareTo(profile.maxLossPerTrade()) > 0) reasons.add("Proposed risk exceeds the configured maximum loss per trade.");
        DailyTradeSummary summary = calculateSummary(today, profile);
        if (summary.initiatedTrades() >= profile.maxTradesPerDay()) {
            reasons.add("Maximum trades per day has already been reached.");
        }
        if (summary.openPositions() >= profile.maxOpenPositions()) {
            reasons.add("Maximum open positions has already been reached.");
        }
        if (symbol != null && trades.existsByTradeDateAndSymbolAndStatusIn(today, symbol,
                EnumSet.of(TradeStatus.INTENDED, TradeStatus.OPEN))) {
            reasons.add("An intended or open trade already exists for this symbol today.");
        }
        if (profile.cooldownSeconds() > 0) {
            PaperTradeEntity latest = trades.findTopByTradeDateOrderByCreatedAtDesc(today).orElse(null);
            if (latest != null) {
                long elapsed = java.time.Duration.between(latest.createdAt(), OffsetDateTime.now(MARKET_ZONE)).getSeconds();
                if (elapsed >= 0 && elapsed < profile.cooldownSeconds()) {
                    reasons.add("Cooldown is active; wait " + (profile.cooldownSeconds() - elapsed) + " more seconds before a new intent.");
                }
            }
        }
        if (summary.dailyLimitReached() || summary.realisedLoss().add(summary.activeRisk()).add(proposedRisk)
                .compareTo(profile.maxDailyLoss()) > 0) {
            reasons.add("This intent would exceed the remaining daily risk capacity.");
        }
        return List.copyOf(reasons);
    }

    private DailyTradeSummary calculateSummary(LocalDate date, TradingProfile profile) {
        List<PaperTradeEntity> dailyTrades = trades.findByTradeDateOrderByCreatedAtDesc(date);
        List<PaperTradeEntity> activeTrades = trades.findByStatusIn(EnumSet.of(TradeStatus.INTENDED, TradeStatus.OPEN));
        BigDecimal realisedPnl = dailyTrades.stream().map(PaperTradeEntity::netPnl).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realisedLoss = dailyTrades.stream().map(PaperTradeEntity::netPnl).filter(value -> value != null)
                .filter(value -> value.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal activeRisk = activeTrades.stream().map(trade -> risk(activeEntry(trade), trade.stopPrice(), activeQuantity(trade)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long openPositions = activeTrades.stream().filter(trade -> trade.status() == TradeStatus.OPEN).count();
        BigDecimal remaining = profile.maxDailyLoss().subtract(realisedLoss).subtract(activeRisk).max(BigDecimal.ZERO);
        return new DailyTradeSummary(date, dailyTrades.size(), openPositions, scale(realisedPnl), scale(realisedLoss),
                scale(activeRisk), scale(remaining), profile.maxDailyLoss(), realisedLoss.add(activeRisk).compareTo(profile.maxDailyLoss()) >= 0);
    }

    private PaperTradeEntity get(UUID id) {
        return trades.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Paper trade was not found"));
    }

    private PaperTradeView toView(PaperTradeEntity trade) {
        return new PaperTradeView(trade.id(), trade.symbol(), trade.side(), trade.status(), trade.tradeDate(),
                trade.createdAt(), trade.filledAt(), trade.closedAt(), trade.intendedEntryPrice(), trade.stopPrice(),
                trade.intendedQuantity(), trade.actualEntryPrice(), trade.slippagePerUnit(), trade.slippageAmount(),
                trade.filledQuantity(), trade.actualExitPrice(),
                trade.grossPnl(), trade.brokerage(), trade.netPnl(), trade.exitReason(), trade.strategy(), trade.notes());
    }

    private String row(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            row.append(csvValue(values[i]));
        }
        return row.append('\n').toString();
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private BigDecimal activeEntry(PaperTradeEntity trade) {
        return trade.actualEntryPrice() == null ? trade.intendedEntryPrice() : trade.actualEntryPrice();
    }

    private long activeQuantity(PaperTradeEntity trade) {
        return trade.filledQuantity() == null ? trade.intendedQuantity() : trade.filledQuantity();
    }

    private BigDecimal risk(BigDecimal entry, BigDecimal stop, long quantity) {
        return entry.subtract(stop).abs().multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal scale(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeSymbol(String value) { return value == null ? null : value.trim().toUpperCase(); }
}
