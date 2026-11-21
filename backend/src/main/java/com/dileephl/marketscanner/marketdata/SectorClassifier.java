package com.dileephl.marketscanner.marketdata;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative local classification used for watchlist grouping; it is not an index constituent feed. */
public final class SectorClassifier {
    private static final Map<String, Set<String>> SECTORS = Map.of(
            "BANKING", Set.of("HDFCBANK", "ICICIBANK", "SBIN", "AXISBANK", "KOTAKBANK", "INDUSINDBK", "BANKBARODA", "PNB", "FEDERALBNK", "IDFCFIRSTB", "AUBANK", "BANDHANBNK"),
            "IT", Set.of("TCS", "INFY", "WIPRO", "HCLTECH", "TECHM", "LTIM", "MPHASIS", "COFORGE", "PERSISTENT"),
            "AUTO", Set.of("TATAMOTORS", "M&M", "MARUTI", "HEROMOTOCO", "EICHERMOT", "BAJAJ-AUTO", "TVSMOTOR"),
            "ENERGY", Set.of("RELIANCE", "ONGC", "NTPC", "POWERGRID", "TATAPOWER", "ADANIENT", "ADANIGREEN"),
            "PHARMA", Set.of("SUNPHARMA", "DRREDDY", "CIPLA", "AUROPHARMA", "DIVISLAB", "BIOCON", "LUPIN"),
            "FMCG", Set.of("HINDUNILVR", "ITC", "NESTLEIND", "BRITANNIA", "TATACONSUM", "DABUR", "MARICO")
    );

    private SectorClassifier() { }

    public static String classify(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return SECTORS.entrySet().stream().filter(entry -> entry.getValue().contains(normalized))
                .map(Map.Entry::getKey).findFirst().orElse("OTHER");
    }

    public static Set<String> symbols(String sector) {
        return SECTORS.getOrDefault(sector == null ? "" : sector.trim().toUpperCase(Locale.ROOT), Set.of());
    }

    public static Set<String> sectors() { return SECTORS.keySet(); }
}
