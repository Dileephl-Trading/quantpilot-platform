package com.dileephl.marketscanner.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExecutionOrderView(
        String orderId,
        String symbol,
        String side,
        long quantity,
        long filledQuantity,
        BigDecimal averagePrice,
        String status,
        String orderType,
        BigDecimal price,
        BigDecimal triggerPrice,
        BigDecimal stopPrice,
        boolean protective,
        String exitRole,
        String parentOrderId,
        String product,
        String exchange,
        String validity,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
