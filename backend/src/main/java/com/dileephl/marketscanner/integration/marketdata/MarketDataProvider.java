package com.dileephl.marketscanner.integration.marketdata;

import com.dileephl.marketscanner.service.SignalEngine.Candle;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Provider-neutral boundary for market data. Strategy and risk code must not know a broker's
 * HTTP URLs, tokens, or wire-format details.
 */
public interface MarketDataProvider {
    String providerName();

    /** The provider's complete instrument-master payload. Persistence and filtering stay provider-neutral. */
    String instrumentMasterCsv();

    List<Candle> historicalCandles(long instrumentToken, CandleInterval interval,
                                  OffsetDateTime from, OffsetDateTime to);
}
