package com.dileephl.marketscanner.live;

import com.dileephl.marketscanner.api.PagedResponse;
import com.dileephl.marketscanner.ingestion.FiveMinuteCandleAggregator;
import com.dileephl.marketscanner.ingestion.MarketTick;
import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.marketdata.LiveFeedStatus;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketDataFeed;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketTick;
import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;
import com.dileephl.marketscanner.config.MarketSessionService;
import com.dileephl.marketscanner.config.ScannerProperties;
import com.dileephl.marketscanner.execution.AutomatedExecutionService;
import com.dileephl.marketscanner.marketdata.MarketInstrumentEntity;
import com.dileephl.marketscanner.marketdata.MarketInstrumentRepository;
import com.dileephl.marketscanner.marketdata.SectorClassifier;
import com.dileephl.marketscanner.service.SignalEngine;
import com.dileephl.marketscanner.service.SignalEngine.Candle;
import com.dileephl.marketscanner.service.SignalEngine.TradeSignal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Service
public class LiveMarketService {
    private static final ZoneOffset INDIA_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private final LiveMarketDataFeed liveFeed;
    private final MarketDataProvider marketDataProvider;
    private final MarketInstrumentRepository instrumentRepository;
    private final WatchlistEntryRepository watchlistRepository;
    private final LiveSignalRepository signalRepository;
    private final LiveCandleRepository candleRepository;
    private final MarketSessionService marketSessionService;
    private final SignalEngine signalEngine;
    private final FiveMinuteCandleAggregator aggregator;
    private final ScannerProperties scannerProperties;
    private final AutomatedExecutionService automatedExecution;
    private final KiteConnectProperties kiteProperties;
    private static final int MAX_PAGE_SIZE = 50;
    private final Map<Long, String> symbolsByToken = new HashMap<>();
    private final Map<String, List<Candle>> candlesBySymbol = new HashMap<>();
    private LocalDate manualStopDate;
    private final ExecutorService executionExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "quantpilot-live-execution");
        thread.setDaemon(true);
        return thread;
    });

    public LiveMarketService(LiveMarketDataFeed liveFeed, MarketDataProvider marketDataProvider,
                             MarketInstrumentRepository instrumentRepository, WatchlistEntryRepository watchlistRepository,
                             LiveSignalRepository signalRepository, LiveCandleRepository candleRepository,
                             MarketSessionService marketSessionService, SignalEngine signalEngine,
                             ScannerProperties scannerProperties, AutomatedExecutionService automatedExecution,
                             KiteConnectProperties kiteProperties) {
        this.liveFeed = liveFeed; this.marketDataProvider = marketDataProvider;
        this.instrumentRepository = instrumentRepository; this.watchlistRepository = watchlistRepository;
        this.signalRepository = signalRepository; this.candleRepository = candleRepository;
        this.marketSessionService = marketSessionService;
        this.signalEngine = signalEngine;
        this.scannerProperties = scannerProperties;
        this.automatedExecution = automatedExecution;
        this.kiteProperties = kiteProperties;
        this.aggregator = new FiveMinuteCandleAggregator(scannerProperties.getCandleInterval().candleDuration());
    }

    @Transactional
    public LiveWatchlistView addToWatchlist(long instrumentToken) {
        String provider = liveFeed.providerName();
        MarketInstrumentEntity instrument = instrumentRepository.findByProviderAndInstrumentToken(provider, instrumentToken)
                .orElseThrow(() -> new IllegalArgumentException("Select a symbol from the synced NSE instrument list"));
        WatchlistEntryEntity entry = watchlistRepository.findByProviderAndInstrumentToken(provider, instrumentToken)
                .orElseGet(() -> {
                    assertCapacityForNewEntry(provider);
                    return new WatchlistEntryEntity(provider, instrumentToken, instrument.getTradingsymbol());
                });
        entry.enable();
        if (!entry.isAutoManaged()) entry.markUserManaged();
        return LiveWatchlistView.from(watchlistRepository.save(entry));
    }

    /**
     * Ensures the default banking/Bank-Nifty watchlist is present for the
     * current trading session. These rows are session-managed and are enabled
     * at the start of each session; all active rows expire at the boundary.
     */
    @Transactional
    public List<LiveWatchlistView> ensureDefaultBankNifty() {
        String provider = liveFeed.providerName();
        expirePreviousDayUserEntries(provider);
        Set<String> defaults = SectorClassifier.symbols("BANKING");
        List<MarketInstrumentEntity> instruments = instrumentRepository
                .findByProviderAndExchangeOrderByTradingsymbolAsc(provider, "NSE");
        int matched = 0;
        for (MarketInstrumentEntity instrument : instruments) {
            if (!defaults.contains(instrument.getTradingsymbol())) continue;
            matched++;
            WatchlistEntryEntity entry = watchlistRepository
                    .findByProviderAndInstrumentToken(provider, instrument.getInstrumentToken())
                    .orElseGet(() -> {
                        assertCapacityForNewEntry(provider);
                        return new WatchlistEntryEntity(provider, instrument.getInstrumentToken(), instrument.getTradingsymbol());
                    });
            entry.markAutoManaged();
            entry.enable();
            watchlistRepository.save(entry);
        }
        if (matched == 0) {
            throw new IllegalStateException("Sync NSE cash symbols before the default Bank Nifty watchlist can be prepared");
        }
        return watchlist();
    }

    @Transactional
    public void removeFromWatchlist(long instrumentToken) {
        WatchlistEntryEntity entry = watchlistRepository.findByProviderAndInstrumentToken(liveFeed.providerName(), instrumentToken)
                .orElseThrow(() -> new IllegalArgumentException("Symbol is not present in the watchlist"));
        entry.disable();
        watchlistRepository.save(entry);
    }

    @Transactional
    public List<LiveWatchlistView> addGroup(String group) {
        Set<String> symbols = switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
            case "bank-nifty", "banking" -> SectorClassifier.symbols("BANKING");
            case "it" -> SectorClassifier.symbols("IT");
            case "auto" -> SectorClassifier.symbols("AUTO");
            case "energy" -> SectorClassifier.symbols("ENERGY");
            case "pharma" -> SectorClassifier.symbols("PHARMA");
            case "fmcg" -> SectorClassifier.symbols("FMCG");
            default -> throw new IllegalArgumentException("Unknown symbol group: " + group);
        };
        List<MarketInstrumentEntity> instruments = instrumentRepository.findByProviderAndExchangeOrderByTradingsymbolAsc(liveFeed.providerName(), "NSE");
        instruments.stream().filter(item -> symbols.contains(item.getTradingsymbol())).forEach(item -> addToWatchlist(item.getInstrumentToken()));
        return watchlist();
    }

    @Transactional(readOnly = true)
    public List<LiveWatchlistView> watchlist() {
        return currentSessionEntries().stream().map(LiveWatchlistView::from).toList();
    }

    private List<WatchlistEntryEntity> currentSessionEntries() {
        // The enabled watchlist is the current-session configuration. All rows
        // expire at the session boundary; the supervisor re-enables only the
        // default banking rows on the next valid session. Current-session
        // signal/candle queries are independently bounded by sessionStart(),
        // so yesterday's observations never leak into today's monitor view.
        return watchlistRepository.findByProviderAndEnabledTrueOrderBySymbolAsc(liveFeed.providerName());
    }

    @Scheduled(fixedDelay = 30000)
    public synchronized void stopAfterSession() {
        if (!marketSessionService.isOpenNow()) endSession();
    }

    public synchronized LiveFeedStatus start() {
        manualStopDate = null;
        return startInternal();
    }

    /** Called by the server-side supervisor; browser authentication is not involved. */
    public synchronized LiveFeedStatus startAutomatically() {
        if (manualStopDate != null && manualStopDate.equals(today())) return liveFeed.status();
        return startInternal();
    }

    /** A manual stop suppresses same-day automatic restarts, but not the next session. */
    public synchronized LiveFeedStatus stop() {
        manualStopDate = today();
        stopFeed();
        return liveFeed.status();
    }

    /** Used for session close or a missing/expired Kite token. */
    public synchronized void stopForSessionBoundary() {
        stopFeed();
    }

    /** Ends the trading session and expires every active watchlist row. */
    @Transactional
    public synchronized int endSession() {
        stopFeed();
        int disabled = 0;
        for (WatchlistEntryEntity entry : watchlistRepository
                .findByProviderAndEnabledTrueOrderBySymbolAsc(liveFeed.providerName())) {
            entry.disable();
            watchlistRepository.save(entry);
            disabled++;
        }
        return disabled;
    }

    private int expirePreviousDayUserEntries(String provider) {
        OffsetDateTime startOfToday = today().atStartOfDay().atOffset(INDIA_OFFSET);
        int expired = 0;
        for (WatchlistEntryEntity entry : watchlistRepository
                .findByProviderAndEnabledTrueAndAutoManagedFalseAndAddedAtBefore(provider, startOfToday)) {
            entry.disable();
            watchlistRepository.save(entry);
            expired++;
        }
        return expired;
    }

    private void assertCapacityForNewEntry(String provider) {
        long enabled = watchlistRepository.findByProviderAndEnabledTrueOrderBySymbolAsc(provider).size();
        int capacity = kiteProperties.getMaxLiveInstruments();
        if (enabled >= capacity) {
            throw new IllegalStateException("The live watchlist is full (maximum " + capacity + " symbols)");
        }
    }

    private synchronized LiveFeedStatus startInternal() {
        if (!marketSessionService.isOpenNow()) {
            throw new IllegalStateException("Live monitoring is available only during the NSE cash session: "
                    + marketSessionService.status().reason());
        }
        String state = liveFeed.status().state();
        if (!"STOPPED".equalsIgnoreCase(state)) return liveFeed.status();
        List<WatchlistEntryEntity> entries = currentSessionEntries();
        if (entries.isEmpty()) throw new IllegalStateException("Add at least one synced symbol to the live watchlist first");
        aggregator.reset();
        symbolsByToken.clear(); candlesBySymbol.clear();
        OffsetDateTime now = OffsetDateTime.now(INDIA_OFFSET);
        OffsetDateTime sessionStart = now.toLocalDate().atTime(LocalTime.of(9, 15)).atOffset(INDIA_OFFSET);
        for (WatchlistEntryEntity entry : entries) {
            symbolsByToken.put(entry.getInstrumentToken(), entry.getSymbol());
            List<Candle> history = marketDataProvider.historicalCandles(entry.getInstrumentToken(), scannerProperties.getCandleInterval(), sessionStart, now);
            // A provider may include the currently forming candle. Only completed buckets may seed evaluation.
            List<Candle> completedHistory = history.stream()
                    .filter(candle -> !candle.timestamp().plus(scannerProperties.getCandleInterval().candleDuration()).isAfter(now))
                    .sorted(java.util.Comparator.comparing(Candle::timestamp))
                    .toList();
            candlesBySymbol.put(entry.getSymbol(), new ArrayList<>(completedHistory));
        }
        Set<Long> tokens = Set.copyOf(symbolsByToken.keySet());
        liveFeed.start(tokens, this::acceptTick);
        return liveFeed.status();
    }

    private void stopFeed() {
        liveFeed.stop();
        symbolsByToken.clear();
        candlesBySymbol.clear();
        aggregator.reset();
    }

    private LocalDate today() { return OffsetDateTime.now(INDIA_OFFSET).toLocalDate(); }

    @PreDestroy
    void shutdownExecutionExecutor() {
        executionExecutor.shutdownNow();
    }

    public LiveFeedStatus status() { return liveFeed.status(); }

    @Transactional(readOnly = true)
    public List<LiveSignalView> recentSignals() { return recentSignals(null, null, 25); }

    @Transactional(readOnly = true)
    public List<LiveSignalView> recentSignals(String symbol, String type, int limit) {
        String symbolFilter = symbol == null ? "" : symbol.trim();
        String typeFilter = type == null ? "" : type.trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return currentSignalsPage(symbolFilter, typeFilter, 0, safeLimit).items();
    }

    @Transactional(readOnly = true)
    public List<LiveCandleView> recentCandles() { return currentCandlesPage(0, 100).items(); }

    @Transactional(readOnly = true)
    public PagedResponse<LiveSignalView> currentSignalsPage(String symbol, String type, int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        String symbolFilter = symbol == null ? "" : symbol.trim();
        String typeFilter = type == null ? "" : type.trim();
        return PagedResponse.from(signalRepository.findSince(sessionStart(), symbolFilter, typeFilter,
                PageRequest.of(safePage, safePageSize)), LiveSignalView::from);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LiveCandleView> currentCandlesPage(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        return PagedResponse.from(candleRepository.findByGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                sessionStart(), PageRequest.of(safePage, safePageSize)), LiveCandleView::from);
    }

    @Transactional(readOnly = true)
    public List<LiveSignalView> historySignals(int days) {
        int safeDays = Math.max(1, Math.min(days, 7));
        OffsetDateTime cutoff = OffsetDateTime.now(INDIA_OFFSET).minusDays(safeDays);
        return signalRepository.findByGeneratedAtAfterOrderByGeneratedAtDesc(cutoff).stream()
                .map(LiveSignalView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LiveCandleView> historyCandles(int days) {
        int safeDays = Math.max(1, Math.min(days, 7));
        OffsetDateTime cutoff = OffsetDateTime.now(INDIA_OFFSET).minusDays(safeDays);
        return candleRepository.findByGeneratedAtAfterOrderByGeneratedAtDesc(cutoff).stream()
                .map(LiveCandleView::from).toList();
    }

    @Transactional(readOnly = true)
    public MonitorHistoryPage historyPage(int days, int signalPage, int candlePage, int pageSize) {
        int safeDays = Math.max(1, Math.min(days, 7));
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        OffsetDateTime cutoff = OffsetDateTime.now(INDIA_OFFSET).minusDays(safeDays);
        return new MonitorHistoryPage(safeDays,
                PagedResponse.from(signalRepository.findSince(cutoff, "", "",
                        PageRequest.of(Math.max(0, signalPage), safePageSize)), LiveSignalView::from),
                PagedResponse.from(candleRepository.findByGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
                        cutoff, PageRequest.of(Math.max(0, candlePage), safePageSize)), LiveCandleView::from));
    }

    private OffsetDateTime sessionStart() {
        return OffsetDateTime.now(INDIA_OFFSET).toLocalDate()
                .atTime(LocalTime.of(9, 15)).atOffset(INDIA_OFFSET);
    }

    public record MonitorHistoryPage(int days, PagedResponse<LiveSignalView> signals,
                                     PagedResponse<LiveCandleView> candles) { }

    private synchronized void acceptTick(LiveMarketTick tick) {
        if (!marketSessionService.isOpen(tick.timestamp())) return;
        String symbol = symbolsByToken.get(tick.instrumentToken());
        if (symbol == null) return;
        aggregator.accept(new MarketTick(symbol, tick.timestamp(), tick.price(), tick.quantity())).ifPresent(candle -> onCompletedCandle(tick.instrumentToken(), symbol, candle));
    }

    private void onCompletedCandle(long instrumentToken, String symbol, Candle candle) {
        List<Candle> history = candlesBySymbol.computeIfAbsent(symbol, ignored -> new ArrayList<>());
        if (history.stream().anyMatch(existing -> existing.timestamp().isEqual(candle.timestamp()))) return;
        history.add(candle);
        if (history.size() > 90) history.removeFirst();
        if (candleRepository.existsByProviderAndInstrumentTokenAndCandleStart(liveFeed.providerName(), instrumentToken, candle.timestamp())) return;
        TradeSignal signal = scannerProperties.isEnabled() && history.size() >= scannerProperties.getMinimumCandles()
                ? signalEngine.evaluate(symbol, List.copyOf(history)) : null;
        String evaluationType = !scannerProperties.isEnabled() ? "NO_TRADE" : signal == null ? "WARMING_UP" : signal.type().name();
        String reasons = !scannerProperties.isEnabled() ? "Scanner strategy is disabled" : signal == null
                ? "Waiting for " + scannerProperties.getMinimumCandles() + " completed candles" : String.join(" · ", signal.reasons());
        candleRepository.save(new LiveCandleEntity(liveFeed.providerName(), instrumentToken, symbol, candle,
                evaluationType, signal == null ? null : signal.referencePrice(), signal == null ? null : signal.vwap(),
                signal == null ? null : signal.invalidationReference(), signal == null ? null : signal.openingRangeHigh(),
                signal == null ? null : signal.openingRangeLow(), signal == null ? null : signal.averageVolume(),
                signal == null ? null : signal.volumeThreshold(), reasons));
        if (signal != null && signal.type() != SignalEngine.SignalType.NO_TRADE
                && !signalRepository.existsByProviderAndInstrumentTokenAndCandleStart(liveFeed.providerName(), instrumentToken, candle.timestamp())) {
            signalRepository.save(new LiveSignalEntity(liveFeed.providerName(), instrumentToken, symbol, candle.timestamp(),
                    signal.type().name(), signal.referencePrice(), signal.vwap(), signal.invalidationReference(),
                    signal.openingRangeHigh(), signal.openingRangeLow(), signal.averageVolume(), signal.volumeThreshold(), reasons));
            // Never block Kite's WebSocket callback while the broker HTTP API
            // and its second risk layer are running. Candle ingestion remains
            // ordered; order submission is serialized on this worker.
            executionExecutor.submit(() -> automatedExecution.onCompletedSignal(liveFeed.providerName(), instrumentToken,
                    symbol, candle, signal, scannerProperties.getCandleInterval()));
        }
    }
}
