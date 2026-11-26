package com.dileephl.marketscanner.execution;

public interface OrderExecutionProvider {
    BrokerOrderResponse place(BrokerOrderRequest request);
    BrokerOrderResponse status(String orderId);
    BrokerOrderResponse cancel(String orderId);
    BrokerOrderResponse modify(String orderId, BrokerOrderRequest request);
    java.util.List<BrokerOrderResponse> cancelOpenOrders();
    java.util.List<BrokerPosition> positions();
    java.util.List<BrokerOrderResponse> squareOffPositions();

    /**
     * Returns the fills recorded by the broker for one order.  Providers that
     * do not expose a fill endpoint may return an empty list; reconciliation
     * will keep the local order status authoritative until fills are available.
     */
    default java.util.List<BrokerTrade> trades(String orderId) { return java.util.List.of(); }

    /**
     * Returns the broker's day-level realised/unrealised position snapshot.
     * The realised value is deliberately kept separate from locally calculated
     * fill P&L because a broker snapshot can aggregate multiple orders for the
     * same symbol.
     */
    default java.util.List<BrokerDayPosition> dayPositions() { return java.util.List.of(); }
}
