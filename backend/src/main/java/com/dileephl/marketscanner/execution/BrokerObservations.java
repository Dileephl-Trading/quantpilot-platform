package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider.BookOrder;
import java.util.List;

/** Broker capabilities available to reconciliation. */
public interface BrokerObservations {
    List<BookOrder> orderBook();
    List<BrokerPosition> positions();
    List<BrokerDayPosition> dayPositions();
    List<BrokerTrade> trades(String orderId);
}
