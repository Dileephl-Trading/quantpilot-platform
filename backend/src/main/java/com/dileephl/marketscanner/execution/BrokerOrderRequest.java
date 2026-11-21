package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;

public record BrokerOrderRequest(String symbol, OrderSide side, long quantity, String orderType,
                                 BigDecimal price, BigDecimal triggerPrice, String product,
                                 String exchange, String validity, BigDecimal stopPrice) { }
