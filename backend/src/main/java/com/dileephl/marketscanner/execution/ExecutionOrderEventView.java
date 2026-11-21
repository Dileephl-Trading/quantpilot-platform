package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExecutionOrderEventView(String orderId, String status, long filledQuantity,
                                      BigDecimal averagePrice, String message, OffsetDateTime observedAt) { }
