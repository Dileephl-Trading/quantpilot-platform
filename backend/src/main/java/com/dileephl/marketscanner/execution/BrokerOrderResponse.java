package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BrokerOrderResponse(String orderId, String symbol, OrderSide side, long quantity,
                                  long filledQuantity, BigDecimal averagePrice, BrokerOrderStatus status,
                                  String message, OffsetDateTime observedAt) { }
