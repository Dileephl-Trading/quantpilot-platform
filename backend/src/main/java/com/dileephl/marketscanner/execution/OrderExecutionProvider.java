package com.dileephl.marketscanner.execution;

public interface OrderExecutionProvider {
    BrokerOrderResponse place(BrokerOrderRequest request);
    BrokerOrderResponse status(String orderId);
    BrokerOrderResponse cancel(String orderId);
    BrokerOrderResponse modify(String orderId, BrokerOrderRequest request);
    java.util.List<BrokerOrderResponse> cancelOpenOrders();
    java.util.List<BrokerOrderResponse> listOrders();
}
