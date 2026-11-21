package com.dileephl.marketscanner.marketdata;

import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MarketInstrumentService {
    private static final String SCOPE = "NSE cash equities (EQ)";
    private final MarketDataProvider marketDataProvider;
    private final MarketInstrumentRepository repository;

    public MarketInstrumentService(MarketDataProvider marketDataProvider, MarketInstrumentRepository repository) {
        this.marketDataProvider = marketDataProvider;
        this.repository = repository;
    }

    @Transactional
    public InstrumentSyncResult syncNseCashEquities() {
        String csv = marketDataProvider.instrumentMasterCsv();
        OffsetDateTime syncedAt = OffsetDateTime.now();
        List<MarketInstrumentEntity> instruments = parseNseCashEquities(csv, syncedAt);
        if (instruments.size() < 500) {
            throw new IllegalStateException("Instrument import returned too few NSE cash equities; existing data was not changed");
        }
        repository.saveAll(instruments);
        return new InstrumentSyncResult(marketDataProvider.providerName(), SCOPE, instruments.size(), syncedAt);
    }

    @Transactional(readOnly = true)
    public List<MarketInstrumentView> searchNseCashEquities(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) return List.of();
        return repository.findTop30ByProviderAndExchangeAndTradingsymbolContainingIgnoreCaseOrderByTradingsymbolAsc(
                        marketDataProvider.providerName(), "NSE", cleanQuery)
                .stream().map(MarketInstrumentView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MarketInstrumentView> filterNseCashEquities(String query, String sector) {
        String cleanQuery = query == null ? "" : query.trim().toUpperCase(Locale.ROOT);
        String cleanSector = sector == null ? "" : sector.trim().toUpperCase(Locale.ROOT);
        return repository.findByProviderAndExchangeOrderByTradingsymbolAsc(marketDataProvider.providerName(), "NSE").stream()
                .filter(item -> cleanQuery.isEmpty() || item.getTradingsymbol().toUpperCase(Locale.ROOT).contains(cleanQuery))
                .filter(item -> cleanSector.isEmpty() || SectorClassifier.classify(item.getTradingsymbol()).equals(cleanSector))
                .limit(100).map(MarketInstrumentView::from).toList();
    }

    public Map<String, Set<String>> predefinedGroups() {
        return Map.of("bank-nifty", SectorClassifier.symbols("BANKING"), "banking", SectorClassifier.symbols("BANKING"),
                "it", SectorClassifier.symbols("IT"), "auto", SectorClassifier.symbols("AUTO"),
                "energy", SectorClassifier.symbols("ENERGY"), "pharma", SectorClassifier.symbols("PHARMA"),
                "fmcg", SectorClassifier.symbols("FMCG"));
    }

    private List<MarketInstrumentEntity> parseNseCashEquities(String csv, OffsetDateTime syncedAt) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(csv))) {
            List<MarketInstrumentEntity> result = new ArrayList<>();
            for (CSVRecord row : parser) {
                if (!"NSE".equals(row.get("exchange")) || !"EQ".equals(row.get("instrument_type"))) continue;
                result.add(new MarketInstrumentEntity(marketDataProvider.providerName(), longValue(row, "instrument_token"),
                        row.get("exchange"), row.get("segment"), row.get("tradingsymbol"), row.get("name"),
                        row.get("instrument_type"), decimalValue(row, "tick_size"), intValue(row, "lot_size"), syncedAt));
            }
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("Unable to parse Kite instrument-master response", error);
        }
    }

    private long longValue(CSVRecord row, String column) { return Long.parseLong(row.get(column)); }
    private int intValue(CSVRecord row, String column) { return Integer.parseInt(row.get(column)); }
    private BigDecimal decimalValue(CSVRecord row, String column) { return new BigDecimal(row.get(column)); }
}
