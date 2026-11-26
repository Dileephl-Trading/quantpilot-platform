package com.dileephl.marketscanner.execution;

import com.dileephl.marketscanner.integration.zerodha.KiteOrderExecutionProvider.BookOrder;
import java.util.List;

record StopCoverage(long open, long protectedQuantity, int stops) {
    static StopCoverage assess(BrokerPosition position, List<BookOrder> book) {
        var exits = book.stream().filter(o -> position.symbol().equalsIgnoreCase(o.state().symbol())
                && position.exchange().equalsIgnoreCase(o.exchange()) && position.product().equalsIgnoreCase(o.product())
                && o.orderType().startsWith("SL")
                && (o.state().status() == BrokerOrderStatus.OPEN || o.state().status() == BrokerOrderStatus.PARTIALLY_FILLED)
                && o.state().side() == (position.quantity() > 0 ? OrderSide.SELL : OrderSide.BUY)).toList();
        return new StopCoverage(Math.abs(position.quantity()),
                exits.stream().mapToLong(o -> o.state().quantity() - o.state().filledQuantity()).sum(), exits.size());
    }
    boolean exact() { return stops == 1 && open == protectedQuantity; }
    String describe(BrokerPosition p) {
        return p.symbol() + ": " + open + " open, " + protectedQuantity + " protected (" + stops + " working stops)";
    }
}
