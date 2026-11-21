package com.dileephl.marketscanner.live;

import com.dileephl.marketscanner.ingestion.FiveMinuteCandleAggregator;
import com.dileephl.marketscanner.ingestion.MarketTick;
import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.marketdata.LiveFeedStatus;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketDataFeed;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketTick;
import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
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

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

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
    private final Map<Long, String> symbolsByToken = new HashMap<>();
    private final Map<String, List<Candle>> candlesBySymbol = new HashMap<>();

    public LiveMarketService(LiveMarketDataFeed liveFeed, MarketDataProvider marketDataProvider,
                             MarketInstrumentRepository instrumentRepository, WatchlistEntryRepository watchlistRepository,
                             LiveSignalRepository signalRepository, LiveCandleRepository candleRepository,
                             MarketSessionService marketSessionService, SignalEngine signalEngine,
                             ScannerProperties scannerProperties, AutomatedExecutionService automatedExecution) {
        this.liveFeed = liveFeed; this.marketDataProvider = marketDataProvider;
        this.instrumentRepository = instrumentRepository; this.watchlistRepository = watchlistRepository;
        this.signalRepository = signalRepository; this.candleRepository = candleRepository;
        this.marketSessionService = marketSessionService;
        this.signalEngine = signalEngine;
        this.scannerProperties = scannerProperties;
        this.automatedExecution = automatedExecution;
        this.aggregator = new FiveMinuteCandleAggregator(scannerProperties.getCandleInterval().candleDuration());
    }

    @Transactional
    public LiveWatchlistView addToWatchlist(long instrumentToken) {
        String provider = liveFeed.providerName();
        MarketInstrumentEntity instrument = instrumentRepository.findByProviderAndInstrumentToken(provider, instrumentToken)
                .orElseThrow(() -> new IllegalArgumentException("Select a symbol from the synced NSE instrument list"));
        WatchlistEntryEntity entry = watchlistRepository.findByProviderAndInstrumentToken(provider, instrumentToken)
                .orElseGet(() -> new WatchlistEntryEntity(provider, instrumentToken, instrument.getTradingsymbol()));
        entry.enable();
        return LiveWatchlistView.from(watchlistRepository.save(entry));
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
        Set<String> symbols = symbolsForGroup(group);
        List<MarketInstrumentEntity> instruments = instrumentRepository.findByProviderAndExchangeOrderByTradingsymbolAsc(liveFeed.providerName(), "NSE");
        instruments.stream().filter(item -> symbols.contains(item.getTradingsymbol())).forEach(item -> addToWatchlist(item.getInstrumentToken()));
        return watchlist();
    }

    @Transactional
    public List<LiveWatchlistView> removeGroup(String group) {
        if (!"STOPPED".equalsIgnoreCase(liveFeed.status().state())) {
            throw new IllegalStateException("Stop the live monitor before removing a symbol group");
        }
        Set<String> symbols = symbolsForGroup(group);
        watchlistRepository.findByProviderAndEnabledTrueOrderBySymbolAsc(liveFeed.providerName()).stream()
                .filter(entry -> symbols.contains(entry.getSymbol()))
                .forEach(entry -> {
                    entry.disable();
                    watchlistRepository.save(entry);
                });
        return watchlist();
    }

    private Set<String> symbolsForGroup(String group) {
        return switch (group == null ? "" : group.toLowerCase(Locale.ROOT)) {
            case "bank-nifty", "banking" -> SectorClassifier.symbols("BANKING");
            case "it" -> SectorClassifier.symbols("IT");
            case "auto" -> SectorClassifier.symbols("AUTO");
            case "energy" -> SectorClassifier.symbols("ENERGY");
            case "pharma" -> SectorClassifier.symbols("PHARMA");
            case "fmcg" -> SectorClassifier.symbols("FMCG");
            default -> throw new IllegalArgumentException("Unknown symbol group: " + group);
        };
    }

    @Transactional(readOnly = true)
    public List<LiveWatchlistView> watchlist() {
        return watchlistRepository.findByProviderAndEnabledTrueOrderBySymbolAsc(liveFeed.providerName()).stream()
                .map(LiveWatchlistView::from).toList();
    }

    public synchronized LiveFeedStatus start() {
        if (!marketSessionService.isOpenNow()) {
            throw new IllegalStateException("Live monitoring is available only during the NSE cash session: "
                    + marketSessionService.status().reason());
        }
        List<WatchlistEntryEntity> entries = watchlistRepository.findByProviderAndEnabledTrueOrderBySymbolAsc(liveFeed.providerName());
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

    public synchronized LiveFeedStatus stop() { liveFeed.stop(); return liveFeed.status(); }
    public LiveFeedStatus status() { return liveFeed.status(); }

    @Transactional(readOnly = true)
    public List<LiveSignalView> recentSignals() { return recentSignals(null, null, 25); }

    @Transactional(readOnly = true)
    public List<LiveSignalView> recentSignals(String symbol, String type, int limit) {
        String symbolFilter = symbol == null ? "" : symbol.trim();
        String typeFilter = type == null ? "" : type.trim();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return signalRepository.findTop200ByOrderByGeneratedAtDesc().stream()
                .filter(item -> symbolFilter.isEmpty() || item.getSymbol().equalsIgnoreCase(symbolFilter))
                .filter(item -> typeFilter.isEmpty() || item.getSignalType().equalsIgnoreCase(typeFilter))
                .limit(safeLimit).map(LiveSignalView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LiveCandleView> recentCandles() { return candleRepository.findTop100ByOrderByCandleStartDesc().stream().map(LiveCandleView::from).toList(); }

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
            automatedExecution.onCompletedSignal(liveFeed.providerName(), instrumentToken, symbol, candle, signal,
                    scannerProperties.getCandleInterval());
        }
    }
}
