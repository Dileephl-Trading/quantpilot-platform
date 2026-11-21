package com.dileephl.marketscanner.integration.marketdata;

import java.util.Set;
import java.util.function.Consumer;

/** Provider-neutral, read-only streaming boundary. It has no broker execution operations. */
public interface LiveMarketDataFeed {
    String providerName();
    void start(Set<Long> instrumentTokens, Consumer<LiveMarketTick> tickConsumer);
    void stop();
    LiveFeedStatus status();
}
