package com.dileephl.marketscanner.marketdata;

import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
import com.dileephl.marketscanner.service.SignalEngine.Candle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketInstrumentServiceTest {
    @Test
    void importsOnlyNseCashEquitiesFromProviderMaster() {
        MarketDataProvider provider = new StubProvider();
        MarketInstrumentRepository repository = mock(MarketInstrumentRepository.class);
        when(repository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        MarketInstrumentService service = new MarketInstrumentService(provider, repository);

        InstrumentSyncResult result = service.syncNseCashEquities();

        assertEquals(501, result.savedInstruments());
        ArgumentCaptor<Iterable<MarketInstrumentEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(saved.capture());
        assertEquals(501, ((List<?>) saved.getValue()).size());
    }

    private static final class StubProvider implements MarketDataProvider {
        @Override public String providerName() { return "TEST"; }
        @Override public String instrumentMasterCsv() {
            StringBuilder csv = new StringBuilder("instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange\n");
            for (int index = 1; index <= 501; index++) {
                csv.append(index).append(",,TEST").append(index).append(",Test Ltd,0,,,0.05,1,EQ,NSE,NSE\n");
            }
            csv.append("900,,,Future,0,,,0.05,25,FUT,NFO-FUT,NFO\n");
            return csv.toString();
        }
        @Override public List<Candle> historicalCandles(long token, CandleInterval interval, OffsetDateTime from, OffsetDateTime to) { return List.of(); }
    }
}
